package org.zhavoronkov.openrouter.proxy.defaults

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.zhavoronkov.openrouter.models.OutputSchema

@DisplayName("SavedSchemaInjector")
class SavedSchemaInjectorTest {

    private val features = OutputSchema(
        name = "features",
        strict = false,
        schema = """{"type":"object","properties":{"name":{"type":"string"}}}"""
    )

    private fun request(json: String): JsonObject = JsonParser.parseString(json).asJsonObject

    @Test
    @DisplayName("a Consumer naming a saved schema without a body gets that schema")
    fun `a Consumer naming a saved schema without a body gets that schema`() {
        val body = request("""{"response_format":{"type":"json_schema","json_schema":{"name":"features"}}}""")

        assertTrue(SavedSchemaInjector.inject(body, listOf(features)))

        assertEquals(
            JsonParser.parseString(
                """{"type":"json_schema","json_schema":{"name":"features","strict":false,
                   "schema":{"type":"object","properties":{"name":{"type":"string"}}}}}"""
            ),
            body["response_format"]
        )
    }

    @Test
    @DisplayName("a saved schema is found by name whatever the case")
    fun `a saved schema is found by name whatever the case`() {
        val body = request("""{"response_format":{"type":"json_schema","json_schema":{"name":"Features"}}}""")

        assertTrue(SavedSchemaInjector.inject(body, listOf(features)))
    }

    @Test
    @DisplayName("a strict flag the Consumer sent is kept")
    fun `a strict flag the Consumer sent is kept`() {
        val body = request(
            """{"response_format":{"type":"json_schema","json_schema":{"name":"features","strict":true}}}"""
        )

        SavedSchemaInjector.inject(body, listOf(features))

        assertTrue(body.getAsJsonObject("response_format").getAsJsonObject("json_schema")["strict"].asBoolean)
    }

    /** A Consumer that sent its own schema meant that schema, whatever it is called. */
    @Test
    @DisplayName("a schema body the Consumer sent is never replaced")
    fun `a schema body the Consumer sent is never replaced`() {
        val json = """{"response_format":{"type":"json_schema",
            "json_schema":{"name":"features","schema":{"type":"object"}}}}"""
        val body = request(json)

        assertFalse(SavedSchemaInjector.inject(body, listOf(features)))
        assertEquals(request(json), body)
    }

    @Test
    @DisplayName("a name that matches no saved schema is left for OpenRouter to judge")
    fun `a name that matches no saved schema is left alone`() {
        val json = """{"response_format":{"type":"json_schema","json_schema":{"name":"other"}}}"""
        val body = request(json)

        assertFalse(SavedSchemaInjector.inject(body, listOf(features)))
        assertEquals(request(json), body)
    }

    @Test
    @DisplayName("requests without a schema response format are left alone")
    fun `requests without a schema response format are left alone`() {
        listOf(
            """{"model":"m"}""",
            """{"response_format":{"type":"json_object"}}""",
            """{"response_format":"json_schema"}""",
            """{"response_format":{"type":"json_schema"}}"""
        ).forEach { json ->
            val body = request(json)
            assertFalse(SavedSchemaInjector.inject(body, listOf(features)), json)
            assertEquals(request(json), body, json)
        }
    }

    @Test
    @DisplayName("a saved schema whose body is not a JSON object is not injected")
    fun `a saved schema whose body is not a JSON object is not injected`() {
        val broken = OutputSchema(name = "features", schema = "{ not json")
        val body = request("""{"response_format":{"type":"json_schema","json_schema":{"name":"features"}}}""")

        assertFalse(SavedSchemaInjector.inject(body, listOf(broken)))
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @ValueSource(
        strings = [
            """{"response_format":{"type":7,"json_schema":{"name":"features"}}}""",
            """{"response_format":{"type":"json_schema","json_schema":"features"}}""",
            """{"response_format":{"type":"json_schema","json_schema":{}}}""",
            """{"response_format":{"type":"json_schema","json_schema":{"name":7}}}""",
            """{"response_format":{"type":"json_schema","json_schema":{"name":["features"]}}}"""
        ]
    )
    @DisplayName("a response format that does not name a schema as a string is left alone")
    fun `a format not naming a schema is left alone`(json: String) {
        val body = request(json)

        assertFalse(SavedSchemaInjector.inject(body, listOf(features)))
        assertEquals(request(json), body)
    }
}
