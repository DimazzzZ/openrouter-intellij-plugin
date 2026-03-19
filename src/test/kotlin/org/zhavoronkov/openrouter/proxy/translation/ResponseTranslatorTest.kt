package org.zhavoronkov.openrouter.proxy.translation

import com.google.gson.JsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.zhavoronkov.openrouter.models.ChatChoice
import org.zhavoronkov.openrouter.models.ChatCompletionResponse
import org.zhavoronkov.openrouter.models.ChatMessage
import org.zhavoronkov.openrouter.models.ChatUsage
import org.zhavoronkov.openrouter.models.ProvidersResponse

@DisplayName("ResponseTranslator Tests")
class ResponseTranslatorTest {

    @Nested
    @DisplayName("translateChatCompletionResponse Tests")
    inner class TranslateResponseTests {

        @Test
        @DisplayName("should map all fields correctly")
        fun testMapAllFields() {
            val response = ChatCompletionResponse(
                model = "openai/gpt-4",
                choices = listOf(
                    ChatChoice(
                        index = 0,
                        message = ChatMessage(role = "assistant", content = JsonPrimitive("Hello!")),
                        finishReason = "stop"
                    )
                ),
                usage = ChatUsage(promptTokens = 10, completionTokens = 5, totalTokens = 15)
            )

            val translated = ResponseTranslator.translateChatCompletionResponse(response, "openai/gpt-4")

            assertEquals("openai/gpt-4", translated.model)
            assertEquals(1, translated.choices.size)
            assertEquals("assistant", translated.choices.first().message.role)
            assertEquals("Hello!", translated.choices.first().message.content.asString)
            assertEquals("stop", translated.choices.first().finishReason)
            assertEquals(10, translated.usage?.promptTokens)
            assertEquals(5, translated.usage?.completionTokens)
            assertEquals(15, translated.usage?.totalTokens)
        }

        @Test
        @DisplayName("should use requested model name in output")
        fun testUsesRequestedModel() {
            val response = ChatCompletionResponse(
                model = "different/model-name",
                choices = listOf(
                    ChatChoice(
                        index = 0,
                        message = ChatMessage(role = "assistant", content = JsonPrimitive("Hi")),
                        finishReason = "stop"
                    )
                )
            )

            val translated = ResponseTranslator.translateChatCompletionResponse(response, "openai/gpt-4o")

            assertEquals("openai/gpt-4o", translated.model)
        }

        @Test
        @DisplayName("should use custom request ID when provided")
        fun testCustomRequestId() {
            val response = ChatCompletionResponse(
                model = "openai/gpt-4",
                choices = listOf(
                    ChatChoice(
                        index = 0,
                        message = ChatMessage(role = "assistant", content = JsonPrimitive("Hi")),
                        finishReason = "stop"
                    )
                )
            )

            val translated = ResponseTranslator.translateChatCompletionResponse(
                response,
                "openai/gpt-4",
                "custom-request-id-123"
            )

            assertEquals("custom-request-id-123", translated.id)
        }

        @Test
        @DisplayName("should generate request ID when not provided")
        fun testGeneratedRequestId() {
            val response = ChatCompletionResponse(
                model = "openai/gpt-4",
                choices = listOf(
                    ChatChoice(
                        index = 0,
                        message = ChatMessage(role = "assistant", content = JsonPrimitive("Hi")),
                        finishReason = "stop"
                    )
                )
            )

            val translated = ResponseTranslator.translateChatCompletionResponse(response, "openai/gpt-4")

            assertTrue(translated.id.startsWith("chatcmpl-"))
            assertTrue(translated.id.length > 10)
        }

        @Test
        @DisplayName("should handle multiple choices")
        fun testMultipleChoices() {
            val response = ChatCompletionResponse(
                model = "openai/gpt-4",
                choices = listOf(
                    ChatChoice(
                        index = 0,
                        message = ChatMessage(role = "assistant", content = JsonPrimitive("Option 1")),
                        finishReason = "stop"
                    ),
                    ChatChoice(
                        index = 1,
                        message = ChatMessage(role = "assistant", content = JsonPrimitive("Option 2")),
                        finishReason = "stop"
                    ),
                    ChatChoice(
                        index = 2,
                        message = ChatMessage(role = "assistant", content = JsonPrimitive("Option 3")),
                        finishReason = "stop"
                    )
                )
            )

            val translated = ResponseTranslator.translateChatCompletionResponse(response, "openai/gpt-4")

            assertEquals(3, translated.choices.size)
            assertEquals(0, translated.choices[0].index)
            assertEquals(1, translated.choices[1].index)
            assertEquals(2, translated.choices[2].index)
            assertEquals("Option 1", translated.choices[0].message.content.asString)
            assertEquals("Option 2", translated.choices[1].message.content.asString)
            assertEquals("Option 3", translated.choices[2].message.content.asString)
        }

        @Test
        @DisplayName("should handle null choices")
        fun testNullChoices() {
            val response = ChatCompletionResponse(
                model = "openai/gpt-4",
                choices = null,
                usage = null
            )

            val translated = ResponseTranslator.translateChatCompletionResponse(response, "openai/gpt-4")

            assertTrue(translated.choices.isEmpty())
        }

        @Test
        @DisplayName("should handle null usage")
        fun testNullUsage() {
            val response = ChatCompletionResponse(
                model = "openai/gpt-4",
                choices = listOf(
                    ChatChoice(
                        index = 0,
                        message = ChatMessage(role = "assistant", content = JsonPrimitive("Hi")),
                        finishReason = "stop"
                    )
                ),
                usage = null
            )

            val translated = ResponseTranslator.translateChatCompletionResponse(response, "openai/gpt-4")

            assertNull(translated.usage)
        }

        @Test
        @DisplayName("should handle null message in choice")
        fun testNullMessageInChoice() {
            val response = ChatCompletionResponse(
                model = "openai/gpt-4",
                choices = listOf(
                    ChatChoice(
                        index = 0,
                        message = null,
                        finishReason = "stop"
                    )
                )
            )

            val translated = ResponseTranslator.translateChatCompletionResponse(response, "openai/gpt-4")

            assertEquals(1, translated.choices.size)
            assertEquals("assistant", translated.choices.first().message.role)
            assertEquals("", translated.choices.first().message.content.asString)
        }

        @Test
        @DisplayName("should handle blank role in message")
        fun testBlankRoleInMessage() {
            val response = ChatCompletionResponse(
                model = "openai/gpt-4",
                choices = listOf(
                    ChatChoice(
                        index = 0,
                        message = ChatMessage(role = "", content = JsonPrimitive("Hi")),
                        finishReason = "stop"
                    )
                )
            )

            val translated = ResponseTranslator.translateChatCompletionResponse(response, "openai/gpt-4")

            // Empty role is passed through - caller should validate
            assertEquals("", translated.choices.first().message.role)
        }

        @Test
        @DisplayName("should handle empty string content in message")
        fun testEmptyStringContentInMessage() {
            val response = ChatCompletionResponse(
                model = "openai/gpt-4",
                choices = listOf(
                    ChatChoice(
                        index = 0,
                        message = ChatMessage(role = "assistant", content = JsonPrimitive("")),
                        finishReason = "stop"
                    )
                )
            )

            val translated = ResponseTranslator.translateChatCompletionResponse(response, "openai/gpt-4")

            assertEquals("", translated.choices.first().message.content.asString)
        }

        @Test
        @DisplayName("should handle null usage token counts")
        fun testNullUsageTokenCounts() {
            val response = ChatCompletionResponse(
                model = "openai/gpt-4",
                choices = listOf(
                    ChatChoice(
                        index = 0,
                        message = ChatMessage(role = "assistant", content = JsonPrimitive("Hi")),
                        finishReason = "stop"
                    )
                ),
                usage = ChatUsage(promptTokens = null, completionTokens = null, totalTokens = null)
            )

            val translated = ResponseTranslator.translateChatCompletionResponse(response, "openai/gpt-4")

            assertEquals(0, translated.usage?.promptTokens)
            assertEquals(0, translated.usage?.completionTokens)
            assertEquals(0, translated.usage?.totalTokens)
        }

        @Test
        @DisplayName("should set created timestamp")
        fun testCreatedTimestamp() {
            val response = ChatCompletionResponse(
                model = "openai/gpt-4",
                choices = listOf(
                    ChatChoice(
                        index = 0,
                        message = ChatMessage(role = "assistant", content = JsonPrimitive("Hi")),
                        finishReason = "stop"
                    )
                )
            )

            val beforeTime = System.currentTimeMillis() / 1000
            val translated = ResponseTranslator.translateChatCompletionResponse(response, "openai/gpt-4")
            val afterTime = System.currentTimeMillis() / 1000

            assertTrue(translated.created >= beforeTime)
            assertTrue(translated.created <= afterTime)
        }
    }

