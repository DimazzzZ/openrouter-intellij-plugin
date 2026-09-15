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
import org.zhavoronkov.openrouter.proxy.models.OpenAIChatChoice
import org.zhavoronkov.openrouter.proxy.models.OpenAIChatCompletionResponse
import org.zhavoronkov.openrouter.proxy.models.OpenAIChatMessage

@DisplayName("ResponseTranslator Branch Tests")
class ResponseTranslatorBranchTest {

    @Test
    @DisplayName("null message falls back to assistant role and empty content")
    fun nullMessageFallsBack() {
        val response = ChatCompletionResponse(
            id = "gen-1",
            model = "openai/gpt-4o",
            choices = listOf(ChatChoice(index = 0, message = null, finishReason = "stop"))
        )
        val translated = ResponseTranslator.translateChatCompletionResponse(response, "gpt-4o")
        val message = translated.choices[0].message
        assertEquals("assistant", message.role)
        assertTrue(message.content.isJsonPrimitive)
        assertEquals("", message.content.asString)
    }

    @Test
    @DisplayName("present message keeps its role and content")
    fun presentMessageKept() {
        val response = ChatCompletionResponse(
            id = "gen-2",
            model = "openai/gpt-4o",
            choices = listOf(ChatChoice(
                index = 0,
                message = ChatMessage(role = "assistant", content = JsonPrimitive("hi")),
                finishReason = "stop"
            ))
        )
        val translated = ResponseTranslator.translateChatCompletionResponse(response, "gpt-4o")
        assertEquals("assistant", translated.choices[0].message.role)
        assertEquals("hi", translated.choices[0].message.content.asString)
    }

    private fun validResponse(
        id: String = "chatcmpl-1",
        model: String = "gpt-4o",
        choices: List<OpenAIChatChoice> = listOf(
            OpenAIChatChoice(
                index = 0,
                message = OpenAIChatMessage(role = "assistant", content = JsonPrimitive("hello"))
            )
        )
    ) = OpenAIChatCompletionResponse(id = id, created = 0, model = model, choices = choices)

    @Test
    @DisplayName("validate accepts a well-formed response")
    fun validateAcceptsValid() {
        assertTrue(ResponseTranslator.validateTranslatedResponse(validResponse()))
    }

    @Test
    @DisplayName("validate rejects a blank id")
    fun validateRejectsBlankId() {
        assertFalse(ResponseTranslator.validateTranslatedResponse(validResponse(id = "")))
    }

    @Test
    @DisplayName("validate rejects a blank model")
    fun validateRejectsBlankModel() {
        assertFalse(ResponseTranslator.validateTranslatedResponse(validResponse(model = "")))
    }

    @Test
    @DisplayName("validate rejects an empty choices list")
    fun validateRejectsEmptyChoices() {
        assertFalse(ResponseTranslator.validateTranslatedResponse(validResponse(choices = emptyList())))
    }

    @Test
    @DisplayName("validate rejects a choice with neither content nor tool calls")
    fun validateRejectsEmptyChoice() {
        val choice = OpenAIChatChoice(
            index = 0,
            message = OpenAIChatMessage(role = "assistant", content = JsonPrimitive(""))
        )
        assertFalse(ResponseTranslator.validateTranslatedResponse(validResponse(choices = listOf(choice))))
    }

    @Test
    @DisplayName("validate rejects a choice with a blank role")
    fun validateRejectsBlankRole() {
        val choice = OpenAIChatChoice(
            index = 0,
            message = OpenAIChatMessage(role = "", content = JsonPrimitive("hi"))
        )
        assertFalse(ResponseTranslator.validateTranslatedResponse(validResponse(choices = listOf(choice))))
    }
}
