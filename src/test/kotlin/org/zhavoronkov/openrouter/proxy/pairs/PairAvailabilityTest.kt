package org.zhavoronkov.openrouter.proxy.pairs

import com.google.gson.JsonParser
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.zhavoronkov.openrouter.models.OpenRouterModelInfo
import org.zhavoronkov.openrouter.models.PresetPair
import org.zhavoronkov.openrouter.models.ResponseFormats
import org.zhavoronkov.openrouter.presets.PresetEntry
import org.zhavoronkov.openrouter.presets.PresetSnapshot

class PairAvailabilityTest {

    private fun model(id: String, vararg declared: String) =
        OpenRouterModelInfo(id = id, name = id, created = 0, supportedParameters = declared.toList())

    private fun preset(slug: String, config: String?) =
        PresetEntry(slug, slug, null, config?.let { JsonParser.parseString(it).asJsonObject })

    private var catalogue: List<OpenRouterModelInfo>? = listOf(
        model("json/only", "response_format"),
        model("schema/only", "structured_outputs")
    )
    private val schema = """{"type":"json_schema","json_schema":{"name":"answer","schema":{"type":"object"}}}"""
    private val search = """"tools":[{"type":"openrouter:web_search"}]"""
    private var snapshot: PresetSnapshot? = PresetSnapshot(
        0,
        listOf(
            preset("json", """{"response_format":{"type":"json_object"}}"""),
            preset("answer", """{"response_format":$schema}"""),
            preset("plain", """{"reasoning":{"effort":"high"}}"""),
            preset("search-json", """{$search,"response_format":{"type":"json_object"}}"""),
            preset("search-schema", """{$search,"response_format":$schema}"""),
            preset("unreadable", null)
        )
    )
    private val lookedUp = mutableListOf<String>()
    private val pairs = PairAvailability(
        presets = { snapshot },
        lookup = { slug -> lookedUp += slug; snapshot?.find(slug) },
        catalogue = { catalogue }
    )

    @Test
    @DisplayName("plain JSON and a schema are gated independently, as in the chat")
    fun independentGates() {
        assertNull(pairs.problem("json/only@preset/json"))
        assertEquals(
            PairProblem.OutputNotServable("answer", "json/only does not support schema-constrained output"),
            pairs.problem("json/only@preset/answer")
        )
        assertNull(pairs.problem("schema/only@preset/answer"))
        assertEquals(
            PairProblem.OutputNotServable("json", "schema/only does not support JSON output"),
            pairs.problem("schema/only@preset/json")
        )
    }

    @Test
    @DisplayName("a preset missing from a read copy is a problem, and asks the copy to look again")
    fun missingPreset() {
        assertEquals(PairProblem.MissingPreset("gone"), pairs.problem("json/only@preset/gone"))
        assertEquals(listOf("gone"), lookedUp)
    }

    /** A slow start must not hide or refuse a pair. */
    @Test
    @DisplayName("nothing is refused while the copy has never been read, nor for a preset it could not read")
    fun notKnown() {
        assertNull(pairs.problem("json/only@preset/unreadable"))

        snapshot = null

        assertNull(pairs.problem("json/only@preset/gone"))
    }

    @Test
    @DisplayName("a model the catalogue does not list, a Router say, gives no output but takes a preset setting none")
    fun unlistedModel() {
        assertEquals(
            PairProblem.OutputNotServable("json", "JSON output support is not known for openrouter/auto"),
            pairs.problem("openrouter/auto@preset/json")
        )
        assertNull(pairs.problem("openrouter/auto@preset/plain"))
    }

    @Test
    @DisplayName("while the catalogue has not loaded, a pair's output is not checked against the model")
    fun catalogueLoading() {
        catalogue = null

        assertNull(pairs.problem("schema/only@preset/json"))
    }

    /** Measured against OpenRouter: web search drops plain JSON every time, and a schema unless the search is native. */
    @Test
    @DisplayName(
        "web search with plain JSON cannot be sent, even while the catalogue loads; with a schema it is warned"
    )
    fun webSearch() {
        catalogue = null

        assertEquals(PairProblem.WebSearchDropsJson("search-json"), pairs.problem("json/only@preset/search-json"))
        assertNull(pairs.problem("schema/only@preset/search-schema"))
        assertEquals(ResponseFormats.WEB_SEARCH_SCHEMA_WARNING, pairs.warning("schema/only@preset/search-schema"))
        assertNull(pairs.warning("schema/only@preset/answer"))
    }

    @Test
    @DisplayName("a pair's preset config is what the proxy strips the Consumer's fields by")
    fun presetConfig() {
        assertEquals(
            setOf("reasoning"),
            pairs.presetConfig(org.zhavoronkov.openrouter.models.PresetPair("m", "plain"))?.keySet()
        )
        assertNull(pairs.presetConfig(org.zhavoronkov.openrouter.models.PresetPair("m", "unreadable")))
    }

    @Test
    @DisplayName("a model id that is not a pair has no problem")
    fun notAPair() {
        assertNull(pairs.problem("json/only"))
    }

    @Test
    @DisplayName("before the presets are read nothing is known of a pair, and nothing warned")
    fun presetsNotRead() {
        snapshot = null

        assertNull(pairs.warning("schema/only@preset/search-schema"))
        assertNull(pairs.preset(PresetPair("m", "plain")))
        assertNull(pairs.presetConfig("plain"))
        assertNull(pairs.warning("json/only"), "not a pair")
    }

    @Test
    @DisplayName("a preset whose config was not read, or that is gone, warns of nothing")
    fun noConfigToWarnOf() {
        assertNull(pairs.warning("schema/only@preset/unreadable"))
        assertNull(pairs.warning("schema/only@preset/gone"))
    }

    @Test
    @DisplayName("a snapshot answers from the presets it was taken with, and looks up a slug they lack")
    fun snapshotLooksUpTheRest() {
        val taken = pairs.snapshot()

        assertEquals("plain", taken.preset(PresetPair("m", "plain"))?.slug)
        taken.problem("json/only@preset/gone")
        assertEquals(listOf("gone"), lookedUp, "a slug the snapshot lacks is looked up")
    }
}
