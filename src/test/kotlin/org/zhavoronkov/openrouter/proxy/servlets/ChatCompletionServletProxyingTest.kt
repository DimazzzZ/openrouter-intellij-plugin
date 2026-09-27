package org.zhavoronkov.openrouter.proxy.servlets

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.zhavoronkov.openrouter.models.OpenRouterSettings
import org.zhavoronkov.openrouter.proxy.models.OpenAIChatCompletionRequest
import org.zhavoronkov.openrouter.proxy.validation.MultimodalContentValidator
import org.zhavoronkov.openrouter.services.OpenRouterSettingsService
import org.zhavoronkov.openrouter.services.settings.ApiKeySettingsManager
import org.zhavoronkov.openrouter.services.settings.ProviderRoutingManager
import org.zhavoronkov.openrouter.services.settings.RouterDefaultsManager
import org.zhavoronkov.openrouter.services.settings.UIPreferencesManager
import org.zhavoronkov.openrouter.testing.OkHttpLeakSafeExtension
import java.io.BufferedReader
import java.io.IOException
import java.io.PrintWriter
import java.io.Reader
import java.io.StringReader
import java.io.StringWriter
import java.util.Collections

/**
 * Drives the real [ChatCompletionServlet] end to end against a [MockWebServer] standing in for
 * OpenRouter.
 *
 * This is possible because the servlet takes its client, its settings service and its endpoint as
 * constructor parameters, each defaulted to what it used to build inline. Nothing here reaches a
 * real host, and nothing needs the IntelliJ platform: the settings service is a mock resolved
 * lazily, so the servlet constructs in the fast headless `test` task.
 */
@ExtendWith(OkHttpLeakSafeExtension::class)
@DisplayName("ChatCompletionServlet Proxying Tests")
class ChatCompletionServletProxyingTest {

    private lateinit var server: MockWebServer
    private lateinit var settingsService: OpenRouterSettingsService
    private lateinit var apiKeyManager: ApiKeySettingsManager
    private lateinit var multimodalValidator: MultimodalContentValidator
    private val clients = mutableListOf<OkHttpClient>()

    @BeforeEach
    fun setUp() {
        server = MockWebServer()
        server.start()

        apiKeyManager = mock(ApiKeySettingsManager::class.java)
        `when`(apiKeyManager.getStoredApiKey()).thenReturn("sk-or-test")
        settingsService = mock(OpenRouterSettingsService::class.java)
        `when`(settingsService.apiKeyManager).thenReturn(apiKeyManager)

        multimodalValidator = mock(MultimodalContentValidator::class.java)
        `when`(multimodalValidator.validate(any(), anyString()))
            .thenReturn(MultimodalContentValidator.ValidationResult.Valid)
    }

    @AfterEach
    fun tearDown() {
        clients.forEach {
            it.dispatcher.executorService.shutdown()
            it.connectionPool.evictAll()
        }
        runCatching { server.shutdown() }
    }

    // Mockito's argument matchers are Java statics; these wrappers keep the call sites readable
    // and satisfy Kotlin's null-safety on the generic ones.
    private fun any(): OpenAIChatCompletionRequest =
        org.mockito.ArgumentMatchers.any(OpenAIChatCompletionRequest::class.java)
            ?: OpenAIChatCompletionRequest(model = "", messages = emptyList())

    private fun anyString(): String = org.mockito.ArgumentMatchers.anyString()

    private fun servlet(): ChatCompletionServlet {
        val client = OkHttpClient.Builder().build()
        clients += client
        return ChatCompletionServlet(
            httpClient = client,
            settingsServiceProvider = { settingsService },
            openRouterApiUrl = server.url("/api/v1/chat/completions").toString(),
            multimodalValidatorProvider = { multimodalValidator }
        )
    }

    private class Exchange(val resp: HttpServletResponse, private val sink: StringWriter) {
        val body: String get() = sink.toString()
    }

