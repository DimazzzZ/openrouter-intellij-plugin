package org.zhavoronkov.openrouter.models

import com.google.gson.JsonParser
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

@DisplayName("ResponseFormats")
class ResponseFormatsTest {

    @Test
    @DisplayName("web search drops plain JSON and may drop a schema")
    fun webSearchEffect() {
        assertEquals(ResponseFormats.WebSearchEffect.DROPS_JSON, ResponseFormats.webSearchEffect(true, schema = false))
        assertEquals(
            ResponseFormats.WebSearchEffect.MAY_DROP_SCHEMA,
            ResponseFormats.webSearchEffect(true, schema = true)
        )
    }

    @Test
    @DisplayName("nothing is dropped without web search, or when no output format is asked for")
    fun noWebSearchEffect() {
        assertNull(ResponseFormats.webSearchEffect(false, schema = false))
        assertNull(ResponseFormats.webSearchEffect(false, schema = true))
        assertNull(ResponseFormats.webSearchEffect(true, schema = null))
    }

    private fun fields(json: String) = JsonParser.parseString(json).asJsonObject

    @ParameterizedTest(name = "[{index}] {0}")
    @ValueSource(
        strings = [
            """{"response_format":"json_object"}""",
            """{"response_format":{"type":7}}""",
            """{"response_format":{"type":"text"}}""",
            """{}"""
        ]
    )
    @DisplayName("a response format of another shape, or of another type, asks for neither JSON nor a schema")
    fun asksForNeither(json: String) {
        assertNull(ResponseFormats.asksForSchema(fields(json)))
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @ValueSource(
        strings = [
            """{"tools":"openrouter:web_search"}""",
            """{"tools":["openrouter:web_search",{"type":7},{"function":{}}]}""",
            """{}"""
        ]
    )
    @DisplayName("tools of another shape offer no web search")
    fun noWebSearch(json: String) {
        assertFalse(ResponseFormats.offersWebSearch(fields(json)))
    }

    @Test
    @DisplayName("a model whose declarations are not known is said to be not known, for JSON and a schema alike")
    fun declarationsNotKnown() {
        assertEquals("JSON output support is not known for m", ResponseFormats.problem("m", null, schema = false))
        assertEquals(
            "Schema-constrained output support is not known for m",
            ResponseFormats.problem("m", null, schema = true)
        )
        assertEquals(
            "m does not support schema-constrained output",
            ResponseFormats.problem("m", listOf("response_format"), schema = true)
        )
    }
}
