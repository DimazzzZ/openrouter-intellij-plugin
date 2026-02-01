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

@DisplayName("OpenRouter Service Usage Endpoint Tests")
class OpenRouterServiceUsageEndpointsTest {

    private lateinit var mockWebServer: MockWebServer
    private lateinit var mockSettingsService: OpenRouterSettingsService
    private lateinit var service: OpenRouterService

    @BeforeEach
    fun setUp() {
        mockWebServer = MockWebServer()
        mockWebServer.start()

        mockSettingsService = mock(OpenRouterSettingsService::class.java)
        `when`(mockSettingsService.getProvisioningKey()).thenReturn("pk-test")

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
    fun `getCredits should parse response`() = runBlocking {
        val response = """
            {
              "data": {
                "total_credits": 100.0,
                "total_usage": 25.0
              }
            }
        """.trimIndent()

        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(response)
        )

        val result = service.getCredits()

        assertTrue(result is ApiResult.Success)
        val success = result as ApiResult.Success
        assertEquals(100.0, success.data.data.totalCredits)
        assertEquals(25.0, success.data.data.totalUsage)
    }

    @Test
    fun `getActivity should parse response`() = runBlocking {
        val response = """
            {
              "data": [
                {
                  "date": "2025-01-01",
                  "model": "openai/gpt-4",
                  "usage": 1.25,
                  "requests": 2,
                  "prompt_tokens": 10,
                  "completion_tokens": 20,
                  "reasoning_tokens": 0
                }
              ]
            }
        """.trimIndent()

        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(response)
        )

        val result = service.getActivity()

        assertTrue(result is ApiResult.Success)
        val success = result as ApiResult.Success
        assertEquals(1, success.data.data.size)
        assertEquals("openai/gpt-4", success.data.data.first().model)
    }
}
