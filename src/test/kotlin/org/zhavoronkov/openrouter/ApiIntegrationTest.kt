package org.zhavoronkov.openrouter

import com.google.gson.Gson
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import java.io.IOException
import java.util.concurrent.TimeUnit

@DisplayName("OpenRouter API Integration Tests")
class ApiIntegrationTest {

    private lateinit var mockWebServer: MockWebServer
    private lateinit var gson: Gson
    private lateinit var httpClient: OkHttpClient
    private val testProvisioningKey = "pk-test-provisioning-key"

    @BeforeEach
    fun setUp() {
        mockWebServer = MockWebServer()
        mockWebServer.start()
        gson = Gson()
        httpClient = OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .build()
    }

    @AfterEach
    fun tearDown() {
        mockWebServer.shutdown()
    }

    @Nested
    @DisplayName("API Keys List Endpoint")
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

            val request = Request.Builder()
                .url(mockWebServer.url("/api/v1/keys"))
                .header("Authorization", "Bearer $testProvisioningKey")
                .header("Content-Type", "application/json")
                .get()
                .build()

            val response = httpClient.newCall(request).execute()

            assertEquals(200, response.code)
            assertEquals("application/json", response.header("Content-Type"))

            val responseBody = response.body?.string()
            assertNotNull(responseBody)

            val parsedResponse = gson.fromJson(responseBody, Map::class.java)
            assertNotNull(parsedResponse["data"])

            val data = parsedResponse["data"] as List<*>
            assertTrue(data.isNotEmpty())

            val firstKey = data[0] as Map<*, *>
            assertEquals("development-key", firstKey["name"])
            assertEquals(false, firstKey["disabled"])
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

            val request = Request.Builder()
                .url(mockWebServer.url("/api/v1/keys"))
                .header("Authorization", "Bearer invalid-key")
                .header("Content-Type", "application/json")
                .get()
                .build()

            val response = httpClient.newCall(request).execute()

            assertEquals(401, response.code)

            val responseBody = response.body?.string()
            val parsedResponse = gson.fromJson(responseBody, Map::class.java)
            assertNotNull(parsedResponse["error"])

            val error = parsedResponse["error"] as Map<*, *>
            assertEquals(401.0, error["code"])
            assertEquals("Invalid API key", error["message"])
        }
    }

    @Nested
    @DisplayName("API Key Creation Endpoint")
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

            val requestBody = """{"name":"IntelliJ IDEA Plugin","limit":null}"""

            val request = Request.Builder()
                .url(mockWebServer.url("/api/v1/keys"))
                .header("Authorization", "Bearer $testProvisioningKey")
                .header("Content-Type", "application/json")
                .post(requestBody.toRequestBody("application/json".toMediaType()))
                .build()

            val response = httpClient.newCall(request).execute()

            assertEquals(200, response.code)

            val responseBody = response.body?.string()
            val parsedResponse = gson.fromJson(responseBody, Map::class.java)
            assertNotNull(parsedResponse["data"])
            assertNotNull(parsedResponse["key"])

            val data = parsedResponse["data"] as Map<*, *>
            assertEquals("IntelliJ IDEA Plugin", data["name"])
            assertEquals(false, data["disabled"])
            assertEquals(0.0, data["usage"])

            val actualKey = parsedResponse["key"] as String
            assertTrue(actualKey.startsWith("sk-or-v1-"))
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

            val requestBody = """{"name":"","limit":null}"""

            val request = Request.Builder()
                .url(mockWebServer.url("/api/v1/keys"))
                .header("Authorization", "Bearer $testProvisioningKey")
                .header("Content-Type", "application/json")
                .post(requestBody.toRequestBody("application/json".toMediaType()))
                .build()

            val response = httpClient.newCall(request).execute()

            assertEquals(400, response.code)

            val responseBody = response.body?.string()
            val parsedResponse = gson.fromJson(responseBody, Map::class.java)
            val error = parsedResponse["error"] as Map<*, *>
            assertEquals(400.0, error["code"])
            assertEquals("Invalid request parameters", error["message"])

            val metadata = error["metadata"] as Map<*, *>
            assertEquals("validation_error", metadata["type"])
            assertEquals("name", metadata["field"])
        }
    }

    @Nested
    @DisplayName("API Key Deletion Endpoint")
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

            val request = Request.Builder()
                .url(mockWebServer.url("/api/v1/keys/test-key"))
                .header("Authorization", "Bearer $testProvisioningKey")
                .header("Content-Type", "application/json")
                .delete()
                .build()

            val response = httpClient.newCall(request).execute()

            assertEquals(200, response.code)

            val responseBody = response.body?.string()
            val parsedResponse = gson.fromJson(responseBody, Map::class.java)
            assertNotNull(parsedResponse["data"])

            val data = parsedResponse["data"] as Map<*, *>
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

            val request = Request.Builder()
                .url(mockWebServer.url("/api/v1/keys/non-existent-key"))
                .header("Authorization", "Bearer $testProvisioningKey")
                .header("Content-Type", "application/json")
                .delete()
                .build()

            val response = httpClient.newCall(request).execute()

            assertEquals(404, response.code)

            val responseBody = response.body?.string()
            val parsedResponse = gson.fromJson(responseBody, Map::class.java)
            val error = parsedResponse["error"] as Map<*, *>
            assertEquals(404.0, error["code"])
            assertEquals("API key not found", error["message"])
        }
    }

    @Nested
    @DisplayName("Key Info Endpoint")
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

            val request = Request.Builder()
                .url(mockWebServer.url("/api/v1/key"))
                .header("Authorization", "Bearer sk-or-v1-test-api-key")
                .header("Content-Type", "application/json")
                .get()
                .build()

            val response = httpClient.newCall(request).execute()

            assertEquals(200, response.code)

            val responseBody = response.body?.string()
            val parsedResponse = gson.fromJson(responseBody, Map::class.java)
            assertNotNull(parsedResponse["data"])

            val data = parsedResponse["data"] as Map<*, *>
            assertEquals("sk-or-v1-abc...xyz", data["label"])
            assertEquals(0.0000495, data["usage"])
            assertEquals(100.0, data["limit"])
            assertEquals(false, data["is_free_tier"])
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
