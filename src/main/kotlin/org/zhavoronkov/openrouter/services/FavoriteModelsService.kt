package org.zhavoronkov.openrouter.services

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import org.zhavoronkov.openrouter.models.OpenRouterModelInfo
import org.zhavoronkov.openrouter.models.OpenRouterModelsResponse
import org.zhavoronkov.openrouter.utils.PluginLogger
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

/**
 * Service for managing favorite models with caching and API interaction
 */
@Service
class FavoriteModelsService {

    companion object {
        private const val CACHE_DURATION_MS = 300000L // 5 minutes
        private const val API_TIMEOUT_SECONDS = 30L

        fun getInstance(): FavoriteModelsService {
            return ApplicationManager.getApplication().getService(FavoriteModelsService::class.java)
        }
    }

    private var cachedModels: List<OpenRouterModelInfo>? = null
    private var cacheTimestamp: Long = 0L
    private val settingsService = OpenRouterSettingsService.getInstance()
    private val openRouterService = OpenRouterService.getInstance()

    /**
     * Get all available models from OpenRouter API with caching
     * @param forceRefresh If true, bypass cache and fetch fresh data
     * @return CompletableFuture with list of models or null on error
     */
    fun getAvailableModels(forceRefresh: Boolean = false): CompletableFuture<List<OpenRouterModelInfo>?> {
        val now = System.currentTimeMillis()
        val isCacheValid = cachedModels != null && (now - cacheTimestamp) < CACHE_DURATION_MS

        if (!forceRefresh && isCacheValid) {
            PluginLogger.Service.debug("Returning cached models (${cachedModels?.size} models)")
            return CompletableFuture.completedFuture(cachedModels)
        }

        PluginLogger.Service.debug("Fetching models from API (forceRefresh: $forceRefresh)")
        return openRouterService.getModels()
            .orTimeout(API_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .thenApply { response: OpenRouterModelsResponse? ->
                if (response?.data != null) {
                    cachedModels = response.data
                    cacheTimestamp = System.currentTimeMillis()
                    PluginLogger.Service.debug("Cached ${cachedModels?.size} models")
                    cachedModels
                } else {
                    PluginLogger.Service.warn("Failed to fetch models: response is null or has no data")
                    null
                }
            }
            .exceptionally { throwable ->
                PluginLogger.Service.error("Error fetching models from API", throwable)
                null
            }
    }

    /**
     * Get current favorite models as full model objects
     * @return List of favorite model info objects
     */
    fun getFavoriteModels(): List<OpenRouterModelInfo> {
        val favoriteIds = settingsService.getFavoriteModels()
        return favoriteIds.map { modelId ->
            // Try to find full model info from cache, otherwise create minimal object
            cachedModels?.find { it.id == modelId } ?: createMinimalModelInfo(modelId)
        }
    }

    /**
     * Set favorite models (preserves order)
     * @param models List of model info objects to set as favorites
     */
    fun setFavoriteModels(models: List<OpenRouterModelInfo>) {
        val modelIds = models.map { it.id }
        settingsService.setFavoriteModels(modelIds)
    }

    /**
     * Add a model to favorites
     * @param model Model to add
     * @return true if added, false if already exists
     */
    fun addFavoriteModel(model: OpenRouterModelInfo): Boolean {
        val currentFavorites = settingsService.getFavoriteModels().toMutableList()
        if (currentFavorites.contains(model.id)) {
            return false
        }
        currentFavorites.add(model.id)
        settingsService.setFavoriteModels(currentFavorites)
        return true
    }

    /**
     * Remove a model from favorites
     * @param modelId Model ID to remove
     * @return true if removed, false if not found
     */
    fun removeFavoriteModel(modelId: String): Boolean {
        val currentFavorites = settingsService.getFavoriteModels().toMutableList()
        val removed = currentFavorites.remove(modelId)
        if (removed) {
            settingsService.setFavoriteModels(currentFavorites)
        }
        return removed
    }

    /**
     * Reorder favorites by moving a model to a new position
     * @param fromIndex Current index
     * @param toIndex Target index
     */
    fun reorderFavorites(fromIndex: Int, toIndex: Int) {
        val currentFavorites = settingsService.getFavoriteModels().toMutableList()
        if (!areIndicesValid(fromIndex, toIndex, currentFavorites.size)) {
            return
        }

        val model = currentFavorites.removeAt(fromIndex)
        currentFavorites.add(toIndex, model)
        settingsService.setFavoriteModels(currentFavorites)
    }

    /**
     * Validates that the given indices are within valid bounds
     */
    private fun areIndicesValid(fromIndex: Int, toIndex: Int, listSize: Int): Boolean {
        return fromIndex in 0 until listSize && toIndex in 0 until listSize
    }

    /**
     * Check if a model is in favorites
     * @param modelId Model ID to check
     * @return true if model is favorited
     */
    fun isFavorite(modelId: String): Boolean {
        return settingsService.isFavoriteModel(modelId)
    }

    /**
     * Clear all favorites
     */
    fun clearAllFavorites() {
        settingsService.setFavoriteModels(emptyList())
    }

    /**
     * Clear the models cache
     */
    fun clearCache() {
        cachedModels = null
        cacheTimestamp = 0L
        PluginLogger.Service.debug("Models cache cleared")
    }

    /**
     * Create a minimal model info object when full data is not available
     */
    private fun createMinimalModelInfo(modelId: String): OpenRouterModelInfo {
        return OpenRouterModelInfo(
            id = modelId,
            name = modelId,
            created = System.currentTimeMillis() / 1000,
            description = null,
            architecture = null,
            topProvider = null,
            pricing = null,
            contextLength = null,
            perRequestLimits = null
        )
    }
}

