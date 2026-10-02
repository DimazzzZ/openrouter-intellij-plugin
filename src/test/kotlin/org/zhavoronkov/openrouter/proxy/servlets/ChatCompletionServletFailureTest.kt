package org.zhavoronkov.openrouter.proxy.servlets

import com.google.gson.JsonObject
import com.google.gson.JsonSyntaxException
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.zhavoronkov.openrouter.presets.PresetEntry
import org.zhavoronkov.openrouter.presets.PresetSnapshot
import org.zhavoronkov.openrouter.proxy.models.OpenAIChatCompletionRequest
import org.zhavoronkov.openrouter.proxy.pairs.PairAvailability
import org.zhavoronkov.openrouter.proxy.validation.MultimodalContentValidator
import org.zhavoronkov.openrouter.requests.RequestRecord
import org.zhavoronkov.openrouter.services.OpenRouterSettingsService
import org.zhavoronkov.openrouter.services.settings.ApiKeySettingsManager
import org.zhavoronkov.openrouter.testing.OkHttpLeakSafeExtension
import java.io.BufferedReader
import java.io.IOException
import java.io.PrintWriter
import java.io.StringReader
import java.io.StringWriter
import java.util.Collections

/**
 * What [ChatCompletionServlet] does when the call to OpenRouter fails before an answer exists -
 * the client's own call throws - and when OpenRouter's refusal carries less than the usual
 * explanation. `ChatCompletionServletProxyingTest` covers the answers; this covers the failures.
 *
 * Each failure is chosen by a client whose interceptor throws it, so nothing leaves the machine.
 */
@ExtendWith(OkHttpLeakSafeExtension::class)
@DisplayName("ChatCompletionServlet Failure Tests")
class ChatCompletionServletFailureTest {

    private lateinit var server: MockWebServer
    private lateinit var settingsService: OpenRouterSettingsService
    private lateinit var multimodalValidator: MultimodalContentValidator
    private val clients = mutableListOf<OkHttpClient>()
    private val recorded = mutableListOf<RequestRecord>()

    @BeforeEach
    fun setUp() {
        server = MockWebServer()
        server.start()

        val apiKeyManager = mock(ApiKeySettingsManager::class.java)
        `when`(apiKeyManager.getStoredApiKey()).thenReturn("sk-or-test")
        settingsService = mock(OpenRouterSettingsService::class.java)
        `when`(settingsService.apiKeyManager).thenReturn(apiKeyManager)

        multimodalValidator = mock(MultimodalContentValidator::class.java)
        `when`(
            multimodalValidator.validate(
                org.mockito.ArgumentMatchers.any(OpenAIChatCompletionRequest::class.java)
                    ?: OpenAIChatCompletionRequest(model = "", messages = emptyList()),
                org.mockito.ArgumentMatchers.anyString()
            )
        ).thenReturn(MultimodalContentValidator.ValidationResult.Valid)
    }

    @AfterEach
    fun tearDown() {
        clients.forEach {
            it.dispatcher.executorService.shutdown()
            it.connectionPool.evictAll()
        }
        runCatching { server.shutdown() }
    }

    private fun servlet(
        client: OkHttpClient = OkHttpClient.Builder().build(),
        snapshot: PresetSnapshot = PresetSnapshot(0, emptyList())
    ): ChatCompletionServlet {
        clients += client
        return ChatCompletionServlet(
            httpClient = client,
            settingsServiceProvider = { settingsService },
            openRouterApiUrl = { server.url("/api/v1/chat/completions").toString() },
            multimodalValidatorProvider = { multimodalValidator },
            requestRecorder = { recorded += it },
            providerLookup = {},
            keepBodies = { false },
            bodiesSaver = { _, _ -> },
            catalogueProvider = { null },
            readMissingPreset = {},
            pairsProvider = {
                PairAvailability(presets = { snapshot }, lookup = { snapshot.find(it) }, catalogue = { null })
            }
        )
    }

    /** A client whose every call throws [failure] before anything is sent. */
    private fun throwing(failure: Exception) =
        OkHttpClient.Builder().addInterceptor(Interceptor { throw failure }).build()

    private class Exchange(val resp: HttpServletResponse, private val sink: StringWriter) {
        val body: String get() = sink.toString()
    }