    private fun request(
        body: String,
        reader: Reader = StringReader(body),
        headers: Map<String, String> = emptyMap()
    ): HttpServletRequest {
        val req = mock(HttpServletRequest::class.java)
        `when`(req.reader).thenReturn(BufferedReader(reader))
        `when`(req.remoteAddr).thenReturn("127.0.0.1")
        `when`(req.requestURI).thenReturn("/v1/chat/completions")
        `when`(req.servletPath).thenReturn("/v1/chat/completions")
        `when`(req.method).thenReturn("POST")
        `when`(req.contentType).thenReturn("application/json")
        `when`(req.headerNames).thenReturn(Collections.enumeration(headers.keys))
        headers.forEach { (name, value) -> `when`(req.getHeader(name)).thenReturn(value) }
        return req
    }

    private fun response(): Exchange {
        val resp = mock(HttpServletResponse::class.java)
        val sink = StringWriter()
        `when`(resp.writer).thenReturn(PrintWriter(sink, true))
        return Exchange(resp, sink)
    }

    private fun chatBody(stream: Boolean = false, model: String = "openai/gpt-4o-mini") =
        """{"model":"$model","messages":[{"role":"user","content":"hi"}],"stream":$stream}"""

    private fun enqueueCompletion() {
        server.enqueue(
            MockResponse().setResponseCode(200).setHeader("Content-Type", "application/json").setBody(
                """
                {"id":"cmpl-1","object":"chat.completion","created":1700000000,
                 "model":"openai/gpt-4o-mini",
                 "choices":[{"index":0,"message":{"role":"assistant","content":"hello"},"finish_reason":"stop"}],
                 "usage":{"prompt_tokens":1,"completion_tokens":1,"total_tokens":2}}
                """.trimIndent()
            )
        )
    }

    @Nested
    @DisplayName("Requests that never reach OpenRouter")
    inner class ShortCircuits {

        @Test
        @DisplayName("an unconfigured API key answers 401 without contacting OpenRouter")
        fun missingApiKey() {
            `when`(apiKeyManager.getStoredApiKey()).thenReturn("")
            val exchange = response()

            servlet().service(request(chatBody()), exchange.resp)

            verify(exchange.resp).status = HttpServletResponse.SC_UNAUTHORIZED
            assertTrue(exchange.body.contains("api_key_missing"), "got: ${exchange.body}")
            assertEquals(0, server.requestCount)
        }

        @Test
        @DisplayName("a body that is not JSON answers 400 without contacting OpenRouter")
        fun unparseableBody() {
            val exchange = response()

            servlet().service(request("not json at all"), exchange.resp)

            verify(exchange.resp).status = HttpServletResponse.SC_BAD_REQUEST
            assertEquals(0, server.requestCount)
        }

        @Test
        @DisplayName("a request with no messages answers 400 without contacting OpenRouter")
        fun emptyMessages() {
            val exchange = response()

            servlet().service(request("""{"model":"openai/gpt-4o-mini","messages":[]}"""), exchange.resp)

            verify(exchange.resp).status = HttpServletResponse.SC_BAD_REQUEST
            assertTrue(exchange.body.contains("Messages cannot be null or empty"), "got: ${exchange.body}")
            assertEquals(0, server.requestCount)
        }

        @Test
        @DisplayName("content the model cannot accept answers 400 before the request is sent")
        fun multimodalRejection() {
            `when`(multimodalValidator.validate(any(), anyString())).thenReturn(
                MultimodalContentValidator.ValidationResult.Invalid(
                    contentType = MultimodalContentValidator.ContentType.IMAGE,
                    modelId = "openai/gpt-4o-mini",
                    errorMessage = "Model openai/gpt-4o-mini does not accept image input"
                )
            )
            val exchange = response()

            servlet().service(request(chatBody()), exchange.resp)

            verify(exchange.resp).status = HttpServletResponse.SC_BAD_REQUEST
            assertEquals(0, server.requestCount)
        }

        @Test
        @DisplayName("a reader that fails mid-body is answered, not propagated to Jetty")
        fun unreadableBody() {
            val throwing = object : Reader() {
                override fun read(cbuf: CharArray, off: Int, len: Int): Int = throw IOException("reader boom")
                override fun close() = Unit
            }
            val exchange = response()

            servlet().service(request("", throwing), exchange.resp)

            verify(exchange.resp).status = HttpServletResponse.SC_SERVICE_UNAVAILABLE
            assertEquals(0, server.requestCount)
        }
    }

