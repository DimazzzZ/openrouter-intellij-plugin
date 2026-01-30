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
 * Integration tests for multimodal support (images, audio, video) using real OpenRouter API.
 *
 * These tests verify that the plugin correctly handles multimodal models with various content types:
 * - Images (GPT-4 Vision, Claude 3)
 * - Audio (Gemini Pro 1.5)
 * - Video (Gemini Pro 1.5)
 * - Text (backward compatibility)
 *
 * Tests use REAL OpenRouter API and consume credits.
 *
 * ⚠️ WARNING: These tests consume real API credits!
 *
 * To run these tests:
 * 1. Create .env file with OPENROUTER_API_KEY=sk-or-v1-...
 * 2. Remove @Disabled annotation below
 * 3. Run: ./gradlew test --tests "MultimodalIntegrationTest"
 *
 * @Tag("e2e") - Marks as end-to-end test (can be excluded from CI/CD)
 */
@DisplayName("Multimodal Integration Tests (Real API)")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Tag("e2e")
@Disabled("Disabled by default to avoid consuming API credits. Enable manually for multimodal testing.")
class MultimodalIntegrationTest {

    private lateinit var httpClient: OkHttpClient
    private lateinit var gson: Gson
    private lateinit var apiKey: String
    private val openRouterApiUrl = "https://openrouter.ai/api/v1"

    // Test media data URLs (generated on-demand)
    private lateinit var imageDataUrl: String
    private lateinit var audioDataUrl: String
    private lateinit var videoDataUrl: String

    @BeforeAll
    fun setUpAll() {
        // Load API key from .env file
        loadEnvFile()

        println("🔑 Loaded API key from .env file")
        println("   API Key: ${apiKey.take(20)}...")

        gson = Gson()
        httpClient = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS) // Multimodal models may take longer
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()

        // Generate test media files on-demand
        generateTestMedia()

