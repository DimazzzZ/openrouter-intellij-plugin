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
import org.zhavoronkov.openrouter.models.ApiResult

@DisplayName("OpenRouter Service Public Endpoint Tests")
class OpenRouterServicePublicEndpointsTest {

    private lateinit var mockWebServer: MockWebServer
    private lateinit var service: OpenRouterService

    @BeforeEach
    fun setUp() {
        mockWebServer = MockWebServer()
        mockWebServer.start()
        service = OpenRouterService(
            gson = Gson(),
            settingsService = mock(OpenRouterSettingsService::class.java),
            baseUrlOverride = mockWebServer.url("/api/v1").toString().removeSuffix("/")
        )
    }

    @AfterEach
    fun tearDown() {
        mockWebServer.shutdown()
    }

    @Test
    fun `getModels should parse response`() = runBlocking {
        val response = """
            {
              "data": [
                {
                  "id": "openai/gpt-4",
                  "name": "GPT-4",
                  "created": 1700000000
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

        val result = service.getModels()

        assertTrue(result is ApiResult.Success)
        val success = result as ApiResult.Success
        assertEquals(1, success.data.data.size)
        assertEquals("openai/gpt-4", success.data.data.first().id)
    }

    @Test
    fun `getProviders should parse response`() = runBlocking {
        val response = """
            {
              "data": [
                {
                  "name": "OpenAI",
                  "slug": "openai",
                  "privacy_policy_url": "https://example.com/privacy",
                  "terms_of_service_url": "https://example.com/terms",
                  "status_page_url": "https://status.example.com"
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

        val result = service.getProviders()

        assertTrue(result is ApiResult.Success)
        val success = result as ApiResult.Success
        assertEquals(1, success.data.data.size)
        assertEquals("openai", success.data.data.first().slug)
    }
}
