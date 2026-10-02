package org.zhavoronkov.openrouter.toolwindow.chat

import com.google.gson.Gson
import com.google.gson.JsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.zhavoronkov.openrouter.models.ChatCompletionResponse
import org.zhavoronkov.openrouter.models.ChatMessage

/**
 * The chat's request beside a pair's preset - the fields the preset sets stay out of it - and what
 * the reply summary makes of a reply that carries less than usual.
 */
@DisplayName("ChatExchange with a preset, and replies that say little")
class ChatExchangePresetFieldsTest {

    private val messages = listOf(ChatMessage(role = "user", content = JsonPrimitive("hi")))
    private val routed = ChatRequestOptions(routerParam = "medium")

    @Test
    @DisplayName("the chat's own max tokens and router plugins stay out of fields the preset sets")
    fun presetFieldsStayOut() {
        val withPreset = ChatExchange.buildRequest(
            "openrouter/auto@preset/research",
            messages,
            routed,
            presetFields = setOf("max_tokens", "plugins")
        )
        val plain = ChatExchange.buildRequest("openrouter/auto@preset/research", messages, routed)

        assertNull(withPreset.maxTokens)
        assertNull(withPreset.plugins)
        assertNotNull(withPreset.temperature, "the preset does not set temperature")
        assertNotNull(plain.maxTokens)
        assertEquals("auto-router", plain.plugins?.single()?.id)
    }

    @Test
    @DisplayName("a message searching like its preset sends no tool of its own, and otherwise sends one")
    fun webSearchLikeThePreset() {
        val searching = ChatRequestOptions(webSearch = true)

        val likePreset = ChatExchange.buildRequest("m", messages, searching, preset = ChatControls(webSearch = true))
        val presetDoesNot = ChatExchange.buildRequest("m", messages, searching, preset = ChatControls())
        val noPreset = ChatExchange.buildRequest("m", messages, searching)

        assertNull(likePreset.tools)
        assertNotNull(presetDoesNot.tools)
        assertNotNull(noPreset.tools)
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @ValueSource(strings = ["{}", """{"usage":{"cost":0.1},"choices":[]}""", """{"usage":{},"choices":[{}]}"""])
    @DisplayName("a reply without usage, a server tool count or a finish reason summarises as none of them")
    fun replyThatSaysLittle(json: String) {
        val reply = Gson().fromJson(json, ChatCompletionResponse::class.java)

        val summary = ChatExchange.summarizeReply(ChatExchange.buildRequest("m", messages, ChatRequestOptions()), reply)

        assertEquals(0, summary.webSearches)
        assertNull(summary.finishReason)
    }

    @Test
    @DisplayName("two schema selections that differ only in case or space are one entry of a set")
    fun schemaSelectionsHashAlike() {
        val selections = setOf(OutputMode.Schema("Answer"), OutputMode.Schema(" answer "), OutputMode.Schema("other"))

        assertEquals(2, selections.size)
        assertTrue(OutputMode.Schema("ANSWER") in selections)
    }
}
