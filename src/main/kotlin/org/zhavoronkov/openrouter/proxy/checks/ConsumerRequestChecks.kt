package org.zhavoronkov.openrouter.proxy.checks

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import org.zhavoronkov.openrouter.models.DataRegion
import org.zhavoronkov.openrouter.models.EntryNames
import org.zhavoronkov.openrouter.models.FixPage
import org.zhavoronkov.openrouter.models.OpenRouterModelInfo
import org.zhavoronkov.openrouter.models.OutputSchema
import org.zhavoronkov.openrouter.proxy.errors.ClearError
import org.zhavoronkov.openrouter.proxy.routing.RouterCatalog
import org.zhavoronkov.openrouter.toolwindow.chat.ChatExchange
import org.zhavoronkov.openrouter.utils.ModelProviderUtils

/**
 * What the proxy can know is wrong with a Consumer's request before sending it, answered with a
 * [ClearError] rather than whatever OpenRouter would refuse it with:
 *
 * - a `json_schema` response format naming a saved Output Schema that does not exist, or whose
 *   body is not a JSON object, and carrying no schema of its own;
 * - a model the catalogue does not list, or - with a Data Region selected, whose catalogue this
 *   is - one the region does not serve;
 * - a response format the model does not declare, judged by the chat's own rule.
 *
 * A slow start must not refuse requests, so nothing about the model is checked while the
 * [catalogue] has not loaded; and a model whose declarations cannot be known from it - a Router,
 * a preset, a Latest Model resolved at request time - is never refused for what it declares. The
 * [region] and the saved [schemas] are asked only when a check needs them.
 */
object ConsumerRequestChecks {

    fun problem(
        body: JsonObject,
        model: String,
        catalogue: List<OpenRouterModelInfo>?,
        region: () -> DataRegion,
        schemas: () -> List<OutputSchema>
    ): ClearError? {
        val format = body.get("response_format")?.asObjectOrNull()
        return missingSavedSchema(format, schemas) ?: modelProblem(format, model, catalogue, region)
    }

    /** What is wrong with [model] for this request, judged against the loaded [catalogue]. */
    private fun modelProblem(
        format: JsonObject?,
        model: String,
        catalogue: List<OpenRouterModelInfo>?,
        region: () -> DataRegion
    ): ClearError? {
        if (catalogue == null || resolvedAtRequestTime(model)) return null
        val entry = ModelProviderUtils.catalogueEntry(model, catalogue) ?: return notServed(model, region())
        return entry.supportedParameters?.let { unsupportedFormat(format, model, it) }
    }

    private fun missingSavedSchema(format: JsonObject?, schemas: () -> List<OutputSchema>): ClearError? {
        if (format?.get("type")?.asStringOrNull() != JSON_SCHEMA) return null
        val jsonSchema = format.get(JSON_SCHEMA)?.asObjectOrNull() ?: return null
        if (jsonSchema.has("schema")) return null
        val name = jsonSchema.get("name")?.asStringOrNull() ?: return null
        val saved = schemas().firstOrNull { EntryNames.same(it.name, name) }
            ?: return ClearError(
                "output_schema_not_found",
                "The response format names the Output Schema '$name', which is not saved, " +
                    "and carries no schema of its own",
                FixPage.OUTPUT_SCHEMAS,
                advice = "save it in"
            )
        if (saved.parsedBody() != null) return null
        return ClearError(
            "output_schema_invalid",
            "The Output Schema '${saved.name}' the response format names is not a JSON object",
            FixPage.OUTPUT_SCHEMAS
        )
    }

    private fun notServed(model: String, region: DataRegion): ClearError =
        if (region == DataRegion.GLOBAL) {
            ClearError(
                "model_not_found",
                "'$model' is not in OpenRouter's model catalogue",
                FixPage.FAVORITE_MODELS,
                "choose a model in"
            )
        } else {
            ClearError(
                "model_not_in_region",
                "'$model' is not served in the ${region.displayName} region",
                FixPage.DATA_REGION,
                "choose another model, or change the region in"
            )
        }

    private fun unsupportedFormat(format: JsonObject?, model: String, declared: List<String>): ClearError? {
        val schema = when (format?.get("type")?.asStringOrNull()) {
            JSON_SCHEMA -> true
            "json_object" -> false
            else -> return null
        }
        val reason = ChatExchange.responseFormatProblem(model, declared, schema) ?: return null
        return ClearError(
            "response_format_not_supported",
            reason,
            FixPage.FAVORITE_MODELS,
            "choose a model that does in"
        )
    }

    /**
     * A Router - any `openrouter/` slug, a beta one included - a preset or a Latest Model: which
     * model answers is decided when the request arrives.
     */
    private fun resolvedAtRequestTime(model: String): Boolean =
        RouterCatalog.isRouter(model) ||
            model.startsWith("openrouter/") ||
            model.startsWith("@preset/") ||
            model.startsWith(ModelProviderUtils.LATEST_MARKER)

    private fun JsonElement.asObjectOrNull(): JsonObject? = takeIf { it.isJsonObject }?.asJsonObject

    private fun JsonElement.asStringOrNull(): String? =
        takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString

    private const val JSON_SCHEMA = "json_schema"
}
