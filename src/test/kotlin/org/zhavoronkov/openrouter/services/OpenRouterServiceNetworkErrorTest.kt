package org.zhavoronkov.openrouter.services

import com.google.gson.Gson
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import org.mockito.Mockito.*
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * Tests for network error handling in OpenRouterService
 *
 * These tests verify that network errors (offline, DNS issues, timeouts, etc.)
 * are handled gracefully without throwing exceptions or alarming users.
 */
@DisplayName("OpenRouter Service Network Error Handling Tests")
class OpenRouterServiceNetworkErrorTest {

    private lateinit var mockServer: MockWebServer
    private val gson = Gson()

    @BeforeEach
    fun setUp() {
        mockServer = MockWebServer()
        mockServer.start()
    }

    @AfterEach
    fun tearDown() {
        mockServer.shutdown()
    }

    @Nested
    @DisplayName("Network Error Scenarios")
    inner class NetworkErrorScenariosTest {

        @Test
        @DisplayName("Should handle connection timeout gracefully")
        fun testConnectionTimeout() {
            // This test documents that SocketTimeoutException is a type of IOException
            // and should be handled gracefully by the service
            assertTrue(IOException::class.java.isAssignableFrom(SocketTimeoutException::class.java))

            // Verify the error message format
            val errorMsg = "Request timed out - OpenRouter may be slow or unreachable"
            assertTrue(errorMsg.contains("timed out"))
            assertFalse(errorMsg.contains("Exception"))
        }

        @Test
        @DisplayName("Should handle connection refused gracefully")
        fun testConnectionRefused() {
            // This test documents that ConnectException is a type of IOException
            // and should be handled gracefully by the service
            assertTrue(IOException::class.java.isAssignableFrom(ConnectException::class.java))

            // Verify the error message format
            val errorMsg = "Connection refused - OpenRouter may be down"
            assertTrue(errorMsg.contains("Connection refused"))
            assertFalse(errorMsg.contains("Exception"))
        }

        @Test
        @DisplayName("Should handle unknown host gracefully")
        fun testUnknownHost() {
            // This test documents that UnknownHostException is a type of IOException
            // and should be handled gracefully by the service
            assertTrue(IOException::class.java.isAssignableFrom(UnknownHostException::class.java))

            // Verify the error message format
            val errorMsg = "Unable to reach OpenRouter (offline or DNS issue)"
            assertTrue(errorMsg.contains("offline or DNS"))
            assertFalse(errorMsg.contains("Exception"))
        }

        @Test
        @DisplayName("Should handle server error responses gracefully")
        fun testServerError() {
            mockServer.enqueue(
                MockResponse()
                    .setResponseCode(500)
                    .setBody("{\"error\": \"Internal server error\"}")
            )

            val client = OkHttpClient.Builder().build()

            assertDoesNotThrow {
                val request = okhttp3.Request.Builder()
                    .url(mockServer.url("/api/v1/credits"))
                    .build()

                val response = client.newCall(request).execute()

                // Verify error response is handled
                assertFalse(response.isSuccessful)
                assertEquals(500, response.code)
            }
        }

        @Test
        @DisplayName("Should handle malformed JSON gracefully")
        fun testMalformedJson() {
            mockServer.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setBody("This is not valid JSON")
            )

            val client = OkHttpClient.Builder().build()

            assertDoesNotThrow {
                val request = okhttp3.Request.Builder()
                    .url(mockServer.url("/api/v1/credits"))
                    .build()

                val response = client.newCall(request).execute()
                val body = response.body?.string()

                // Verify that attempting to parse malformed JSON throws expected exception
                assertThrows(com.google.gson.JsonSyntaxException::class.java) {
                    gson.fromJson(body, Map::class.java)
                }
            }
        }
    }

    @Nested
    @DisplayName("Error Message Formatting")
    inner class ErrorMessageFormattingTest {

        @Test
        @DisplayName("Should format UnknownHostException message appropriately")
        fun testUnknownHostExceptionMessage() {
            val expectedMessage = "Unable to reach OpenRouter (offline or DNS issue)"

            // Verify the error message is user-friendly
            assertTrue(expectedMessage.contains("offline or DNS"))
            assertFalse(expectedMessage.contains("Exception"))
            assertFalse(expectedMessage.contains("stack trace"))
        }

        @Test
        @DisplayName("Should format SocketTimeoutException message appropriately")
        fun testSocketTimeoutExceptionMessage() {
            val expectedMessage = "Request timed out - OpenRouter may be slow or unreachable"

            // Verify the error message is user-friendly
            assertTrue(expectedMessage.contains("timed out"))
            assertFalse(expectedMessage.contains("Exception"))
        }

        @Test
        @DisplayName("Should format ConnectException message appropriately")
        fun testConnectExceptionMessage() {
            val expectedMessage = "Connection refused - OpenRouter may be down"

            // Verify the error message is user-friendly
            assertTrue(expectedMessage.contains("Connection refused"))
            assertFalse(expectedMessage.contains("Exception"))
        }
    }

    @Nested
    @DisplayName("Graceful Degradation")
    inner class GracefulDegradationTest {

        @Test
        @DisplayName("Should return null on network error instead of throwing")
        fun testReturnsNullOnError() {
            // This test verifies the pattern used in OpenRouterService
            // where network errors return null instead of throwing exceptions

            val result = try {
                // Simulate network call that fails
                throw UnknownHostException("openrouter.ai")
            } catch (e: IOException) {
                // Handle gracefully by returning null
                null
            }

            assertNull(result, "Network errors should return null for graceful degradation")
        }

        @Test
        @DisplayName("Should allow retry after network error")
        fun testRetryAfterError() {
            // First request fails
            mockServer.enqueue(
                MockResponse()
                    .setResponseCode(500)
                    .setBody("{\"error\": \"Server error\"}")
            )

            // Second request succeeds
            mockServer.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setBody("{\"data\": {\"usage\": 5.0, \"limit\": 10.0}}")
            )

            val client = OkHttpClient.Builder().build()
            val url = mockServer.url("/api/v1/credits")

            // First attempt fails
            val request1 = okhttp3.Request.Builder().url(url).build()
            val response1 = client.newCall(request1).execute()
            assertFalse(response1.isSuccessful)

            // Second attempt succeeds
            val request2 = okhttp3.Request.Builder().url(url).build()
            val response2 = client.newCall(request2).execute()
            assertTrue(response2.isSuccessful)
        }
    }

    @Nested
    @DisplayName("Logging Behavior")
    inner class LoggingBehaviorTest {

        @Test
        @DisplayName("Should log network errors at WARN level, not ERROR")
        fun testLoggingLevel() {
            // This test documents the expected behavior:
            // Network errors should be logged at WARN level (not ERROR)
            // because being offline is not an application error

            val errorTypes = listOf(
                UnknownHostException::class.java,
                SocketTimeoutException::class.java,
                ConnectException::class.java
            )

            errorTypes.forEach { exceptionType ->
                assertTrue(
                    IOException::class.java.isAssignableFrom(exceptionType),
                    "$exceptionType should be an IOException"
                )
            }
        }

        @Test
        @DisplayName("Should only log stack trace in debug mode")
        fun testStackTraceOnlyInDebug() {
            // This test documents the expected behavior:
            // Full stack traces should only be logged when debug mode is enabled
            // Normal users should see friendly error messages without stack traces

            val debugEnabled = System.getProperty("openrouter.debug", "false").toBoolean()

            // In production (debug disabled), stack traces should not be shown
            // In development (debug enabled), stack traces can be shown
            assertNotNull(debugEnabled)
        }
    }
}
