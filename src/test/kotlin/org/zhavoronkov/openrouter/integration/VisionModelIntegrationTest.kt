package org.zhavoronkov.openrouter.integration

import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.zhavoronkov.openrouter.utils.TestMediaGenerator
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Integration tests for vision model support using real OpenRouter API.
 *
 * These tests verify that the plugin correctly handles vision models with image content.
 * Tests use REAL OpenRouter API and consume credits.
 *
 * ⚠️ WARNING: These tests consume real API credits!
 *
 * To run these tests:
 * - Ensure .env file exists with OPENROUTER_API_KEY
 * - Run: ./gradlew test --tests "VisionModelIntegrationTest"
 *
 * @Tag("e2e") - Marks as end-to-end test (can be excluded from CI/CD)
 */
@DisplayName("Vision Model Integration Tests (Real API)")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Tag("e2e")
@Disabled("Disabled by default to avoid consuming API credits. Enable manually for vision model testing.")
class VisionModelIntegrationTest {

    private lateinit var httpClient: OkHttpClient
    private lateinit var gson: Gson
    private lateinit var apiKey: String
    private val openRouterApiUrl = "https://openrouter.ai/api/v1"

    // Test images
    private lateinit var simpleImageBase64: String
    private lateinit var gradientImageBase64: String
    private lateinit var patternImageBase64: String

    @BeforeAll
    fun setUpAll() {
        // Load API key from .env file
        loadEnvFile()

        println("🔑 Loaded API key from .env file")
        println("   API Key: ${apiKey.take(20)}...")

        gson = Gson()
        httpClient = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS) // Vision models may take longer
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()

        // Generate test images
        generateTestImages()

