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

@DisplayName("OpenRouter Service API Key CRUD Tests")
class OpenRouterServiceApiKeyCrudTest {

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
    fun `createApiKey should parse success response`() = runBlocking {
        val response = """
            {
              "data": {
                "name": "IntelliJ",
                "label": "sk-or-v1-123",
                "limit": 100.0,
                "usage": 0.0,
                "disabled": false,
                "created_at": "2025-01-01",
                "updated_at": null,
                "hash": "hash-1"
              },
              "key": "sk-or-v1-new-key"
            }
        """.trimIndent()

        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(response)
        )

        val result = service.createApiKey("IntelliJ", limit = 100.0)

        assertTrue(result is ApiResult.Success)
        val success = result as ApiResult.Success
        assertEquals("IntelliJ", success.data.data.name)
        assertEquals("sk-or-v1-new-key", success.data.key)
    }

    @Test
    fun `createApiKey should return error on failure response`() = runBlocking {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(400)
                .setBody("Bad Request")
        )

        val result = service.createApiKey("BadKey")

        assertTrue(result is ApiResult.Error)
        val error = result as ApiResult.Error
        assertEquals(400, error.statusCode)
    }

    @Test
    fun `deleteApiKey should parse success response`() = runBlocking {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"deleted\": true}")
        )

        val result = service.deleteApiKey("hash-1")

        assertTrue(result is ApiResult.Success)
        val success = result as ApiResult.Success
        assertEquals(true, success.data.deleted)
    }

    @Test
    fun `deleteApiKey should return error on failure response`() = runBlocking {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(404)
                .setBody("Not Found")
        )

        val result = service.deleteApiKey("missing")

        assertTrue(result is ApiResult.Error)
        val error = result as ApiResult.Error
        assertEquals(404, error.statusCode)
    }
}