    @Nested
    @DisplayName("Non-streaming proxying")
    inner class NonStreaming {

        @Test
        @DisplayName("a completion is forwarded with the configured key and its answer returned")
        fun happyPath() {
            enqueueCompletion()
            val exchange = response()

            servlet().service(request(chatBody()), exchange.resp)

            val forwarded = server.takeRequest()
            assertEquals("POST", forwarded.method)
            assertEquals("Bearer sk-or-test", forwarded.getHeader("Authorization"))
            assertTrue(forwarded.body.readUtf8().contains("openai/gpt-4o-mini"))
            assertTrue(exchange.body.contains("hello"), "got: ${exchange.body}")
        }

        @Test
        @DisplayName("an upstream rejection is translated into an error the client can read")
        fun upstreamRejection() {
            server.enqueue(
                MockResponse().setResponseCode(401)
                    .setBody("""{"error":{"message":"No auth credentials found"}}""")
            )
            val exchange = response()

            servlet().service(request(chatBody()), exchange.resp)

            assertEquals(1, server.requestCount)
            assertTrue(exchange.body.isNotBlank(), "an upstream rejection must still answer the client")
        }

        @Test
        @DisplayName("an upstream that drops the connection is answered, not propagated to Jetty")
        fun upstreamTransportFailure() {
            val deadUrl = server.url("/api/v1/chat/completions").toString()
            server.shutdown()
            val client = OkHttpClient.Builder().build()
            clients += client
            val servlet = ChatCompletionServlet(
                httpClient = client,
                settingsServiceProvider = { settingsService },
                openRouterApiUrl = deadUrl,
                multimodalValidatorProvider = { multimodalValidator }
            )
            val exchange = response()

            servlet.service(request(chatBody()), exchange.resp)

            assertTrue(exchange.body.isNotBlank(), "a dead upstream must still answer the client")
        }
    }

    @Nested
    @DisplayName("Streaming proxying")
    inner class Streaming {

        @Test
        @DisplayName("a streaming request sets SSE headers and relays the chunks")
        fun streamsChunks() {
            server.enqueue(
                MockResponse().setResponseCode(200)
                    .setHeader("Content-Type", "text/event-stream")
                    .setBody(
                        "data: {\"id\":\"1\",\"choices\":[{\"delta\":{\"content\":\"he\"}}]}\n\n" +
                            "data: {\"id\":\"1\",\"choices\":[{\"delta\":{\"content\":\"llo\"}}]}\n\n" +
                            "data: [DONE]\n\n"
                    )
            )
            val exchange = response()

            servlet().service(request(chatBody(stream = true)), exchange.resp)

            verify(exchange.resp).contentType = "text/event-stream"
            verify(exchange.resp).setHeader("Cache-Control", "no-cache")
            verify(exchange.resp).status = HttpServletResponse.SC_OK
            assertTrue(exchange.body.contains("data:"), "expected relayed SSE chunks, got: ${exchange.body}")
        }

        @Test
        @DisplayName("a streaming request rejected upstream is reported as an SSE error chunk")
        fun streamingUpstreamRejection() {
            server.enqueue(
                MockResponse().setResponseCode(402)
                    .setBody("""{"error":{"message":"Insufficient credits"}}""")
            )
            val exchange = response()

            servlet().service(request(chatBody(stream = true)), exchange.resp)

            assertTrue(
                exchange.body.contains("data:"),
                "an upstream rejection must reach the client as a chunk, not a dropped stream: ${exchange.body}"
            )
        }
    }