    @Nested
    @DisplayName("validateTranslatedResponse Tests")
    inner class ValidateResponseTests {

        @Test
        @DisplayName("should accept valid response")
        fun testValidResponse() {
            val response = ChatCompletionResponse(
                model = "openai/gpt-4",
                choices = listOf(
                    ChatChoice(
                        index = 0,
                        message = ChatMessage(role = "assistant", content = JsonPrimitive("Hello!")),
                        finishReason = "stop"
                    )
                )
            )

            val translated = ResponseTranslator.translateChatCompletionResponse(response, "openai/gpt-4")

            assertTrue(ResponseTranslator.validateTranslatedResponse(translated))
        }

        @Test
        @DisplayName("should reject blank content")
        fun testRejectBlankContent() {
            val response = ChatCompletionResponse(
                model = "openai/gpt-4",
                choices = listOf(
                    ChatChoice(
                        index = 0,
                        message = ChatMessage(role = "assistant", content = JsonPrimitive("")),
                        finishReason = "stop"
                    )
                )
            )

            val translated = ResponseTranslator.translateChatCompletionResponse(response, "openai/gpt-4")

            assertFalse(ResponseTranslator.validateTranslatedResponse(translated))
        }

        @Test
        @DisplayName("should reject whitespace-only content")
        fun testRejectWhitespaceContent() {
            val response = ChatCompletionResponse(
                model = "openai/gpt-4",
                choices = listOf(
                    ChatChoice(
                        index = 0,
                        message = ChatMessage(role = "assistant", content = JsonPrimitive("   ")),
                        finishReason = "stop"
                    )
                )
            )

            val translated = ResponseTranslator.translateChatCompletionResponse(response, "openai/gpt-4")

            assertFalse(ResponseTranslator.validateTranslatedResponse(translated))
        }

        @Test
        @DisplayName("should reject empty choices")
        fun testRejectEmptyChoices() {
            val response = ChatCompletionResponse(
                model = "openai/gpt-4",
                choices = emptyList()
            )

            val translated = ResponseTranslator.translateChatCompletionResponse(response, "openai/gpt-4")

            assertFalse(ResponseTranslator.validateTranslatedResponse(translated))
        }
    }

