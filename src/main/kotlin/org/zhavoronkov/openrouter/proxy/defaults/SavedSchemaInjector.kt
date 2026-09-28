package org.zhavoronkov.openrouter.proxy.defaults

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import org.zhavoronkov.openrouter.models.OutputSchema

/**
 * Lets a Consumer ask for a saved Output Schema by name: a `json_schema` response format that names
 * a saved schema and carries no `schema` of its own gets that schema's body, and its strict flag
 * when the Consumer did not set one. A Consumer can then ask for the shape the user keeps in the
 * IDE without holding a copy of it.
 *
 * Only fills in what the Consumer left out, following the other proxy injectors. A schema body the
 * Consumer sent is never replaced, whatever its name; a name that matches no saved schema is left
 * for OpenRouter to judge; and a saved body that is not a JSON object is never sent.
 */
object SavedSchemaInjector {

    /** @return true when a saved schema was put into the request. */
    fun inject(body: JsonObject, schemas: List<OutputSchema>): Boolean {
        val format = body.get(RESPONSE_FORMAT_KEY)?.asObjectOrNull() ?: return false
        if (format.get(TYPE_KEY)?.asStringOrNull() != JSON_SCHEMA_TYPE) return false
        val jsonSchema = format.get(JSON_SCHEMA_KEY)?.asObjectOrNull() ?: return false
        if (jsonSchema.has(SCHEMA_KEY)) return false
        val name = jsonSchema.get(NAME_KEY)?.asStringOrNull() ?: return false
        val saved = schemas.firstOrNull { OutputSchema.sameName(it.name, name) } ?: return false
        val schemaBody = saved.parsedBody() ?: return false
        jsonSchema.add(SCHEMA_KEY, schemaBody)
        if (!jsonSchema.has(STRICT_KEY)) jsonSchema.addProperty(STRICT_KEY, saved.strict)
        return true
    }

    private fun JsonElement.asObjectOrNull(): JsonObject? = takeIf { it.isJsonObject }?.asJsonObject

    private fun JsonElement.asStringOrNull(): String? =
        takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString

    private const val RESPONSE_FORMAT_KEY = "response_format"
    private const val TYPE_KEY = "type"
    private const val JSON_SCHEMA_TYPE = "json_schema"
    private const val JSON_SCHEMA_KEY = "json_schema"
    private const val SCHEMA_KEY = "schema"
    private const val NAME_KEY = "name"
    private const val STRICT_KEY = "strict"
}
