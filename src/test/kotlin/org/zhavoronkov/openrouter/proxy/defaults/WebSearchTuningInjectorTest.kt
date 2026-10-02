package org.zhavoronkov.openrouter.proxy.defaults

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.zhavoronkov.openrouter.models.WebSearchEngine
import org.zhavoronkov.openrouter.models.WebSearchSettings

@DisplayName("WebSearchTuningInjector")
class WebSearchTuningInjectorTest {

    private val tuned = WebSearchSettings(
        engine = WebSearchEngine.EXA,
        maxResults = 8,
        includeDomains = listOf("docs.gradle.org"),
        mode = "deep"
    )

    private fun request(json: String): JsonObject = JsonParser.parseString(json).asJsonObject

    // --- The web search server tool -------------------------------------------

    @Test
    @DisplayName("a Consumer's bare web search tool gets the saved tuning as its parameters")
    fun `a Consumer's bare web search tool gets the saved tuning`() {
        val body = request("""{"model":"m","messages":[],"tools":[{"type":"openrouter:web_search"}]}""")

        assertTrue(WebSearchTuningInjector.inject(body, tuned))

        assertEquals(
            JsonParser.parseString(
                """[{"type":"openrouter:web_search","parameters":
                    {"engine":"exa","max_results":8,"allowed_domains":["docs.gradle.org"],"mode":"deep"}}]"""
            ),
            body["tools"]
        )
    }

    /** What the Consumer sent is its decision; the saved tuning only fills what it left out. */
    @Test
    @DisplayName("parameters the Consumer set on the tool are kept as it sent them")
    fun `parameters the Consumer set on the tool are kept`() {
        val body = request(
            """{"tools":[{"type":"openrouter:web_search","parameters":{"engine":"native","max_results":2}}]}"""
        )

        WebSearchTuningInjector.inject(body, tuned)

        val parameters = body.getAsJsonArray("tools")[0].asJsonObject.getAsJsonObject("parameters")
        assertEquals("native", parameters["engine"].asString)
        assertEquals(2, parameters["max_results"].asInt)
        assertEquals("docs.gradle.org", parameters.getAsJsonArray("allowed_domains")[0].asString)
        assertFalse(parameters.has("mode"), "the saved mode belongs to the saved engine, not to the one sent")
    }

    @Test
    @DisplayName("the Consumer's own function tools are left untouched")
    fun `the Consumer's own function tools are left untouched`() {
        val json = """{"tools":[{"type":"function","function":{"name":"read_file"}}]}"""
        val body = request(json)

        assertFalse(WebSearchTuningInjector.inject(body, tuned))
        assertEquals(request(json), body)
    }

    // --- The deprecated web plugin, which Consumers may still send ------------

    @Test
    @DisplayName("a Consumer's deprecated web plugin entry is tuned in the plugin's own spelling")
    fun `a Consumer's deprecated web plugin entry is tuned in its own spelling`() {
        val body = request("""{"plugins":[{"id":"web"}]}""")

        assertTrue(WebSearchTuningInjector.inject(body, tuned))

        assertEquals(
            JsonParser.parseString(
                """[{"id":"web","engine":"exa","max_results":8,"include_domains":["docs.gradle.org"],"mode":"deep"}]"""
            ),
            body["plugins"]
        )
    }

    // --- Nothing the Consumer did not ask for ----------------------------------

    /**
     * A search is charged, so the proxy never starts one: without a web search from the Consumer
     * nothing is added.
     */
    @Test
    @DisplayName("a request without a web search is left alone")
    fun `a request without a web search is left alone`() {
        val json = """{"model":"m","messages":[],"plugins":[{"id":"auto-router","cost_tier":"low"}]}"""
        val body = request(json)

        assertFalse(WebSearchTuningInjector.inject(body, tuned))
        assertEquals(request(json), body)
        assertFalse(WebSearchTuningInjector.inject(request("""{"model":"m"}"""), tuned))
    }

    @Test
    @DisplayName("an untouched Web Search page changes nothing")
    fun `an untouched Web Search page changes nothing`() {
        val json = """{"tools":[{"type":"openrouter:web_search"}],"plugins":[{"id":"web"}]}"""
        val body = request(json)

        assertFalse(WebSearchTuningInjector.inject(body, WebSearchSettings()))
        assertEquals(request(json), body)
    }
}
