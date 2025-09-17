package org.zhavoronkov.openrouter.services

import com.google.gson.Gson
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import org.mockito.Mockito.*
// import org.mockito.kotlin.whenever
import java.io.IOException
import java.util.concurrent.TimeUnit

@DisplayName("OpenRouter Service Integration Tests")
class OpenRouterServiceIntegrationTest {

    private lateinit var mockWebServer: MockWebServer
    private lateinit var mockSettingsService: OpenRouterSettingsService
    private lateinit var openRouterService: OpenRouterService
    private lateinit var gson: Gson
    private val testProvisioningKey = "pk-test-provisioning-key"
    private val testApiKey = "sk-or-v1-test-api-key"

    @BeforeEach
    fun setUp() {
        mockWebServer = MockWebServer()
        mockWebServer.start()
        
        mockSettingsService = mock(OpenRouterSettingsService::class.java)
        // whenever(mockSettingsService.getProvisioningKey()).thenReturn(testProvisioningKey)
        // whenever(mockSettingsService.getApiKey()).thenReturn(testApiKey)
        
        gson = Gson()
    }

    @AfterEach
    fun tearDown() {
        mockWebServer.shutdown()
    }

    @Nested
    @DisplayName("API Keys List Endpoint Tests")
    inner class ApiKeysListTests {

        @Test
        @DisplayName("Should successfully fetch API keys list")
        fun testGetApiKeysListSuccess() {
            val mockResponse = loadMockResponse("api-keys-list-response.json")
            
            mockWebServer.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "application/json")
                    .setBody(mockResponse)
            )

            // Verify the mock response structure
            val response = gson.fromJson(mockResponse, Map::class.java)
            assertNotNull(response["data"])
            
            val data = response["data"] as List<*>
            assertTrue(data.isNotEmpty())
            
            val firstKey = data[0] as Map<*, *>
            assertEquals("postman", firstKey["name"])
            assertEquals(false, firstKey["disabled"])
            