    private fun request(
        body: String,
        authorization: String? = "Bearer sk-or-from-the-consumer"
    ): HttpServletRequest {
        val req = mock(HttpServletRequest::class.java)
        `when`(req.reader).thenReturn(BufferedReader(StringReader(body)))
        `when`(req.remoteAddr).thenReturn("127.0.0.1")
        `when`(req.requestURI).thenReturn("/v1/chat/completions")
        `when`(req.servletPath).thenReturn("/v1/chat/completions")
        `when`(req.method).thenReturn("POST")
        `when`(req.contentType).thenReturn("application/json")
        `when`(req.headerNames).thenReturn(Collections.enumeration(listOf("Authorization")))
        `when`(req.getHeader("Authorization")).thenReturn(authorization)
        return req
    }

    private fun response(): Exchange {
        val resp = mock(HttpServletResponse::class.java)
        val sink = StringWriter()
        `when`(resp.writer).thenReturn(PrintWriter(sink, true))
        return Exchange(resp, sink)
    }

    private fun chatBody(stream: Boolean, model: String = "openai/gpt-4o-mini") =
        """{"model":"$model","messages":[{"role":"user","content":"hi"}],"stream":$stream}"""

    @Nested
    @DisplayName("A streaming call that throws")
    inner class StreamingFailures {

        @ParameterizedTest(name = "{0}")
        @MethodSource("$SOURCES#streamingFailures")
        fun `is told to the client as a chunk and recorded with its kind`(failure: Exception, recordedAs: String) {
            val exchange = response()

            servlet(throwing(failure)).service(request(chatBody(stream = true)), exchange.resp)

            assertTrue(exchange.body.contains("data:"), "the stream must end in a chunk: ${exchange.body}")
            val error = recorded.single().error.orEmpty()
            assertTrue(error.startsWith(recordedAs), "recorded as: $error")
        }
    }

    @Nested
    @DisplayName("A non-streaming call that throws")
    inner class NonStreamingFailures {

        @Test
        fun `an internal error answers 500 and is recorded as one`() {
            val exchange = response()

            servlet(throwing(IllegalStateException("broken state"))).service(
                request(chatBody(stream = false)),
                exchange.resp
            )

            verify(exchange.resp).status = HttpServletResponse.SC_INTERNAL_SERVER_ERROR
            assertTrue(recorded.single().error.orEmpty().contains("broken state"), "recorded: ${recorded.single()}")
        }

        @Test
        fun `an unreadable reply answers 500 and is recorded as an invalid request`() {
            val exchange = response()

            servlet(throwing(JsonSyntaxException("bad json"))).service(
                request(chatBody(stream = false)),
                exchange.resp
            )

            verify(exchange.resp).status = HttpServletResponse.SC_INTERNAL_SERVER_ERROR
            assertTrue(recorded.single().error.orEmpty().contains("bad json"), "recorded: ${recorded.single()}")
        }
    }

    @Nested
    @DisplayName("Refusals that say less than usual")
    inner class TerseRefusals {

        private fun errorChunkFor(status: Int, body: String): String {
            server.enqueue(MockResponse().setResponseCode(status).setBody(body))
            val exchange = response()

            servlet().service(request(chatBody(stream = true)), exchange.resp)

            return exchange.body
        }

        @Test
        fun `a status it has no wording for quotes the upstream message`() {
            val chunk = errorChunkFor(418, """{"error":{"message":"I am a teapot"}}""")

            assertTrue(chunk.contains("I am a teapot"), "got: $chunk")
        }

        @Test
        fun `a status it has no wording for and no message names the status`() {
            val chunk = errorChunkFor(418, "not json at all")

            assertTrue(chunk.contains("Request failed (HTTP 418)"), "got: $chunk")
        }

        @Test
        fun `a server error with no message still points at the status page`() {
            val chunk = errorChunkFor(502, "<html>Bad Gateway</html>")

            assertTrue(chunk.contains("OpenRouter server error (HTTP 502)"), "got: $chunk")
            assertTrue(!chunk.contains("Details:"), "no details to show: $chunk")
        }

        @Test
        fun `a free-tier rate limit adds the tip about paid models`() {
            val chunk = errorChunkFor(429, """{"error":{"message":"free-models-per-min limit"}}""")

            assertTrue(chunk.contains("Free tier models have lower rate limits"), "got: $chunk")
        }

        @Test
        fun `a rate limit with no message gives no tip`() {
            val chunk = errorChunkFor(429, "slow down")

            assertTrue(chunk.contains("Rate limit exceeded"), "got: $chunk")
            assertTrue(!chunk.contains("Free tier models"), "no message to tell a free model by: $chunk")
        }

        @Test
        fun `a free tier that ended without naming the paid slug offers only the alternatives`() {
            val chunk = errorChunkFor(404, """{"error":{"message":"The free period has ended for this model"}}""")

            assertTrue(chunk.contains("Free Tier Ended"), "got: $chunk")
            assertTrue(!chunk.contains("switch to the paid version"), "no slug to offer: $chunk")
        }

        @Test
        fun `no endpoints with no model named falls back to the requested model`() {
            val chunk = errorChunkFor(404, """{"error":{"message":"No endpoints found."}}""")

            assertTrue(chunk.contains("the requested model"), "got: $chunk")
        }
    }

