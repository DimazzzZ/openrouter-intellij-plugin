package org.zhavoronkov.openrouter.models

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParseException
import com.google.gson.JsonParser
import com.google.gson.Strictness
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import java.io.IOException
import java.io.StringReader

/**
 * An Output Schema: a named JSON Schema the user saved, to ask for a reply in exactly that shape.
 *
 * It is the inner object of OpenRouter's `json_schema` response format as it is: [name] is both
 * what the Output mode control lists and the `name` OpenRouter receives, so there is one name
 * rather than two that can disagree; [strict] is whether the model must follow the schema exactly;
 * [schema] is the schema itself, kept as the text the user wrote so that saving and reopening it
 * changes nothing about its layout.
 *
 * The fields are `var` with defaults because the IDE's settings serializer needs a no-argument
 * constructor and writable properties; nothing else mutates them.
 */
data class OutputSchema(
    var name: String = "",
    var strict: Boolean = true,
    var schema: String = ""
) {

    /**
     * [schema] as the JSON object OpenRouter receives, or null when it is not one. Parsed with the
     * same strict rules [validateBody] applies, so what was accepted on the page is what is sent.
     */
    fun parsedBody(): JsonObject? =
        // Unreachable branch: Parsed.Element.element is non-null, so ?.takeIf sees null only when parsing failed
        (parse(schema) as? Parsed.Element)?.element?.takeIf { it.isJsonObject }?.asJsonObject

    /** What checking a schema body found. */
    sealed interface BodyCheck {
        val message: String

        /**
         * A JSON object, declaring [fields] entries under `properties`. Only the JSON is checked,
         * not the schema's meaning, so the message says as much and no more.
         */
        data class Valid(val fields: Int) : BodyCheck {
            override val message: String
                get() = "Valid JSON object, $fields ${if (fields == 1) "field" else "fields"}"
        }

        data class Invalid(override val message: String) : BodyCheck
    }

    companion object {

        /**
         * Checks a schema body as the user types it.
         *
         * Parsed strictly: the lenient mode Gson defaults to accepts unquoted keys, single quotes
         * and trailing content, none of which a provider receiving the schema is obliged to, so a
         * body that only lenient parsing accepts would fail at request time instead of here.
         */
        fun validateBody(body: String): BodyCheck {
            if (body.isBlank()) return BodyCheck.Invalid("The schema is empty")
            val parsed = when (val result = parse(body)) {
                is Parsed.Failure -> return BodyCheck.Invalid("Not valid JSON: ${result.problem}")
                is Parsed.Element -> result.element
            }
            if (!parsed.isJsonObject) return BodyCheck.Invalid("A schema must be a JSON object")
            val properties = parsed.asJsonObject.get("properties")
            val fields = if (properties != null && properties.isJsonObject) properties.asJsonObject.size() else 0
            return BodyCheck.Valid(fields)
        }

        /**
         * Why [name] cannot be used, or null when it can, by the rule every saved name follows
         * ([EntryNames]). [existing] are the names already taken, not counting the schema being
         * edited. "Off" is the Output mode control's own entry, so no schema may repeat it; "JSON
         * (no schema)" cannot be a name at all.
         */
        fun nameProblem(name: String, existing: List<String>): String? =
            EntryNames.problem(name, existing, "A schema", RESERVED_NAMES)

        private val RESERVED_NAMES = mapOf("Off" to "'Off' is already an entry of the Output mode control")

        private sealed interface Parsed {
            data class Element(val element: JsonElement) : Parsed
            data class Failure(val problem: String) : Parsed
        }

        private fun parse(body: String): Parsed {
            val reader = JsonReader(StringReader(body)).apply { strictness = Strictness.STRICT }
            val element = try {
                JsonParser.parseReader(reader)
            } catch (e: JsonParseException) {
                return Parsed.Failure(describe(e.message))
            }
            return if (hasTrailingContent(reader)) Parsed.Failure(TRAILING_CONTENT) else Parsed.Element(element)
        }

        /**
         * Gson's parse errors are written for the developer calling it - they name its own API,
         * give a JSON path and link to its troubleshooting page. The person editing a schema needs
         * what went wrong and where, in one line.
         */
        private fun describe(raw: String?): String {
            // Unreachable branch: every JsonParseException parseReader throws has a message, its own or its cause's
            // toString()
            val first = raw.orEmpty().lineSequence().first().substringAfterLast("Exception: ")
            val position = POSITION.find(first)?.let { "at line ${it.groupValues[1]}, column ${it.groupValues[2]}" }
            val problem = when {
                first.startsWith("End of input") -> "the schema ends too early"
                first.startsWith("Expected value") -> "a value is missing"
                else -> "unexpected character"
            }
            return listOfNotNull(problem, position).joinToString(" ")
        }

        private val POSITION = Regex("""at line (\d+) column (\d+)""")
        private const val TRAILING_CONTENT = "there is more after the schema ends"

        /**
         * Strict mode refuses a second top-level value by throwing from peek() itself, so a failure
         * there is the same finding as a token there: something follows the schema.
         */
        private fun hasTrailingContent(reader: JsonReader): Boolean = try {
            // Unreachable branch: in strict mode peek() after a whole value returns END_DOCUMENT or throws, never
            // another token
            reader.peek() != JsonToken.END_DOCUMENT
        } catch (e: IOException) {
            true
        }
    }
}
