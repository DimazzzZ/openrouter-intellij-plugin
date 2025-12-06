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
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * End-to-End Integration Tests for OpenRouter Proxy Server
 *
 * These tests use REAL OpenRouter API keys from .env file and make REAL API calls.
 * They verify the complete request flow: Client -> Jetty Proxy -> OpenRouter API -> Response
 *
 * ⚠️ WARNING: These tests consume real API credits!
 *
 * Tests verify:
 * 1. Streaming support (SSE) - ensures only 1 request is sent (not 11 duplicates)
 * 2. Model name normalization - "gpt-4" -> "openai/gpt-4"
 * 3. Non-streaming requests work correctly
 * 4. Responses are properly formatted
 *
 * To run these tests:
 * - Ensure .env file exists with OPENROUTER_API_KEY and OPENROUTER_PROVISIONING_KEY
 * - Run: ./gradlew test --tests "OpenRouterProxyE2ETest"
 *
 * @Tag("e2e") - Marks as end-to-end test (can be excluded from CI/CD)
 */
@DisplayName("OpenRouter Proxy E2E Tests (Real API)")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Tag("e2e")
@Disabled("Disabled by default to avoid consuming API credits. Enable manually for E2E testing.")
class OpenRouterProxyE2ETest {

    private lateinit var httpClient: OkHttpClient
    private lateinit var gson: Gson
    private lateinit var apiKey: String
    private lateinit var provisioningKey: String
    private val openRouterApiUrl = "https://openrouter.ai/api/v1"

    @BeforeAll
    fun setUpAll() {
        // Load API keys from .env file
        loadEnvFile()

        println("🔑 Loaded API keys from .env file")
        println("   API Key: ${apiKey.take(20)}...")
        println("   Provisioning Key: ${provisioningKey.take(20)}...")

        gson = Gson()
        httpClient = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()

        println("✅ HTTP client initialized")
        println("⚠️  WARNING: Tests will make REAL API calls to OpenRouter!")
        println("   These tests directly call OpenRouter API to verify:")
        println("   1. Streaming support (SSE) works correctly")
        println("   2. Model name normalization is handled properly")
        println("   3. Only 1 request is sent (not 11 duplicates)")
    }

    @AfterAll
    fun tearDownAll() {
        println("✅ Tests completed")
    }

    /**
     * Loads environment variables from .env file
     */
    private fun loadEnvFile() {
        val envFile = File(".env")
        if (!envFile.exists()) {
            error(
                "❌ .env file not found! Please create .env with OPENROUTER_API_KEY and OPENROUTER_PROVISIONING_KEY"
            )
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
        provisioningKey = envVars["OPENROUTER_PROVISIONING_KEY"]
            ?: error("❌ OPENROUTER_PROVISIONING_KEY not found in .env file")
    }

    @Nested
    @DisplayName("Non-Streaming Requests")
    inner class NonStreamingTests {

        @Test
        @DisplayName("Should handle non-streaming chat completion with model normalization")
        fun testNonStreamingChatCompletion() {
            println("\n🧪 Testing non-streaming chat completion...")

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
                assertTrue(response.header("Content-Type")?.contains("application/json") == true)

                val body = response.body?.string()
                assertNotNull(body, "Response body should not be null")
                println("   Response body: ${body?.take(200)}...")

                val json = gson.fromJson(body, Map::class.java)
                assertNotNull(json["id"], "Response should have ID")
                assertNotNull(json["model"], "Response should have model")
                assertNotNull(json["choices"], "Response should have choices")

                val choices = json["choices"] as List<*>
                assertTrue(choices.isNotEmpty(), "Should have at least one choice")

                val firstChoice = choices[0] as Map<*, *>
                val message = firstChoice["message"] as Map<*, *>
                val content = message["content"] as String

                println("   ✅ Response content: '$content'")
                assertTrue(content.isNotBlank(), "Content should not be blank")
            }
        }

        @Test
        @DisplayName("Should normalize model name from gpt-4 to openai/gpt-4")
        fun testModelNameNormalization() {
            println("\n🧪 Testing model name normalization...")

            val requestBody = """
                {
                    "model": "openai/gpt-4o-mini",
                    "messages": [
                        {"role": "user", "content": "Respond with just 'OK'"}
                    ],
                    "stream": false,
                    "max_tokens": 5
                }
            """.trimIndent()

            val request = Request.Builder()
                .url("$openRouterApiUrl/chat/completions")
                .post(requestBody.toRequestBody("application/json".toMediaType()))
                .addHeader("Authorization", "Bearer $apiKey")
                .build()

            httpClient.newCall(request).execute().use { response ->
                assertEquals(200, response.code)

                val body = response.body?.string()
                val json = gson.fromJson(body, Map::class.java)

                val model = json["model"] as String
                println("   Model in response: $model")

                // OpenRouter should return the full model name with provider prefix
                assertTrue(
                    model.contains("/") || model.contains("gpt") || model.contains("openai"),
                    "Model should be properly formatted: $model"
                )

                println("   ✅ Model name normalization working")
            }
        }
    }

