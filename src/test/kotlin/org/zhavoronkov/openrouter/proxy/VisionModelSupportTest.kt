package org.zhavoronkov.openrouter.proxy

import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.zhavoronkov.openrouter.proxy.models.OpenAIChatCompletionRequest
import org.zhavoronkov.openrouter.proxy.models.OpenAIChatMessage
import org.zhavoronkov.openrouter.proxy.translation.RequestTranslator

/**
 * Unit tests for vision model support with image content.
 * Tests the ability to handle both text-only and multimodal (text + image) messages.
 */
@DisplayName("Vision Model Support Tests")
class VisionModelSupportTest {

    private val gson = Gson()

    @Nested
    @DisplayName("Message Content Type Tests")
    inner class ContentTypeTests {

        @Test
        @DisplayName("Should support simple text content as JsonPrimitive")
        fun testSimpleTextContent() {
            // Given: Message with simple text content
            val message = OpenAIChatMessage(
                role = "user",
                content = JsonPrimitive("What is the capital of France?")
            )

            // When: Serialize to JSON
            val json = gson.toJson(message)

            // Then: Content should be a simple string
            assertTrue(json.contains("\"content\":\"What is the capital of France?\""))
        }

        @Test
        @DisplayName("Should support structured content with text and image")
        fun testStructuredContent() {
            // Given: Message with structured content (text + image)
            val contentArray = JsonArray().apply {
                add(
                    JsonObject().apply {
                        addProperty("type", "text")
                        addProperty("text", "What's in this image?")
                    }
                )
                add(
                    JsonObject().apply {
                        addProperty("type", "image_url")
                        add(
                            "image_url",
                            JsonObject().apply {
                                addProperty("url", "data:image/png;base64,SHORT_TEST_IMAGE")
                            }
                        )
                    }
                )
            }

            val message = OpenAIChatMessage(
                role = "user",
                content = contentArray
            )

            // When: Serialize to JSON
            val json = gson.toJson(message)

            // Then: Content should be an array
            assertTrue(json.contains("\"type\":\"text\""))
            assertTrue(json.contains("\"type\":\"image_url\""))
            assertTrue(json.contains("\"image_url\""))
        }

        @Test
        @DisplayName("Should support multiple images in one message")
        fun testMultipleImages() {
            // Given: Message with multiple images
            val contentArray = JsonArray().apply {
                add(
                    JsonObject().apply {
                        addProperty("type", "text")
                        addProperty("text", "Compare these images")
                    }
                )
                add(
                    JsonObject().apply {
                        addProperty("type", "image_url")
                        add(
                            "image_url",
                            JsonObject().apply {
                                addProperty("url", "data:image/png;base64,image1")
                            }
                        )
                    }
                )
                add(
                    JsonObject().apply {
                        addProperty("type", "image_url")
                        add(
                            "image_url",
                            JsonObject().apply {
                                addProperty("url", "data:image/png;base64,image2")
                            }
                        )
                    }
                )
            }

            val message = OpenAIChatMessage(
                role = "user",
                content = contentArray
            )

            // When: Get content as JsonArray
            val content = message.content as JsonArray

            // Then: Should have 3 elements (1 text + 2 images)
            assertEquals(3, content.size())
        }
    }

    @Nested
    @DisplayName("Request Translation Tests")
    inner class RequestTranslationTests {

        @Test
        @DisplayName("Should translate request with vision content correctly")
        fun testVisionRequestTranslation() {
            // Given: Request with vision content
            val contentArray = JsonArray().apply {
                add(
                    JsonObject().apply {
                        addProperty("type", "text")
                        addProperty("text", "Describe this image")
                    }
                )
                add(
                    JsonObject().apply {
                        addProperty("type", "image_url")
                        add(
                            "image_url",
                            JsonObject().apply {
                                addProperty("url", "data:image/png;base64,test")
                            }
                        )
                    }
                )
            }

            val request = OpenAIChatCompletionRequest(
                model = "gpt-4-vision-preview",
                messages = listOf(
                    OpenAIChatMessage(role = "user", content = contentArray)
                ),
                maxTokens = 300
            )

            // When: Translate request
            val translated = RequestTranslator.translateChatCompletionRequest(request)

            // Then: Content should be preserved
            assertEquals(1, translated.messages.size)
            assertEquals("user", translated.messages[0].role)
            assertTrue(translated.messages[0].content is JsonArray)
            assertEquals(2, (translated.messages[0].content as JsonArray).size())
        }
    }
}
