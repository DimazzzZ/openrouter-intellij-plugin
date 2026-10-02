package org.zhavoronkov.openrouter.settings.presets

import com.google.gson.JsonParser
import com.google.gson.JsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.zhavoronkov.openrouter.models.OutputSchema
import org.zhavoronkov.openrouter.models.ResponseFormats
import org.zhavoronkov.openrouter.presets.PresetEntry

@DisplayName("PresetDraft")
class PresetDraftTest {

    private fun entry(config: String, prompt: String? = null) =
        PresetEntry("research", "Research", prompt, JsonParser.parseString(config).asJsonObject)

    private val schema = OutputSchema("answer", strict = true, schema = """{"type":"object"}""")

    @Test
    @DisplayName("a new preset has no settings, and adding one twice adds it once")
    fun empty() {
        val draft = PresetDraft.empty("research")

        assertEquals(emptyList<PresetSetting>(), draft.settings)
        assertEquals("{}", draft.config().toString())

        draft.add(PresetSetting.WEB_SEARCH)
        draft.add(PresetSetting.WEB_SEARCH)

        assertEquals(listOf(PresetSetting.WEB_SEARCH), draft.settings)
        assertEquals("""{"tools":[{"type":"openrouter:web_search"}]}""", draft.config().toString())
    }

    @Test
    @DisplayName("an existing preset shows what it sets and writes back what it does not know, unchanged")
    fun roundTrip() {
        val config = """{"model":"openai/gpt-5-nano","max_tokens":40,"seed":7,
            "provider":{"only":["azure"],"max_price":{"prompt":1}},
            "tools":[{"type":"function","function":{"name":"f"}},{"type":"openrouter:web_search","parameters":{"engine":"exa"}}]}"""
        val draft = PresetDraft.of(entry(config, prompt = "Be brief."))

        assertEquals(
            listOf(
                PresetSetting.MODEL,
                PresetSetting.WEB_SEARCH,
                PresetSetting.PROVIDER_ROUTING,
                PresetSetting.MAX_TOKENS,
                PresetSetting.SYSTEM_PROMPT
            ),
            draft.settings
        )
        assertEquals("Be brief.", draft.systemPrompt)
        assertEquals(JsonParser.parseString(config), draft.config())
    }

    @Test
    @DisplayName("removing web search keeps the preset's other tools")
    fun removeWebSearch() {
        val draft = PresetDraft.of(
            entry("""{"tools":[{"type":"function","function":{"name":"f"}},{"type":"openrouter:web_search"}]}""")
        )

        draft.remove(PresetSetting.WEB_SEARCH)

        assertEquals("""{"tools":[{"type":"function","function":{"name":"f"}}]}""", draft.config().toString())
    }

    @Test
    @DisplayName("a saved schema is copied in, remembered by name, and offered again once it changes")
    fun schemaCopy() {
        val draft = PresetDraft.empty("research")
        draft.add(PresetSetting.OUTPUT)

        draft.setSchema(schema)

        assertEquals("answer", draft.outputSchemaName)
        assertEquals(
            """{"type":"json_schema","json_schema":{"name":"answer","strict":true,"schema":{"type":"object"}}}""",
            draft.config().get("response_format").toString()
        )
        assertNull(draft.staleSchema(listOf(schema)))
        val changed = schema.copy(schema = """{"type":"object","required":["a"]}""")
        assertEquals(changed, draft.staleSchema(listOf(changed)))
    }

    @Test
    @DisplayName("reasoning keeps what else its block says; verbosity is stored as OpenRouter spells it")
    fun reasoningAndVerbosity() {
        val draft = PresetDraft.of(entry("""{"reasoning":{"effort":"low","max_tokens":500}}"""))
        draft.add(PresetSetting.VERBOSITY)

        draft.reasoningLabel = "High"
        draft.verbosityLabel = "XHigh"

        assertEquals("""{"effort":"high","max_tokens":500}""", draft.config().get("reasoning").toString())
        assertEquals(JsonPrimitive("xhigh"), draft.config().get("verbosity"))
        assertEquals("High", draft.reasoningLabel)
    }

    @Test
    @DisplayName("the web-search rule warns when OpenRouter will drop the output format")
    fun webSearchWarning() {
        val draft = PresetDraft.empty("research")
        draft.add(PresetSetting.WEB_SEARCH)
        assertNull(draft.webSearchWarning)

        draft.add(PresetSetting.OUTPUT)
        assertEquals(ResponseFormats.WEB_SEARCH_DROPS_JSON, draft.webSearchWarning)

        draft.setSchema(schema)
        assertEquals(ResponseFormats.WEB_SEARCH_SCHEMA_WARNING, draft.webSearchWarning)
    }

    @Test
    @DisplayName("a preset cannot be saved with a bad slug, a taken one, or an empty model")
    fun problems() {
        assertTrue(PresetDraft.empty("Bad Slug").problem(isNew = true, takenSlugs = emptyList())!!.contains("slug"))
        assertTrue(PresetDraft.empty("research").problem(true, listOf("Research"))!!.contains("already exists"))
        assertNull(PresetDraft.empty("research").problem(isNew = false, takenSlugs = listOf("research")))

        val draft = PresetDraft.empty("research").apply { add(PresetSetting.MODEL) }
        assertTrue(draft.problem(true, emptyList())!!.contains("model"))
    }

    @Test
    @DisplayName("the summary says what the preset sets")
    fun summary() {
        val draft = PresetDraft.empty("research")
        assertEquals(PresetDraft.NOTHING_SET, draft.summary)

        draft.add(PresetSetting.WEB_SEARCH)
        draft.add(PresetSetting.OUTPUT)
        draft.add(PresetSetting.REASONING)

        assertEquals("web search, JSON output, reasoning medium", draft.summary)
    }
}
