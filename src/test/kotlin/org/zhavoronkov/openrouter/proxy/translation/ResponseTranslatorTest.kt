package org.zhavoronkov.openrouter.proxy.translation

import com.google.gson.JsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.zhavoronkov.openrouter.models.ChatChoice
import org.zhavoronkov.openrouter.models.ChatCompletionResponse
import org.zhavoronkov.openrouter.models.ChatMessage
import org.zhavoronkov.openrouter.models.ChatUsage
import org.zhavoronkov.openrouter.models.ProvidersResponse

@DisplayName("ResponseTranslator Tests")
class ResponseTranslatorTest {

    @Test
    fun `translateChatCompletionResponse should map fields`() {
        val response = ChatCompletionResponse(
            model = "openai/gpt-4",
            choices = listOf(
                ChatChoice(
                    index = 0,
                    message = ChatMessage(role = "assistant", content = JsonPrimitive("Hi")),
                    finishReason = "stop"
                )
            ),
            usage = ChatUsage(promptTokens = 1, completionTokens = 2, totalTokens = 3)
        )

        val translated = ResponseTranslator.translateChatCompletionResponse(response, "openai/gpt-4")

        assertEquals("openai/gpt-4", translated.model)
        assertEquals(1, translated.choices.size)
        assertEquals(3, translated.usage?.totalTokens)
    }

    @Test
    fun `validateTranslatedResponse should reject blank content`() {
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
    fun `createErrorResponse should include message`() {
        val error = ResponseTranslator.createErrorResponse("boom")

        assertEquals("boom", error.error.message)
    }

    @Test
    fun `translateModelsResponse returns core list`() {
        val translated = ResponseTranslator.translateModelsResponse(ProvidersResponse(emptyList()))

        assertTrue(translated.data.isNotEmpty())
    }
}
