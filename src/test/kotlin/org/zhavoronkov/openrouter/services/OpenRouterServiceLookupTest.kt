package org.zhavoronkov.openrouter.services

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.ResponseBody.Companion.toResponseBody
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.junit.jupiter.params.provider.MethodSource
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.zhavoronkov.openrouter.models.ApiResult
import org.zhavoronkov.openrouter.models.ChatCompletionRequest
import org.zhavoronkov.openrouter.models.ChatMessage
import org.zhavoronkov.openrouter.models.DataRegion
import org.zhavoronkov.openrouter.services.settings.ApiKeySettingsManager
import org.zhavoronkov.openrouter.testing.OkHttpLeakSafeExtension
import java.io.IOException

/**
 * The lookups [OpenRouterService] answers with null or an error rather than a value: the provider
 * of a generation, a key's own info, a preset's designated version, and the models of a region.
 * Each is driven against a local [MockWebServer], so every answer is one the test chose.
 */
@ExtendWith(OkHttpLeakSafeExtension::class)
@DisplayName("OpenRouter Service Lookup Tests")
class OpenRouterServiceLookupTest {

    private lateinit var mockWebServer: MockWebServer
    private lateinit var mockSettingsService: OpenRouterSettingsService
    private lateinit var mockApiKeyManager: ApiKeySettingsManager
    private val services = mutableListOf<OpenRouterService>()
    private val clients = mutableListOf<OkHttpClient>()

    @BeforeEach
    fun setUp() {
        mockWebServer = MockWebServer()
        mockWebServer.start()

        mockSettingsService = mock(OpenRouterSettingsService::class.java)
        `when`(mockSettingsService.getApiKey()).thenReturn("sk-or-test")
        `when`(mockSettingsService.getProvisioningKey()).thenReturn("pk-test")
        mockApiKeyManager = mock(ApiKeySettingsManager::class.java)
        `when`(mockApiKeyManager.getStoredApiKey()).thenReturn("sk-or-test")
        `when`(mockSettingsService.apiKeyManager).thenReturn(mockApiKeyManager)
    }

    @AfterEach
    fun tearDown() {
        services.forEach { it.dispose() }
        clients.forEach {
            it.dispatcher.executorService.shutdown()
            it.connectionPool.evictAll()
        }
        runCatching { mockWebServer.shutdown() }
    }

    private fun service(client: OkHttpClient = OkHttpClient.Builder().build()): OpenRouterService {
        clients += client
        return OpenRouterService(
            gson = Gson(),
            client = client,
            settingsService = mockSettingsService,
            baseUrlOverride = mockWebServer.url("/api/v1").toString().removeSuffix("/")
        ).also { services += it }
    }

    private fun enqueue(code: Int, body: String) {
        mockWebServer.enqueue(
            MockResponse().setResponseCode(code).setHeader("Content-Type", "application/json").setBody(body)
        )
    }

    @Nested
    @DisplayName("getGenerationProvider")
    inner class GenerationProvider {

        @Test
        fun `names the provider the generation record carries`() = runBlocking {
            enqueue(200, """{"data":{"id":"gen-1","provider_name":"Anthropic"}}""")

            assertEquals("Anthropic", service().getGenerationProvider("gen-1"))
            val recorded = mockWebServer.takeRequest()
            assertEquals("/api/v1/generation?id=gen-1", recorded.path)
            assertEquals("Bearer sk-or-test", recorded.getHeader("Authorization"))
        }

        @Test
        fun `is null while OpenRouter has no record yet`() = runBlocking {
            enqueue(404, """{"error":{"message":"Generation not found"}}""")

            assertNull(service().getGenerationProvider("gen-1"))
        }

        @ParameterizedTest(name = "[{index}] {0}")
        @ValueSource(
            strings = [
                """{"data":{"id":"gen-1"}}""",
                """{"data":{"provider_name":{"name":"Anthropic"}}}""",
                """{"other":true}""",
                """["not","an","object"]""",
                """{"data":"a string, not an object"}""",
                """{"data":{"provider_name":"""
            ]
        )
        fun `is null for a body it cannot read a provider from`(body: String) = runBlocking {
            enqueue(200, body)

            assertNull(service().getGenerationProvider("gen-1"))
        }

        @Test
        fun `is null when the request fails in transport`() = runBlocking {
            assertNull(service(failingClient()).getGenerationProvider("gen-1"))
        }
    }

