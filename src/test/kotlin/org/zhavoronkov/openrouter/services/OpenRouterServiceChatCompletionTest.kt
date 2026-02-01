package org.zhavoronkov.openrouter.services

import com.google.gson.Gson
import com.google.gson.JsonPrimitive
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.zhavoronkov.openrouter.models.ApiResult
import org.zhavoronkov.openrouter.models.ChatCompletionRequest
import org.zhavoronkov.openrouter.models.ChatMessage

@DisplayName("OpenRouter Service Chat Completion Tests")
class OpenRouterServiceChatCompletionTest {

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
        mockWebServer.shutdown()
    }

    @Test
    fun `createChatCompletion should parse successful response`() = runBlocking {
        val response = """
            {
              "id": "cmpl-1",
              "object": "chat.completion",
              "created": 1700000000,
              "model": "openai/gpt-4",
              "choices": [
                {
                  "index": 0,
                  "message": {
                    "role": "assistant",
                    "content": "Hello!"
                  },
                  "finish_reason": "stop"
                }
              ],
              "usage": {
                "prompt_tokens": 10,
                "completion_tokens": 5,
                "total_tokens": 15
              }
            }
        """.trimIndent()

        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(response)
        )

        val request = ChatCompletionRequest(
            model = "openai/gpt-4",
            messages = listOf(ChatMessage(role = "user", content = JsonPrimitive("Hi")))
        )

        val result = service.createChatCompletion(request)

        assertTrue(result is ApiResult.Success)
        val success = result as ApiResult.Success
        assertEquals("cmpl-1", success.data.id)
        assertEquals(1, success.data.choices?.size)
    }
}
