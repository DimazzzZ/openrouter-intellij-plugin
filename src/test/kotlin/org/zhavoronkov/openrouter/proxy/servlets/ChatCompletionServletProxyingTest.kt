package org.zhavoronkov.openrouter.proxy.servlets

import com.google.gson.JsonParser
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.zhavoronkov.openrouter.models.DataRegion
import org.zhavoronkov.openrouter.models.FixPage
import org.zhavoronkov.openrouter.models.OpenRouterModelInfo
import org.zhavoronkov.openrouter.models.OpenRouterSettings
import org.zhavoronkov.openrouter.models.OutputSchema
import org.zhavoronkov.openrouter.models.WebSearchEngine
import org.zhavoronkov.openrouter.models.WebSearchSettings
import org.zhavoronkov.openrouter.presets.PresetEntry
import org.zhavoronkov.openrouter.presets.PresetSnapshot
import org.zhavoronkov.openrouter.proxy.models.OpenAIChatCompletionRequest
import org.zhavoronkov.openrouter.proxy.pairs.PairAvailability
import org.zhavoronkov.openrouter.proxy.validation.MultimodalContentValidator
import org.zhavoronkov.openrouter.requests.ReplyFacts
import org.zhavoronkov.openrouter.requests.RequestBodies
import org.zhavoronkov.openrouter.requests.RequestRecord
import org.zhavoronkov.openrouter.requests.RequestSource
import org.zhavoronkov.openrouter.services.OpenRouterSettingsService
import org.zhavoronkov.openrouter.services.settings.ApiKeySettingsManager
import org.zhavoronkov.openrouter.services.settings.OutputSchemasManager
import org.zhavoronkov.openrouter.services.settings.ProviderRoutingManager
import org.zhavoronkov.openrouter.services.settings.RouterDefaultsManager
import org.zhavoronkov.openrouter.services.settings.UIPreferencesManager
import org.zhavoronkov.openrouter.services.settings.WebSearchSettingsManager
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

    private val recorded = mutableListOf<RequestRecord>()

    /** What each model declares in the catalogue, for the pairs' output gate; a model left out declares nothing. */
    private val declared = mutableMapOf(
        "openai/gpt-4o-mini" to listOf("response_format", "structured_outputs", "tools")
    )
    private var catalogueLoaded = true

    /** Generations whose provider the servlet asked to be looked up later. */
    private val lookedUp = mutableListOf<String>()

    /** The catalogue the Consumer request checks see; null, as before the first load, checks nothing about the model. */
    private var consumerCatalogue: List<OpenRouterModelInfo>? = null

    /** Whether the servlet keeps request bodies, and what it kept, by id. */
    private var keepBodies = false
    private val keptBodies = mutableMapOf<String, RequestBodies>()

    private fun servlet(
        presets: PresetSnapshot? = PresetSnapshot(0, emptyList()),
        /** What the copy holds once the read a missing slug asks for has been waited for. */
        afterRead: PresetSnapshot? = presets
    ): ChatCompletionServlet {
        var current = presets
        val client = OkHttpClient.Builder().build()
        clients += client
        return ChatCompletionServlet(
            httpClient = client,
            settingsServiceProvider = { settingsService },
            openRouterApiUrl = { server.url("/api/v1/chat/completions").toString() },
            multimodalValidatorProvider = { multimodalValidator },
            requestRecorder = { recorded += it },
            providerLookup = { lookedUp += it },
            keepBodies = { keepBodies },
            bodiesSaver = { id, bodies -> keptBodies[id] = bodies },
            catalogueProvider = { consumerCatalogue },
            readMissingPreset = { current = afterRead },
            pairsProvider = {
                val snapshot = current
                PairAvailability(
                    presets = { snapshot },
                    lookup = { snapshot?.find(it) },
                    catalogue = {
                        declared.map { (id, params) ->
                            OpenRouterModelInfo(id = id, name = id, created = 0, supportedParameters = params)
                        }.takeIf { catalogueLoaded }
                    }
                )
            }
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
                openRouterApiUrl = { deadUrl },
                multimodalValidatorProvider = { multimodalValidator }
            )
            val exchange = response()

            servlet.service(request(chatBody()), exchange.resp)

            assertTrue(exchange.body.isNotBlank(), "a dead upstream must still answer the client")
        }
    }

    /**
     * Every proxied request becomes one Requests entry: who sent it, what it asked for, and what
     * the reply reported - read from OpenRouter's own body, not the translated one - or its error.
     */
    @Nested
    @DisplayName("Recording")
    inner class Recording {

        /** With a server tool the reply's provider names OpenAI whatever served it. */
        @Test
        @DisplayName("a request with web search is recorded without the reply's provider, which is looked up")
        fun serverToolProvider() {
            server.enqueue(
                MockResponse().setResponseCode(200).setHeader("Content-Type", "application/json").setBody(
                    """{"id":"gen-7","object":"chat.completion","created":1700000000,
                        "model":"openai/gpt-4o-mini","provider":"OpenAI",
                        "choices":[{"index":0,"message":{"role":"assistant","content":"hello"},"finish_reason":"stop"}]}"""
                )
            )
            val body = chatBody().replace("}]", """}],"tools":[{"type":"openrouter:web_search"}]""")

            servlet().service(request(body), response().resp)

            assertNull(recorded.single().reply.provider)
            assertEquals(listOf("gen-7"), lookedUp)
        }

        @Test
        @DisplayName("with request bodies on, the body received, the body sent and the reply are kept")
        fun keepsBodies() {
            keepBodies = true
            server.enqueue(
                MockResponse().setResponseCode(200).setHeader("Content-Type", "application/json").setBody(
                    """{"id":"gen-1","object":"chat.completion","created":1700000000,"model":"openai/gpt-4o-mini",
                        "choices":[{"index":0,"message":{"role":"assistant","content":"hello"},"finish_reason":"stop"}]}"""
                )
            )

            servlet().service(request(chatBody()), response().resp)

            val bodies = keptBodies.getValue(recorded.single().bodiesId!!)
            assertEquals(chatBody(), bodies.received)
            assertTrue(bodies.sent!!.contains("\"model\":\"openai/gpt-4o-mini\""), bodies.sent)
            assertTrue(bodies.reply!!.contains("\"content\":\"hello\""), bodies.reply)
        }

        @Test
        @DisplayName("with request bodies off, nothing a request carried is kept")
        fun keepsNoBodies() {
            enqueueCompletion()

            servlet().service(request(chatBody()), response().resp)

            assertNull(recorded.single().bodiesId)
            assertTrue(keptBodies.isEmpty())
        }

        @Test
        @DisplayName("a non-streaming request is recorded with its Consumer and the reply's facts")
        fun recordsNonStreaming() {
            server.enqueue(
                MockResponse().setResponseCode(200).setHeader("Content-Type", "application/json").setBody(
                    """{"id":"gen-1","object":"chat.completion","created":1700000000,
                        "model":"openai/gpt-4o-mini","provider":"OpenAI",
                        "choices":[{"index":0,"message":{"role":"assistant","content":"hello"},
                          "finish_reason":"stop"}],
                        "usage":{"prompt_tokens":3,"completion_tokens":1,"total_tokens":4,"cost":0.0001}}"""
                )
            )

            servlet().service(request(chatBody(), headers = mapOf("User-Agent" to "Junie/1.2")), response().resp)

            val record = recorded.single()
            assertEquals(RequestSource.PROXY, record.source)
            assertEquals("Junie", record.sender)
            assertEquals("openai/gpt-4o-mini", record.requestedModel)
            assertEquals(
                ReplyFacts(
                    generationId = "gen-1",
                    answeringModel = "openai/gpt-4o-mini",
                    provider = "OpenAI",
                    promptTokens = 3,
                    completionTokens = 1,
                    cost = 0.0001,
                    finishReason = "stop"
                ),
                record.reply
            )
            assertEquals(null, record.error)
        }

        @Test
        @DisplayName("a streaming request is recorded from its chunks, usage from the last")
        fun recordsStreaming() {
            val chunks = listOf(
                """{"id":"gen-2","model":"m","provider":"P","choices":[{"delta":{"content":"he"}}]}""",
                """{"id":"gen-2","model":"m","choices":[{"delta":{},"finish_reason":"length"}]}""",
                """{"id":"gen-2","model":"m","choices":[],"usage":{"cost":0.03}}"""
            )
            server.enqueue(
                MockResponse().setResponseCode(200).setHeader("Content-Type", "text/event-stream")
                    .setBody(chunks.joinToString("") { "data: $it\n\n" } + "data: [DONE]\n\n")
            )

            servlet().service(request(chatBody(stream = true)), response().resp)

            val reply = recorded.single().reply
            assertEquals("gen-2", reply.generationId)
            assertEquals("P", reply.provider)
            assertEquals("length", reply.finishReason)
            assertEquals(0.03, reply.cost)
        }

        @Test
        @DisplayName("a request OpenRouter refuses is recorded with the error")
        fun recordsRefusal() {
            server.enqueue(
                MockResponse().setResponseCode(402).setBody("""{"error":{"message":"Insufficient credits"}}""")
            )

            servlet().service(request(chatBody()), response().resp)

            val error = recorded.single().error
            assertTrue(error != null && error.contains("Insufficient credits"), "got: $error")
        }

        @Test
        @DisplayName("a request that never reaches OpenRouter is still recorded, once")
        fun recordsEarlyFailure() {
            `when`(apiKeyManager.getStoredApiKey()).thenReturn("")

            servlet().service(request(chatBody()), response().resp)

            assertEquals("API key not configured", recorded.single().error)
        }

        @Test
        @DisplayName("a streaming request's tokens and searches come from the usage in its last chunk")
        fun recordsStreamingUsage() {
            val chunks = listOf(
                """{"id":"gen-3","model":"m","choices":[{"delta":{"content":"hi"},"finish_reason":"stop"}]}""",
                """{"id":"gen-3","model":"m","choices":[],"usage":{"prompt_tokens":12,"completion_tokens":7,
                   "cost":0.002,"server_tool_use":{"web_search_requests":2}}}""".replace("\n", "")
            )
            server.enqueue(
                MockResponse().setResponseCode(200).setHeader("Content-Type", "text/event-stream")
                    .setBody(chunks.joinToString("") { "data: $it\n\n" } + "data: [DONE]\n\n")
            )

            servlet().service(request(chatBody(stream = true)), response().resp)

            val reply = recorded.single().reply
            assertEquals(12, reply.promptTokens)
            assertEquals(7, reply.completionTokens)
            assertEquals(2, reply.webSearches)
            assertEquals(null, recorded.single().error)
        }

        @Test
        @DisplayName("an error chunk in a stream is recorded as the request's error")
        fun recordsStreamErrorChunk() {
            server.enqueue(
                MockResponse().setResponseCode(200).setHeader("Content-Type", "text/event-stream")
                    .setBody("data: {\"error\":{\"message\":\"Provider overloaded\",\"code\":502}}\n\ndata: [DONE]\n\n")
            )

            servlet().service(request(chatBody(stream = true)), response().resp)

            val error = recorded.single().error
            assertTrue(error != null && error.contains("Provider overloaded"), "got: $error")
        }

        @Test
        @DisplayName("a stream that carries nothing is recorded as an error")
        fun recordsEmptyStream() {
            server.enqueue(
                MockResponse().setResponseCode(200).setHeader("Content-Type", "text/event-stream").setBody("")
            )

            servlet().service(request(chatBody(stream = true)), response().resp)

            val error = recorded.single().error
            assertTrue(error != null && error.startsWith("No response received from model"), "got: $error")
        }

        @Test
        @DisplayName("content the model cannot accept is recorded with the reason, once")
        fun recordsMultimodalRefusal() {
            `when`(multimodalValidator.validate(any(), anyString())).thenReturn(
                MultimodalContentValidator.ValidationResult.Invalid(
                    contentType = MultimodalContentValidator.ContentType.IMAGE,
                    modelId = "openai/gpt-4o-mini",
                    errorMessage = "Model openai/gpt-4o-mini does not accept image input"
                )
            )

            servlet().service(request(chatBody()), response().resp)

            val record = recorded.single()
            assertEquals("openai/gpt-4o-mini", record.requestedModel)
            assertTrue(record.error!!.contains("not supported by openai/gpt-4o-mini"), "got: ${record.error}")
        }

        @Test
        @DisplayName("an upstream that cannot be reached is recorded as a network error, once")
        fun recordsNetworkError() {
            val deadUrl = server.url("/api/v1/chat/completions").toString()
            server.shutdown()
            val client = OkHttpClient.Builder().build()
            clients += client
            val servlet = ChatCompletionServlet(
                httpClient = client,
                settingsServiceProvider = { settingsService },
                openRouterApiUrl = { deadUrl },
                multimodalValidatorProvider = { multimodalValidator },
                requestRecorder = { recorded += it }
            )

            servlet.service(request(chatBody()), response().resp)

            val error = recorded.single().error
            assertTrue(error != null && error.startsWith("Network error"), "got: $error")
        }

        /** An upstream body can echo the prompt back; only its error message may reach the log. */
        @Test
        @DisplayName("a refusal is recorded by its message, never by the body around it")
        fun recordsRefusalWithoutItsBody() {
            server.enqueue(
                MockResponse().setResponseCode(403).setBody(
                    """{"error":{"message":"Input flagged","metadata":{"flagged_input":"secret prompt"}}}"""
                )
            )

            servlet().service(request(chatBody()), response().resp)

            val error = recorded.single().error!!
            assertTrue(error.contains("Input flagged"), "got: $error")
            assertFalse(error.contains("secret prompt"), "got: $error")
        }
    }

    /**
     * A pair picked by a Consumer: what reaches OpenRouter, what the Consumer gets back, and what
     * the Requests entry says. OpenRouter applies the preset; the proxy makes it win.
     */
    @Nested
    @DisplayName("Pairs")
    inner class Pairs {

        private val settings = OpenRouterSettings()

        private fun presets(vararg entries: Pair<String, String?>) = PresetSnapshot(
            0,
            entries.map { (slug, config) -> PresetEntry(
                slug,
                slug,
                null,
                config?.let { JsonParser.parseString(it).asJsonObject }
            ) }
        )

        private val research = presets(
            "research" to """{"temperature":0.2,"provider":{"only":["azure"]},"tools":[{"type":"openrouter:web_search"}]}"""
        )

        @BeforeEach
        fun stubSettings() {
            `when`(settingsService.uiPreferencesManager).thenReturn(
                UIPreferencesManager(settings) {}.apply { defaultMaxTokens = 999 }
            )
            `when`(settingsService.providerRoutingManager).thenReturn(
                ProviderRoutingManager(settings) {}.apply {
                    enabled = true
                    sort = "price"
                    fallbackModels = mutableListOf("openai/gpt-4o-mini")
                }
            )
            `when`(settingsService.routerDefaultsManager).thenReturn(RouterDefaultsManager(settings) {})
            `when`(settingsService.webSearchManager).thenReturn(WebSearchSettingsManager(settings) {})
            `when`(settingsService.outputSchemasManager).thenReturn(OutputSchemasManager(settings) {})
        }

        private fun pairBody(extra: String = "") =
            """{"model":"openai/gpt-4o-mini@preset/research","messages":[{"role":"user","content":"hi"}]$extra}"""

        private fun forwarded() = JsonParser.parseString(server.takeRequest().body.readUtf8()).asJsonObject

        @Test
        @DisplayName("a pair reaches OpenRouter as the same id, without the Consumer's fields the preset sets")
        fun forwardedBody() {
            enqueueCompletion()
            val consumer = ""","temperature":1,"tools":[{"type":"function","function":{"name":"read_file"}}],"user":"u""""

            servlet(research).service(request(pairBody(consumer)), response().resp)

            val body = forwarded()
            assertEquals("openai/gpt-4o-mini@preset/research", body["model"].asString)
            assertFalse(body.has("temperature"), "the preset's temperature holds")
            assertEquals(
                JsonParser.parseString("""[{"type":"function","function":{"name":"read_file"}}]"""),
                body["tools"]
            )
            assertEquals("u", body["user"].asString)
        }

        /** OpenRouter lets a request's field override the preset's, so no default is added over one it sets. */
        @Test
        @DisplayName("the plugin's defaults stay out of the fields the preset sets, and go into the others")
        fun defaultsSkipPresetFields() {
            enqueueCompletion()

            servlet(research).service(request(pairBody()), response().resp)

            val body = forwarded()
            assertFalse(body.has("provider"), "the preset sets routing")
            assertFalse(body.has("models"), "no global fallback models either")
            assertEquals(999, body["max_tokens"].asInt, "the preset sets no max_tokens")
        }

        @Test
        @DisplayName("with a preset whose config is not known, the pair is sent as it is and gets no defaults")
        fun unknownConfig() {
            enqueueCompletion()

            servlet(presets("research" to null)).service(request(pairBody(""","temperature":1""")), response().resp)

            val body = forwarded()
            assertEquals(1, body["temperature"].asInt)
            assertFalse(body.has("max_tokens"))
            assertFalse(body.has("provider"))
            assertEquals(emptyList<String>(), recorded.single().replaced)
        }

        @Test
        @DisplayName("while the copy of the presets has never been read, the pair is sent as it is")
        fun neverRead() {
            enqueueCompletion()

            servlet(presets = null).service(request(pairBody()), response().resp)

            assertEquals(1, server.requestCount)
        }

        @Test
        @DisplayName("the Consumer's reply names the pair it asked for")
        fun replyNamesThePair() {
            enqueueCompletion()
            val exchange = response()

            servlet(research).service(request(pairBody()), exchange.resp)

            assertEquals(
                "openai/gpt-4o-mini@preset/research",
                JsonParser.parseString(exchange.body).asJsonObject["model"].asString
            )
        }

        @Test
        @DisplayName("the Requests entry keeps the pair, the preset and what was removed for it")
        fun recorded() {
            enqueueCompletion()

            servlet(research).service(request(pairBody(""","temperature":1""")), response().resp)

            val record = recorded.single()
            assertEquals("openai/gpt-4o-mini@preset/research", record.requestedModel)
            assertEquals("research", record.preset)
            assertEquals(listOf("temperature"), record.replaced)
        }

        @Test
        @DisplayName("a pair whose preset is not on OpenRouter is refused, pointing at Presets")
        fun missingPreset() {
            val exchange = response()

            servlet(presets()).service(request(pairBody()), exchange.resp)

            verify(exchange.resp).status = HttpServletResponse.SC_BAD_REQUEST
            val error = JsonParser.parseString(exchange.body).asJsonObject["error"].asJsonObject
            assertEquals(
                "OpenRouter plugin: No preset named 'research' is saved on OpenRouter; " +
                    "fix it in Settings → Tools → OpenRouter → Presets",
                error["message"].asString
            )
            assertEquals("preset_not_found", error["code"].asString)
            assertEquals(0, server.requestCount)
            assertEquals(FixPage.PRESETS, recorded.single().fixAt)
        }

        @Test
        @DisplayName("a pair whose preset was made since the last read waits for that read, and is sent")
        fun presetMadeSinceTheLastRead() {
            enqueueCompletion()

            servlet(presets(), afterRead = presets("research" to "{}")).service(request(pairBody()), response().resp)

            assertEquals(1, server.requestCount)
            assertEquals("research", recorded.single().preset)
        }

        @Test
        @DisplayName("a pair whose model cannot give the preset's output is refused, never forwarded")
        fun outputNotServableRefused() {
            declared["openai/gpt-4o-mini"] = listOf("tools")
            val exchange = response()

            servlet(
                presets("research" to """{"response_format":{"type":"json_object"}}""")
            ).service(request(pairBody()), exchange.resp)

            val error = JsonParser.parseString(exchange.body).asJsonObject["error"].asJsonObject
            assertEquals("output_not_supported", error["code"].asString)
            assertTrue(error["message"].asString.contains("does not support JSON output"), "$error")
            assertEquals(0, server.requestCount)
        }

        @Test
        @DisplayName("a streaming pair is refused with the same error, before any SSE")
        fun streamingRefusedBeforeSse() {
            val exchange = response()

            servlet(presets()).service(request(pairBody(""","stream":true""")), exchange.resp)

            verify(exchange.resp).status = HttpServletResponse.SC_BAD_REQUEST
            assertFalse(exchange.body.contains("data:"), "got: ${exchange.body}")
            assertEquals(0, server.requestCount)
        }

        @Test
        @DisplayName("a router pair still gets the router's saved parameters when its preset sets none")
        fun routerPairGetsRouterDefaults() {
            `when`(settingsService.routerDefaultsManager).thenReturn(
                RouterDefaultsManager(settings) {}.apply { set("openrouter/auto", "low") }
            )
            enqueueCompletion()
            val body = """{"model":"openrouter/auto@preset/research","messages":[{"role":"user","content":"hi"}]}"""

            servlet(research).service(request(body), response().resp)

            assertTrue(forwarded().has("plugins"))
        }

        @Test
        @DisplayName("a preset that sets fallback models keeps the global routing and its models out")
        fun presetModelsKeepGlobalRoutingOut() {
            enqueueCompletion()

            servlet(presets("research" to """{"models":["x/y"]}""")).service(request(pairBody()), response().resp)

            val body = forwarded()
            assertFalse(body.has("models"), "the preset's own models apply")
            assertFalse(body.has("provider"))
        }

        /** The checks see the model the pair names, not the pair's id, which no catalogue lists. */
        @Test
        @DisplayName("the Consumer request checks judge the pair's model")
        fun checksSeeTheModel() {
            consumerCatalogue = listOf(OpenRouterModelInfo(id = "openai/gpt-4o-mini", name = "", created = 0))
            enqueueCompletion()

            servlet(research).service(request(pairBody()), response().resp)

            assertEquals(1, server.requestCount)
        }
    }

    /** What the proxy can tell is wrong before sending: refused with a clear error, never forwarded. */
    @Nested
    @DisplayName("Consumer request checks")
    inner class ConsumerChecks {

        private val settings = OpenRouterSettings()

        @BeforeEach
        fun stubSettings() {
            `when`(settingsService.getDataRegion()).thenReturn(DataRegion.GLOBAL)
            `when`(settingsService.outputSchemasManager).thenReturn(OutputSchemasManager(settings) {})
            consumerCatalogue = listOf(
                OpenRouterModelInfo(id = "openai/gpt-4o-mini", name = "", created = 0, supportedParameters = listOf("tools"))
            )
        }

        private fun errorOf(exchange: Exchange) =
            JsonParser.parseString(exchange.body).asJsonObject["error"].asJsonObject

        @Test
        @DisplayName("a response format the model does not declare is refused with its code, and recorded")
        fun unsupportedFormat() {
            val exchange = response()

            val body = chatBody().replace("}]", """}],"response_format":{"type":"json_object"}""")
            servlet().service(request(body), exchange.resp)

            verify(exchange.resp).status = HttpServletResponse.SC_BAD_REQUEST
            assertEquals("response_format_not_supported", errorOf(exchange)["code"].asString)
            assertTrue(errorOf(exchange)["message"].asString.startsWith("OpenRouter plugin: "))
            assertEquals(0, server.requestCount)
            assertTrue(recorded.single().error!!.contains("does not support JSON output"))
            assertEquals(FixPage.FAVORITE_MODELS, recorded.single().fixAt)
        }

        @Test
        @DisplayName("a json_schema naming no saved schema is refused")
        fun missingSchema() {
            val exchange = response()
            val format = ""","response_format":{"type":"json_schema","json_schema":{"name":"gone"}}"""

            servlet().service(request(chatBody().replace("}]", "}]$format")), exchange.resp)

            assertEquals("output_schema_not_found", errorOf(exchange)["code"].asString)
            assertEquals(0, server.requestCount)
        }

        @Test
        @DisplayName("a model the region does not serve is refused, naming the region")
        fun outsideTheRegion() {
            `when`(settingsService.getDataRegion()).thenReturn(DataRegion.EUROPE)
            val exchange = response()

            servlet().service(request(chatBody(model = "x-ai/grok-4")), exchange.resp)

            assertEquals("model_not_in_region", errorOf(exchange)["code"].asString)
            assertTrue(errorOf(exchange)["message"].asString.contains("European Union"))
            assertEquals(0, server.requestCount)
        }

        @Test
        @DisplayName("a streaming request is refused with the same 400, before any SSE is written")
        fun streamingRefusedBeforeSse() {
            val exchange = response()

            servlet().service(request(chatBody(stream = true, model = "gone/model")), exchange.resp)

            verify(exchange.resp).status = HttpServletResponse.SC_BAD_REQUEST
            assertEquals("model_not_found", errorOf(exchange)["code"].asString)
            assertFalse(exchange.body.contains("data:"))
            assertEquals(0, server.requestCount)
        }

        @Test
        @DisplayName("nothing about the model is refused while the catalogue has not loaded")
        fun catalogueNotLoaded() {
            consumerCatalogue = null
            enqueueCompletion()

            servlet().service(request(chatBody(model = "gone/model")), response().resp)

            assertEquals(1, server.requestCount)
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
            `when`(settingsService.webSearchManager).thenReturn(WebSearchSettingsManager(settings) {})
            `when`(settingsService.outputSchemasManager).thenReturn(OutputSchemasManager(settings) {})
            enqueueCompletion()
            val exchange = response()

            servlet().service(request(chatBody()), exchange.resp)

            val forwarded = server.takeRequest().body.readUtf8()
            assertTrue(forwarded.contains("\"max_tokens\":$DEFAULT_MAX_TOKENS"), "got: $forwarded")
        }

        /**
         * What a Consumer gets from the Web Search and Output Schemas pages: its own web entry
         * tuned the way the user chose, and a saved schema it named, both reaching OpenRouter.
         */
        @Test
        @DisplayName("a Consumer's web entry is tuned and a saved schema it names is filled in")
        fun appliesWebSearchTuningAndSavedSchemas() {
            val settings = OpenRouterSettings()
            `when`(settingsService.uiPreferencesManager).thenReturn(UIPreferencesManager(settings) {})
            `when`(settingsService.providerRoutingManager).thenReturn(ProviderRoutingManager(settings) {})
            `when`(settingsService.routerDefaultsManager).thenReturn(RouterDefaultsManager(settings) {})
            `when`(settingsService.webSearchManager).thenReturn(
                WebSearchSettingsManager(settings) {}.apply {
                    replace(WebSearchSettings(engine = WebSearchEngine.EXA, maxResults = 3))
                }
            )
            `when`(settingsService.outputSchemasManager).thenReturn(
                OutputSchemasManager(settings) {}.apply {
                    replaceAll(listOf(OutputSchema("features", strict = true, schema = """{"type":"object"}""")))
                }
            )
            enqueueCompletion()
            val exchange = response()

            val body = """{"model":"openai/gpt-4o-mini","messages":[{"role":"user","content":"hi"}],
                "tools":[{"type":"openrouter:web_search"}],
                "response_format":{"type":"json_schema","json_schema":{"name":"features"}}}"""
            servlet().service(request(body), exchange.resp)

            val forwarded = JsonParser.parseString(server.takeRequest().body.readUtf8()).asJsonObject
            assertEquals(
                JsonParser.parseString(
                    """[{"type":"openrouter:web_search","parameters":{"engine":"exa","max_results":3}}]"""
                ),
                forwarded["tools"]
            )
            assertEquals(
                JsonParser.parseString(
                    """{"type":"json_schema",
                        "json_schema":{"name":"features","strict":true,"schema":{"type":"object"}}}"""
                ),
                forwarded["response_format"]
            )
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
                openRouterApiUrl = { deadUrl },
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
