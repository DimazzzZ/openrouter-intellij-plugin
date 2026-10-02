package org.zhavoronkov.openrouter.settings.presets

import com.google.gson.JsonParser
import com.google.gson.JsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.EnumSource
import org.junit.jupiter.params.provider.ValueSource
import org.zhavoronkov.openrouter.models.OutputSchema
import org.zhavoronkov.openrouter.presets.PresetEntry

/**
 * [PresetDraft] on configs OpenRouter may hold but the dialog never writes: fields of another JSON
 * type, nulls, and settings started from nothing. `PresetDraftTest` covers the usual round trips.
 */
@DisplayName("PresetDraft on configs of another shape")
class PresetDraftShapesTest {

    private fun draftOf(config: String?, prompt: String? = null): PresetDraft {
        val parsed = config?.let { JsonParser.parseString(it).asJsonObject }
        return PresetDraft.of(PresetEntry("research", "Research", prompt, parsed))
    }

    private val schema = OutputSchema("answer", strict = true, schema = """{"type":"object"}""")

    @ParameterizedTest
    @EnumSource(PresetSetting::class)
    @DisplayName("every setting added to an empty preset starts from a value OpenRouter accepts")
    fun startingValues(setting: PresetSetting) {
        val draft = PresetDraft.empty("research")

        draft.add(setting)

        val expected = mapOf(
            PresetSetting.MODEL to "\"\"",
            PresetSetting.WEB_SEARCH to """{"type":"openrouter:web_search"}""",
            PresetSetting.OUTPUT to """{"type":"json_object"}""",
            PresetSetting.REASONING to """{"effort":"medium"}""",
            PresetSetting.VERBOSITY to "\"medium\"",
            PresetSetting.PROVIDER_ROUTING to "{}",
            PresetSetting.TEMPERATURE to "0.7",
            PresetSetting.TOP_P to "1",
            PresetSetting.MAX_TOKENS to "4096",
            PresetSetting.SYSTEM_PROMPT to "\"\""
        )
        assertEquals(listOf(setting), draft.settings)
        assertEquals(expected.getValue(setting), draft[setting].toString())
    }

    @Test
    @DisplayName("a system prompt is not a config field: it is kept apart from the config")
    fun systemPromptStaysOutOfConfig() {
        val draft = PresetDraft.empty("research").apply { add(PresetSetting.SYSTEM_PROMPT) }

        assertEquals("", draft.systemPrompt)
        assertEquals("{}", draft.config().toString())
    }

    @Test
    @DisplayName("a preset with no config, a null field or an empty prompt sets none of them")
    fun nothingSet() {
        assertEquals(emptyList<PresetSetting>(), draftOf(null).settings)
        assertEquals(emptyList<PresetSetting>(), draftOf("""{"model":null}""", prompt = "").settings)
        assertNull(draftOf(null).systemPrompt)
    }