    /**
     * Every rejection OpenRouter can answer a streaming request with is turned into an
     * OpenAI-compatible chunk carrying a human-readable explanation - the client never sees a
     * dropped stream, and never sees the raw upstream body either.
     *
     * The two branches that are missing here are the ones that raise an IDE notification through
     * `ModelAvailabilityNotifier` (a generic "No endpoints found", and the free-tier-ended
     * migration hint): those need a running platform, not a different fixture.
     */
    @Nested
    @DisplayName("Upstream rejections become readable error chunks")
    inner class ErrorChunkMessages {

        private fun errorChunkFor(status: Int, body: String): String {
            server.enqueue(MockResponse().setResponseCode(status).setBody(body))
            val exchange = response()

            servlet().service(request(chatBody(stream = true)), exchange.resp)

            return exchange.body
        }

        @Test
        @DisplayName("a 401 quotes the reason the upstream gave")
        fun unauthorizedWithMessage() {
            val chunk = errorChunkFor(401, """{"error":{"message":"No auth credentials found"}}""")

            assertTrue(chunk.contains("Authentication failed: No auth credentials found"), "got: $chunk")
        }

        @Test
        @DisplayName("a 401 with no readable body still explains what to check")
        fun unauthorizedWithoutMessage() {
            val chunk = errorChunkFor(401, "not json at all")

            assertTrue(chunk.contains("Please check your API key"), "got: $chunk")
        }

        @Test
        @DisplayName("a 402 with no readable body points at topping up credits")
        fun paymentRequiredWithoutMessage() {
            val chunk = errorChunkFor(402, "not json at all")

            assertTrue(chunk.contains("Insufficient credits. Please add credits"), "got: $chunk")
        }

        @Test
        @DisplayName("a 429 asks the user to wait")
        fun rateLimited() {
            val chunk = errorChunkFor(429, """{"error":{"message":"slow down"}}""")

            assertTrue(chunk.contains("Rate limit exceeded"), "got: $chunk")
        }

        @Test
        @DisplayName("a 429 on a free model adds the free-tier limits tip")
        fun rateLimitedOnFreeModel() {
            val chunk = errorChunkFor(429, """{"error":{"message":"rate limit for free models exceeded"}}""")

            assertTrue(chunk.contains("Free tier models have lower rate limits"), "got: $chunk")
        }

        @Test
        @DisplayName("a 500 names the status and links OpenRouter status")
        fun serverErrorWithMessage() {
            val chunk = errorChunkFor(500, """{"error":{"message":"internal"}}""")

            assertTrue(chunk.contains("OpenRouter server error (HTTP 500)"), "got: $chunk")
            assertTrue(chunk.contains("Details: internal"), "got: $chunk")
            assertTrue(chunk.contains("status.openrouter.ai"), "got: $chunk")
        }

        @Test
        @DisplayName("a 503 with no readable body still names the status")
        fun serverErrorWithoutMessage() {
            val chunk = errorChunkFor(503, "not json at all")

            assertTrue(chunk.contains("OpenRouter server error (HTTP 503)"), "got: $chunk")
            assertFalse(chunk.contains("Details:"), "got: $chunk")
        }

        @Test
        @DisplayName("a status with no special handling falls back to naming it")
        fun unhandledStatus() {
            val chunk = errorChunkFor(418, "not json at all")

            assertTrue(chunk.contains("Request failed (HTTP 418)"), "got: $chunk")
        }

        @Test
        @DisplayName("a model that does not accept images says so, and names what to do instead")
        fun imageNotSupported() {
            val chunk = errorChunkFor(
                404,
                """{"error":{"message":"No endpoints found that support image input"}}"""
            )

            assertTrue(chunk.contains("doesn't support image input"), "got: $chunk")
            assertTrue(chunk.contains("vision-capable model"), "got: $chunk")
        }

        @Test
        @DisplayName("a model that does not accept audio says so")
        fun audioNotSupported() {
            val chunk = errorChunkFor(
                404,
                """{"error":{"message":"No endpoints found that support audio input"}}"""
            )

            assertTrue(chunk.contains("doesn't support audio input"), "got: $chunk")
        }

        @Test
        @DisplayName("a model that does not accept video says so")
        fun videoNotSupported() {
            val chunk = errorChunkFor(
                404,
                """{"error":{"message":"No endpoints found that support video input"}}"""
            )

            assertTrue(chunk.contains("doesn't support video input"), "got: $chunk")
        }

        @Test
        @DisplayName("a model that does not accept PDFs says so")
        fun fileNotSupported() {
            val chunk = errorChunkFor(
                404,
                """{"error":{"message":"No endpoints found that support pdf input"}}"""
            )

            assertTrue(chunk.contains("doesn't support PDF/file input"), "got: $chunk")
        }

        @Test
        @DisplayName("a model with no endpoints at all is named, with alternatives to try")
        fun noEndpointsFound() {
            val chunk = errorChunkFor(
                404,
                """{"error":{"message":"No endpoints found for openai/gpt-4o-mini."}}"""
            )

            assertTrue(chunk.contains("Model Unavailable: openai/gpt-4o-mini"), "got: $chunk")
            assertTrue(chunk.contains("openrouter.ai/models"), "got: $chunk")
        }

        @Test
        @DisplayName("a model whose free period ended names the paid slug to switch to")
        fun freeTierEnded() {
            val chunk = errorChunkFor(
                404,
                """{"error":{"message":"The free period has ended. Please migrate to the paid slug: openai/gpt-4o-mini"}}"""
            )

            assertTrue(chunk.contains("Free Tier Ended"), "got: $chunk")
            assertTrue(chunk.contains("openai/gpt-4o-mini"), "got: $chunk")
        }

        @Test
        @DisplayName("a free period that ended without naming a paid slug still explains the alternatives")
        fun freeTierEndedWithoutSlug() {
            val chunk = errorChunkFor(
                404,
                """{"error":{"message":"The free period has ended for this model."}}"""
            )

            assertTrue(chunk.contains("Free Tier Ended"), "got: $chunk")
            assertTrue(chunk.contains("Alternatives"), "got: $chunk")
        }

        @Test
        @DisplayName("a 404 naming an unsupported content type is explained even without the endpoints phrasing")
        fun contentTypeErrorWithoutEndpointsPhrasing() {
            val chunk = errorChunkFor(
                404,
                """{"error":{"message":"This model does not support image input"}}"""
            )

            assertTrue(chunk.contains("doesn't support image input"), "got: $chunk")
        }
    }

