package org.zhavoronkov.openrouter.services

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import kotlinx.coroutines.withTimeout
import org.zhavoronkov.openrouter.constants.OpenRouterConstants
import org.zhavoronkov.openrouter.models.ApiResult
import org.zhavoronkov.openrouter.models.OpenRouterModelInfo
import org.zhavoronkov.openrouter.utils.PluginLogger

/**
 * Service for managing favorite models with caching and API interaction
 * Implements Disposable for dynamic plugin support
 *
 * Note: This is a light service (uses @Service annotation) and must be final
 */
@Service
@Suppress("TooManyFunctions")
class FavoriteModelsService(
    private val settingsService: OpenRouterSettingsService? = null,
    private val openRouterService: OpenRouterService? = null
) : Disposable {

    companion object {
        fun getInstance(): FavoriteModelsService {
            return ApplicationManager.getApplication().getService(FavoriteModelsService::class.java)
        }
    }

    private var cachedModels: List<OpenRouterModelInfo>? = null
    private var cacheTimestamp: Long = 0L
    private val settings: OpenRouterSettingsService by lazy {
        settingsService ?: OpenRouterSettingsService.getInstance()
    }
    private val routerService: OpenRouterService by lazy {
        openRouterService ?: OpenRouterService.getInstance()
    }

    /**
     * Get all available models from OpenRouter API with caching
     * @param forceRefresh If true, bypass cache and fetch fresh data
     * @return List of models or null on error
     */
    suspend fun getAvailableModels(forceRefresh: Boolean = false): List<OpenRouterModelInfo>? {
        val now = System.currentTimeMillis()
        val isCacheValid = cachedModels != null && (now - cacheTimestamp) < OpenRouterConstants.MODELS_CACHE_DURATION_MS

        if (!forceRefresh && isCacheValid) {
            PluginLogger.Service.debug("Returning cached models (${cachedModels?.size} models)")
            return cachedModels
        }

        PluginLogger.Service.debug("[OpenRouter] Fetching models from API (forceRefresh: $forceRefresh)")
        return try {
            withTimeout(OpenRouterConstants.API_TIMEOUT_MS) {
                val result = routerService.getModels()
                when (result) {
                    is ApiResult.Success -> {
                        val response = result.data
                        cachedModels = response.data
                        cacheTimestamp = System.currentTimeMillis()
                        PluginLogger.Service.info("[OpenRouter] Successfully cached ${cachedModels?.size} models")
                        cachedModels
                    }
                    is ApiResult.Error -> {
                        PluginLogger.Service.warn("[OpenRouter] Failed to fetch models: ${result.message}")
                        null
                    }
                }
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            // Coroutine was cancelled (e.g., timeout or parent job cancelled) - must rethrow
            throw e
        } catch (e: java.util.concurrent.TimeoutException) {
            PluginLogger.Service.warn("[OpenRouter] Model fetch timed out", e)
            PluginLogger.Service.error("[OpenRouter] Timeout details", e)
            null
        } catch (e: java.io.IOException) {
            PluginLogger.Service.error("[OpenRouter] Error fetching models from API", e)
            null
        } catch (e: IllegalStateException) {
            PluginLogger.Service.error("[OpenRouter] Error fetching models from API", e)
            null
        }
    }

    /**
     * Get current favorite models as full model objects
     * @return List of favorite model info objects
     */
    fun getFavoriteModels(): List<OpenRouterModelInfo> {
        val favoriteIds = settings.favoriteModelsManager.getFavoriteModels()
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
        settings.favoriteModelsManager.setFavoriteModels(modelIds)
    }

    /**
     * Add a model to favorites
     * @param model Model to add
     * @return true if added, false if already exists
     */
    fun addFavoriteModel(model: OpenRouterModelInfo): Boolean {
        val currentFavorites = settings.favoriteModelsManager.getFavoriteModels().toMutableList()
        if (currentFavorites.contains(model.id)) {
            return false
        }
        currentFavorites.add(model.id)
        settings.favoriteModelsManager.setFavoriteModels(currentFavorites)
        return true
    }

    /**
     * Remove a model from favorites
     * @param modelId Model ID to remove
     * @return true if removed, false if not found
     */
    fun removeFavoriteModel(modelId: String): Boolean {
        val currentFavorites = settings.favoriteModelsManager.getFavoriteModels().toMutableList()
        val removed = currentFavorites.remove(modelId)
        if (removed) {
            settings.favoriteModelsManager.setFavoriteModels(currentFavorites)
        }
        return removed
    }

    /**
     * Reorder favorites by moving a model to a new position
     * @param fromIndex Current index
     * @param toIndex Target index
     */
    fun reorderFavorites(fromIndex: Int, toIndex: Int) {
        val currentFavorites = settings.favoriteModelsManager.getFavoriteModels().toMutableList()
        if (!areIndicesValid(fromIndex, toIndex, currentFavorites.size)) {
            return
        }

        val model = currentFavorites.removeAt(fromIndex)
        currentFavorites.add(toIndex, model)
        settings.favoriteModelsManager.setFavoriteModels(currentFavorites)
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
        return settings.favoriteModelsManager.isFavoriteModel(modelId)
    }

    /**
     * Get a model by ID from the cache
     * @param modelId Model ID to look up
     * @return Model info if found in cache, null otherwise
     */
    fun getModelById(modelId: String): OpenRouterModelInfo? {
        return cachedModels?.find { it.id == modelId }
    }

    /**
     * Get cached models without triggering a fetch
     * @return Cached models or null if cache is empty
     */
    fun getCachedModels(): List<OpenRouterModelInfo>? {
        return cachedModels
    }

    /**
     * Clear all favorites
     */
    fun clearAllFavorites() {
        settings.favoriteModelsManager.setFavoriteModels(emptyList())
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

    /**
     * Dispose method for dynamic plugin support
     * Clears cached data to prevent memory leaks
     */
    override fun dispose() {
        PluginLogger.Service.info("Disposing FavoriteModelsService - clearing cache")
        clearCache()
        PluginLogger.Service.info("FavoriteModelsService disposed successfully")
    }
}
