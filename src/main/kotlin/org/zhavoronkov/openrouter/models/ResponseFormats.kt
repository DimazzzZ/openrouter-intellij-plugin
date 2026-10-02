package org.zhavoronkov.openrouter.models

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import org.zhavoronkov.openrouter.utils.asObjectOrNull
import org.zhavoronkov.openrouter.utils.asStringOrNull

/**
 * What OpenRouter does with a response format: which models can give one, and what its web search
 * drops. The chat's Output mode control, a pair's preset and a Consumer's own request are all
 * judged by these rules, so they live apart from any one of them; so do the request fields they
 * are spelled in.
 */
object ResponseFormats {

    /** The request field a response format is sent in. */
    const val FIELD = "response_format"

    /** The response format type for a JSON object with no schema to follow. */
    const val JSON_OBJECT = "json_object"

    /** The response format type for a reply in the shape of a schema, and the key its schema sits under. */
    const val JSON_SCHEMA = "json_schema"

    /** OpenRouter's web search server tool; see its guide in OpenRouter's documentation. */
    const val WEB_SEARCH_TOOL = "openrouter:web_search"

    /**
     * Measured against OpenRouter: with the web search server tool a `json_schema` is kept only
     * when the provider runs its own search (OpenAI's, say); with Exa, the search every other
     * model gets, it is dropped without an error.
     */
    const val WEB_SEARCH_SCHEMA_WARNING =
        "With web search, the schema holds only where the provider searches natively; otherwise the reply is plain text"

    /** Measured against OpenRouter: the web search server tool drops `json_object` whatever searches. */
    const val WEB_SEARCH_DROPS_JSON =
        "OpenRouter drops plain JSON when the model may search the web; turn web search off"

    /** [WEB_SEARCH_DROPS_JSON] for a pair whose preset searches, which no switch turns off. */
    const val WEB_SEARCH_PRESET_DROPS_JSON =
        "OpenRouter drops plain JSON when the model may search the web, and this pair's preset lets it"

    /** The `supported_parameters` entry that declares plain JSON output. */
    private const val DECLARES_JSON = "response_format"

    /** The `supported_parameters` entry that declares schema-constrained output. */
    private const val DECLARES_SCHEMA = "structured_outputs"

    /**
     * Whether [fields] - a request, or a preset's config - asks for a reply in the shape of a
     * schema (true) or for plain JSON (false); null when it asks for neither.
     */
    fun asksForSchema(fields: JsonObject): Boolean? =
        when (fields.get(FIELD)?.asObjectOrNull()?.get("type")?.asStringOrNull()) {
            JSON_SCHEMA -> true
            JSON_OBJECT -> false
            else -> null
        }

    /** Whether [fields] - a request, or a preset's config - offers OpenRouter's web search tool. */
    fun offersWebSearch(fields: JsonObject): Boolean =
        // Unreachable branch: asJsonArray never returns null for an element isJsonArray accepted
        fields.get("tools")?.takeIf { it.isJsonArray }?.asJsonArray?.any(::isWebSearchTool) == true

    /** Whether [tool], one entry of a `tools` list, is OpenRouter's web search tool. */
    fun isWebSearchTool(tool: JsonElement): Boolean =
        tool.asObjectOrNull()?.get("type")?.asStringOrNull() == WEB_SEARCH_TOOL

    /** What OpenRouter's web search does to the reply a response format asks for. */
    enum class WebSearchEffect {
        /** Plain JSON is dropped every time: [WEB_SEARCH_DROPS_JSON]. */
        DROPS_JSON,

        /** A schema holds only where the provider searches natively: [WEB_SEARCH_SCHEMA_WARNING]. */
        MAY_DROP_SCHEMA
    }

    /**
     * What web search, when it [searches], does to a reply asked for in the shape of a schema
     * (when [schema]) or as plain JSON, or null when nothing is lost - no search, or [schema] null
     * for no response format. The chat's Output mode, a pair's preset and the preset dialog all
     * ask this, so they cannot disagree.
     */
    fun webSearchEffect(searches: Boolean, schema: Boolean?): WebSearchEffect? = when {
        !searches || schema == null -> null
        schema -> WebSearchEffect.MAY_DROP_SCHEMA
        else -> WebSearchEffect.DROPS_JSON
    }

    /**
     * Why [model], declaring [declared], cannot give plain JSON - or, when [schema], a reply in the
     * shape of a schema - or null when it can. [declared] is null when what the model declares is
     * not known, which is said as such rather than as missing support.
     */
    fun problem(model: String, declared: List<String>?, schema: Boolean): String? =
        if (schema) {
            capabilityReason(model, declared, DECLARES_SCHEMA, "schema-constrained output")
        } else {
            capabilityReason(model, declared, DECLARES_JSON, "JSON output")
        }

    private fun capabilityReason(model: String, declared: List<String>?, parameter: String, what: String): String? =
        when {
            // Unreachable branch: problem() passes one of two non-empty literals as what
            declared == null -> "${what.replaceFirstChar { it.uppercase() }} support is not known for $model"
            parameter in declared -> null
            else -> "$model does not support $what"
        }
}
