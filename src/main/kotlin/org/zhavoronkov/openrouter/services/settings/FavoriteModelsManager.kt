package org.zhavoronkov.openrouter.services.settings

import org.zhavoronkov.openrouter.models.OpenRouterSettings

/**
 * Manages favorite models configuration.
 * Handles adding, removing, and querying favorite models.
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
}
