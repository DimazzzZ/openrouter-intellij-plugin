package org.zhavoronkov.openrouter.services

import com.google.gson.Gson
import com.google.gson.JsonPrimitive
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
import org.zhavoronkov.openrouter.models.ChatCompletionRequest
import org.zhavoronkov.openrouter.models.ChatMessage
import org.zhavoronkov.openrouter.testing.OkHttpLeakSafeExtension

@Tag("functional")
@ExtendWith(OkHttpLeakSafeExtension::class)
@DisplayName("OpenRouter Service Branch Tests")
class OpenRouterServiceBranchTest {

    private lateinit var mockWebServer: MockWebServer
    private lateinit var mockSettingsService: OpenRouterSettingsService
    private lateinit var mockApiKeyManager: org.zhavoronkov.openrouter.services.settings.ApiKeySettingsManager
    private lateinit var service: OpenRouterService

    @BeforeEach
    fun setUp() {
        mockWebServer = MockWebServer()
        mockWebServer.start()
        mockSettingsService = mock(OpenRouterSettingsService::class.java)
        `when`(mockSettingsService.getApiKey()).thenReturn("sk-or-test")
        mockApiKeyManager = mock(org.zhavoronkov.openrouter.services.settings.ApiKeySettingsManager::class.java)
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

    private fun chatRequest() = ChatCompletionRequest(
        model = "openai/gpt-4o",
        messages = listOf(ChatMessage(role = "user", content = JsonPrimitive("Hi")))
    )

    @Test
    @DisplayName("createChatCompletion maps a non-2xx to an Error carrying the status code")
    fun chatCompletionHttpError() = runBlocking {
        mockWebServer.enqueue(
            MockResponse().setResponseCode(429).setBody("""{"error":{"message":"rate limited"}}""")
        )
        val result = service.createChatCompletion(chatRequest())
        assertTrue(result is ApiResult.Error, "expected error, got $result")
        assertEquals(429, (result as ApiResult.Error).statusCode)
    }

    @Test
    @DisplayName("createChatCompletion maps a malformed 2xx body to a parse Error")
    fun chatCompletionParseError() = runBlocking {
        mockWebServer.enqueue(
            MockResponse().setResponseCode(200).setHeader("Content-Type", "application/json")
                .setBody("{ not valid json ")
        )
        val result = service.createChatCompletion(chatRequest())
        assertTrue(result is ApiResult.Error, "expected parse error, got $result")
        assertNotNull((result as ApiResult.Error).throwable)
    }

    @Test
    @DisplayName("createChatCompletion returns Error when no API key is configured")
    fun chatCompletionNoKey() = runBlocking {
        `when`(mockApiKeyManager.getStoredApiKey()).thenReturn(null)
        val result = service.createChatCompletion(chatRequest())
        assertTrue(result is ApiResult.Error, "expected error, got $result")
        assertEquals("No API key configured", (result as ApiResult.Error).message)
    }

    @Test
    @DisplayName("getPreset maps a malformed 2xx body to a parse Error")
    fun getPresetParseError() = runBlocking {
        mockWebServer.enqueue(
            MockResponse().setResponseCode(200).setHeader("Content-Type", "application/json")
                .setBody("{ broken ")
        )
        val result = service.getPreset("email")
        assertTrue(result is ApiResult.Error, "expected parse error, got $result")
        assertNotNull((result as ApiResult.Error).throwable)
    }

    @Test
    @DisplayName("createOrUpdatePreset sends system_prompt and maps a malformed body to a parse Error")
    fun createPresetParseErrorWithSystemPrompt() = runBlocking {
        mockWebServer.enqueue(
            MockResponse().setResponseCode(200).setHeader("Content-Type", "application/json")
                .setBody("{ broken ")
        )
        val result = service.createOrUpdatePreset(
            slug = "email",
            config = mapOf("model" to "openai/gpt-4o"),
            systemPrompt = "be terse"
        )
        assertTrue(result is ApiResult.Error, "expected parse error, got $result")
        assertNotNull((result as ApiResult.Error).throwable)
        val recorded = mockWebServer.takeRequest()
        assertEquals("POST", recorded.method)
        assertEquals("/api/v1/presets/email/chat/completions", recorded.path)
        assertTrue(recorded.body.readUtf8().contains("system_prompt"))
    }

    @Test
    @DisplayName("createOrUpdatePreset maps a non-2xx to an Error carrying the status code")
    fun createPresetHttpError() = runBlocking {
        mockWebServer.enqueue(
            MockResponse().setResponseCode(500).setBody("""{"error":{"message":"boom"}}""")
        )
        val result = service.createOrUpdatePreset("email", mapOf("model" to "x"), systemPrompt = null)
        assertTrue(result is ApiResult.Error, "expected error, got $result")
        assertEquals(500, (result as ApiResult.Error).statusCode)
    }
}
