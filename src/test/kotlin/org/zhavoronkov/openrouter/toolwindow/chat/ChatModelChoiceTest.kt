package org.zhavoronkov.openrouter.toolwindow.chat

import com.google.gson.JsonParser
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.zhavoronkov.openrouter.presets.PresetEntry
import org.zhavoronkov.openrouter.presets.PresetSnapshot
import org.zhavoronkov.openrouter.proxy.pairs.PairAvailability
import org.zhavoronkov.openrouter.proxy.pairs.PairProblem

class ChatModelChoiceTest {

    private val research =
        PresetEntry("research", "Research", null, JsonParser.parseString("""{"temperature":0.2}""").asJsonObject)
    private val routed =
        PresetEntry(
            "routed",
            "Routed",
            null,
            JsonParser.parseString("""{"provider":{"only":["azure"]}}""").asJsonObject
        )
    private val searching =
        PresetEntry(
            "web",
            "Web",
            null,
            JsonParser.parseString("""{"tools":[{"type":"openrouter:web_search"}]}""").asJsonObject
        )
    private val fallbacks =
        PresetEntry("fallbacks", "Fallbacks", null, JsonParser.parseString("""{"models":["a/b"]}""").asJsonObject)
    private val snapshot = PresetSnapshot(0, listOf(research, routed, searching, fallbacks))
    private val pairs = PairAvailability(presets = { snapshot }, lookup = snapshot::find, catalogue = { null })

    @Test
    @DisplayName("a plain model is sent as it is, and judged as itself")
    fun plainModel() {
        assertEquals(ChatModelChoice("openai/gpt-4o", "openai/gpt-4o"), ChatModelChoice.of("openai/gpt-4o", pairs))
    }

    /** OpenRouter applies the preset; the controls are judged by the model the pair names. */
    @Test
    @DisplayName("a pair is sent as it is, judged by its model, and brings its preset")
    fun pair() {
        val choice = ChatModelChoice.of("openai/gpt-4o:nitro@preset/research", pairs)

        assertEquals("openai/gpt-4o:nitro@preset/research", choice.picked)
        assertEquals("openai/gpt-4o:nitro", choice.model)
        assertEquals(research, choice.preset)
        assertNull(choice.problem)
    }

    @Test
    @DisplayName("a pair whose preset is gone says so, as it does for a Consumer")
    fun missingPreset() {
        assertEquals(PairProblem.MissingPreset("gone"), ChatModelChoice.of("openai/gpt-4o@preset/gone", pairs).problem)
    }

    @Test
    @DisplayName("only a preset that sets routing replaces the routing defaults")
    fun presetRouting() {
        assertTrue(ChatModelChoice.of("m@preset/routed", pairs).presetRouting)
        assertTrue(ChatModelChoice.of("m@preset/fallbacks", pairs).presetRouting, "fallback models are routing too")
        assertFalse(ChatModelChoice.of("m@preset/research", pairs).presetRouting)
        assertFalse(ChatModelChoice.of("m", pairs).presetRouting)
    }

    @Test
    @DisplayName("only a preset with the web search tool searches whatever the switch says")
    fun presetSearches() {
        assertTrue(ChatModelChoice.of("m@preset/web", pairs).presetSearches)
        assertFalse(ChatModelChoice.of("m@preset/research", pairs).presetSearches)
        assertFalse(ChatModelChoice.of("m", pairs).presetSearches)
    }

    /** A control at the preset's value sends nothing, so the preset's own field applies whole. */
    @Test
    @DisplayName("controls still at the preset's values send nothing; a changed one is sent")
    fun controlsAtPresetValues() {
        val preset = ChatControls(
            webSearch = true,
            outputMode = OutputMode.PlainJson,
            reasoning = "High",
            verbosity = "Low"
        )
        val same = ChatRequestOptions(
            reasoning = "High",
            verbosity = "Low",
            webSearch = true,
            outputMode = OutputMode.PlainJson
        )

        val unchanged = ChatExchange.buildRequest("m@preset/p", emptyList(), same, preset = preset)
        val changed = ChatExchange.buildRequest(
            "m@preset/p",
            emptyList(),
            same.copy(reasoning = "Low"),
            preset = preset
        )

        assertNull(unchanged.reasoning)
        assertNull(unchanged.verbosity)
        assertNull(unchanged.tools)
        assertNull(unchanged.responseFormat)
        assertEquals("low", changed.reasoning?.effort)
    }

    @Test
    @DisplayName("a router pair is recognised as its router")
    fun routerPair() {
        val request = ChatExchange.buildRequest(
            "openrouter/auto@preset/p",
            emptyList(),
            ChatRequestOptions(routerParam = "medium")
        )
        val plain = ChatExchange.buildRequest(
            "openrouter/auto",
            emptyList(),
            ChatRequestOptions(routerParam = "medium")
        )

        assertNotNull(request.plugins)
        assertEquals(plain.plugins, request.plugins)
    }

    /** OpenRouter lets a request's field override the preset's. */
    @Test
    @DisplayName("the chat's fixed sampling settings stay out of what a pair's preset sets")
    fun presetFields() {
        val request = ChatExchange.buildRequest(
            "m@preset/p",
            emptyList(),
            ChatRequestOptions(),
            presetFields = setOf("temperature")
        )

        assertNull(request.temperature)
        assertEquals(4096, request.maxTokens)
    }

    @Test
    @DisplayName("a pair whose preset is known but whose config is not neither routes nor searches")
    fun presetWithoutConfig() {
        val choice = ChatModelChoice("m@preset/unread", preset = PresetEntry("unread", "Unread", null, null))

        assertFalse(choice.presetRouting)
        assertFalse(choice.presetSearches)
    }
}