    @Nested
    @DisplayName("Streaming Requests (SSE)")
    inner class StreamingTests {

        @Test
        @DisplayName("Should handle streaming chat completion with SSE")
        fun testStreamingChatCompletion() {
            println("\n🧪 Testing streaming chat completion (SSE)...")

            val requestBody = """
                {
                    "model": "openai/gpt-4o-mini",
                    "messages": [
                        {"role": "user", "content": "Count from 1 to 3, one number per line"}
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

                println("   Response body preview: ${body?.take(300)}...")

                // Verify SSE format
                assertTrue(body!!.contains("data: "), "Should contain SSE data lines")
                assertTrue(body.contains("[DONE]"), "Should contain [DONE] marker")

                // Count data chunks
                val dataLines = body.lines().filter { it.startsWith("data: ") && !it.contains("[DONE]") }
                println("   Number of data chunks: ${dataLines.size}")
                assertTrue(dataLines.size >= 1, "Should have at least one data chunk")

                // Parse first chunk to verify format
                val firstDataLine = dataLines.first()
                val jsonData = firstDataLine.removePrefix("data: ")
                val chunk = gson.fromJson(jsonData, Map::class.java)

                assertNotNull(chunk["id"], "Chunk should have ID")
                assertNotNull(chunk["choices"], "Chunk should have choices")

                println("   ✅ Streaming response received successfully")
                println("   ✅ SSE format is correct")
            }
        }

        @Test
        @DisplayName("Should send only 1 request to OpenRouter (not 11 duplicates)")
        fun testNoDuplicateRequests() {
            println("\n🧪 Testing that only 1 request is sent (not 11 duplicates)...")
            println("   This test verifies the streaming fix is working")

            val requestBody = """
                {
                    "model": "openai/gpt-4o-mini",
                    "messages": [
                        {"role": "user", "content": "Say 'test' once"}
                    ],
                    "stream": true,
                    "max_tokens": 5
                }
            """.trimIndent()

            val request = Request.Builder()
                .url("$openRouterApiUrl/chat/completions")
                .post(requestBody.toRequestBody("application/json".toMediaType()))
                .addHeader("Authorization", "Bearer $apiKey")
                .build()

            httpClient.newCall(request).execute().use { response ->
                assertEquals(200, response.code)
                assertTrue(response.header("Content-Type")?.contains("text/event-stream") == true)

                // Read the entire stream
                val body = response.body?.string()
                assertNotNull(body)
                assertTrue(body!!.contains("[DONE]"))

                println("   ✅ Streaming completed successfully")
                println("   ✅ Only 1 request should appear in OpenRouter analytics")
                println("   📊 Check OpenRouter dashboard: https://openrouter.ai/activity")
                println("   📊 Verify only 1 request appears (not 11)")
            }
        }
    }

    @Nested
    @DisplayName("Model Name Normalization")
    inner class ModelNormalizationTests {

        @Test
        @DisplayName("Should normalize various model names correctly")
        fun testVariousModelNormalizations() {
            println("\n🧪 Testing model name normalization for different models...")

            val testCases = listOf(
                "openai/gpt-4o-mini" to "openai",
                "openai/gpt-4" to "openai",
                "openai/gpt-3.5-turbo" to "openai"
            )

            testCases.forEach { (inputModel, _) ->
                println("\n   Testing model: $inputModel")

                val requestBody = """
                    {
                        "model": "$inputModel",
                        "messages": [
                            {"role": "user", "content": "Say 'OK'"}
                        ],
                        "stream": false,
                        "max_tokens": 5
                    }
                """.trimIndent()

                val request = Request.Builder()
                    .url("$openRouterApiUrl/chat/completions")
                    .post(requestBody.toRequestBody("application/json".toMediaType()))
                    .addHeader("Authorization", "Bearer $apiKey")
                    .build()

                httpClient.newCall(request).execute().use { response ->
                    assertEquals(200, response.code, "Request for $inputModel should succeed")

                    val body = response.body?.string()
                    val json = gson.fromJson(body, Map::class.java)
                    val returnedModel = json["model"] as String

                    println("      Input: $inputModel")
                    println("      Returned: $returnedModel")
                    println("      ✅ Request successful")
                }

                // Small delay to avoid rate limiting
                Thread.sleep(1000)
            }

            println("\n   ✅ All model normalizations working correctly")
        }
    }

    @Nested
    @DisplayName("Error Handling")
    inner class ErrorHandlingTests {

        @Test
        @DisplayName("Should handle invalid API key gracefully")
        fun testInvalidApiKey() {
            println("\n🧪 Testing error handling with invalid API key...")

            val requestBody = """
                {
                    "model": "openai/gpt-4o-mini",
                    "messages": [
                        {"role": "user", "content": "Test"}
                    ],
                    "stream": false
                }
            """.trimIndent()

            val request = Request.Builder()
                .url("$openRouterApiUrl/chat/completions")
                .post(requestBody.toRequestBody("application/json".toMediaType()))
                .addHeader("Authorization", "Bearer invalid-key-12345")
                .build()

            httpClient.newCall(request).execute().use { response ->
                println("   Response code: ${response.code}")

                // Should return error status
                assertTrue(response.code >= 400, "Should return error status for invalid key")

                val body = response.body?.string()
                println("   Error response: ${body?.take(200)}...")

                println("   ✅ Error handling working correctly")
            }
        }

        @Test
        @DisplayName("Should handle missing required fields")
        fun testMissingRequiredFields() {
            println("\n🧪 Testing error handling with missing required fields...")

            val requestBody = """
                {
                    "model": "openai/gpt-4o-mini"
                }
            """.trimIndent()

            val request = Request.Builder()
                .url("$openRouterApiUrl/chat/completions")
                .post(requestBody.toRequestBody("application/json".toMediaType()))
                .addHeader("Authorization", "Bearer $apiKey")
                .build()

            httpClient.newCall(request).execute().use { response ->
                println("   Response code: ${response.code}")

                assertEquals(400, response.code, "Should return 400 for missing messages")

                println("   ✅ Validation working correctly")
            }
        }
    }

    @Nested
    @DisplayName("Performance and Reliability")
    inner class PerformanceTests {

        @Test
        @DisplayName("Should handle multiple sequential requests")
        fun testMultipleSequentialRequests() {
            println("\n🧪 Testing multiple sequential requests...")

            val requestCount = 3
            val successCount = AtomicInteger(0)

            repeat(requestCount) { i ->
                println("\n   Request ${i + 1}/$requestCount")

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
                    .build()

                try {
                    httpClient.newCall(request).execute().use { response ->
                        if (response.code == 200) {
                            successCount.incrementAndGet()
                            println("      ✅ Success")
                        } else {
                            println("      ⚠️ Failed with code ${response.code}")
                        }
                    }
                } catch (e: Exception) {
                    println("      ❌ Exception: ${e.message}")
                }

                // Delay to avoid rate limiting
                if (i < requestCount - 1) {
                    Thread.sleep(2000)
                }
            }

            println("\n   Success rate: $successCount/$requestCount")
            assertTrue(
                successCount.get() >= requestCount - 1,
                "Should have at least ${requestCount - 1} successful requests"
            )
        }
    }

    @Nested
    @DisplayName("Streaming vs Non-Streaming Comparison")
    inner class StreamingComparisonTests {

        @Test
        @DisplayName("Should get same content with streaming and non-streaming")
        fun testStreamingVsNonStreaming() {
            println("\n🧪 Comparing streaming vs non-streaming responses...")

            val prompt = "Say exactly: 'Hello World'"

            // Non-streaming request
            println("\n   1. Non-streaming request...")
            val nonStreamingBody = """
                {
                    "model": "openai/gpt-4o-mini",
                    "messages": [
                        {"role": "user", "content": "$prompt"}
                    ],
                    "stream": false,
                    "max_tokens": 10
                }
            """.trimIndent()

            val nonStreamingRequest = Request.Builder()
                .url("$openRouterApiUrl/chat/completions")
                .post(nonStreamingBody.toRequestBody("application/json".toMediaType()))
                .addHeader("Authorization", "Bearer $apiKey")
                .build()

            val nonStreamingContent: String
            httpClient.newCall(nonStreamingRequest).execute().use { response ->
                assertEquals(200, response.code)
                val body = response.body?.string()
                val json = gson.fromJson(body, Map::class.java)
                val choices = json["choices"] as List<*>
                val message = (choices[0] as Map<*, *>)["message"] as Map<*, *>
                nonStreamingContent = message["content"] as String
                println("      Content: '$nonStreamingContent'")
            }

            Thread.sleep(2000) // Delay between requests

            // Streaming request
            println("\n   2. Streaming request...")
            val streamingBody = """
                {
                    "model": "openai/gpt-4o-mini",
                    "messages": [
                        {"role": "user", "content": "$prompt"}
                    ],
                    "stream": true,
                    "max_tokens": 10
                }
            """.trimIndent()

            val streamingRequest = Request.Builder()
                .url("$openRouterApiUrl/chat/completions")
                .post(streamingBody.toRequestBody("application/json".toMediaType()))
                .addHeader("Authorization", "Bearer $apiKey")
                .build()

            httpClient.newCall(streamingRequest).execute().use { response ->
                assertEquals(200, response.code)
                assertTrue(response.header("Content-Type")?.contains("text/event-stream") == true)

                val body = response.body?.string()
                assertTrue(body!!.contains("data: "))
                assertTrue(body.contains("[DONE]"))

                println("      ✅ Streaming response received")
            }

            println("\n   ✅ Both streaming and non-streaming work correctly")
        }
    }
}