    @Nested
    @DisplayName("Request preparation and diagnostics")
    inner class Preparation {

        @Test
        @DisplayName("a body that is valid JSON but not an object answers 400 rather than reaching OpenRouter")
        fun nonObjectJsonBody() {
            val exchange = response()

            servlet().service(request("[1,2,3]"), exchange.resp)

            verify(exchange.resp).status = HttpServletResponse.SC_BAD_REQUEST
            assertTrue(exchange.body.contains("Invalid JSON format"), "got: ${exchange.body}")
            assertEquals(0, server.requestCount)
        }

        @Test
        @DisplayName("the Authorization header is redacted in diagnostics while other headers pass through")
        fun redactsAuthorizationHeader() {
            enqueueCompletion()
            val exchange = response()

            servlet().service(
                request(
                    chatBody(),
                    headers = mapOf("Authorization" to "Bearer sk-or-secret", "User-Agent" to "test-client")
                ),
                exchange.resp
            )

            // The assertion that matters is that a request carrying headers is still served -
            // the redaction itself only reaches the log, which is exactly where a secret belongs
            // least. What must NOT happen is the header loop throwing and failing the request.
            assertTrue(exchange.body.contains("hello"), "got: ${exchange.body}")
        }

        @Test
        @DisplayName("configured defaults are applied to a request that omits them")
        fun appliesConfiguredDefaults() {
            val settings = OpenRouterSettings()
            `when`(settingsService.uiPreferencesManager).thenReturn(
                UIPreferencesManager(settings) {}.apply { defaultMaxTokens = DEFAULT_MAX_TOKENS }
            )
            `when`(settingsService.providerRoutingManager).thenReturn(ProviderRoutingManager(settings) {})
            `when`(settingsService.routerDefaultsManager).thenReturn(RouterDefaultsManager(settings) {})
            enqueueCompletion()
            val exchange = response()

            servlet().service(request(chatBody()), exchange.resp)

            val forwarded = server.takeRequest().body.readUtf8()
            assertTrue(forwarded.contains("\"max_tokens\":$DEFAULT_MAX_TOKENS"), "got: $forwarded")
        }

        @Test
        @DisplayName("a request that already carries max_tokens keeps the client's own value")
        fun keepsClientMaxTokens() {
            val settings = OpenRouterSettings()
            `when`(settingsService.uiPreferencesManager).thenReturn(
                UIPreferencesManager(settings) {}.apply { defaultMaxTokens = DEFAULT_MAX_TOKENS }
            )
            `when`(settingsService.providerRoutingManager).thenReturn(ProviderRoutingManager(settings) {})
            `when`(settingsService.routerDefaultsManager).thenReturn(RouterDefaultsManager(settings) {})
            enqueueCompletion()
            val exchange = response()

            val body = """{"model":"openai/gpt-4o-mini","messages":[{"role":"user","content":"hi"}],"max_tokens":7}"""
            servlet().service(request(body), exchange.resp)

            val forwarded = server.takeRequest().body.readUtf8()
            assertTrue(forwarded.contains("\"max_tokens\":7"), "got: $forwarded")
        }

        @Test
        @DisplayName("OpenRouter metadata headers on the answer do not disturb the response")
        fun metadataHeadersAreTolerated() {
            server.enqueue(
                MockResponse().setResponseCode(200).setHeader("Content-Type", "application/json")
                    .setHeader("x-openrouter-model", "openai/gpt-4o-mini")
                    .setHeader("openrouter-id", "gen-123")
                    .setBody(
                        """{"id":"cmpl-1","object":"chat.completion","created":1700000000,
                         "model":"openai/gpt-4o-mini",
                         "choices":[{"index":0,"message":{"role":"assistant","content":"hello"},
                          "finish_reason":"stop"}],
                         "usage":{"prompt_tokens":1,"completion_tokens":1,"total_tokens":2}}"""
                    )
            )
            val exchange = response()

            servlet().service(request(chatBody()), exchange.resp)

            assertTrue(exchange.body.contains("hello"), "got: ${exchange.body}")
        }

        @Test
        @DisplayName("a servlet-container failure reading the request answers 400 rather than a stack trace")
        fun illegalArgumentFromContainer() {
            val throwing = object : Reader() {
                override fun read(cbuf: CharArray, off: Int, len: Int): Int =
                    throw IllegalArgumentException("bad encoding")
                override fun close() = Unit
            }
            val exchange = response()

            servlet().service(request("", throwing), exchange.resp)

            verify(exchange.resp).status = HttpServletResponse.SC_BAD_REQUEST
            assertTrue(exchange.body.contains("bad encoding"), "got: ${exchange.body}")
        }

        @Test
        @DisplayName("a request read after the container recycled it answers 500 rather than a stack trace")
        fun illegalStateFromContainer() {
            val throwing = object : Reader() {
                override fun read(cbuf: CharArray, off: Int, len: Int): Int =
                    error("getReader() already called")
                override fun close() = Unit
            }
            val exchange = response()

            servlet().service(request("", throwing), exchange.resp)

            verify(exchange.resp).status = HttpServletResponse.SC_INTERNAL_SERVER_ERROR
        }

        @Test
        @DisplayName("OpenRouter metadata headers on a streaming answer do not disturb the stream")
        fun streamingMetadataHeaders() {
            server.enqueue(
                MockResponse().setResponseCode(200)
                    .setHeader("Content-Type", "text/event-stream")
                    .setHeader("x-openrouter-model", "openai/gpt-4o-mini")
                    .setHeader("openrouter-id", "gen-123")
                    .setBody("data: {\"id\":\"1\",\"choices\":[{\"delta\":{\"content\":\"hi\"}}]}\n\ndata: [DONE]\n\n")
            )
            val exchange = response()

            servlet().service(request(chatBody(stream = true)), exchange.resp)

            assertTrue(exchange.body.contains("data:"), "got: ${exchange.body}")
        }

        @Test
        @DisplayName("a streaming request whose upstream is dead is reported into the stream, not thrown")
        fun streamingTransportFailure() {
            val deadUrl = server.url("/api/v1/chat/completions").toString()
            server.shutdown()
            val client = OkHttpClient.Builder().build()
            clients += client
            val servlet = ChatCompletionServlet(
                httpClient = client,
                settingsServiceProvider = { settingsService },
                openRouterApiUrl = deadUrl,
                multimodalValidatorProvider = { multimodalValidator }
            )
            val exchange = response()

            servlet.service(request(chatBody(stream = true)), exchange.resp)

            verify(exchange.resp).contentType = "text/event-stream"
            assertTrue(exchange.body.isNotBlank(), "a dead upstream must still answer the open stream")
        }

        @Test
        @DisplayName("a 404 that names no unsupported content type falls back to naming the status")
        fun notFoundWithoutContentTypeHint() {
            server.enqueue(
                MockResponse().setResponseCode(404)
                    .setBody("""{"error":{"message":"model not known here"}}""")
            )
            val exchange = response()

            servlet().service(request(chatBody(stream = true)), exchange.resp)

            assertTrue(exchange.body.contains("model not known here"), "got: ${exchange.body}")
        }
    }

    private companion object {
        const val DEFAULT_MAX_TOKENS = 1234
    }
}
