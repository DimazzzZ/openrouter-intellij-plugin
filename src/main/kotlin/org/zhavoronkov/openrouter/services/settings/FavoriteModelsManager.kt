package org.zhavoronkov.openrouter.services.settings

import org.zhavoronkov.openrouter.models.FavoriteModelGroupData
import org.zhavoronkov.openrouter.models.OpenRouterSettings
import org.zhavoronkov.openrouter.utils.ModelProviderUtils

/**
 * Manages favorite models configuration.
 * Handles adding, removing, and querying favorite models, plus the grouped
 * representation used by the variant-aware picker.
 *
 * The flat [OpenRouterSettings.favoriteModels] list is authoritative.
 * The grouped view is computed on-demand from the flat list.
 */
class FavoriteModelsManager(
    private val settings: OpenRouterSettings,
    private val notifyChange: () -> Unit
) {

    fun getFavoriteModels(): List<String> {
        return settings.favoriteModels.toList()
    }

    fun addFavoriteModel(modelId: String) {
        if (!settings.favoriteModels.contains(modelId)) {
            settings.favoriteModels.add(modelId)
            notifyChange()
        }
    }

    fun removeFavoriteModel(modelId: String) {
        settings.favoriteModels.remove(modelId)
        notifyChange()
    }

    fun setFavoriteModels(models: List<String>) {
        settings.favoriteModels.clear()
        settings.favoriteModels.addAll(models)
        notifyChange()
    }

    fun isFavoriteModel(modelId: String): Boolean {
        return settings.favoriteModels.contains(modelId)
    }

    fun clearFavoriteModels() {
        settings.favoriteModels.clear()
        notifyChange()
    }

    // --- Grouped model support ---

    /**
     * Get the grouped representation of favorites. Each group has a base model ID
     * and its selected variant suffixes.
     * This is computed on-demand from the flat list, so it's always in sync.
     */
    fun getGroups(): List<FavoriteModelGroupData> {
        return buildGroupsFromFlat(settings.favoriteModels)
    }

    /**
     * Rebuild the grouped representation from the flat list.
     * Groups models by their base ID (variant suffix stripped).
     */
    private fun buildGroupsFromFlat(flatList: List<String>): List<FavoriteModelGroupData> {
        val groupMap = linkedMapOf<String, MutableList<String>>()

        for (modelId in flatList) {
            val parsed = ModelProviderUtils.parseModelId(modelId)
            val baseId = ModelProviderUtils.stripVariant(modelId)
            val variantSuffix = when {
                parsed.variant != null -> parsed.variant.suffix
                parsed.unknownVariant != null -> parsed.unknownVariant
                else -> null
            }

            val variants = groupMap.getOrPut(baseId) { mutableListOf() }
            if (variantSuffix != null) {
                variants.add(variantSuffix)
            }
        }

        return groupMap.map { (baseId, variants) ->
            FavoriteModelGroupData(baseId = baseId, variants = variants.toMutableList())
        }
    }
}