    @Nested
    @DisplayName("fetchKeyInfo")
    inner class KeyInfo {

        @Test
        fun `asks for a blank key without calling OpenRouter`() = runBlocking {
            val result = service().fetchKeyInfo("  ")

            assertEquals("Key is required", (result as ApiResult.Error).message)
            assertEquals(0, mockWebServer.requestCount)
        }

        @Test
        fun `reads the key's own info with that key`() = runBlocking {
            enqueue(200, """{"data":{"label":"sk-or-v1-abc","usage":1.5,"allowed_data_regions":["eu"]}}""")

            val result = service().fetchKeyInfo("sk-or-other")

            assertEquals("sk-or-v1-abc", (result as ApiResult.Success).data.data.label)
            val recorded = mockWebServer.takeRequest()
            assertEquals("/api/v1/key", recorded.path)
            assertEquals("Bearer sk-or-other", recorded.getHeader("Authorization"))
        }

        @Test
        fun `reports a body that is not JSON as a parse error`() = runBlocking {
            enqueue(200, "{not json")

            val result = service().fetchKeyInfo("sk-or-other") as ApiResult.Error

            assertEquals("Failed to parse response", result.message)
            assertEquals(200, result.statusCode)
        }

        @Test
        fun `reports a refusal with OpenRouter's own message and status`() = runBlocking {
            enqueue(401, """{"error":{"message":"User not found.","code":401}}""")

            val result = service().fetchKeyInfo("sk-or-other") as ApiResult.Error

            assertEquals("User not found.", result.message)
            assertEquals(401, result.statusCode)
        }

        @Test
        fun `falls back to its own message for a refusal with an empty body`() = runBlocking {
            enqueue(500, "")

            val result = service().fetchKeyInfo("sk-or-other") as ApiResult.Error

            assertEquals("Failed to read key info", result.message)
        }
    }

    @Nested
    @DisplayName("getPresetVersionJson")
    inner class PresetVersion {

        @Test
        fun `is null without a stored API key, and asks nothing`() = runBlocking {
            `when`(mockApiKeyManager.getStoredApiKey()).thenReturn("")

            assertNull(service().getPresetVersionJson("probe"))
            assertEquals(0, mockWebServer.requestCount)
        }

        @ParameterizedTest(name = "[{index}] {0}")
        @ValueSource(
            strings = [
                """{"data":{"slug":"probe"}}""",
                """{"data":{"designated_version":"v1"}}""",
                """{"other":true}""",
                """{"data":"a string, not an object"}""",
                """["not","an","object"]""",
                """{"data":{"""
            ]
        )
        fun `is null for a body without a readable designated version`(body: String) = runBlocking {
            enqueue(200, body)

            assertNull(service().getPresetVersionJson("probe"))
        }

        @Test
        fun `is null when the request fails in transport`() = runBlocking {
            assertNull(service(failingClient()).getPresetVersionJson("probe"))
        }
    }

    @Nested
    @DisplayName("getModelsInRegion")
    inner class ModelsInRegion {

        @ParameterizedTest
        @EnumSource(DataRegion::class)
        fun `asks the global endpoint, with the region as a query value when it has one`(region: DataRegion) =
            runBlocking {
                enqueue(200, """{"data":[{"id":"openai/gpt-4o","name":"GPT-4o","created":1}]}""")

                val result = service().getModelsInRegion(region)

                assertEquals("openai/gpt-4o", (result as ApiResult.Success).data.data.single().id)
                val expected = region.queryValue?.let { "/api/v1/models?region=$it" } ?: "/api/v1/models"
                assertEquals(expected, mockWebServer.takeRequest().path)
            }
    }

