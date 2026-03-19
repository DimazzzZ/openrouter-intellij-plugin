package org.zhavoronkov.openrouter.proxy.translation

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.zhavoronkov.openrouter.proxy.models.OpenAIChatCompletionRequest
import org.zhavoronkov.openrouter.proxy.models.OpenAIChatMessage

@DisplayName("RequestTranslator Tests")
class RequestTranslatorTest {

    @Nested
    @DisplayName("translateChatCompletionRequest Tests")
    inner class TranslateRequestTests {

        @Test
        @DisplayName("should map fields and use defaults for missing values")
        fun testMapFieldsWithDefaults() {
            val openAIRequest = OpenAIChatCompletionRequest(
                model = "openai/gpt-4",
                messages = listOf(
                    OpenAIChatMessage(role = "user", content = JsonPrimitive("Hello"))
                ),
                temperature = null,
                maxTokens = null,
                stream = true
            )

            val translated = RequestTranslator.translateChatCompletionRequest(openAIRequest)

            assertEquals("openai/gpt-4", translated.model)
            assertEquals(1, translated.messages.size)
            assertEquals("user", translated.messages.first().role)
            assertEquals(0.7, translated.temperature) // Default temperature
            assertEquals(true, translated.stream)
        }

        @Test
        @DisplayName("should preserve all optional parameters when provided")
        fun testPreserveAllOptionalParameters() {
            val openAIRequest = OpenAIChatCompletionRequest(
                model = "anthropic/claude-3.5-sonnet",
                messages = listOf(
                    OpenAIChatMessage(role = "user", content = JsonPrimitive("Test"))
                ),
                temperature = 0.5,
                maxTokens = 1000,
                topP = 0.9,
                frequencyPenalty = 0.5,
                presencePenalty = 0.3,
                stop = listOf("STOP", "END"),
                stream = false
            )

            val translated = RequestTranslator.translateChatCompletionRequest(openAIRequest)

            assertEquals("anthropic/claude-3.5-sonnet", translated.model)
            assertEquals(0.5, translated.temperature)
            assertEquals(1000, translated.maxTokens)
            assertEquals(0.9, translated.topP)
            assertEquals(0.5, translated.frequencyPenalty)
            assertEquals(0.3, translated.presencePenalty)
            assertEquals(listOf("STOP", "END"), translated.stop)
            assertEquals(false, translated.stream)
        }

        @Test
        @DisplayName("should default stream to false when null")
        fun testDefaultStreamToFalse() {
            val openAIRequest = OpenAIChatCompletionRequest(
                model = "openai/gpt-4",
                messages = listOf(
                    OpenAIChatMessage(role = "user", content = JsonPrimitive("Hello"))
                ),
                stream = null
            )

            val translated = RequestTranslator.translateChatCompletionRequest(openAIRequest)

            assertEquals(false, translated.stream)
        }

        @Test
        @DisplayName("should translate multiple messages preserving order")
        fun testMultipleMessages() {
            val openAIRequest = OpenAIChatCompletionRequest(
                model = "openai/gpt-4",
                messages = listOf(
                    OpenAIChatMessage(role = "system", content = JsonPrimitive("You are helpful")),
                    OpenAIChatMessage(role = "user", content = JsonPrimitive("Hello")),
                    OpenAIChatMessage(role = "assistant", content = JsonPrimitive("Hi there!")),
                    OpenAIChatMessage(role = "user", content = JsonPrimitive("How are you?"))
                )
            )

            val translated = RequestTranslator.translateChatCompletionRequest(openAIRequest)

            assertEquals(4, translated.messages.size)
            assertEquals("system", translated.messages[0].role)
            assertEquals("user", translated.messages[1].role)
            assertEquals("assistant", translated.messages[2].role)
            assertEquals("user", translated.messages[3].role)
        }

        @Test
        @DisplayName("should translate message with name field")
        fun testMessageWithName() {
            val openAIRequest = OpenAIChatCompletionRequest(
                model = "openai/gpt-4",
                messages = listOf(
                    OpenAIChatMessage(
                        role = "user",
                        content = JsonPrimitive("Hello"),
                        name = "John"
                    )
                )
            )

            val translated = RequestTranslator.translateChatCompletionRequest(openAIRequest)

            assertEquals("John", translated.messages.first().name)
        }

        @Test
        @DisplayName("should handle array content (multimodal)")
        fun testArrayContent() {
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
                                addProperty("url", "https://example.com/image.png")
                            }
                        )
                    }
                )
            }

            val openAIRequest = OpenAIChatCompletionRequest(
                model = "openai/gpt-4-vision-preview",
                messages = listOf(
                    OpenAIChatMessage(role = "user", content = contentArray)
                )
            )

            val translated = RequestTranslator.translateChatCompletionRequest(openAIRequest)

            assertTrue(translated.messages.first().content.isJsonArray)
            assertEquals(2, translated.messages.first().content.asJsonArray.size())
        }
    }

    @Nested
    @DisplayName("validateTranslatedRequest Tests")
    inner class ValidateRequestTests {

        @Test
        @DisplayName("should accept valid request with all fields")
        fun testValidRequestWithAllFields() {
            val openAIRequest = OpenAIChatCompletionRequest(
                model = "openai/gpt-4",
                messages = listOf(OpenAIChatMessage(role = "user", content = JsonPrimitive("Hi"))),
                temperature = 0.5,
                maxTokens = 100,
                topP = 0.5
            )

            val translated = RequestTranslator.translateChatCompletionRequest(openAIRequest)

            assertTrue(RequestTranslator.validateTranslatedRequest(translated))
        }

        @Test
        @DisplayName("should reject blank model")
        fun testRejectBlankModel() {
            val openAIRequest = OpenAIChatCompletionRequest(
                model = "",
                messages = listOf(OpenAIChatMessage(role = "user", content = JsonPrimitive("Hi")))
            )

            val translated = RequestTranslator.translateChatCompletionRequest(openAIRequest)

            assertFalse(RequestTranslator.validateTranslatedRequest(translated))
        }

        @Test
        @DisplayName("should reject whitespace-only model")
        fun testRejectWhitespaceModel() {
            val openAIRequest = OpenAIChatCompletionRequest(
                model = "   ",
                messages = listOf(OpenAIChatMessage(role = "user", content = JsonPrimitive("Hi")))
            )

            val translated = RequestTranslator.translateChatCompletionRequest(openAIRequest)

            assertFalse(RequestTranslator.validateTranslatedRequest(translated))
        }

        @Test
        @DisplayName("should reject empty messages list")
        fun testRejectEmptyMessages() {
            val openAIRequest = OpenAIChatCompletionRequest(
                model = "openai/gpt-4",
                messages = emptyList()
            )

            val translated = RequestTranslator.translateChatCompletionRequest(openAIRequest)

            assertFalse(RequestTranslator.validateTranslatedRequest(translated))
        }

        @Test
        @DisplayName("should reject blank role")
        fun testRejectBlankRole() {
            val openAIRequest = OpenAIChatCompletionRequest(
                model = "openai/gpt-4",
                messages = listOf(OpenAIChatMessage(role = "", content = JsonPrimitive("Hi")))
            )

            val translated = RequestTranslator.translateChatCompletionRequest(openAIRequest)

            assertFalse(RequestTranslator.validateTranslatedRequest(translated))
        }

        @Test
        @DisplayName("should reject blank content")
        fun testRejectBlankContent() {
            val openAIRequest = OpenAIChatCompletionRequest(
                model = "openai/gpt-4",
                messages = listOf(OpenAIChatMessage(role = "user", content = JsonPrimitive("")))
            )

            val translated = RequestTranslator.translateChatCompletionRequest(openAIRequest)

            assertFalse(RequestTranslator.validateTranslatedRequest(translated))
        }

        @Test
        @DisplayName("should reject whitespace-only content")
        fun testRejectWhitespaceContent() {
            val openAIRequest = OpenAIChatCompletionRequest(
                model = "openai/gpt-4",
                messages = listOf(OpenAIChatMessage(role = "user", content = JsonPrimitive("   ")))
            )

            val translated = RequestTranslator.translateChatCompletionRequest(openAIRequest)

            assertFalse(RequestTranslator.validateTranslatedRequest(translated))
        }

        @Test
        @DisplayName("should reject empty array content")
        fun testRejectEmptyArrayContent() {
            val openAIRequest = OpenAIChatCompletionRequest(
                model = "openai/gpt-4",
                messages = listOf(OpenAIChatMessage(role = "user", content = JsonArray()))
            )

            val translated = RequestTranslator.translateChatCompletionRequest(openAIRequest)

            assertFalse(RequestTranslator.validateTranslatedRequest(translated))
        }

        @Test
        @DisplayName("should accept non-empty array content")
        fun testAcceptNonEmptyArrayContent() {
            val contentArray = JsonArray().apply {
                add(
                    JsonObject().apply {
                        addProperty("type", "text")
                        addProperty("text", "Hello")
                    }
                )
            }

            val openAIRequest = OpenAIChatCompletionRequest(
                model = "openai/gpt-4",
                messages = listOf(OpenAIChatMessage(role = "user", content = contentArray))
            )

            val translated = RequestTranslator.translateChatCompletionRequest(openAIRequest)

            assertTrue(RequestTranslator.validateTranslatedRequest(translated))
        }

        @Test
        @DisplayName("should reject temperature out of range (too high)")
        fun testRejectTemperatureTooHigh() {
            val openAIRequest = OpenAIChatCompletionRequest(
                model = "openai/gpt-4",
                messages = listOf(OpenAIChatMessage(role = "user", content = JsonPrimitive("Hi"))),
                temperature = 2.5
            )

            val translated = RequestTranslator.translateChatCompletionRequest(openAIRequest)

            assertFalse(RequestTranslator.validateTranslatedRequest(translated))
        }

        @Test
        @DisplayName("should reject temperature out of range (negative)")
        fun testRejectNegativeTemperature() {
            val openAIRequest = OpenAIChatCompletionRequest(
                model = "openai/gpt-4",
                messages = listOf(OpenAIChatMessage(role = "user", content = JsonPrimitive("Hi"))),
                temperature = -0.1
            )

            val translated = RequestTranslator.translateChatCompletionRequest(openAIRequest)

            assertFalse(RequestTranslator.validateTranslatedRequest(translated))
        }

        @Test
        @DisplayName("should accept temperature at boundaries")
        fun testAcceptTemperatureBoundaries() {
            // Temperature = 0
            val request1 = OpenAIChatCompletionRequest(
                model = "openai/gpt-4",
                messages = listOf(OpenAIChatMessage(role = "user", content = JsonPrimitive("Hi"))),
                temperature = 0.0
            )
            assertTrue(
                RequestTranslator.validateTranslatedRequest(
                    RequestTranslator.translateChatCompletionRequest(request1)
                )
            )

            // Temperature = 2
            val request2 = OpenAIChatCompletionRequest(
                model = "openai/gpt-4",
                messages = listOf(OpenAIChatMessage(role = "user", content = JsonPrimitive("Hi"))),
                temperature = 2.0
            )
            assertTrue(
                RequestTranslator.validateTranslatedRequest(
                    RequestTranslator.translateChatCompletionRequest(request2)
                )
            )
        }

        @Test
        @DisplayName("should reject non-positive maxTokens")
        fun testRejectNonPositiveMaxTokens() {
            val openAIRequest = OpenAIChatCompletionRequest(
                model = "openai/gpt-4",
                messages = listOf(OpenAIChatMessage(role = "user", content = JsonPrimitive("Hi"))),
                maxTokens = 0
            )

            val translated = RequestTranslator.translateChatCompletionRequest(openAIRequest)

            assertFalse(RequestTranslator.validateTranslatedRequest(translated))
        }

        @Test
        @DisplayName("should reject negative maxTokens")
        fun testRejectNegativeMaxTokens() {
            val openAIRequest = OpenAIChatCompletionRequest(
                model = "openai/gpt-4",
                messages = listOf(OpenAIChatMessage(role = "user", content = JsonPrimitive("Hi"))),
                maxTokens = -100
            )

            val translated = RequestTranslator.translateChatCompletionRequest(openAIRequest)

            assertFalse(RequestTranslator.validateTranslatedRequest(translated))
        }

        @Test
        @DisplayName("should reject topP out of range (too high)")
        fun testRejectTopPTooHigh() {
            val openAIRequest = OpenAIChatCompletionRequest(
                model = "openai/gpt-4",
                messages = listOf(OpenAIChatMessage(role = "user", content = JsonPrimitive("Hi"))),
                topP = 1.5
            )

            val translated = RequestTranslator.translateChatCompletionRequest(openAIRequest)

            assertFalse(RequestTranslator.validateTranslatedRequest(translated))
        }

        @Test
        @DisplayName("should reject topP out of range (negative)")
        fun testRejectNegativeTopP() {
            val openAIRequest = OpenAIChatCompletionRequest(
                model = "openai/gpt-4",
                messages = listOf(OpenAIChatMessage(role = "user", content = JsonPrimitive("Hi"))),
                topP = -0.1
            )

            val translated = RequestTranslator.translateChatCompletionRequest(openAIRequest)

            assertFalse(RequestTranslator.validateTranslatedRequest(translated))
        }

        @Test
        @DisplayName("should accept topP at boundaries")
        fun testAcceptTopPBoundaries() {
            // topP = 0
            val request1 = OpenAIChatCompletionRequest(
                model = "openai/gpt-4",
                messages = listOf(OpenAIChatMessage(role = "user", content = JsonPrimitive("Hi"))),
                topP = 0.0
            )
            assertTrue(
                RequestTranslator.validateTranslatedRequest(
                    RequestTranslator.translateChatCompletionRequest(request1)
                )
            )

            // topP = 1
            val request2 = OpenAIChatCompletionRequest(
                model = "openai/gpt-4",
                messages = listOf(OpenAIChatMessage(role = "user", content = JsonPrimitive("Hi"))),
                topP = 1.0
            )
            assertTrue(
                RequestTranslator.validateTranslatedRequest(
                    RequestTranslator.translateChatCompletionRequest(request2)
                )
            )
        }

        @Test
        @DisplayName("should reject JsonObject content (invalid type)")
        fun testRejectJsonObjectContent() {
            val openAIRequest = OpenAIChatCompletionRequest(
                model = "openai/gpt-4",
                messages = listOf(OpenAIChatMessage(role = "user", content = JsonObject()))
            )

            val translated = RequestTranslator.translateChatCompletionRequest(openAIRequest)

            assertFalse(RequestTranslator.validateTranslatedRequest(translated))
        }
    }

    @Nested
    @DisplayName("getModelMappings Tests")
    inner class ModelMappingsTests {

        @Test
        @DisplayName("should return model mappings for documentation")
        fun testGetModelMappings() {
            val mappings = RequestTranslator.getModelMappings()

            assertNotNull(mappings)
            assertTrue(mappings.isNotEmpty())
            assertEquals("openai/gpt-4", mappings["gpt-4"])
            assertEquals("openai/gpt-4o", mappings["gpt-4o"])
            assertEquals("openai/gpt-4o-mini", mappings["gpt-4o-mini"])
            assertEquals("openai/gpt-3.5-turbo", mappings["gpt-3.5-turbo"])
            assertEquals("anthropic/claude-3.5-sonnet", mappings["claude-3.5-sonnet"])
            assertEquals("google/gemini-pro", mappings["gemini-pro"])
        }
    }
}
