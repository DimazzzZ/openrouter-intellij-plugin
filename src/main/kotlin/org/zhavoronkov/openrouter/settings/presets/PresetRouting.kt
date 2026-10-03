package org.zhavoronkov.openrouter.settings.presets

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParseException
import org.zhavoronkov.openrouter.models.ProviderRoutingPreferences

/**
 * A preset's `provider` block and the Provider Routing form, which edits only some of its fields:
 * the form's fields are replaced from the form, and every other field - a price cap, say - is
 * kept as it was.
 */
object PresetRouting {

    private val gson = Gson()

    /** The fields the Provider Routing form edits. */
    private val FORM_KEYS = listOf(
        "order",
        "allow_fallbacks",
        "sort",
        "require_parameters",
        "data_collection",
        "quantizations",
        "only",
        "ignore"
    )

    /** What the form shows for [block]; a `sort` object the form cannot show is left out of it. */
    fun preferencesOf(block: JsonObject): ProviderRoutingPreferences {
        val shown = block.deepCopy()
        if (shown.get("sort")?.let { it.isJsonPrimitive && it.asJsonPrimitive.isString } != true) shown.remove("sort")
        return try {
            gson.fromJson(shown, ProviderRoutingPreferences::class.java)
        } catch (e: JsonParseException) {
            ProviderRoutingPreferences()
        }
    }

    /** [original] with the form's fields replaced by [edited]; a `sort` object is kept when the form sets none. */
    fun merged(original: JsonObject, edited: ProviderRoutingPreferences): JsonObject {
        val keptSort = original.get("sort")?.takeIf { it.isJsonObject && edited.sort == null }
        val out = original.deepCopy()
        FORM_KEYS.forEach(out::remove)
        gson.toJsonTree(edited).asJsonObject.entrySet().forEach { (key, value) -> out.add(key, value) }
        keptSort?.let { out.add("sort", it) }
        return out
    }

    /** One line for the dialog's row, e.g. "only azure · sort price"; "OpenRouter's default" when empty. */
    fun summary(block: JsonObject): String {
        if (block.size() == 0) return "OpenRouter's default"
        return block.entrySet().joinToString(" · ") { (key, value) ->
            val shown = when {
                value.isJsonArray -> value.asJsonArray.joinToString(
                    ", "
                ) { if (it.isJsonPrimitive) it.asString else it.toString() }
                value.isJsonPrimitive -> value.asString
                else -> "…"
            }
            "${key.replace('_', ' ')} $shown"
        }
    }
}
