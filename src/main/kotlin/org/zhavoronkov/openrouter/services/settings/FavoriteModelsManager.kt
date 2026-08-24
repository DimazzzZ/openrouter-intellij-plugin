package org.zhavoronkov.openrouter.services.settings

import org.zhavoronkov.openrouter.models.FavoriteModelGroupData
import org.zhavoronkov.openrouter.models.OpenRouterSettings
import org.zhavoronkov.openrouter.utils.ModelProviderUtils

/**
 * Manages favorite models configuration.
 * Handles adding, removing, and querying favorite models, plus the grouped
 * representation used by the Phase 2 variant-aware picker.
 *
 * The flat [OpenRouterSettings.favoriteModels] list remains authoritative on the wire
 * (favorites are still sent as `base:variant` strings). The grouped view in
 * [OpenRouterSettings.favoriteModelGroups] is a convenience layer kept in sync
 * with the flat list on every mutation.
 */
class FavoriteModelsManager(
    private val settings: OpenRouterSettings,
    private val notifyChange: () -> Unit
) {

    init {
        migrateToGroupedIfNeeded()
    }

    fun getFavoriteModels(): List<String> {
        return settings.favoriteModels.toList()
    }

    fun addFavoriteModel(modelId: String) {
        if (!settings.favoriteModels.contains(modelId)) {
            settings.favoriteModels.add(modelId)
            syncGroupsFromFlat()
            notifyChange()
        }
    }

    fun removeFavoriteModel(modelId: String) {
        settings.favoriteModels.remove(modelId)
        syncGroupsFromFlat()
        notifyChange()
    }

    fun setFavoriteModels(models: List<String>) {
        settings.favoriteModels.clear()
        settings.favoriteModels.addAll(models)
        syncGroupsFromFlat()
        notifyChange()
    }

    fun isFavoriteModel(modelId: String): Boolean {
        return settings.favoriteModels.contains(modelId)
    }

    fun clearFavoriteModels() {
        settings.favoriteModels.clear()
        settings.favoriteModelGroups.clear()
        notifyChange()
    }

    // --- Grouped model support (Phase 2, D9) ---

    /**
     * Get the grouped representation of favorites. Each group has a base model ID
     * and its selected variant suffixes.
     */
    fun getGroups(): List<FavoriteModelGroupData> {
        return settings.favoriteModelGroups.toList()
    }

    /**
     * One-time migration from flat favorites list to grouped representation.
     * Idempotent: guarded by [OpenRouterSettings.favoriteModelsGroupedMigrated].
     */
    internal fun migrateToGroupedIfNeeded() {
        if (settings.favoriteModelsGroupedMigrated) return

        val groups = buildGroupsFromFlat(settings.favoriteModels)
        settings.favoriteModelGroups.clear()
        settings.favoriteModelGroups.addAll(groups)
        settings.favoriteModelsGroupedMigrated = true
    }

    /**
     * Rebuild the grouped representation from the flat list.
     * Groups models by their base ID (variant suffix stripped).
     */
    internal fun buildGroupsFromFlat(flatList: List<String>): List<FavoriteModelGroupData> {
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

    /**
     * Sync the grouped representation after a flat-list mutation.
     */
    private fun syncGroupsFromFlat() {
        val groups = buildGroupsFromFlat(settings.favoriteModels)
        settings.favoriteModelGroups.clear()
        settings.favoriteModelGroups.addAll(groups)
    }
}
