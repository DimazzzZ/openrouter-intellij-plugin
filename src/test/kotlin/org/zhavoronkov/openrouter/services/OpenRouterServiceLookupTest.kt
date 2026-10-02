package org.zhavoronkov.openrouter.services

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.OkHttpClient
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

    /** A client whose every call fails with an [IOException] that has no message. */
    private fun failingClient(): OkHttpClient =
        OkHttpClient.Builder().addInterceptor(Interceptor { throw IOException() }).build()

    companion object {
        private fun case(name: String, call: suspend (OpenRouterService) -> ApiResult<*>) =
            org.junit.jupiter.params.provider.Arguments.of(name, call)

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
}