        println("✅ HTTP client initialized")
        println("✅ Test images generated")
        println("⚠️  WARNING: Tests will make REAL API calls to OpenRouter with vision models!")
    }

    @AfterAll
    fun tearDownAll() {
        println("✅ Vision model tests completed")
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

    private fun generateTestImages() {
        // Generate test images on-demand (stored in .gradle/test-media/)
        simpleImageBase64 = TestMediaGenerator.getImageDataUrl("test-image-1.png")
        gradientImageBase64 = TestMediaGenerator.getImageDataUrl("test-image-2.png")
        patternImageBase64 = TestMediaGenerator.getImageDataUrl("test-image-3.png")

        println("📸 Test images ready in ${TestMediaGenerator.getMediaDir()}")
    }

    @Nested
    @DisplayName("Vision Model API Tests")
    inner class VisionModelApiTests {

        @Test
        @DisplayName("Should successfully analyze image with GPT-4 Vision")
        fun testGpt4VisionImageAnalysis() {
            println("\n🧪 Testing GPT-4 Vision with image analysis...")

            // Given: Request with image content
            val requestBody = createVisionRequest(
                model = "openai/gpt-4-vision-preview",
                imageDataUrl = simpleImageBase64,
                prompt = "What text do you see in this image?"
            )



            // When: Send request to OpenRouter
            val request = Request.Builder()
                .url("$openRouterApiUrl/chat/completions")
                .post(requestBody.toRequestBody("application/json".toMediaType()))
                .addHeader("Authorization", "Bearer $apiKey")
                .addHeader("HTTP-Referer", "https://github.com/zhavoronkov/openrouter-intellij-plugin")
                .addHeader("X-Title", "OpenRouter IntelliJ Plugin - Vision Test")
                .build()

            val response = httpClient.newCall(request).execute()

            // Then: Response should be successful
            assertTrue(response.isSuccessful, "Response should be successful")
            val responseBody = response.body?.string()
            assertNotNull(responseBody, "Response body should not be null")

            val jsonResponse = gson.fromJson(responseBody, JsonObject::class.java)
            assertNotNull(jsonResponse.getAsJsonArray("choices"), "Response should have choices")
            assertTrue(jsonResponse.getAsJsonArray("choices").size() > 0, "Should have at least one choice")

            val content = jsonResponse.getAsJsonArray("choices")[0]
                .asJsonObject.getAsJsonObject("message")
                .get("content").asString

            println("✅ GPT-4 Vision response: $content")
            assertFalse(content.isBlank(), "Response content should not be blank")
        }

        @Test
        @DisplayName("Should handle multiple images in one request")
        fun testMultipleImagesInRequest() {
            println("\n🧪 Testing vision model with multiple images...")

            // Given: Request with multiple images
            val requestBody = createMultiImageRequest(
                model = "openai/gpt-4-vision-preview",
                imageDataUrls = listOf(simpleImageBase64, gradientImageBase64),
                prompt = "Describe the differences between these two images."
            )

            // When: Send request
            val request = Request.Builder()
                .url("$openRouterApiUrl/chat/completions")
                .post(requestBody.toRequestBody("application/json".toMediaType()))
                .addHeader("Authorization", "Bearer $apiKey")
                .addHeader("HTTP-Referer", "https://github.com/zhavoronkov/openrouter-intellij-plugin")
                .addHeader("X-Title", "OpenRouter IntelliJ Plugin - Multi-Image Test")
                .build()

            val response = httpClient.newCall(request).execute()

            // Then: Should successfully process multiple images
            assertTrue(response.isSuccessful, "Response should be successful")
            val responseBody = response.body?.string()
            assertNotNull(responseBody)

            val jsonResponse = gson.fromJson(responseBody, JsonObject::class.java)
            val content = jsonResponse.getAsJsonArray("choices")[0]
                .asJsonObject.getAsJsonObject("message")
                .get("content").asString

            println("✅ Multi-image response: $content")
            assertFalse(content.isBlank())
        }

        @Test
        @DisplayName("Should work with Claude 3 vision model")
        fun testClaude3Vision() {
            println("\n🧪 Testing Claude 3 with vision...")

            // Given: Request for Claude 3 with image
            val requestBody = createVisionRequest(
                model = "anthropic/claude-3-haiku",
                imageDataUrl = patternImageBase64,
                prompt = "What shapes and colors do you see in this image?"
            )

            // When: Send request
            val request = Request.Builder()
                .url("$openRouterApiUrl/chat/completions")
                .post(requestBody.toRequestBody("application/json".toMediaType()))
                .addHeader("Authorization", "Bearer $apiKey")
                .addHeader("HTTP-Referer", "https://github.com/zhavoronkov/openrouter-intellij-plugin")
                .addHeader("X-Title", "OpenRouter IntelliJ Plugin - Claude Vision Test")
                .build()

            val response = httpClient.newCall(request).execute()

            // Then: Should successfully analyze image
            assertTrue(response.isSuccessful, "Response should be successful")
            val responseBody = response.body?.string()
            assertNotNull(responseBody)

            val jsonResponse = gson.fromJson(responseBody, JsonObject::class.java)
            val content = jsonResponse.getAsJsonArray("choices")[0]
                .asJsonObject.getAsJsonObject("message")
                .get("content").asString

            println("✅ Claude 3 vision response: $content")
            assertFalse(content.isBlank())
        }
    }

    @Nested
    @DisplayName("Content Format Tests")
    inner class ContentFormatTests {

        @Test
        @DisplayName("Should handle text-only content in vision model")
        fun testTextOnlyWithVisionModel() {
            println("\n🧪 Testing vision model with text-only content...")

            // Given: Text-only request to vision model
            val requestJson = JsonObject().apply {
                addProperty("model", "openai/gpt-4-vision-preview")
                add("messages", JsonArray().apply {
                    add(JsonObject().apply {
                        addProperty("role", "user")
                        addProperty("content", "What is 2+2?")
                    })
                })
                addProperty("max_tokens", 100)
            }

            // When: Send request
            val request = Request.Builder()
                .url("$openRouterApiUrl/chat/completions")
                .post(gson.toJson(requestJson).toRequestBody("application/json".toMediaType()))
                .addHeader("Authorization", "Bearer $apiKey")
                .addHeader("HTTP-Referer", "https://github.com/zhavoronkov/openrouter-intellij-plugin")
                .build()

            val response = httpClient.newCall(request).execute()

            // Then: Should work with text-only content
            assertTrue(response.isSuccessful)
            val responseBody = response.body?.string()
            assertNotNull(responseBody)

            println("✅ Text-only request to vision model succeeded")
        }
    }


    // Helper methods
    private fun createVisionRequest(model: String, imageDataUrl: String, prompt: String): String {
        val requestJson = JsonObject().apply {
            addProperty("model", model)
            add("messages", JsonArray().apply {
                add(JsonObject().apply {
                    addProperty("role", "user")
                    add("content", JsonArray().apply {
                        add(JsonObject().apply {
                            addProperty("type", "text")
                            addProperty("text", prompt)
                        })
                        add(JsonObject().apply {
                            addProperty("type", "image_url")
                            add("image_url", JsonObject().apply {
                                addProperty("url", imageDataUrl)
                            })
                        })
                    })
                })
            })
            addProperty("max_tokens", 300)
        }
        return gson.toJson(requestJson)
    }

    private fun createMultiImageRequest(model: String, imageDataUrls: List<String>, prompt: String): String {
        val requestJson = JsonObject().apply {
            addProperty("model", model)
            add("messages", JsonArray().apply {
                add(JsonObject().apply {
                    addProperty("role", "user")
                    add("content", JsonArray().apply {
                        add(JsonObject().apply {
                            addProperty("type", "text")
                            addProperty("text", prompt)
                        })
                        imageDataUrls.forEach { imageUrl ->
                            add(JsonObject().apply {
                                addProperty("type", "image_url")
                                add("image_url", JsonObject().apply {
                                    addProperty("url", imageUrl)
                                })
                            })
                        }
                    })
                })
            })
            addProperty("max_tokens", 500)
        }
        return gson.toJson(requestJson)
    }
}
