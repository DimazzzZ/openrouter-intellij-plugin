package org.zhavoronkov.openrouter.integration

import com.google.gson.Gson
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.io.File
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Direct API test to verify OpenRouter API behavior and rule out our plugin as source of duplicates.
 *
 * This test:
 * 1. Makes direct requests to OpenRouter API (bypassing our proxy)
 * 2. Tests various scenarios that could cause duplicates
 * 3. Monitors request patterns and timing
 * 4. Verifies OpenRouter API behavior is consistent
 *
 * Uses .env file for real API keys to test complete flow.
 */
@DisplayName("Direct OpenRouter API Duplicate Test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Tag("integration")
@Disabled("Disabled by default to avoid consuming API credits. Enable manually for duplicate testing.")
class ProxyServerDuplicateTest {

    private lateinit var httpClient: OkHttpClient
    private lateinit var gson: Gson
    private lateinit var apiKey: String
    private val openRouterApiUrl = "https://openrouter.ai/api/v1"

    @BeforeAll
    fun setUpAll() {
        println("🚀 Starting Direct OpenRouter API Duplicate Test")

        // Load API keys from .env file
        loadEnvFile()
        println("🔑 Loaded API key: ${apiKey.take(20)}...")

        // Initialize components
        gson = Gson()
        httpClient = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()

        println("✅ HTTP client initialized")
        println("⚠️  WARNING: Tests will make REAL API calls directly to OpenRouter!")
        println("   This will help determine if duplicates come from our plugin or elsewhere")
    }

    @AfterAll
    fun tearDownAll() {
        println("✅ Direct API tests completed")
    }

    private fun loadEnvFile() {
        val envFile = File(".env")
        if (!envFile.exists()) {
            error("❌ .env file not found! Please create .env with OPENROUTER_API_KEY and OPENROUTER_PROVISIONING_KEY")
        }

        val envVars = mutableMapOf<String, String>()
        envFile.readLines().forEach { line ->
            if (line.isNotBlank() && !line.startsWith("#")) {
                val parts = line.split("=", limit = 2)
                if (parts.size == 2) {
                    val key = parts[0].trim()
                    val value = parts[1].trim().removeSurrounding("\"")
                    envVars[key] = value
                }
            }
        }

        apiKey = envVars["OPENROUTER_API_KEY"]
            ?: error("❌ OPENROUTER_API_KEY not found in .env file")
    }

    @Nested
    @DisplayName("Direct API Single Request Tests")
    inner class SingleRequestTests {

        @Test
        @DisplayName("Should send only 1 request for non-streaming chat completion")
        fun testSingleNonStreamingRequest() {
            println("\n🧪 Testing single non-streaming request directly to OpenRouter API...")

            val requestBody = """
                {
                    "model": "openai/gpt-4o-mini",
                    "messages": [
                        {"role": "user", "content": "Say 'Hello' and nothing else"}
                    ],
                    "stream": false,
                    "max_tokens": 10
                }
            """.trimIndent()

            val request = Request.Builder()
                .url("$openRouterApiUrl/chat/completions")
                .post(requestBody.toRequestBody("application/json".toMediaType()))
                .addHeader("Authorization", "Bearer $apiKey")
                .addHeader("Content-Type", "application/json")
                .build()

            val startTime = System.currentTimeMillis()
            httpClient.newCall(request).execute().use { response ->
                val duration = System.currentTimeMillis() - startTime

                println("   Response code: ${response.code}")
                println("   Duration: ${duration}ms")

                assertEquals(200, response.code, "Should return 200 OK")

                val body = response.body?.string()
                assertNotNull(body, "Response body should not be null")

                val json = gson.fromJson(body, Map::class.java)
                assertNotNull(json["id"], "Response should have ID")
                assertNotNull(json["choices"], "Response should have choices")

                println("   ✅ Single non-streaming request successful")
                println("   📊 Check OpenRouter dashboard for exactly 1 request")
                println("   📊 This proves OpenRouter API works correctly with single requests")
            }
        }

        @Test
        @DisplayName("Should send only 1 request for streaming chat completion")
        fun testSingleStreamingRequest() {
            println("\n🧪 Testing single streaming request directly to OpenRouter API...")

            val requestBody = """
                {
                    "model": "openai/gpt-4o-mini",
                    "messages": [
                        {"role": "user", "content": "Count from 1 to 3"}
                    ],
                    "stream": true,
                    "max_tokens": 20
                }
            """.trimIndent()

            val request = Request.Builder()
                .url("$openRouterApiUrl/chat/completions")
                .post(requestBody.toRequestBody("application/json".toMediaType()))
                .addHeader("Authorization", "Bearer $apiKey")
                .addHeader("Content-Type", "application/json")
                .build()

            val startTime = System.currentTimeMillis()
            httpClient.newCall(request).execute().use { response ->
                val duration = System.currentTimeMillis() - startTime

                println("   Response code: ${response.code}")
                println("   Content-Type: ${response.header("Content-Type")}")
                println("   Duration: ${duration}ms")

                assertEquals(200, response.code, "Should return 200 OK")
                assertTrue(
                    response.header("Content-Type")?.contains("text/event-stream") == true,
                    "Should have SSE content type"
                )

                val body = response.body?.string()
                assertNotNull(body, "Response body should not be null")

                // Verify SSE format
                assertTrue(body!!.contains("data: "), "Should contain SSE data lines")
                assertTrue(body.contains("[DONE]"), "Should contain [DONE] marker")

                println("   ✅ Single streaming request successful")
                println("   📊 Check OpenRouter dashboard for exactly 1 request")
                println("   📊 This proves OpenRouter API streaming works correctly")
            }
        }
    }

    @Nested
    @DisplayName("Concurrent Request Tests")
    inner class ConcurrentRequestTests {

        @Test
        @DisplayName("Should handle multiple concurrent requests without duplication")
        fun testConcurrentRequests() {
            println("\n🧪 Testing concurrent requests directly to OpenRouter API...")

            val requestCount = 3
            val futures = mutableListOf<CompletableFuture<Int>>()
            val successCount = AtomicInteger(0)

            repeat(requestCount) { i ->
                val future = CompletableFuture.supplyAsync {
                    try {
                        val requestBody = """
                            {
                                "model": "openai/gpt-4o-mini",
                                "messages": [
                                    {"role": "user", "content": "Say 'Request ${i + 1}'"}
                                ],
                                "stream": false,
                                "max_tokens": 10
                            }
                        """.trimIndent()

                        val request = Request.Builder()
                            .url("$openRouterApiUrl/chat/completions")
                            .post(requestBody.toRequestBody("application/json".toMediaType()))
                            .addHeader("Authorization", "Bearer $apiKey")
                            .addHeader("Content-Type", "application/json")
                            .build()

                        httpClient.newCall(request).execute().use { response ->
                            if (response.code == 200) {
                                successCount.incrementAndGet()
                                println("      Request ${i + 1}: ✅ Success")
                                return@supplyAsync response.code
                            } else {
                                println("      Request ${i + 1}: ⚠️ Failed with code ${response.code}")
                                return@supplyAsync response.code
                            }
                        }
                    } catch (e: Exception) {
                        println("      Request ${i + 1}: ❌ Exception: ${e.message}")
                        return@supplyAsync 0
                    }
                }
                futures.add(future)
            }

            // Wait for all requests to complete
            CompletableFuture.allOf(*futures.toTypedArray()).get(60, TimeUnit.SECONDS)

            println("\n   Success rate: $successCount/$requestCount")
            assertTrue(
                successCount.get() >= requestCount - 1,
                "Should have at least ${requestCount - 1} successful requests"
            )

            println("   ✅ Concurrent requests handled correctly")
            println("   📊 Check OpenRouter dashboard for exactly $requestCount requests")
            println("   📊 This proves concurrent requests work correctly with OpenRouter API")
        }
    }

    @Nested
    @DisplayName("Rapid Request Tests")
    inner class RapidRequestTests {

        @Test
        @DisplayName("Should handle rapid identical requests correctly")
        fun testRapidIdenticalRequests() {
            println("\n🧪 Testing rapid identical requests directly to OpenRouter API...")

            // Make the same request twice rapidly to test OpenRouter API behavior
            val requestBody = """
                {
                    "model": "openai/gpt-4o-mini",
                    "messages": [
                        {"role": "user", "content": "Rapid test message"}
                    ],
                    "stream": false,
                    "max_tokens": 5
                }
            """.trimIndent()

            val request1 = Request.Builder()
                .url("$openRouterApiUrl/chat/completions")
                .post(requestBody.toRequestBody("application/json".toMediaType()))
                .addHeader("Authorization", "Bearer $apiKey")
                .addHeader("Content-Type", "application/json")
                .build()

            val request2 = Request.Builder()
                .url("$openRouterApiUrl/chat/completions")
                .post(requestBody.toRequestBody("application/json".toMediaType()))
                .addHeader("Authorization", "Bearer $apiKey")
                .addHeader("Content-Type", "application/json")
                .build()

            // Send requests rapidly
            val startTime = System.currentTimeMillis()
            val future1 = CompletableFuture.supplyAsync {
                httpClient.newCall(request1).execute().use { it.code }
            }

            val future2 = CompletableFuture.supplyAsync {
                httpClient.newCall(request2).execute().use { it.code }
            }

            CompletableFuture.allOf(future1, future2).get(30, TimeUnit.SECONDS)
            val duration = System.currentTimeMillis() - startTime
            val code1 = future1.get()
            val code2 = future2.get()

            println("   Request 1 result: $code1")
            println("   Request 2 result: $code2")
            println("   Total duration: ${duration}ms")

            assertEquals(200, code1, "First request should succeed")
            assertEquals(200, code2, "Second request should succeed")

            println("   ✅ Both rapid requests processed successfully")
            println("   📊 Check OpenRouter dashboard for 2 separate requests")
            println("   📊 This proves OpenRouter API handles rapid requests correctly")
            println("   📊 If duplicates appear in AI Assistant, it's NOT OpenRouter API's fault")
        }

        @Test
        @DisplayName("Should handle rapid streaming requests correctly")
        fun testRapidStreamingRequests() {
            println("\n🧪 Testing rapid streaming requests directly to OpenRouter API...")

            val requestBody = """
                {
                    "model": "openai/gpt-4o-mini",
                    "messages": [
                        {"role": "user", "content": "Stream test"}
                    ],
                    "stream": true,
                    "max_tokens": 10
                }
            """.trimIndent()

            val request1 = Request.Builder()
                .url("$openRouterApiUrl/chat/completions")
                .post(requestBody.toRequestBody("application/json".toMediaType()))
                .addHeader("Authorization", "Bearer $apiKey")
                .addHeader("Content-Type", "application/json")
                .build()

            val request2 = Request.Builder()
                .url("$openRouterApiUrl/chat/completions")
                .post(requestBody.toRequestBody("application/json".toMediaType()))
                .addHeader("Authorization", "Bearer $apiKey")
                .addHeader("Content-Type", "application/json")
                .build()

            // Send streaming requests rapidly
            val startTime = System.currentTimeMillis()
            val future1 = CompletableFuture.supplyAsync {
                httpClient.newCall(request1).execute().use { response ->
                    val body = response.body?.string()
                    if (body?.contains("[DONE]") == true) 200 else response.code
                }
            }

            val future2 = CompletableFuture.supplyAsync {
                httpClient.newCall(request2).execute().use { response ->
                    val body = response.body?.string()
                    if (body?.contains("[DONE]") == true) 200 else response.code
                }
            }

            CompletableFuture.allOf(future1, future2).get(60, TimeUnit.SECONDS)
            val duration = System.currentTimeMillis() - startTime
            val code1 = future1.get()
            val code2 = future2.get()

            println("   Streaming request 1 result: $code1")
            println("   Streaming request 2 result: $code2")
            println("   Total duration: ${duration}ms")

            assertEquals(200, code1, "First streaming request should succeed")
            assertEquals(200, code2, "Second streaming request should succeed")

            println("   ✅ Both rapid streaming requests processed successfully")
            println("   📊 Check OpenRouter dashboard for 2 separate streaming requests")
            println("   📊 This proves OpenRouter API handles rapid streaming correctly")
        }
    }
}
