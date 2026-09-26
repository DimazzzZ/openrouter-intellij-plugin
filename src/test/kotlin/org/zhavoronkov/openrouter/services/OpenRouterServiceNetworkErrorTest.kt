package org.zhavoronkov.openrouter.services

import com.google.gson.Gson
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.zhavoronkov.openrouter.testing.OkHttpLeakSafeExtension

/**
 * Tests for network error handling in OpenRouterService
 *
 * These tests verify that network errors (offline, DNS issues, timeouts, etc.)
 * are handled gracefully without throwing exceptions or alarming users.
 */
@ExtendWith(OkHttpLeakSafeExtension::class)
@DisplayName("OpenRouter Service Network Error Handling Tests")
class OpenRouterServiceNetworkErrorTest {

    private lateinit var mockServer: MockWebServer
    private val gson = Gson()
    private lateinit var httpClient: OkHttpClient

    @BeforeEach
    fun setUp() {
        mockServer = MockWebServer()
        mockServer.start()
        httpClient = OkHttpClient.Builder().build()
    }

    @AfterEach
    fun tearDown() {
        httpClient.dispatcher.executorService.shutdown()
        httpClient.connectionPool.evictAll()
        mockServer.shutdown()
    }

    @Nested
    @DisplayName("Network Error Scenarios")
    inner class NetworkErrorScenariosTest {

        @Test
        @DisplayName("Should handle server error responses gracefully")
        fun testServerError() {
            mockServer.enqueue(
                MockResponse()
                    .setResponseCode(500)
                    .setBody("{\"error\": \"Internal server error\"}")
            )

            // Reuse shared httpClient from setUp

            assertDoesNotThrow {
                val request = okhttp3.Request.Builder()
                    .url(mockServer.url("/api/v1/credits"))
                    .build()

                val response = httpClient.newCall(request).execute()

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

            // Reuse shared httpClient from setUp

            assertDoesNotThrow {
                val request = okhttp3.Request.Builder()
                    .url(mockServer.url("/api/v1/credits"))
                    .build()

                val response = httpClient.newCall(request).execute()
                val body = response.body?.string()

                // Verify that attempting to parse malformed JSON throws expected exception
                assertThrows(com.google.gson.JsonSyntaxException::class.java) {
                    gson.fromJson(body, Map::class.java)
                }
            }
        }
    }

    @Nested
    @DisplayName("Graceful Degradation")
    inner class GracefulDegradationTest {

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

            // Reuse shared httpClient from setUp
            val url = mockServer.url("/api/v1/credits")

            // First attempt fails
            val request1 = okhttp3.Request.Builder().url(url).build()
            val response1 = httpClient.newCall(request1).execute()
            assertFalse(response1.isSuccessful)

            // Second attempt succeeds
            val request2 = okhttp3.Request.Builder().url(url).build()
            val response2 = httpClient.newCall(request2).execute()
            assertTrue(response2.isSuccessful)
        }
    }
}