    @Nested
    @DisplayName("createErrorResponse Tests")
    inner class ErrorResponseTests {

        @Test
        @DisplayName("should include error message")
        fun testErrorMessage() {
            val error = ResponseTranslator.createErrorResponse("Something went wrong")

            assertEquals("Something went wrong", error.error.message)
        }

        @Test
        @DisplayName("should use default error type")
        fun testDefaultErrorType() {
            val error = ResponseTranslator.createErrorResponse("Error")

            assertEquals("invalid_request_error", error.error.type)
        }

        @Test
        @DisplayName("should use custom error type")
        fun testCustomErrorType() {
            val error = ResponseTranslator.createErrorResponse("Timeout", "timeout_error")

            assertEquals("timeout_error", error.error.type)
        }

        @Test
        @DisplayName("should include error code when provided")
        fun testErrorCode() {
            val error = ResponseTranslator.createErrorResponse(
                "Invalid key",
                "invalid_request_error",
                "invalid_api_key"
            )

            assertEquals("invalid_api_key", error.error.code)
        }

        @Test
        @DisplayName("should have null code when not provided")
        fun testNullCode() {
            val error = ResponseTranslator.createErrorResponse("Error")

            assertNull(error.error.code)
        }
    }

    @Nested
    @DisplayName("createAuthErrorResponse Tests")
    inner class AuthErrorResponseTests {

        @Test
        @DisplayName("should create authentication error response")
        fun testAuthErrorResponse() {
            val error = ResponseTranslator.createAuthErrorResponse()

            assertNotNull(error.error.message)
            assertTrue(error.error.message.contains("API key"))
            assertEquals("invalid_request_error", error.error.type)
            assertEquals("invalid_api_key", error.error.code)
        }

        @Test
        @DisplayName("should include OpenAI API key URL")
        fun testIncludesApiKeyUrl() {
            val error = ResponseTranslator.createAuthErrorResponse()

            assertTrue(error.error.message.contains("platform.openai.com/account/api-keys"))
        }
    }