    @Test
    @DisplayName("without a base URL override, a region's models are asked of the global host")
    fun modelsInRegionAskTheGlobalHost() = runBlocking {
        var asked: String? = null
        val answering = OkHttpClient.Builder().addInterceptor(
            Interceptor { chain ->
                asked = chain.request().url.toString()
                okhttp3.Response.Builder()
                    .request(chain.request())
                    .protocol(okhttp3.Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body("""{"data":[]}""".toResponseBody())
                    .build()
            }
        ).build()
        clients += answering
        val service = OpenRouterService(gson = Gson(), client = answering, settingsService = mockSettingsService)
            .also { services += it }

        service.getModelsInRegion(DataRegion.EUROPE)

        assertEquals("${DataRegion.GLOBAL.baseUrl}/models?region=eu", asked)
    }

    @Test
    @DisplayName("createOrUpdatePreset with a JsonObject config sends its fields as they are")
    fun createOrUpdatePresetFromJson() = runBlocking {
        enqueue(200, """{"data":{"id":"1","name":"email","slug":"email","status":"active"}}""")
        val config = JsonObject().apply {
            addProperty("model", "openai/gpt-4o")
            add("provider", JsonObject().apply { addProperty("sort", "price") })
        }

        val result = service().createOrUpdatePreset("email", config, systemPrompt = null)

        assertTrue(result is ApiResult.Success, "expected success, got $result")
        val sent = mockWebServer.takeRequest().body.readUtf8()
        assertTrue(sent.contains(""""model":"openai/gpt-4o""""), sent)
        assertTrue(sent.contains(""""sort":"price""""), sent)
    }

    /**
     * Every call that turns an IOException into an error falls back to "Network error" when the
     * exception carries no message - which is what an [IOException] with no argument does.
     */
    @ParameterizedTest(name = "{0}")
    @MethodSource("org.zhavoronkov.openrouter.services.OpenRouterServiceLookupTest#messagelessFailures")
    @DisplayName("a transport failure with no message reads as Network error")
    fun messagelessTransportFailure(name: String, call: suspend (OpenRouterService) -> ApiResult<*>) = runBlocking {
        val result = call(service(failingClient()))

        assertEquals("Network error", (result as ApiResult.Error).message, name)
    }

    /** A client whose every call fails with [failure] - by default an [IOException] that has no message. */
    private fun failingClient(failure: IOException = IOException()): OkHttpClient =
        OkHttpClient.Builder().addInterceptor(Interceptor { throw failure }).build()

    companion object {
        private fun case(name: String, call: suspend (OpenRouterService) -> ApiResult<*>) =
            org.junit.jupiter.params.provider.Arguments.of(name, call)

        @JvmStatic
        fun publicEndpoints() = listOf(
            case("getModels") { it.getModels() },
            case("getAllModels") { it.getAllModels() },
            case("getModelsInRegion") { it.getModelsInRegion(DataRegion.EUROPE) },
            case("getModelsCount") { it.getModelsCount() },
            case("getProviders") { it.getProviders() }
        )

        @JvmStatic
        fun messagelessFailures() = listOf(
            case("getGenerationStats") { it.getGenerationStats("gen-1") },
            case("createChatCompletion") {
                it.createChatCompletion(
                    ChatCompletionRequest(
                        model = "openai/gpt-4o-mini",
                        messages = listOf(ChatMessage(role = "user", content = JsonPrimitive("hi")))
                    )
                )
            },
            case("testApiKey") { it.testApiKey("sk-or-test") },
            case("getApiKeysList") { it.getApiKeysList("pk-test") },
            case("fetchKeyInfo") { it.fetchKeyInfo("sk-or-test") },
            case("createApiKey") { it.createApiKey("name") },
            case("deleteApiKey") { it.deleteApiKey("hash") },
            case("getCredits") { it.getCredits() },
            case("getActivity") { it.getActivity() },
            case("getProviders") { it.getProviders() },
            case("exchangeAuthCode") { it.exchangeAuthCode("code", "verifier") },
            case("getPresets") { it.getPresets() },
            case("getPreset") { it.getPreset("email") },
            case("createOrUpdatePreset") { it.createOrUpdatePreset("email", mapOf("model" to "x"), null) }
        )
    }

    @Nested
    @DisplayName("Answers that are neither the usual value nor the usual error")
    inner class UnusualAnswers {

        @Test
        fun `a key info refusal without an error message shows the body itself`() = runBlocking {
            enqueue(403, """{"detail":"forbidden"}""")

            val result = service().fetchKeyInfo("sk-or-other") as ApiResult.Error

            assertEquals("""{"detail":"forbidden"}""", result.message)
            assertEquals(403, result.statusCode)
        }

        @Test
        fun `a key info transport failure keeps its message`() = runBlocking {
            val result = service(failingClient(IOException("connection reset"))).fetchKeyInfo("sk-or-other")

            assertEquals("connection reset", (result as ApiResult.Error).message)
        }

        @Test
        fun `a preset version is null without any stored API key`() = runBlocking {
            `when`(mockApiKeyManager.getStoredApiKey()).thenReturn(null)

            assertNull(service().getPresetVersionJson("probe"))
            assertEquals(0, mockWebServer.requestCount)
        }

        @Test
        fun `a preset version is null for a body without data`() = runBlocking {
            enqueue(200, """{"other":true}""")

            assertNull(service().getPresetVersionJson("probe"))
        }

        @Test
        fun `a refused auth code exchange shows OpenRouter's body`() = runBlocking {
            enqueue(400, """{"error":"invalid code"}""")

            val result = service().exchangeAuthCode("code", "verifier") as ApiResult.Error

            assertEquals("""{"error":"invalid code"}""", result.message)
            assertEquals(400, result.statusCode)
        }

        @Test
        fun `a refused auth code exchange with an empty body names its status`() = runBlocking {
            enqueue(400, "")

            val result = service().exchangeAuthCode("code", "verifier") as ApiResult.Error

            assertEquals("Failed to exchange auth code (HTTP 400)", result.message)
        }
    }

    @Nested
    @DisplayName("Public endpoints that fail")
    inner class PublicEndpointFailures {

        @ParameterizedTest(name = "{0}")
        @MethodSource("org.zhavoronkov.openrouter.services.OpenRouterServiceLookupTest#publicEndpoints")
        fun `a refusal shows its body, or a fallback naming what was fetched`(
            name: String,
            call: suspend (OpenRouterService) -> ApiResult<*>
        ) = runBlocking {
            enqueue(503, "upstream down")
            enqueue(503, "")
            val service = service()

            val withBody = call(service) as ApiResult.Error
            val blank = call(service) as ApiResult.Error

            assertEquals("upstream down", withBody.message, name)
            assertEquals(503, withBody.statusCode, name)
            assertTrue(blank.message.startsWith("Failed to fetch "), "$name: ${blank.message}")
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("org.zhavoronkov.openrouter.services.OpenRouterServiceLookupTest#publicEndpoints")
        fun `a transport failure keeps its message, or reads as Network error without one`(
            name: String,
            call: suspend (OpenRouterService) -> ApiResult<*>
        ) = runBlocking {
            val withMessage = call(service(failingClient(IOException("connection reset")))) as ApiResult.Error
            val without = call(service(failingClient())) as ApiResult.Error

            assertEquals("connection reset", withMessage.message, name)
            assertEquals("Network error", without.message, name)
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("org.zhavoronkov.openrouter.services.OpenRouterServiceLookupTest#publicEndpoints")
        fun `a body that is not JSON is a parse error`(
            name: String,
            call: suspend (OpenRouterService) -> ApiResult<*>
        ) = runBlocking {
            enqueue(200, "{not json")

            val result = call(service()) as ApiResult.Error

            assertTrue(result.message.startsWith("Failed to parse "), "$name: ${result.message}")
            assertEquals(200, result.statusCode, name)
        }
    }
}
