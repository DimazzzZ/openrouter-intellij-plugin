package org.zhavoronkov.openrouter.proxy.translation

import com.google.gson.JsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.zhavoronkov.openrouter.proxy.models.OpenAIChatCompletionRequest
import org.zhavoronkov.openrouter.proxy.models.OpenAIChatMessage

@DisplayName("RequestTranslator Tests")
class RequestTranslatorTest {

    @Test
    fun `translateChatCompletionRequest should map fields and defaults`() {
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
        assertEquals(0.7, translated.temperature)
        assertEquals(true, translated.stream)
    }

    @Test
    fun `validateTranslatedRequest should reject blank model`() {
        val openAIRequest = OpenAIChatCompletionRequest(
            model = "",
            messages = listOf(OpenAIChatMessage(role = "user", content = JsonPrimitive("Hi")))
        )

        val translated = RequestTranslator.translateChatCompletionRequest(openAIRequest)

        assertFalse(RequestTranslator.validateTranslatedRequest(translated))
    }

    @Test
    fun `validateTranslatedRequest should accept array content`() {
        val openAIRequest = OpenAIChatCompletionRequest(
            model = "openai/gpt-4",
            messages = listOf(
                OpenAIChatMessage(role = "user", content = com.google.gson.JsonArray())
            )
        )

        val translated = RequestTranslator.translateChatCompletionRequest(openAIRequest)

        assertFalse(RequestTranslator.validateTranslatedRequest(translated), "Empty array should be invalid")
    }

    @Test
    fun `validateTranslatedRequest should accept valid request`() {
        val openAIRequest = OpenAIChatCompletionRequest(
            model = "openai/gpt-4",
            messages = listOf(OpenAIChatMessage(role = "user", content = JsonPrimitive("Hi"))),
            temperature = 0.5,
            maxTokens = 10,
            topP = 0.5
        )

        val translated = RequestTranslator.translateChatCompletionRequest(openAIRequest)

        assertTrue(RequestTranslator.validateTranslatedRequest(translated))
    }
}
