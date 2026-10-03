package org.zhavoronkov.openrouter.proxy.checks

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.zhavoronkov.openrouter.models.DataRegion
import org.zhavoronkov.openrouter.models.FixPage
import org.zhavoronkov.openrouter.models.OpenRouterModelInfo
import org.zhavoronkov.openrouter.models.OutputSchema

class ConsumerRequestChecksTest {

    private fun model(id: String, vararg declared: String) =
        OpenRouterModelInfo(id = id, name = id, created = 0, supportedParameters = declared.toList())

    private val catalogue = listOf(
        model("json/only", "response_format"),
        model("schema/only", "structured_outputs"),
        OpenRouterModelInfo(id = "silent/model", name = "", created = 0)
    )
    private val schemas = listOf(
        OutputSchema("answer", schema = """{"type":"object"}"""),
        OutputSchema("broken", schema = "[1]")
    )

    private fun body(json: String): JsonObject = JsonParser.parseString(json).asJsonObject

    private fun check(
        body: String,
        model: String,
        catalogue: List<OpenRouterModelInfo>? = this.catalogue,
        region: DataRegion = DataRegion.GLOBAL
    ) = ConsumerRequestChecks.problem(body(body), model, catalogue, { region }, { schemas })

    private val jsonObject = """{"response_format":{"type":"json_object"}}"""
    private val inlineSchema = """{"response_format":{"type":"json_schema","json_schema":{"name":"x","schema":{}}}}"""

    @Test
    @DisplayName("a request with nothing wrong passes")
    fun nothingWrong() {
        assertNull(check("{}", "json/only"))
        assertNull(check(jsonObject, "json/only"))
        assertNull(check(inlineSchema, "schema/only"))
    }

    @Test
    @DisplayName("a response format the model does not declare is refused with the chat's reason")
    fun unsupportedFormat() {
        val json = check(jsonObject, "schema/only")!!
        assertEquals("response_format_not_supported", json.code)
        assertEquals("schema/only does not support JSON output", json.problem)
        assertEquals(FixPage.FAVORITE_MODELS, json.fixAt)

        assertEquals("response_format_not_supported", check(inlineSchema, "json/only")?.code)
    }

    @Test
    @DisplayName("a json_schema naming no saved schema, with none of its own, is refused, catalogue or not")
    fun missingSavedSchema() {
        val named = """{"response_format":{"type":"json_schema","json_schema":{"name":"gone"}}}"""

        val error = check(named, "schema/only", catalogue = null)!!

        assertEquals("output_schema_not_found", error.code)
        assertEquals(FixPage.OUTPUT_SCHEMAS, error.fixAt)
        val saved = """{"response_format":{"type":"json_schema","json_schema":{"name":"ANSWER"}}}"""
        assertNull(check(saved, "schema/only"))
    }

    @Test
    @DisplayName("a json_schema naming a saved schema whose body is broken is refused rather than sent without one")
    fun brokenSavedSchema() {
        val named = """{"response_format":{"type":"json_schema","json_schema":{"name":"broken"}}}"""

        assertEquals("output_schema_invalid", check(named, "schema/only")?.code)
    }

    @Test
    @DisplayName("a model id typed in another case is the same model")
    fun caseInsensitive() {
        assertNull(check("{}", "JSON/Only"))
    }

    @Test
    @DisplayName("a model the catalogue does not list is refused, as out of the catalogue or out of the region")
    fun unlistedModel() {
        assertEquals("model_not_found", check("{}", "gone/model")?.code)

        val regional = check("{}", "gone/model", region = DataRegion.EUROPE)!!
        assertEquals("model_not_in_region", regional.code)
        assertEquals(FixPage.DATA_REGION, regional.fixAt)
        assertEquals("'gone/model' is not served in the European Union region", regional.problem)
    }

    @Test
    @DisplayName("a routing variant is served wherever its base model is")
    fun variant() {
        assertNull(check(jsonObject, "json/only:nitro"))
    }

    /** A slow start must not refuse requests. */
    @Test
    @DisplayName("nothing about the model is checked while the catalogue has not loaded")
    fun catalogueNotLoaded() {
        assertNull(check(jsonObject, "gone/model", catalogue = null))
    }

    @Test
    @DisplayName("a Router or a preset is never refused for what it declares")
    fun resolvedAtRequestTime() {
        listOf("openrouter/auto", "openrouter/bodybuilder", "@preset/email").forEach {
            assertNull(check(jsonObject, it), it)
        }
    }

    @Test
    @DisplayName("a Latest Model is judged by its own catalogue entry, as the chat judges it")
    fun latestModel() {
        val latest = catalogue + model("~schema/only-latest", "structured_outputs")

        assertNull(check(inlineSchema, "~schema/only-latest", latest))
        assertEquals("response_format_not_supported", check(jsonObject, "~schema/only-latest", latest)?.code)
        assertEquals("model_not_found", check(jsonObject, "~gone/model-latest", latest)?.code)
    }

    @Test
    @DisplayName("a model listed without its declarations is not refused for a response format")
    fun declarationsUnknown() {
        assertNull(check(jsonObject, "silent/model"))
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @ValueSource(
        strings = [
            """{"response_format":{}}""",
            """{"response_format":{"type":7}}""",
            """{"response_format":{"type":"json_schema"}}""",
            """{"response_format":{"type":"json_schema","json_schema":"answer"}}""",
            """{"response_format":{"type":"json_schema","json_schema":{}}}""",
            """{"response_format":{"type":"json_schema","json_schema":{"name":7}}}""",
            """{"response_format":{"type":"json_schema","json_schema":{"name":null}}}"""
        ]
    )
    @DisplayName("a json_schema format that names no schema is not looked up among the saved ones")
    fun formatNamingNoSchema(json: String) {
        assertNull(check(json, "schema/only", catalogue = null))
    }
}