    @Nested
    @DisplayName("translateModelsResponse Tests")
    inner class TranslateModelsResponseTests {

        @Test
        @DisplayName("should return core models list")
        fun testReturnsCoreModels() {
            val translated = ResponseTranslator.translateModelsResponse(ProvidersResponse(emptyList()))

            assertTrue(translated.data.isNotEmpty())
            assertEquals("list", translated.`object`)
        }

        @Test
        @DisplayName("should include gpt-4 models")
        fun testIncludesGpt4Models() {
            val translated = ResponseTranslator.translateModelsResponse(ProvidersResponse(emptyList()))

            val modelIds = translated.data.map { it.id }
            assertTrue(modelIds.contains("gpt-4"))
            assertTrue(modelIds.contains("gpt-4-turbo"))
            assertTrue(modelIds.contains("gpt-4o"))
            assertTrue(modelIds.contains("gpt-4o-mini"))
        }

        @Test
        @DisplayName("should include gpt-3.5-turbo")
        fun testIncludesGpt35Turbo() {
            val translated = ResponseTranslator.translateModelsResponse(ProvidersResponse(emptyList()))

            val modelIds = translated.data.map { it.id }
            assertTrue(modelIds.contains("gpt-3.5-turbo"))
        }

        @Test
        @DisplayName("models should have correct structure")
        fun testModelStructure() {
            val translated = ResponseTranslator.translateModelsResponse(ProvidersResponse(emptyList()))

            val model = translated.data.first()
            assertNotNull(model.id)
            assertNotNull(model.ownedBy)
            assertTrue(model.created > 0)
            assertNotNull(model.permission)
            assertTrue(model.permission.isNotEmpty())
        }

        @Test
        @DisplayName("model permissions should have correct structure")
        fun testModelPermissionStructure() {
            val translated = ResponseTranslator.translateModelsResponse(ProvidersResponse(emptyList()))

            val permission = translated.data.first().permission.first()
            assertNotNull(permission.id)
            assertTrue(permission.created > 0)
            assertEquals("*", permission.organization)
            assertTrue(permission.allowSampling)
            assertTrue(permission.allowView)
        }
    }

    @Nested
    @DisplayName("createHealthCheckResponse Tests")
    inner class HealthCheckResponseTests {

        @Test
        @DisplayName("should return health check status")
        fun testHealthCheckStatus() {
            val response = ResponseTranslator.createHealthCheckResponse()

            assertEquals("ok", response["status"])
        }

        @Test
        @DisplayName("should include service name")
        fun testHealthCheckServiceName() {
            val response = ResponseTranslator.createHealthCheckResponse()

            assertEquals("openrouter-proxy", response["service"])
        }

        @Test
        @DisplayName("should include version")
        fun testHealthCheckVersion() {
            val response = ResponseTranslator.createHealthCheckResponse()

            assertEquals("1.0.0", response["version"])
        }

        @Test
        @DisplayName("should include timestamp")
        fun testHealthCheckTimestamp() {
            val beforeTime = System.currentTimeMillis()
            val response = ResponseTranslator.createHealthCheckResponse()
            val afterTime = System.currentTimeMillis()

            val timestamp = response["timestamp"] as Long
            assertTrue(timestamp >= beforeTime)
            assertTrue(timestamp <= afterTime)
        }
    }
}
