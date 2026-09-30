package org.zhavoronkov.openrouter.proxy.pairs

import com.google.gson.JsonObject
import org.zhavoronkov.openrouter.models.OpenRouterModelInfo
import org.zhavoronkov.openrouter.models.PresetPair
import org.zhavoronkov.openrouter.presets.PresetCopyService
import org.zhavoronkov.openrouter.presets.PresetEntry
import org.zhavoronkov.openrouter.presets.PresetSnapshot
import org.zhavoronkov.openrouter.services.FavoriteModelsService
import org.zhavoronkov.openrouter.toolwindow.chat.ChatExchange
import org.zhavoronkov.openrouter.utils.ModelProviderUtils

/** Why a pair cannot be sent with its preset. */
sealed interface PairProblem {
    val message: String

    data class MissingPreset(val preset: String) : PairProblem {
        override val message get() = "No preset named '$preset' is saved on OpenRouter"
    }

    data class OutputNotServable(val preset: String, val reason: String) : PairProblem {
        override val message get() = "The preset '$preset' asks for an output this model cannot give: $reason"
    }

    /** Measured against OpenRouter: its web search drops plain JSON, whatever the model. */
    data class WebSearchDropsJson(val preset: String) : PairProblem {
        override val message get() =
            "The preset '$preset' asks for plain JSON with web search, which OpenRouter drops; " +
                "use a schema, or turn web search off"
    }
}

/**
 * Whether a pair can be sent with its preset, asked the same way everywhere a pair appears -
 * `/v1/models`, a Consumer's request, the Favorite Models page - so a pair is hidden, refused and
 * marked for the same reasons, all decided from the plugin's copy of the presets.
 *
 * Nothing is refused while the copy has never been read, nor for a preset whose config the copy
 * could not read: a slow start must not hide or refuse a pair. The output gate is the chat's
 * ([ChatExchange]), fed what the pair's model declares in the catalogue; while the catalogue has
 * not loaded, only the web-search rule, which needs no catalogue, is applied.
 */
class PairAvailability(
    private val presets: () -> PresetSnapshot?,
    private val lookup: (slug: String) -> PresetEntry?,
    private val catalogue: () -> List<OpenRouterModelInfo>?
) {

    /** Why [pair] cannot be sent, or null when it can. */
    fun problem(pair: PresetPair): PairProblem? {
        presets() ?: return null
        val entry = lookup(pair.preset) ?: return PairProblem.MissingPreset(pair.preset)
        val config = entry.config ?: return null
        val schema = when (outputType(config)) {
            JSON_OBJECT -> false
            JSON_SCHEMA -> true
            else -> return null
        }
        if (!schema && searches(config)) return PairProblem.WebSearchDropsJson(entry.slug)
        val models = catalogue() ?: return null
        return ChatExchange.responseFormatProblem(pair.model, declaredBy(pair.model, models), schema)
            ?.let { PairProblem.OutputNotServable(entry.slug, it) }
    }

    /** What may still go wrong with a pair that can be sent, or null: a schema that web search may drop. */
    fun warning(pair: PresetPair): String? {
        val config = presets()?.find(pair.preset)?.config ?: return null
        return ChatExchange.WEB_SEARCH_SCHEMA_WARNING.takeIf { outputType(config) == JSON_SCHEMA && searches(config) }
    }

    /** [pair]'s preset as the plugin last read it, or null when it has not read it. */
    fun preset(pair: PresetPair): PresetEntry? = presets()?.find(pair.preset)

    /** What the preset [slug] names sets, or null when the copy does not know. */
    fun presetConfig(slug: String): JsonObject? = presets()?.find(slug)?.config

    /** The request fields [pair]'s preset sets, or null when they are not known. */
    fun presetConfig(pair: PresetPair): JsonObject? = presets()?.find(pair.preset)?.config

    /** Why the favourite [id] cannot be sent, or null when it can or is not a pair. */
    fun problem(id: String): PairProblem? = PresetPair.parse(id)?.let(::problem)

    fun warning(id: String): String? = PresetPair.parse(id)?.let(::warning)

    /** The same questions asked of the presets and the catalogue as they are now, read once. */
    fun snapshot(): PairAvailability {
        val snapshot = presets()
        val models = catalogue()
        return PairAvailability({ snapshot }, { slug -> snapshot?.find(slug) ?: lookup(slug) }, { models })
    }

    private fun outputType(config: JsonObject): String? =
        config.get("response_format")?.takeIf { it.isJsonObject }?.asJsonObject?.get("type")
            ?.takeIf { it.isJsonPrimitive }?.asString

    private fun searches(config: JsonObject): Boolean =
        config.get("tools")?.takeIf { it.isJsonArray }?.asJsonArray?.any { tool ->
            tool.isJsonObject && tool.asJsonObject.get("type")?.takeIf { it.isJsonPrimitive }?.asString == WEB_SEARCH
        } == true

    /** What [model] declares, as [ModelProviderUtils.catalogueEntry] finds it. */
    private fun declaredBy(model: String, models: List<OpenRouterModelInfo>): List<String>? =
        ModelProviderUtils.catalogueEntry(model, models)?.supportedParameters

    companion object {
        private const val JSON_OBJECT = "json_object"
        private const val JSON_SCHEMA = "json_schema"
        private const val WEB_SEARCH = "openrouter:web_search"

        /** Asked of the plugin's copy of the presets and the loaded catalogue. */
        fun fromSettings(): PairAvailability {
            val copy = PresetCopyService.getInstance().copy
            return PairAvailability(
                presets = copy::snapshot,
                lookup = copy::find,
                catalogue = { FavoriteModelsService.getInstance().getCachedModels() }
            )
        }
    }
}
