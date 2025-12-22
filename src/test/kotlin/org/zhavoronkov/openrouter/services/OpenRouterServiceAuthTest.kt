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
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.zhavoronkov.openrouter.models.ApiResult
import org.zhavoronkov.openrouter.models.ExchangeAuthCodeResponse
import org.zhavoronkov.openrouter.utils.OpenRouterRequestBuilder
import java.io.IOException

@DisplayName("OpenRouter Service Authentication Tests")
class OpenRouterServiceAuthTest {

    private lateinit var mockWebServer: MockWebServer
    private lateinit var mockSettingsService: OpenRouterSettingsService
    private lateinit var service: OpenRouterService
    private lateinit var gson: Gson

    @BeforeEach
    fun setUp() {
        mockWebServer = MockWebServer()
        mockWebServer.start()

        mockSettingsService = mock(OpenRouterSettingsService::class.java)
        `when`(mockSettingsService.getProvisioningKey()).thenReturn("test-provisioning-key")
        `when`(mockSettingsService.getApiKey()).thenReturn("test-api-key")

        gson = Gson()
        service = OpenRouterService(gson = gson, settingsService = mockSettingsService)
    }

    @AfterEach
    fun tearDown() {
        mockWebServer.shutdown()
    }

    @Nested
    @DisplayName("PKCE Auth Code Exchange Tests")
    inner class AuthCodeExchangeTests {

        @Test
        @DisplayName("Should successfully exchange auth code for API key")
        fun testExchangeAuthCodeSuccess() = runBlocking {
            val mockResponse = """
                {
                    "key": "sk-or-v1-new-api-key-1234567890"
                }
            """.trimIndent()

            mockWebServer.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "application/json")
                    .setBody(mockResponse)
            )

            // Update service to use mock server URL
            val testService = OpenRouterService(
                gson = gson,
                settingsService = mockSettingsService,
                baseUrlOverride = mockWebServer.url("/").toString()
            )

            val result = testService.exchangeAuthCode("test-auth-code", "test-code-verifier")

