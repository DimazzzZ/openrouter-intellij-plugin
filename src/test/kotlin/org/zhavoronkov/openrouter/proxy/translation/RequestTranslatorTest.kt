package org.zhavoronkov.openrouter.proxy.translation

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.zhavoronkov.openrouter.proxy.models.OpenAIChatCompletionRequest
import org.zhavoronkov.openrouter.proxy.models.OpenAIChatMessage

class RequestTranslatorTest {

    @Test
    fun `translateChatCompletionRequest should pass through model name exactly`() {
        // Given
        val openAIRequest = OpenAIChatCompletionRequest(
            model = "gpt-4o-mini",
            messages = listOf(
                OpenAIChatMessage(role = "user", content = "Hello")
            ),
            temperature = 0.7,
            max_tokens = 150,
            stream = false
        )

        // When
        val result = RequestTranslator.translateChatCompletionRequest(openAIRequest)

        // Then
        assertEquals("gpt-4o-mini", result.model, "Model should be passed through exactly")
        assertEquals(1, result.messages.size)
        assertEquals("user", result.messages[0].role)
        assertEquals("Hello", result.messages[0].content)
        assertEquals(0.7, result.temperature)
        assertEquals(150, result.maxTokens)
        assertEquals(false, result.stream, "Stream flag should be passed through as-is")
    }

    @Test
    fun `translateChatCompletionRequest should pass through stream flag when true`() {
        // Given
        val openAIRequest = OpenAIChatCompletionRequest(
            model = "gpt-4o",
            messages = listOf(
                OpenAIChatMessage(role = "user", content = "Test")
            ),
            stream = true // This should be passed through
        )

        // When
        val result = RequestTranslator.translateChatCompletionRequest(openAIRequest)

        // Then
        assertEquals(true, result.stream, "Stream flag should be passed through as-is")
    }

    @Test
    fun `translateChatCompletionRequest should use defaults for optional parameters`() {
        // Given
        val openAIRequest = OpenAIChatCompletionRequest(
            model = "gpt-3.5-turbo",
            messages = listOf(
                OpenAIChatMessage(role = "user", content = "Test")
            )
            // No optional parameters provided
        )

        // When
        val result = RequestTranslator.translateChatCompletionRequest(openAIRequest)

        // Then: With pure passthrough (defaultMaxTokens disabled), no defaults are applied
        assertEquals(0.7, result.temperature)
        assertNull(result.maxTokens, "maxTokens should be null when no max_tokens provided and defaults disabled")
        assertEquals(false, result.stream)
        assertNull(result.topP)
        assertNull(result.frequencyPenalty)
        assertNull(result.presencePenalty)
        assertNull(result.stop)
    }

    @Test
    fun `translateChatCompletionRequest should handle multiple messages`() {
        // Given
        val openAIRequest = OpenAIChatCompletionRequest(
            model = "gpt-4",
            messages = listOf(
                OpenAIChatMessage(role = "system", content = "You are a helpful assistant"),
                OpenAIChatMessage(role = "user", content = "Hello"),
                OpenAIChatMessage(role = "assistant", content = "Hi there!"),
                OpenAIChatMessage(role = "user", content = "How are you?")
            )
        )

        // When
        val result = RequestTranslator.translateChatCompletionRequest(openAIRequest)

        // Then
        assertEquals(4, result.messages.size)
        assertEquals("system", result.messages[0].role)
        assertEquals("You are a helpful assistant", result.messages[0].content)
        assertEquals("user", result.messages[1].role)
        assertEquals("Hello", result.messages[1].content)
        assertEquals("assistant", result.messages[2].role)
        assertEquals("Hi there!", result.messages[2].content)
        assertEquals("user", result.messages[3].role)
        assertEquals("How are you?", result.messages[3].content)
    }

    @Test
    fun `translateChatCompletionRequest should handle all optional parameters`() {
        // Given
        val openAIRequest = OpenAIChatCompletionRequest(
            model = "gpt-4-turbo",
            messages = listOf(
                OpenAIChatMessage(role = "user", content = "Test")
            ),
            temperature = 1.2,
            max_tokens = 500,
            top_p = 0.9,
            frequency_penalty = 0.5,
            presence_penalty = 0.3,
            stop = listOf("END", "STOP")
        )

        // When
        val result = RequestTranslator.translateChatCompletionRequest(openAIRequest)

        // Then
        assertEquals(1.2, result.temperature)
        assertEquals(500, result.maxTokens)
        assertEquals(0.9, result.topP)
        assertEquals(0.5, result.frequencyPenalty)
        assertEquals(0.3, result.presencePenalty)
        assertEquals(listOf("END", "STOP"), result.stop)
    }

    @Test
    fun `validateTranslatedRequest should return true for valid request`() {
        // Given
        val validRequest = RequestTranslator.translateChatCompletionRequest(
            OpenAIChatCompletionRequest(
                model = "gpt-4o-mini",
                messages = listOf(
                    OpenAIChatMessage(role = "user", content = "Hello")
                )
            )
        )

        // When
        val result = RequestTranslator.validateTranslatedRequest(validRequest)

        // Then
        assertTrue(result, "Valid request should pass validation")
    }

    @Test
    fun `validateTranslatedRequest should return false for invalid model`() {
        // Given
        val invalidRequest = RequestTranslator.translateChatCompletionRequest(
            OpenAIChatCompletionRequest(
                model = "", // Invalid empty model
                messages = listOf(
                    OpenAIChatMessage(role = "user", content = "Hello")
                )
            )
        )

        // When
        val result = RequestTranslator.validateTranslatedRequest(invalidRequest)

        // Then
        assertFalse(result, "Request with empty model should fail validation")
    }

    @Test
    fun `validateTranslatedRequest should return false for empty messages`() {
        // Given
        val invalidRequest = RequestTranslator.translateChatCompletionRequest(
            OpenAIChatCompletionRequest(
                model = "gpt-4o-mini",
                messages = emptyList() // Invalid empty messages
            )
        )

        // When
        val result = RequestTranslator.validateTranslatedRequest(invalidRequest)

        // Then
        assertFalse(result, "Request with empty messages should fail validation")
    }

    @Test
    fun `validateTranslatedRequest should return false for invalid temperature`() {
        // Given
        val openAIRequest = OpenAIChatCompletionRequest(
            model = "gpt-4o-mini",
            messages = listOf(
                OpenAIChatMessage(role = "user", content = "Hello")
            ),
            temperature = 3.0 // Invalid temperature > 2.0
        )
        val invalidRequest = RequestTranslator.translateChatCompletionRequest(openAIRequest)

        // When
        val result = RequestTranslator.validateTranslatedRequest(invalidRequest)

        // Then
        assertFalse(result, "Request with invalid temperature should fail validation")
    }

    // Note: translateMessage is a private function, tested indirectly through translateChatCompletionRequest

    @Test
    fun `simplified approach should work with various model names`() {
        // Test that any model name is passed through exactly
        val testModels = listOf(
            "gpt-4o-mini",
            "gpt-4o",
            "gpt-4",
            "gpt-4-turbo",
            "gpt-3.5-turbo",
            "claude-3.5-sonnet",
            "gemini-pro",
            "custom-model-name",
            "provider/model-name"
        )

        testModels.forEach { modelName ->
            // Given
            val request = OpenAIChatCompletionRequest(
                model = modelName,
                messages = listOf(
                    OpenAIChatMessage(role = "user", content = "Test")
                )
            )

            // When
            val result = RequestTranslator.translateChatCompletionRequest(request)

            // Then
            assertEquals(modelName, result.model, "Model '$modelName' should be passed through exactly")
        }
    }
}