    @Test
    @DisplayName("tools that are not a list hide no web search, and are written back as they were")
    fun toolsNotAList() {
        val draft = draftOf("""{"tools":"web"}""")

        assertFalse(draft.has(PresetSetting.WEB_SEARCH))
        assertEquals("{}", draft.config().toString())
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @ValueSource(
        strings = [
            """{"response_format":"json_schema"}""",
            """{"response_format":{"type":"json_schema","json_schema":"answer"}}""",
            """{"response_format":{"type":"json_schema","json_schema":{"name":7}}}""",
            """{"response_format":{"type":7}}"""
        ]
    )
    @DisplayName("an output of another shape names no schema and is not plain JSON")
    fun misshapenOutput(config: String) {
        val draft = draftOf(config)

        assertNull(draft.outputSchemaName)
        assertFalse(draft.plainJson)
        assertNull(draft.staleSchema(listOf(schema)))
        assertEquals("output format", draft.summary)
    }

    @Test
    @DisplayName("a schema whose body does not parse is not copied in")
    fun unparseableSchema() {
        val draft = PresetDraft.empty("research").apply { add(PresetSetting.OUTPUT) }

        draft.setSchema(schema.copy(schema = "{not json"))

        assertTrue(draft.plainJson, "the output stays what it was")
    }

    @Test
    @DisplayName("a copied schema is stale when its saved original changed strictness or was not marked")
    fun staleness() {
        val copied = """{"response_format":{"type":"json_schema",
            "json_schema":{"name":"answer","strict":true,"schema":{"type":"object"}}}}"""
        val unmarked = """{"response_format":{"type":"json_schema",
            "json_schema":{"name":"answer","schema":{"type":"object"}}}}"""

        assertNull(draftOf(copied).staleSchema(listOf(schema)))
        assertNull(draftOf(copied).staleSchema(listOf(schema.copy(name = "other"))), "not saved")
        assertNull(draftOf("{}").staleSchema(listOf(schema)), "no output")
        val loosened = schema.copy(strict = false)
        assertEquals(loosened, draftOf(copied).staleSchema(listOf(loosened)))
        assertEquals(schema, draftOf(unmarked).staleSchema(listOf(schema)))
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @ValueSource(
        strings = ["""{"reasoning":"high"}""", """{"reasoning":{"effort":"extreme"}}""", """{"reasoning":{"effort":3}}"""]
    )
    @DisplayName("a reasoning block of another shape has no label, and says only that reasoning is set")
    fun unlabelledReasoning(config: String) {
        val draft = draftOf(config)

        assertNull(draft.reasoningLabel)
        assertEquals("reasoning set", draft.summary)
    }

    @Test
    @DisplayName("a reasoning label replaces a block that is not an object, and an unlisted one changes nothing")
    fun settingReasoning() {
        val draft = draftOf("""{"reasoning":"high"}""")

        draft.reasoningLabel = "Unlisted"
        assertEquals(JsonPrimitive("high"), draft[PresetSetting.REASONING])

        draft.reasoningLabel = "Low"
        assertEquals("""{"effort":"low"}""", draft[PresetSetting.REASONING].toString())
    }

    @Test
    @DisplayName("a verbosity of another shape has no label, and an unlisted label changes nothing")
    fun verbosity() {
        val draft = draftOf("""{"verbosity":3}""")

        assertNull(draft.verbosityLabel)
        draft.verbosityLabel = "Unlisted"
        assertEquals(JsonPrimitive(3), draft[PresetSetting.VERBOSITY])
    }

    @Test
    @DisplayName("an output with no web search loses nothing")
    fun outputWithoutSearch() {
        val draft = PresetDraft.empty("research").apply { add(PresetSetting.OUTPUT) }

        assertNull(draft.webSearchWarning)
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @CsvSource(
        delimiter = '|',
        value = [
            """{"model":"   "}    | Enter a model""",
            """{"model":7}        | Enter a model""",
            """{"model":"a/b"}    | """
        ]
    )
    @DisplayName("a model that is blank or not a string cannot be saved")
    fun modelProblems(config: String, problem: String?) {
        val found = draftOf(config).problem(isNew = false, takenSlugs = emptyList())

        if (problem == null) assertNull(found) else assertTrue(found.orEmpty().startsWith(problem), "got: $found")
    }

    @Test
    @DisplayName("a preset without an output names no schema, is not plain JSON, and has nothing stale")
    fun noOutput() {
        val draft = draftOf("""{"temperature":0.2}""")

        assertNull(draft.outputSchemaName)
        assertFalse(draft.plainJson)
        assertNull(draft.staleSchema(listOf(schema)))
    }

    @Test
    @DisplayName("a json_schema output whose schema has no name names nothing, and nothing is stale")
    fun schemaWithoutName() {
        val draft = draftOf("""{"response_format":{"type":"json_schema","json_schema":{"schema":{}}}}""")

        assertNull(draft.outputSchemaName)
        assertNull(draft.staleSchema(listOf(schema)))
    }

    @Test
    @DisplayName("a preset without reasoning has no label, and setting one starts a reasoning block")
    fun reasoningFromNothing() {
        val draft = draftOf("{}")

        assertNull(draft.reasoningLabel)
        draft.reasoningLabel = "High"

        assertEquals("""{"effort":"high"}""", draft[PresetSetting.REASONING].toString())
        assertEquals("High", draft.reasoningLabel)
    }

    @Test
    @DisplayName("web search with no output asked for loses nothing")
    fun searchWithoutOutput() {
        val draft = PresetDraft.empty("research").apply { add(PresetSetting.WEB_SEARCH) }

        assertNull(draft.webSearchWarning)
    }
}
