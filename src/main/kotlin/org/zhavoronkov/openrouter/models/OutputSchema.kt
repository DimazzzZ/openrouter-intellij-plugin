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
         * Why [name] cannot be used, or null when it can. [existing] are the names already taken,
         * not counting the schema being edited. Compared without case, since two entries in a
         * drop-down that differ only in case read as the same one.
         */
        fun nameProblem(name: String, existing: List<String>): String? {
            val trimmed = name.trim()
            if (trimmed.isEmpty()) return "A schema needs a name"
            if (!NAME_PATTERN.matches(trimmed)) return NAME_RULE
            RESERVED_NAMES.firstOrNull { sameName(it, trimmed) }?.let {
                return "'$it' is already an entry of the Output mode control"
            }
            val clash = existing.firstOrNull { sameName(it, trimmed) }
            return clash?.let { "A schema named '$it' already exists" }
        }

        /**
         * Whether two schema names name the same schema: compared trimmed and without case, since
         * two entries in a drop-down that differ only in case read as the same one. Every place
         * that matches names - uniqueness here, a selection finding its schema, two selections
         * being equal - goes through this.
         */
        fun sameName(a: String, b: String): Boolean = a.trim().equals(b.trim(), ignoreCase = true)

        /** The key [sameName] compares by, for anything that hashes names. */
        fun nameKey(name: String): String = name.trim().lowercase()

        /**
         * Entries the Output mode control lists besides the saved schemas, which a schema name
         * must not repeat. "JSON (no schema)" cannot be a name at all, so only Off is listed.
         */
        private val RESERVED_NAMES = listOf("Off")

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
         * The name is sent to whichever provider serves the request. OpenRouter documents no rule
         * for it, but OpenAI's API accepts only letters, digits, underscores and hyphens, up to 64
         * of them, and refuses the request otherwise - so a name outside that set would save here
         * and fail at request time on those endpoints.
         */
        private val NAME_PATTERN = Regex("[A-Za-z0-9_-]{1,64}")
        private const val NAME_RULE = "Use only letters, digits, _ and - (no spaces), up to 64 characters"

        /**
         * Strict mode refuses a second top-level value by throwing from peek() itself, so a failure
         * there is the same finding as a token there: something follows the schema.
         */
        private fun hasTrailingContent(reader: JsonReader): Boolean = try {
            reader.peek() != JsonToken.END_DOCUMENT
        } catch (e: IOException) {
            true
        }
    }
}
