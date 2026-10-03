package org.zhavoronkov.openrouter.models

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

@DisplayName("OutputSchema")
class OutputSchemaTest {

    private fun invalidMessage(body: String): String {
        val result = OutputSchema.validateBody(body)
        assertTrue(result is OutputSchema.BodyCheck.Invalid, "expected '$body' to be rejected, got $result")
        return (result as OutputSchema.BodyCheck.Invalid).message
    }

    @Test
    @DisplayName("a valid schema is confirmed, reporting how many fields it declares")
    fun `a valid schema is confirmed with its field count`() {
        val body = """
            {"type": "object", "properties": {"name": {"type": "string"}, "age": {"type": "integer"}}}
        """

        assertEquals(OutputSchema.BodyCheck.Valid(fields = 2), OutputSchema.validateBody(body))
        assertEquals("Valid JSON object, 2 fields", OutputSchema.BodyCheck.Valid(2).message)
        assertEquals("Valid JSON object, 1 field", OutputSchema.BodyCheck.Valid(1).message)
    }

    @Test
    @DisplayName("an object without properties is valid and declares no fields")
    fun `an object without properties declares no fields`() {
        assertEquals(OutputSchema.BodyCheck.Valid(fields = 0), OutputSchema.validateBody("""{"type": "object"}"""))
    }

    /** The last one names a key in the words Gson's own error uses, which hides where it went wrong. */
    @ParameterizedTest
    @ValueSource(
        strings = [
            "{", """{"type": }""", """{type: "object"}""", """{'type': 'object'}""", """{} {}""",
            """{"Exception: x": 1 2}"""
        ]
    )
    @DisplayName("malformed JSON is rejected with a message")
    fun `malformed JSON is rejected with a message`(body: String) {
        val message = invalidMessage(body)

        assertTrue(message.startsWith("Not valid JSON: "), message)
        assertTrue(message.length > "Not valid JSON: ".length, "the message must say what is wrong: '$message'")
        assertTrue(
            !message.contains('\n') && !message.contains("http"),
            "one readable line, not a stack of hints: '$message'"
        )
    }

    @Test
    @DisplayName("a parse error says what is wrong and where")
    fun `a parse error says what is wrong and where`() {
        assertEquals("Not valid JSON: unexpected character at line 1, column 3", invalidMessage("""{type: "object"}"""))
        assertEquals("Not valid JSON: there is more after the schema ends", invalidMessage("{} {}"))
    }

    @ParameterizedTest
    @ValueSource(strings = ["[]", "\"object\"", "42", "true", "null"])
    @DisplayName("JSON that parses but is not an object is rejected")
    fun `JSON that is not an object is rejected`(body: String) {
        assertEquals("A schema must be a JSON object", invalidMessage(body))
    }

    @ParameterizedTest
    @ValueSource(strings = ["", "   ", "\n"])
    @DisplayName("an empty body is rejected")
    fun `an empty body is rejected`(body: String) {
        assertEquals("The schema is empty", invalidMessage(body))
    }

    @Test
    @DisplayName("a name must not be blank")
    fun `a name must not be blank`() {
        assertEquals("A schema needs a name", OutputSchema.nameProblem("  ", emptyList()))
    }

    @ParameterizedTest
    @ValueSource(strings = ["Off", "off", " OFF "])
    @DisplayName("a schema cannot take the name of the Output mode control's own entry")
    fun `a schema cannot take the name of the Output mode control's own entry`(name: String) {
        assertEquals(
            "'Off' is already an entry of the Output mode control",
            OutputSchema.nameProblem(name, emptyList())
        )
    }

    @Test
    @DisplayName("two schemas cannot share a name, whatever the case")
    fun `two schemas cannot share a name`() {
        assertEquals(
            "A schema named 'Features' already exists",
            OutputSchema.nameProblem("features", listOf("Features", "Other"))
        )
        assertNull(OutputSchema.nameProblem("new-one", listOf("Features")))
    }

    @ParameterizedTest
    @ValueSource(
        strings = [
            "with space", "quote\"", "a<b", "tab\there", "ümlaut",
            "xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx",
        ]
    )
    @DisplayName("a name a provider would refuse is rejected before it is saved")
    fun `a name a provider would refuse is rejected`(name: String) {
        assertEquals(
            "Use only letters, digits, _ and - (no spaces), up to 64 characters",
            OutputSchema.nameProblem(name, emptyList())
        )
    }

    @ParameterizedTest
    @ValueSource(strings = ["features", "Feature_List-2", "x"])
    @DisplayName("a name of letters, digits, underscores and hyphens is accepted")
    fun `a name of letters digits underscores and hyphens is accepted`(name: String) {
        assertNull(OutputSchema.nameProblem(name, emptyList()))
    }

    @ParameterizedTest
    @ValueSource(strings = ["""{"properties": 5}""", """{"properties": ["a"]}""", """{"properties": null}"""])
    @DisplayName("properties that are not an object declare no fields")
    fun `properties that are not an object declare no fields`(body: String) {
        assertEquals(OutputSchema.BodyCheck.Valid(fields = 0), OutputSchema.validateBody(body))
    }

    @Test
    @DisplayName("the body sent is the schema as a JSON object")
    fun `the body sent is the schema as a JSON object`() {
        val body = OutputSchema(name = "s", schema = """{"type": "object"}""").parsedBody()

        assertNotNull(body)
        assertEquals("object", body?.get("type")?.asString)
    }

    @ParameterizedTest
    @ValueSource(strings = ["{", """{type: "object"}""", "{} {}", "[]", "42"])
    @DisplayName("a schema that is not a JSON object, or not JSON at all, has no body to send")
    fun `a schema that is not a JSON object has no body to send`(schema: String) {
        assertNull(OutputSchema(name = "s", schema = schema).parsedBody())
    }
}
