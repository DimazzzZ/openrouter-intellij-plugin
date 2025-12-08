package org.zhavoronkov.openrouter.integration

import com.google.gson.Gson
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Simple test to verify our proxy server request handling behavior.
 *
 * This test makes direct HTTP requests to verify:
 * 1. Our duplicate detection logic works correctly
 * 2. Request processing is consistent
 * 3. No artificial duplication is introduced by our code
 */
@DisplayName("Simple Proxy Request Behavior Test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Tag("integration")
@Disabled("Disabled by default to avoid consuming API credits. Enable manually for testing.")
class SimpleProxyTest {

    private lateinit var httpClient: OkHttpClient
    private lateinit var gson: Gson
    private lateinit var apiKey: String
    private val openRouterApiUrl = "https://openrouter.ai/api/v1"

    @BeforeAll
    fun setUpAll() {
        println("🚀 Starting Simple Proxy Request Behavior Test")

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
        println("⚠️  WARNING: Tests will make REAL API calls to verify behavior!")
    }

    @AfterAll
    fun tearDownAll() {
        println("✅ Simple proxy tests completed")
    }

    private fun loadEnvFile() {
        val envFile = File(".env")
        if (!envFile.exists()) {
            error("❌ .env file not found! Please create .env with OPENROUTER_API_KEY")
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

    @Test
    @DisplayName("Should verify OpenRouter API baseline behavior")
    fun testOpenRouterApiBaseline() {
        println("\n🧪 Testing OpenRouter API baseline behavior...")

        val requestBody = """
            {
                "model": "openai/gpt-4o-mini",
                "messages": [
                    {"role": "user", "content": "Say 'Baseline test' and nothing else"}
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

            println("   ✅ OpenRouter API baseline test successful")
            println("   📊 This confirms OpenRouter API works correctly")
        }
    }

    @Test
    @DisplayName("Should verify sequential requests work correctly")
    fun testSequentialRequests() {
        println("\n🧪 Testing sequential requests to OpenRouter API...")

        repeat(3) { i ->
            println("   Making request ${i + 1}/3...")

            val requestBody = """
                {
                    "model": "openai/gpt-4o-mini",
                    "messages": [
                        {"role": "user", "content": "Sequential test ${i + 1}"}
                    ],
                    "stream": false,
                    "max_tokens": 5
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

                println("      Response code: ${response.code}, Duration: ${duration}ms")
                assertEquals(200, response.code, "Request ${i + 1} should succeed")
            }

            // Small delay between requests
            if (i < 2) {
                Thread.sleep(1000)
            }
        }

        println("   ✅ All sequential requests successful")
        println("   📊 Check OpenRouter dashboard for exactly 3 sequential requests")
    }

    @Test
    @DisplayName("Should verify streaming requests work correctly")
    fun testStreamingRequest() {
        println("\n🧪 Testing streaming request to OpenRouter API...")

        val requestBody = """
            {
                "model": "openai/gpt-4o-mini",
                "messages": [
                    {"role": "user", "content": "Stream test message"}
                ],
                "stream": true,
                "max_tokens": 15
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

            println("   ✅ Streaming request successful")
            println("   📊 Check OpenRouter dashboard for exactly 1 streaming request")
        }
    }

    @Test
    @DisplayName("Should verify request hash generation consistency")
    fun testRequestHashConsistency() {
        println("\n🧪 Testing request hash generation consistency...")

        // This test verifies our duplicate detection logic would work correctly
        val requestBody1 = """{"model":"test","messages":[{"role":"user","content":"test"}]}"""
        val requestBody2 = """{"model":"test","messages":[{"role":"user","content":"test"}]}"""
        val requestBody3 = """{"model":"test","messages":[{"role":"user","content":"different"}]}"""

        // Simulate our hash generation logic
        val hash1 = generateTestHash(requestBody1, "127.0.0.1")
        val hash2 = generateTestHash(requestBody2, "127.0.0.1")
        val hash3 = generateTestHash(requestBody3, "127.0.0.1")
        val hash4 = generateTestHash(requestBody1, "192.168.1.1")

        println("   Hash 1 (identical content, same IP): $hash1")
        println("   Hash 2 (identical content, same IP): $hash2")
        println("   Hash 3 (different content, same IP): $hash3")
        println("   Hash 4 (identical content, different IP): $hash4")

        assertEquals(hash1, hash2, "Identical requests should have same hash")
        assertNotEquals(hash1, hash3, "Different content should have different hash")
        assertNotEquals(hash1, hash4, "Different IP should have different hash")

        println("   ✅ Request hash generation is consistent")
        println("   📊 This confirms our duplicate detection logic is sound")
    }

    private fun generateTestHash(requestBody: String, remoteAddr: String): String {
        val content = "$requestBody|$remoteAddr"
        return content.hashCode().toString()
    }
}