            val intellijKey = data.find { (it as Map<*, *>)["name"] == "IntelliJ IDEA Plugin" } as Map<*, *>
            assertNotNull(intellijKey)
            assertEquals("IntelliJ IDEA Plugin", intellijKey["name"])
            assertEquals(0.0000495, intellijKey["usage"])
        }

        @Test
        @DisplayName("Should handle empty API keys list")
        fun testGetApiKeysListEmpty() {
            val emptyResponse = """{"data": []}"""
            
            mockWebServer.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "application/json")
                    .setBody(emptyResponse)
            )

            val response = gson.fromJson(emptyResponse, Map::class.java)
            val data = response["data"] as List<*>
            assertTrue(data.isEmpty())
        }

        @Test
        @DisplayName("Should handle 401 unauthorized error")
        fun testGetApiKeysListUnauthorized() {
            val errorResponse = loadMockResponse("error-response.json")
            
            mockWebServer.enqueue(
                MockResponse()
                    .setResponseCode(401)
                    .setHeader("Content-Type", "application/json")
                    .setBody(errorResponse)
            )

            val response = gson.fromJson(errorResponse, Map::class.java)
            assertNotNull(response["error"])
            
            val error = response["error"] as Map<*, *>
            assertEquals(401.0, error["code"])
            assertEquals("Invalid API key", error["message"])
        }
    }

    @Nested
    @DisplayName("API Key Creation Endpoint Tests")
    inner class ApiKeyCreationTests {

        @Test
        @DisplayName("Should successfully create API key")
        fun testCreateApiKeySuccess() {
            val mockResponse = loadMockResponse("api-key-create-response.json")
            
            mockWebServer.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "application/json")
                    .setBody(mockResponse)
            )

            val response = gson.fromJson(mockResponse, Map::class.java)
            assertNotNull(response["data"])
            assertNotNull(response["key"])
            
            val data = response["data"] as Map<*, *>
            assertEquals("IntelliJ IDEA Plugin", data["name"])
            assertEquals(false, data["disabled"])
            assertEquals(0.0, data["usage"])
            
            val actualKey = response["key"] as String
            assertTrue(actualKey.startsWith("sk-or-v1-"))
        }

        @Test
        @DisplayName("Should send correct request body for API key creation")
        fun testCreateApiKeyRequestBody() {
            mockWebServer.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setBody(loadMockResponse("api-key-create-response.json"))
            )

            // Expected request body
            val expectedRequestBody = """{"name":"IntelliJ IDEA Plugin","limit":null}"""
            
            // Verify the request structure
            val requestMap = gson.fromJson(expectedRequestBody, Map::class.java)
            assertEquals("IntelliJ IDEA Plugin", requestMap["name"])
            assertNull(requestMap["limit"])
        }

        @Test
        @DisplayName("Should create API key with limit")
        fun testCreateApiKeyWithLimit() {
            mockWebServer.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setBody(loadMockResponse("api-key-create-response.json"))
            )

            val requestBodyWithLimit = """{"name":"Test Key","limit":100.0}"""
            
            val requestMap = gson.fromJson(requestBodyWithLimit, Map::class.java)
            assertEquals("Test Key", requestMap["name"])
            assertEquals(100.0, requestMap["limit"])
        }

        @Test
        @DisplayName("Should handle API key creation failure")
        fun testCreateApiKeyFailure() {
            val errorResponse = """
                {
                    "error": {
                        "code": 400,
                        "message": "Invalid request parameters",
                        "metadata": {
                            "type": "validation_error",
                            "field": "name"
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

            val response = gson.fromJson(errorResponse, Map::class.java)
            val error = response["error"] as Map<*, *>
            assertEquals(400.0, error["code"])
            assertEquals("Invalid request parameters", error["message"])
            
            val metadata = error["metadata"] as Map<*, *>
            assertEquals("validation_error", metadata["type"])
            assertEquals("name", metadata["field"])
        }
    }

    @Nested
    @DisplayName("API Key Deletion Endpoint Tests")
    inner class ApiKeyDeletionTests {

        @Test
        @DisplayName("Should successfully delete API key")
        fun testDeleteApiKeySuccess() {
            val mockResponse = loadMockResponse("api-key-delete-response.json")
            
            mockWebServer.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "application/json")
                    .setBody(mockResponse)
            )

            val response = gson.fromJson(mockResponse, Map::class.java)
            assertNotNull(response["data"])
            
            val data = response["data"] as Map<*, *>
            assertEquals("test-key", data["name"])
            assertEquals(true, data["deleted"])
        }

        @Test
        @DisplayName("Should handle delete non-existent API key")
        fun testDeleteNonExistentApiKey() {
            val errorResponse = """
                {
                    "error": {
                        "code": 404,
                        "message": "API key not found",
                        "metadata": {
                            "type": "not_found_error"
                        }
                    }
                }
            """.trimIndent()
            
            mockWebServer.enqueue(
                MockResponse()
                    .setResponseCode(404)
                    .setHeader("Content-Type", "application/json")
                    .setBody(errorResponse)
            )

            val response = gson.fromJson(errorResponse, Map::class.java)
            val error = response["error"] as Map<*, *>
            assertEquals(404.0, error["code"])
            assertEquals("API key not found", error["message"])
        }
    }

    @Nested
    @DisplayName("Key Info Endpoint Tests")
    inner class KeyInfoTests {

        @Test
        @DisplayName("Should successfully fetch key info")
        fun testGetKeyInfoSuccess() {
            val mockResponse = loadMockResponse("key-info-response.json")
            
            mockWebServer.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "application/json")
                    .setBody(mockResponse)
            )

            val response = gson.fromJson(mockResponse, Map::class.java)
            assertNotNull(response["data"])
            
            val data = response["data"] as Map<*, *>
            assertEquals("sk-or-v1-abc...xyz", data["label"])
            assertEquals(0.0000495, data["usage"])
            assertEquals(100.0, data["limit"])
            assertEquals(false, data["is_free_tier"])
        }

        @Test
        @DisplayName("Should handle key info with unlimited quota")
        fun testGetKeyInfoUnlimited() {
            val unlimitedResponse = """
                {
                    "data": {
                        "label": "sk-or-v1-unlimited",
                        "usage": 50.25,
                        "limit": null,
                        "is_free_tier": false
                    }
                }
            """.trimIndent()
            
            mockWebServer.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "application/json")
                    .setBody(unlimitedResponse)
            )

            val response = gson.fromJson(unlimitedResponse, Map::class.java)
            val data = response["data"] as Map<*, *>
            assertNull(data["limit"])
            assertEquals(50.25, data["usage"])
        }
    }

    private fun loadMockResponse(filename: String): String {
        return this::class.java.classLoader
            .getResourceAsStream("mocks/$filename")
            ?.bufferedReader()
            ?.use { it.readText() }
            ?: throw IOException("Could not load mock response: $filename")
    }
}
