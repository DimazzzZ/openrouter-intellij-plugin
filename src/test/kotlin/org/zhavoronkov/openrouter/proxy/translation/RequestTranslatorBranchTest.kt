package org.zhavoronkov.openrouter.proxy.translation

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.zhavoronkov.openrouter.models.ChatCompletionRequest
import org.zhavoronkov.openrouter.models.ChatMessage
import org.zhavoronkov.openrouter.proxy.models.OpenAIChatCompletionRequest
import org.zhavoronkov.openrouter.proxy.models.OpenAIChatMessage
import org.zhavoronkov.openrouter.proxy.models.OpenAIToolChoice
import org.zhavoronkov.openrouter.proxy.models.OpenAIToolChoiceFunction

@DisplayName("RequestTranslator Branch Tests")
class RequestTranslatorBranchTest {

    private fun request(
        content: com.google.gson.JsonElement = JsonPrimitive("Hi"),
        temperature: Double? = null,
        maxTokens: Int? = null,
        topP: Double? = null,
        toolChoice: OpenAIToolChoice? = null
    ) = OpenAIChatCompletionRequest(
        model = "openai/gpt-4o",
        messages = listOf(OpenAIChatMessage(role = "user", content = content)),
        temperature = temperature,
        maxTokens = maxTokens,
        topP = topP,
        toolChoice = toolChoice
    )

    @Test
    @DisplayName("toolChoice with a named function is translated")
    fun toolChoiceWithFunction() {
        val translated = RequestTranslator.translateChatCompletionRequest(
            request(toolChoice = OpenAIToolChoice(type = "function", function = OpenAIToolChoiceFunction("lookup")))
        )
        assertNotNull(translated.toolChoice)
        assertEquals("lookup", translated.toolChoice?.function?.name)
    }

    @Test
    @DisplayName("toolChoice without a function leaves the function null")
    fun toolChoiceWithoutFunction() {
        val translated = RequestTranslator.translateChatCompletionRequest(
            request(toolChoice = OpenAIToolChoice(type = "auto", function = null))
        )
        assertNotNull(translated.toolChoice)
        assertEquals(null, translated.toolChoice?.function)
    }

    @Test
    @DisplayName("validate accepts a non-empty JSON-array content")
    fun validateAcceptsArrayContent() {
        val array = JsonArray().apply { add(JsonPrimitive("part")) }
        val translated = RequestTranslator.translateChatCompletionRequest(request(content = array))
        assertTrue(RequestTranslator.validateTranslatedRequest(translated))
    }

    @Test
    @DisplayName("validate rejects a blank string content")
    fun validateRejectsBlankStringContent() {
        val translated = RequestTranslator.translateChatCompletionRequest(request(content = JsonPrimitive("   ")))
        assertFalse(RequestTranslator.validateTranslatedRequest(translated))
    }

    @Test
    @DisplayName("validate rejects an empty JSON-array content")
    fun validateRejectsEmptyArrayContent() {
        val translated = RequestTranslator.translateChatCompletionRequest(request(content = JsonArray()))
        assertFalse(RequestTranslator.validateTranslatedRequest(translated))
    }

    @Test
    @DisplayName("validate rejects an object content (else arm)")
    fun validateRejectsObjectContent() {
        val translated = RequestTranslator.translateChatCompletionRequest(request(content = JsonObject()))
        assertFalse(RequestTranslator.validateTranslatedRequest(translated))
    }

    @Test
    @DisplayName("validate rejects an out-of-range temperature")
    fun validateRejectsBadTemperature() {
        val translated = RequestTranslator.translateChatCompletionRequest(request(temperature = 5.0))
        assertFalse(RequestTranslator.validateTranslatedRequest(translated))
    }

    @Test
    @DisplayName("validate rejects a non-positive maxTokens")
    fun validateRejectsBadMaxTokens() {
        val translated = RequestTranslator.translateChatCompletionRequest(request(maxTokens = 0))
        assertFalse(RequestTranslator.validateTranslatedRequest(translated))
    }

    @Test
    @DisplayName("validate rejects an out-of-range topP")
    fun validateRejectsBadTopP() {
        val translated = RequestTranslator.translateChatCompletionRequest(request(topP = 2.0))
        assertFalse(RequestTranslator.validateTranslatedRequest(translated))
    }

    @Test
    @DisplayName("validate accepts a request with no temperature at all")
    fun validateAcceptsAbsentTemperature() {
        // translateChatCompletionRequest always substitutes a default, so this shape only reaches
        // validateTranslatedRequest from a caller that built the request itself - which the public
        // signature allows.
        val built = ChatCompletionRequest(
            model = "openai/gpt-4o",
            messages = listOf(ChatMessage(role = "user", content = JsonPrimitive("Hi"))),
            temperature = null,
            maxTokens = null,
            topP = null
        )

        assertTrue(RequestTranslator.validateTranslatedRequest(built))
    }

    @Test
    @DisplayName("validate rejects a temperature below the allowed range")
    fun validateRejectsNegativeTemperature() {
        val translated = RequestTranslator.translateChatCompletionRequest(request(temperature = -1.0))

        assertFalse(RequestTranslator.validateTranslatedRequest(translated))
    }

    @Test
    @DisplayName("validate rejects content that is a primitive but not a string")
    fun validateRejectsNonStringPrimitiveContent() {
        val translated = RequestTranslator.translateChatCompletionRequest(request(content = JsonPrimitive(42)))

        assertFalse(
            RequestTranslator.validateTranslatedRequest(translated),
            "a number is a JSON primitive but not a message body"
        )
    }

    @Test
    @DisplayName("validate rejects an empty content array")
    fun validateRejectsEmptyContentArray() {
        val translated = RequestTranslator.translateChatCompletionRequest(request(content = JsonArray()))

        assertFalse(RequestTranslator.validateTranslatedRequest(translated))
    }
}
