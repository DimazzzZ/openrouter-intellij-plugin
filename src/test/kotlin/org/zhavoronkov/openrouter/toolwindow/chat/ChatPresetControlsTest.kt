package org.zhavoronkov.openrouter.toolwindow.chat

import com.google.gson.JsonParser
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.zhavoronkov.openrouter.models.OutputSchema
import org.zhavoronkov.openrouter.presets.PresetEntry
import org.zhavoronkov.openrouter.settings.presets.PresetSetting

class ChatPresetControlsTest {

    private val answer = OutputSchema("answer", schema = """{"type":"object"}""")

    private fun preset(config: String) = PresetEntry("p", "P", null, JsonParser.parseString(config).asJsonObject)

    @Test
    @DisplayName("a preset's web search, output, reasoning and verbosity become the controls")
    fun fromPreset() {
        val controls = ChatPresetControls.of(
            preset(
                """{"tools":[{"type":"openrouter:web_search"}],"response_format":{"type":"json_object"},
                    "reasoning":{"effort":"high"},"verbosity":"low"}"""
            ),
            listOf(answer)
        )

        assertEquals(
            ChatControls(webSearch = true, outputMode = OutputMode.PlainJson, reasoning = "High", verbosity = "Low"),
            controls
        )
    }

    @Test
    @DisplayName("a preset that sets nothing leaves every control at its default")
    fun nothingSet() {
        assertEquals(ChatControls(), ChatPresetControls.of(preset("{}"), listOf(answer)))
    }

    @Test
    @DisplayName("a schema the plugin has saved is named; one it has not shows Off, and the preset's own applies")
    fun schemas() {
        val saved = preset("""{"response_format":{"type":"json_schema","json_schema":{"name":"ANSWER","schema":{}}}}""")
        val unsaved =
            preset("""{"response_format":{"type":"json_schema","json_schema":{"name":"other","schema":{}}}}""")

        assertEquals(OutputMode.Schema("answer"), ChatPresetControls.of(saved, listOf(answer)).outputMode)
        assertEquals(OutputMode.Off, ChatPresetControls.of(unsaved, listOf(answer)).outputMode)
    }

    @Test
    @DisplayName("the controls become a model-less preset that sets only what they changed")
    fun draft() {
        val options =
            ChatRequestOptions(
                reasoning = "Low",
                verbosity = ChatExchange.UNCHANGED,
                webSearch = true,
                outputMode = OutputMode.Schema("answer")
            )

        val draft = ChatPresetControls.draftOf(options, listOf(answer))

        assertEquals(listOf(PresetSetting.WEB_SEARCH, PresetSetting.OUTPUT, PresetSetting.REASONING), draft.settings)
        assertEquals("answer", draft.outputSchemaName)
        assertFalse(draft.has(PresetSetting.MODEL))
        assertTrue(ChatPresetControls.draftOf(ChatRequestOptions(), emptyList()).settings.isEmpty())
    }

    @Test
    @DisplayName("a preset edited since it was applied counts as changed")
    fun changed() {
        val before = preset("""{"temperature":0.2}""")

        assertFalse(ChatPresetControls.changed(before, before.copy()))
        assertTrue(ChatPresetControls.changed(before, preset("""{"temperature":0.3}""")))
        assertTrue(ChatPresetControls.changed(before, null))
        assertFalse(ChatPresetControls.changed(null, null))
    }
}
