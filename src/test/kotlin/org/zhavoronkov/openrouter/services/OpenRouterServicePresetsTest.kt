package org.zhavoronkov.openrouter.services

import com.google.gson.Gson
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.zhavoronkov.openrouter.models.ApiResult
import org.zhavoronkov.openrouter.testing.OkHttpLeakSafeExtension

@Tag("functional")
@ExtendWith(OkHttpLeakSafeExtension::class)
@DisplayName("OpenRouter Service Presets Tests")
class OpenRouterServicePresetsTest {

    private lateinit var mockWebServer: MockWebServer
    private lateinit var mockSettingsService: OpenRouterSettingsService
    private lateinit var service: OpenRouterService

    @BeforeEach
    fun setUp() {
        mockWebServer = MockWebServer()
        mockWebServer.start()

        mockSettingsService = mock(OpenRouterSettingsService::class.java)
        `when`(mockSettingsService.getApiKey()).thenReturn("sk-or-test")
        val mockApiKeyManager = mock(org.zhavoronkov.openrouter.services.settings.ApiKeySettingsManager::class.java)
        `when`(mockApiKeyManager.getStoredApiKey()).thenReturn("sk-or-test")
        `when`(mockSettingsService.apiKeyManager).thenReturn(mockApiKeyManager)

        service = OpenRouterService(
            gson = Gson(),
            settingsService = mockSettingsService,
            baseUrlOverride = mockWebServer.url("/api/v1").toString().removeSuffix("/")
        )
    }

    @AfterEach
    fun tearDown() {
        service.dispose()
        mockWebServer.shutdown()
    }

    @Test
    @DisplayName("getPresets parses the list, uses API key auth on GET /presets")
    fun getPresetsReadPath() = runBlocking {
        val body = """
            {"data":[
              {"id":"1","name":"email","slug":"email","status":"active",
               "creator_user_id":null,"designated_version_id":"v1",
               "created_at":"2026-01-01T00:00:00Z","updated_at":null}
            ]}
        """.trimIndent()
        mockWebServer.enqueue(
            MockResponse().setResponseCode(200).setHeader("Content-Type", "application/json").setBody(body)
        )

        val result = service.getPresets()

        assertTrue(result is ApiResult.Success, "expected success, got $result")
        val list = (result as ApiResult.Success).data.data
        assertEquals(1, list.size)
        assertEquals("email", list[0].slug)

        val recorded = mockWebServer.takeRequest()
        assertEquals("GET", recorded.method)
        assertEquals("/api/v1/presets", recorded.path)
        assertEquals("Bearer sk-or-test", recorded.getHeader("Authorization"))
    }

    @Test
    @DisplayName("getPreset(slug) parses the designated version with untyped config")
    fun getPresetReadOne() = runBlocking {
        val body = """
            {"data":{"id":"1","name":"email","slug":"email","status":"active",
              "creator_user_id":"user_1","designated_version_id":"v1",
              "designated_version":{"id":"v1","version":2,"system_prompt":"be terse",
                "config":{"model":"openai/gpt-4o","temperature":0.3,"top_p":0.8}}}}
        """.trimIndent()
        mockWebServer.enqueue(
            MockResponse().setResponseCode(200).setHeader("Content-Type", "application/json").setBody(body)
        )

        val result = service.getPreset("email")

        assertTrue(result is ApiResult.Success, "expected success, got $result")
        val preset = (result as ApiResult.Success).data.data
        assertNotNull(preset.designatedVersion)
        val version = preset.designatedVersion!!
        assertEquals(2, version.version)
        assertEquals("be terse", version.systemPrompt)
        assertEquals("openai/gpt-4o", version.config?.get("model"))
        assertTrue(version.config?.containsKey("top_p") == true)

        val recorded = mockWebServer.takeRequest()
        assertEquals("/api/v1/presets/email", recorded.path)
    }

    @Test
    @DisplayName("createOrUpdatePreset POSTs a ChatRequest-shaped body preserving unknown config keys")
    fun createWritePath() = runBlocking {
        val response = """
            {"data":{"id":"1","name":"email","slug":"email","status":"active",
              "designated_version":{"id":"v3","version":3,"system_prompt":"be terse",
                "config":{"model":"openai/gpt-4o","temperature":0.3,"top_p":0.8,
                          "provider":{"order":["anthropic"]}}}}}
        """.trimIndent()
        mockWebServer.enqueue(
            MockResponse().setResponseCode(200).setHeader("Content-Type", "application/json").setBody(response)
        )

        val config: Map<String, Any?> = mapOf(
            "model" to "openai/gpt-4o",
            "temperature" to 0.3,
            "top_p" to 0.8,
            "provider" to mapOf("order" to listOf("anthropic"))
        )
        val result = service.createOrUpdatePreset("email", config, "be terse")

        assertTrue(result is ApiResult.Success, "expected success, got $result")
        val version = (result as ApiResult.Success).data.data.designatedVersion!!
        assertEquals(3, version.version)
        assertTrue(version.config?.containsKey("provider") == true, "unknown key preserved on read-back")

        val recorded = mockWebServer.takeRequest()
        assertEquals("POST", recorded.method)
        assertEquals("/api/v1/presets/email/chat/completions", recorded.path)
        assertEquals("Bearer sk-or-test", recorded.getHeader("Authorization"))
        val sent = recorded.body.readUtf8()
        assertTrue(sent.contains(""""model":"openai/gpt-4o""""), "model in body: $sent")
        assertTrue(sent.contains(""""top_p":0.8"""), "unknown config key sent verbatim: $sent")
        assertTrue(sent.contains(""""order":["anthropic"]"""), "nested config preserved: $sent")
    }

    @Test
    @DisplayName("getPresets surfaces auth failure as ApiResult.Error, does not throw")
    fun getPresetsAuthFailure() = runBlocking {
        mockWebServer.enqueue(
            MockResponse().setResponseCode(401).setBody("""{"error":{"message":"No auth"}}""")
        )
        val result = service.getPresets()
        assertTrue(result is ApiResult.Error, "expected error, got $result")
        assertEquals(401, (result as ApiResult.Error).statusCode)
    }
}