        println("✅ HTTP client initialized")
        println("✅ Test media ready")
        println("⚠️  WARNING: Tests will make REAL API calls to OpenRouter with multimodal models!")
    }

    @AfterAll
    fun tearDownAll() {
        println("✅ Multimodal tests completed")
        println("📁 Test media files stored in: ${TestMediaGenerator.getMediaDir()}")
        println("   (Files are cached and reused across test runs)")
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

    private fun generateTestMedia() {
        println("📦 Generating test media files (if not already cached)...")

        // Generate image (lightweight, using Java AWT - no external dependency)
        imageDataUrl = TestMediaGenerator.getImageDataUrl("test-image.png")
        println("   ✅ Image ready")

        // Generate audio (using media-gen CLI tool)
        audioDataUrl = TestMediaGenerator.getAudioDataUrl("test-audio.mp3")
        println("   ✅ Audio ready")

        // Generate video (using media-gen CLI tool)
        videoDataUrl = TestMediaGenerator.getVideoDataUrl("test-video.mp4")
        println("   ✅ Video ready")

        println("📸 Test media stored in: ${TestMediaGenerator.getMediaDir()}")
    }

    @Nested
    @DisplayName("Image Support Tests")
    inner class ImageSupportTests {

        @Test
        @DisplayName("Should successfully analyze image with GPT-4o")
        fun testImageAnalysisGpt4() {
            println("\n🧪 Testing image analysis with GPT-4o...")

            // Given: Request with image content
            val requestBody = createVisionRequest(
                model = "openai/gpt-4o",
                imageDataUrl = imageDataUrl,
                prompt = "Describe what you see in this image."
            )

            // When: Send request to OpenRouter
            val response = sendRequest(requestBody, "GPT-4o Vision Test")

            // Then: Should get successful response with content
            assertSuccessfulResponse(response, "image analysis")
        }

        @Test
        @DisplayName("Should successfully analyze image with Claude 3")
        fun testImageAnalysisClaude() {
            println("\n🧪 Testing image analysis with Claude 3...")

            // Given: Request with image content
            val requestBody = createVisionRequest(
                model = "anthropic/claude-3-haiku",
                imageDataUrl = imageDataUrl,
                prompt = "What do you see in this image?"
            )

            // When: Send request to OpenRouter
            val response = sendRequest(requestBody, "Claude Vision Test")

            // Then: Should get successful response with content
            assertSuccessfulResponse(response, "Claude image analysis")
        }
    }

    @Nested
    @DisplayName("Audio Support Tests")
    inner class AudioSupportTests {

        @Test
        @DisplayName("Should successfully process audio with Gemini 2.5 Flash")
        fun testAudioProcessing() {
            println("\n🧪 Testing audio processing with Gemini 2.5 Flash...")

            // Given: Request with audio content
            val requestBody = createAudioRequest(
                model = "google/gemini-2.5-flash",
                audioDataUrl = audioDataUrl,
                prompt = "Describe the audio you hear. What frequency or tone is it?"
            )

            // When: Send request to OpenRouter
            val response = sendRequest(requestBody, "Audio Test")

            // Then: Should get successful response
            assertSuccessfulResponse(response, "audio processing")
        }
    }

    @Nested
    @DisplayName("Video Support Tests")
    inner class VideoSupportTests {

        @Test
        @DisplayName("Should successfully process video with Gemini 2.5 Flash")
        fun testVideoProcessing() {
            println("\n🧪 Testing video processing with Gemini 2.5 Flash...")

            // Given: Request with video content
            val requestBody = createVideoRequest(
                model = "google/gemini-2.5-flash",
                videoDataUrl = videoDataUrl,
                prompt = "Describe what you see in this video. What is happening?"
            )

            // When: Send request to OpenRouter
            val response = sendRequest(requestBody, "Video Test")

            // Then: Should get successful response
            assertSuccessfulResponse(response, "video processing")
        }
    }

    @Nested
    @DisplayName("Mixed Content Tests")
    inner class MixedContentTests {

        @Test
        @DisplayName("Should handle multiple images in one request")
        fun testMultipleImages() {
            println("\n🧪 Testing multiple images in one request...")

            // Given: Request with multiple images
            val image2DataUrl = TestMediaGenerator.getImageDataUrl("test-image-2.png")
            val requestBody = createMultiImageRequest(
                model = "openai/gpt-4o",
                imageDataUrls = listOf(imageDataUrl, image2DataUrl),
                prompt = "Compare these two images. What are the differences?"
            )

            // When: Send request to OpenRouter
            val response = sendRequest(requestBody, "Multi-Image Test")

            // Then: Should get successful response
            assertSuccessfulResponse(response, "multiple images")
        }

        @Test
        @DisplayName("Should handle text-only content with multimodal model")
        fun testTextOnlyWithMultimodalModel() {
            println("\n🧪 Testing text-only content with multimodal model...")

            // Given: Text-only request to multimodal model
            val requestBody = createTextOnlyRequest(
                model = "openai/gpt-4o",
                prompt = "What is the capital of France?"
            )

            // When: Send request to OpenRouter
            val response = sendRequest(requestBody, "Text-Only Test")

            // Then: Should get successful response
            val responseBody = response.body?.string()
            assertNotNull(responseBody, "Response body should not be null")

            val jsonResponse = gson.fromJson(responseBody, JsonObject::class.java)
            val content = jsonResponse.getAsJsonArray("choices")
                .get(0).asJsonObject
                .getAsJsonObject("message")
                .get("content").asString

            assertTrue(
                content.contains("Paris", ignoreCase = true),
                "Response should mention Paris, got: $content"
            )

            println("✅ Text-only test succeeded")
        }
    }

    // Helper methods
    private fun sendRequest(requestBody: String, testName: String): okhttp3.Response {
        val request = Request.Builder()
            .url("$openRouterApiUrl/chat/completions")
            .post(requestBody.toRequestBody("application/json".toMediaType()))
            .header("Authorization", "Bearer $apiKey")
            .header("HTTP-Referer", "https://github.com/openrouter-intellij-plugin")
            .header("X-Title", "OpenRouter IntelliJ Plugin - $testName")
            .build()

        return httpClient.newCall(request).execute()
    }

    private fun assertSuccessfulResponse(response: okhttp3.Response, testType: String) {
        assertTrue(response.isSuccessful, "Expected successful response for $testType, got ${response.code}")

        val responseBody = response.body?.string()
        assertNotNull(responseBody, "Response body should not be null for $testType")

        val jsonResponse = gson.fromJson(responseBody, JsonObject::class.java)
        assertTrue(jsonResponse.has("choices"), "Response should have choices for $testType")

        val content = jsonResponse.getAsJsonArray("choices")
            .get(0).asJsonObject
            .getAsJsonObject("message")
            .get("content").asString

        assertFalse(content.isBlank(), "Response content should not be empty for $testType")

        println("✅ $testType succeeded")
        println("   Response: ${content.take(100)}...")
    }

    // Request builder methods
    private fun createVisionRequest(model: String, imageDataUrl: String, prompt: String): String {
        val requestJson = JsonObject().apply {
            addProperty("model", model)
            add(
                "messages",
                JsonArray().apply {
                    add(
                        JsonObject().apply {
                            addProperty("role", "user")
                            add(
                                "content",
                                JsonArray().apply {
                                    add(
                                        JsonObject().apply {
                                            addProperty("type", "text")
                                            addProperty("text", prompt)
                                        }
                                    )
                                    add(
                                        JsonObject().apply {
                                            addProperty("type", "image_url")
                                            add(
                                                "image_url",
                                                JsonObject().apply {
                                                    addProperty("url", imageDataUrl)
                                                }
                                            )
                                        }
                                    )
                                }
                            )
                        }
                    )
                }
            )
            addProperty("max_tokens", 300)
        }
        return gson.toJson(requestJson)
    }

    private fun createAudioRequest(model: String, audioDataUrl: String, prompt: String): String {
        val requestJson = JsonObject().apply {
            addProperty("model", model)
            add(
                "messages",
                JsonArray().apply {
                    add(
                        JsonObject().apply {
                            addProperty("role", "user")
                            add(
                                "content",
                                JsonArray().apply {
                                    add(
                                        JsonObject().apply {
                                            addProperty("type", "text")
                                            addProperty("text", prompt)
                                        }
                                    )
                                    add(
                                        JsonObject().apply {
                                            addProperty("type", "audio_url")
                                            add(
                                                "audio_url",
                                                JsonObject().apply {
                                                    addProperty("url", audioDataUrl)
                                                }
                                            )
                                        }
                                    )
                                }
                            )
                        }
                    )
                }
            )
            addProperty("max_tokens", 300)
        }
        return gson.toJson(requestJson)
    }

    private fun createVideoRequest(model: String, videoDataUrl: String, prompt: String): String {
        val requestJson = JsonObject().apply {
            addProperty("model", model)
            add(
                "messages",
                JsonArray().apply {
                    add(
                        JsonObject().apply {
                            addProperty("role", "user")
                            add(
                                "content",
                                JsonArray().apply {
                                    add(
                                        JsonObject().apply {
                                            addProperty("type", "text")
                                            addProperty("text", prompt)
                                        }
                                    )
                                    add(
                                        JsonObject().apply {
                                            addProperty("type", "video_url")
                                            add(
                                                "video_url",
                                                JsonObject().apply {
                                                    addProperty("url", videoDataUrl)
                                                }
                                            )
                                        }
                                    )
                                }
                            )
                        }
                    )
                }
            )
            addProperty("max_tokens", 500)
        }
        return gson.toJson(requestJson)
    }

    private fun createMultiImageRequest(model: String, imageDataUrls: List<String>, prompt: String): String {
        val requestJson = JsonObject().apply {
            addProperty("model", model)
            add(
                "messages",
                JsonArray().apply {
                    add(
                        JsonObject().apply {
                            addProperty("role", "user")
                            add(
                                "content",
                                JsonArray().apply {
                                    add(
                                        JsonObject().apply {
                                            addProperty("type", "text")
                                            addProperty("text", prompt)
                                        }
                                    )
                                    imageDataUrls.forEach { imageUrl ->
                                        add(
                                            JsonObject().apply {
                                                addProperty("type", "image_url")
                                                add(
                                                    "image_url",
                                                    JsonObject().apply {
                                                        addProperty("url", imageUrl)
                                                    }
                                                )
                                            }
                                        )
                                    }
                                }
                            )
                        }
                    )
                }
            )
            addProperty("max_tokens", 500)
        }
        return gson.toJson(requestJson)
    }

    private fun createTextOnlyRequest(model: String, prompt: String): String {
        val requestJson = JsonObject().apply {
            addProperty("model", model)
            add(
                "messages",
                JsonArray().apply {
                    add(
                        JsonObject().apply {
                            addProperty("role", "user")
                            addProperty("content", prompt)
                        }
                    )
                }
            )
            addProperty("max_tokens", 100)
        }
        return gson.toJson(requestJson)
    }
}