            assertTrue(result is ApiResult.Success)
            val successResult = result as ApiResult.Success
            assertEquals(200, successResult.statusCode)
            assertEquals("sk-or-v1-new-api-key-1234567890", successResult.data.key)
        }

        @Test
        @DisplayName("Should handle auth code exchange failure")
        fun testExchangeAuthCodeFailure() = runBlocking {
            val errorResponse = """
                {
                    "error": {
                        "code": 400,
                        "message": "Invalid code or verifier",
                        "metadata": {
                            "type": "invalid_request"
                        }
                    }
                }
            """.trimIndent()

            mockWebServer.enqueue(
                MockResponse()
                    .setResponseCode(400)
                    .setHeader("Content-Type", "application/json")
                    .setBody(errorResponse)
            )

            val testService = OpenRouterService(
                gson = gson,
                settingsService = mockSettingsService,
                baseUrlOverride = mockWebServer.url("/").toString()
            )

            val result = testService.exchangeAuthCode("invalid-code", "invalid-verifier")

            assertTrue(result is ApiResult.Error)
            val errorResult = result as ApiResult.Error
            assertEquals(400, errorResult.statusCode)
            assertTrue(errorResult.message.contains("Invalid code or verifier"))
        }

        @Test
        @DisplayName("Should handle network errors during auth code exchange")
        fun testExchangeAuthCodeNetworkError() = runBlocking {
            // Simulate network error by not enqueuing any response
            val testService = OpenRouterService(
                gson = gson,
                settingsService = mockSettingsService,
                baseUrlOverride = mockWebServer.url("/").toString()
            )

            val result = testService.exchangeAuthCode("test-code", "test-verifier")

            assertTrue(result is ApiResult.Error)
            val errorResult = result as ApiResult.Error
            assertTrue(errorResult.message.contains("Network error") ||
                      errorResult.message.contains("Unable to reach"))
        }
    }

    @Nested
    @DisplayName("API Key Validation Tests")
    inner class ApiKeyValidationTests {

        @Test
        @DisplayName("Should validate API key successfully")
        fun testValidateApiKeySuccess() = runBlocking {
            val mockResponse = """
                {
                    "data": {
                        "key": "sk-or-v1-valid-key",
                        "name": "Test Key",
                        "usage": 0.0,
                        "limit": 100.0,
                        "disabled": false
                    }
                }
            """.trimIndent()

            mockWebServer.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "application/json")
                    .setBody(mockResponse)
            )

            val testService = OpenRouterService(
                gson = gson,
                settingsService = mockSettingsService,
                baseUrlOverride = mockWebServer.url("/").toString()
            )

            val result = testService.testApiKey("valid-api-key")

            assertTrue(result is ApiResult.Success)
            val successResult = result as ApiResult.Success
            assertEquals(true, successResult.data)
        }

        @Test
        @DisplayName("Should handle invalid API key")
        fun testValidateInvalidApiKey() = runBlocking {
            val errorResponse = """
                {
                    "error": {
                        "code": 401,
                        "message": "Invalid API key",
                        "metadata": {
                            "type": "authentication_error"
                        }
                    }
                }
            """.trimIndent()

            mockWebServer.enqueue(
                MockResponse()
                    .setResponseCode(401)
                    .setHeader("Content-Type", "application/json")
                    .setBody(errorResponse)
            )

            val testService = OpenRouterService(
                gson = gson,
                settingsService = mockSettingsService,
                baseUrlOverride = mockWebServer.url("/").toString()
            )

            val result = testService.testApiKey("invalid-api-key")

            assertTrue(result is ApiResult.Error)
            val errorResult = result as ApiResult.Error
            assertEquals(401, errorResult.statusCode)
            assertTrue(errorResult.message.contains("Invalid API key"))
        }
    }

    @Nested
    @DisplayName("Provisioning Key Tests")
    inner class ProvisioningKeyTests {

        @Test
        @DisplayName("Should fetch API keys list with provisioning key")
        fun testGetApiKeysListWithProvisioningKey() = runBlocking {
            val mockResponse = """
                {
                    "data": [
                        {
                            "name": "IntelliJ IDEA Plugin",
                            "usage": 12.75,
                            "limit": 100.0,
                            "disabled": false,
                            "hash": "abc123"
                        },
                        {
                            "name": "Development Key",
                            "usage": 5.25,
                            "limit": 50.0,
                            "disabled": false,
                            "hash": "def456"
                        }
                    ]
                }
            """.trimIndent()

            mockWebServer.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "application/json")
                    .setBody(mockResponse)
            )

            val testService = OpenRouterService(
                gson = gson,
                settingsService = mockSettingsService,
                baseUrlOverride = mockWebServer.url("/").toString()
            )

            val result = testService.getApiKeysList("test-provisioning-key")

            assertTrue(result is ApiResult.Success)
            val successResult = result as ApiResult.Success
            assertEquals(2, successResult.data.data.size)
            assertEquals("IntelliJ IDEA Plugin", successResult.data.data[0].name)
        }

        @Test
        @DisplayName("Should handle invalid provisioning key")
        fun testInvalidProvisioningKey() = runBlocking {
            val errorResponse = """
                {
                    "error": {
                        "code": 401,
                        "message": "Invalid provisioning key",
                        "metadata": {
                            "type": "authentication_error"
                        }
                    }
                }
            """.trimIndent()

            mockWebServer.enqueue(
                MockResponse()
                    .setResponseCode(401)
                    .setHeader("Content-Type", "application/json")
                    .setBody(errorResponse)
            )

            val testService = OpenRouterService(
                gson = gson,
                settingsService = mockSettingsService,
                baseUrlOverride = mockWebServer.url("/").toString()
            )

            val result = testService.getApiKeysList("invalid-provisioning-key")

            assertTrue(result is ApiResult.Error)
            val errorResult = result as ApiResult.Error
            assertEquals(401, errorResult.statusCode)
            assertTrue(errorResult.message.contains("Invalid provisioning key"))
        }
    }

    // Helper class to allow custom base URL for testing
    private class TestableOpenRouterService(
        private val gson: Gson,
        private val settingsService: OpenRouterSettingsService,
        private val baseUrl: String = "https://openrouter.ai/api/v1"
    ) : org.zhavoronkov.openrouter.services.OpenRouterService(gson, settingsService) {

        override fun getBaseUrl(): String = baseUrl
    }
}
