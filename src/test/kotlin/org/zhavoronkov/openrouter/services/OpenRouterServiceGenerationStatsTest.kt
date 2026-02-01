package org.zhavoronkov.openrouter.services

import com.google.gson.Gson
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

@DisplayName("OpenRouter Service Generation Stats Tests")
class OpenRouterServiceGenerationStatsTest {

    private lateinit var mockWebServer: MockWebServer
    private lateinit var mockSettingsService: OpenRouterSettingsService
    private lateinit var service: OpenRouterService

    @BeforeEach
    fun setUp() {
        mockWebServer = MockWebServer()
        mockWebServer.start()

        mockSettingsService = mock(OpenRouterSettingsService::class.java)
        `when`(mockSettingsService.getApiKey()).thenReturn("sk-or-test")

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
    fun `getGenerationStats should parse response`() = runBlocking {
        val response = """
            {
              "id": "gen-1",
              "model": "openai/gpt-4",
              "created": 1700000000,
              "total_cost": 0.42,
              "usage": {
                "prompt_tokens": 10,
                "completion_tokens": 20,
                "total_tokens": 30
              }
            }
        """.trimIndent()

        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(response)
        )

        val result = service.getGenerationStats("gen-1")

        assertTrue(result is ApiResult.Success)
        val success = result as ApiResult.Success
        assertEquals("gen-1", success.data.id)
        assertEquals(30, success.data.usage?.totalTokens)
    }
}