    /**
     * Bodies whose shape is not the one the code reading them expects. Gson answers null, not an
     * exception, for an empty or blank string, and reading a field of the wrong JSON type throws
     * something other than JsonSyntaxException - each of these once escaped the servlet, leaving
     * the client a bare Jetty 500 or a stream cut off with no explanation.
     */
    @Nested
    @DisplayName("Bodies that are not the shape they should be")
    inner class MisshapenBodies {

        @ParameterizedTest(name = "[{index}] \"{0}\"")
        @ValueSource(strings = ["", "   ", "\n"])
        fun `an empty request body answers 400 without contacting OpenRouter`(body: String) {
            val exchange = response()

            servlet().service(request(body), exchange.resp)

            verify(exchange.resp).status = HttpServletResponse.SC_BAD_REQUEST
            assertTrue(exchange.body.contains("Invalid JSON format"), "got: ${exchange.body}")
            assertEquals(0, server.requestCount)
        }

        @ParameterizedTest(name = "[{index}] \"{0}\"")
        @ValueSource(strings = ["", "   "])
        fun `a refusal with an empty body still ends the stream in an explanation`(body: String) {
            server.enqueue(MockResponse().setResponseCode(502).setBody(body))
            val exchange = response()

            servlet().service(request(chatBody(stream = true)), exchange.resp)

            assertTrue(exchange.body.contains("OpenRouter server error (HTTP 502)"), "got: ${exchange.body}")
        }

        @ParameterizedTest(name = "[{index}] {0}")
        @ValueSource(
            strings = [
                """{"error":{"message":{"detail":"nested"}}}""",
                """{"error":"a bare string"}""",
                """{"error":null}""",
                """{"error":{"message":7}}""",
                """{"error":{}}""",
                """{"detail":"no error field"}"""
            ]
        )
        fun `a refusal whose error is the wrong shape is explained by its status`(body: String) {
            server.enqueue(MockResponse().setResponseCode(400).setBody(body))
            val exchange = response()

            servlet().service(request(chatBody(stream = true)), exchange.resp)

            assertTrue(exchange.body.contains("Request failed (HTTP 400)"), "got: ${exchange.body}")
        }

        @ParameterizedTest(name = "[{index}] {0}")
        @MethodSource("$SOURCES#misshapenStreamErrors")
        fun `an error chunk of the wrong shape is still forwarded as an error`(chunk: String, shown: String) {
            server.enqueue(
                MockResponse().setResponseCode(200).setHeader("Content-Type", "text/event-stream")
                    .setBody("data: $chunk\n\ndata: [DONE]\n\n")
            )
            val exchange = response()

            servlet().service(request(chatBody(stream = true)), exchange.resp)

            assertTrue(exchange.body.contains(shown), "got: ${exchange.body}")
            assertTrue(recorded.single().error.orEmpty().contains(shown), "recorded: ${recorded.single()}")
        }
    }

    companion object {
        private const val SOURCES = "org.zhavoronkov.openrouter.proxy.servlets.ChatCompletionServletFailureTest"

        @JvmStatic
        fun misshapenStreamErrors(): List<Arguments> = listOf(
            Arguments.of("""{"error":"provider overloaded"}""", "provider overloaded"),
            Arguments.of("""{"error":{"message":{"detail":"nested"}}}""", "Unknown error from model"),
            Arguments.of("""{"error":{"code":500}}""", "Unknown error from model")
        )

        @JvmStatic
        fun streamingFailures(): List<Arguments> = listOf(
            Arguments.of(IOException("connection reset"), "Network error"),
            Arguments.of(JsonSyntaxException("bad json"), "Invalid response"),
            Arguments.of(IllegalStateException("broken state"), "Internal error"),
            Arguments.of(IllegalArgumentException("bad argument"), "Invalid request")
        )
    }

    @Nested
    @DisplayName("Requests that fail on the way in, and answers that are not the usual shape")
    inner class Edges {

        @Test
        fun `a JSON error while checking the request answers 500 and is recorded as an invalid request`() {
            `when`(
                multimodalValidator.validate(
                    org.mockito.ArgumentMatchers.any(OpenAIChatCompletionRequest::class.java)
                        ?: OpenAIChatCompletionRequest(model = "", messages = emptyList()),
                    org.mockito.ArgumentMatchers.anyString()
                )
            ).thenThrow(JsonSyntaxException("unreadable content part"))
            val exchange = response()

            servlet().service(request(chatBody(stream = false)), exchange.resp)

            verify(exchange.resp).status = HttpServletResponse.SC_INTERNAL_SERVER_ERROR
            assertEquals("Invalid request: unreadable content part", recorded.single().error)
            assertEquals(0, server.requestCount, "nothing is sent once the request cannot be checked")
        }

        @Test
        fun `a redirect OpenRouter does not complete is told to the stream by its status`() {
            server.enqueue(MockResponse().setResponseCode(302).setBody("moved"))
            val exchange = response()

            servlet().service(request(chatBody(stream = true)), exchange.resp)

            assertTrue(exchange.body.contains("Request failed (HTTP 302)"), "got: ${exchange.body}")
            assertTrue(exchange.body.contains("data: [DONE]"), "the stream is ended: ${exchange.body}")
        }

        @Test
        fun `a pair's stream names the pair the Consumer asked for`() {
            server.enqueue(
                MockResponse().setResponseCode(200).setHeader("Content-Type", "text/event-stream").setBody(
                    "data: {\"id\":\"c1\",\"object\":\"chat.completion.chunk\",\"created\":1," +
                        "\"model\":\"openai/gpt-4o-mini\",\"choices\":[{\"index\":0," +
                        "\"delta\":{\"content\":\"hi\"},\"finish_reason\":null}]}\n\ndata: [DONE]\n\n"
                )
            )
            val snapshot = PresetSnapshot(0, listOf(PresetEntry("research", "Research", null, JsonObject())))
            val exchange = response()

            servlet(snapshot = snapshot).service(
                request(chatBody(stream = true, model = "openai/gpt-4o-mini@preset/research")),
                exchange.resp
            )

            assertTrue(
                exchange.body.contains("\"model\":\"openai/gpt-4o-mini@preset/research\""),
                "got: ${exchange.body}"
            )
            assertEquals(1, server.requestCount)
        }

        @Test
        fun `an Authorization header listed without a value is served like any other request`() {
            server.enqueue(
                MockResponse().setResponseCode(200).setHeader("Content-Type", "application/json").setBody(
                    """{"id":"c1","object":"chat.completion","created":1,"model":"openai/gpt-4o-mini",
                       "choices":[{"index":0,"message":{"role":"assistant","content":"hello"},
                       "finish_reason":"stop"}]}"""
                )
            )
            val exchange = response()

            servlet().service(request(chatBody(stream = false), authorization = null), exchange.resp)

            assertTrue(exchange.body.contains("hello"), "got: ${exchange.body}")
            assertNull(recorded.single().error, "recorded: ${recorded.single()}")
        }

        @Test
        fun `the servlet the proxy server builds with no arguments needs nothing running to construct`() {
            // Every service it needs is resolved on first use, so building it touches none
            assertNotNull(ChatCompletionServlet())
        }
    }
}
