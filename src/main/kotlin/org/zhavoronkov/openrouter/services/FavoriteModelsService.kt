package org.zhavoronkov.openrouter.services

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import kotlinx.coroutines.withTimeout
import org.zhavoronkov.openrouter.constants.OpenRouterConstants
import org.zhavoronkov.openrouter.models.ApiResult
import org.zhavoronkov.openrouter.models.OpenRouterModelInfo
import org.zhavoronkov.openrouter.models.PresetPair
import org.zhavoronkov.openrouter.utils.PluginLogger
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration.Companion.milliseconds

/**
 * Service for managing favorite models with caching and API interaction
 * Implements Disposable for dynamic plugin support
 *
 * Note: This is a light service (uses @Service annotation) and must be final
 *
 * @param clock injected so the cache-expiry decision can be exercised without waiting out
 *  [org.zhavoronkov.openrouter.constants.OpenRouterConstants.MODELS_CACHE_DURATION_MS].
 *  Defaults to the wall clock, so production behaviour is unchanged.
 */
@Service
@Suppress("TooManyFunctions")
class FavoriteModelsService(
    private val settingsService: OpenRouterSettingsService? = null,
    private val openRouterService: OpenRouterService? = null,
    private val clock: () -> Long = System::currentTimeMillis
) : Disposable {

    companion object {
        fun getInstance(): FavoriteModelsService {
            return ApplicationManager.getApplication().getService(FavoriteModelsService::class.java)
        }
    }

    /**
     * What one fetch of the catalogue left: every model, the text-output ones the pickers list,
     * and when. One reference, so a reader on another thread never sees half of one fetch.
     */
    private class Catalogue(val all: List<OpenRouterModelInfo>, val fetchedAt: Long) {
        val textOutput: List<OpenRouterModelInfo> = all.filter(::outputsText)
    }

    @Volatile
    private var catalogue: Catalogue? = null

    /**
     * Moves on at every [clearCache], so a fetch that started before one - for the Data Region
     * that was just left, say - cannot put its answer back in the cache after it.
     */
    private val generation = AtomicInteger()

    private val textOutputModels: List<OpenRouterModelInfo>?
        get() = catalogue?.textOutput
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
        val cached = catalogue
        if (!forceRefresh && cached != null &&
            clock() - cached.fetchedAt < OpenRouterConstants.MODELS_CACHE_DURATION_MS
        ) {
            PluginLogger.Service.debug("Returning cached models (${cached.textOutput.size} models)")
            return cached.textOutput
        }

        PluginLogger.Service.debug("Fetching models from API (forceRefresh: $forceRefresh)")
        val startedIn = generation.get()
        return try {
            withTimeout(OpenRouterConstants.API_TIMEOUT_MS.milliseconds) {
                val result = routerService.getAllModels()
                when (result) {
                    is ApiResult.Success -> keep(Catalogue(result.data.data, clock()), startedIn).textOutput
                    is ApiResult.Error -> {
                        PluginLogger.Service.warn("Failed to fetch models: ${result.message}")
                        null
                    }
                }
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            // Coroutine was cancelled (e.g., timeout or parent job cancelled) - must rethrow
            throw e
        } catch (e: java.util.concurrent.TimeoutException) {
            PluginLogger.Service.warn("Model fetch timed out", e)
            PluginLogger.Service.error("Timeout details", e)
            null
        } catch (e: java.io.IOException) {
            PluginLogger.Service.error("Error fetching models from API", e)
            null
        } catch (e: IllegalStateException) {
            PluginLogger.Service.error("Error fetching models from API", e)
            null
        }
    }

    /**
     * Caches [fetched] unless the cache was cleared while it was being fetched: then it answers a
     * question nobody asks any more, and is handed back to its caller without being kept.
     */
    private fun keep(fetched: Catalogue, startedIn: Int): Catalogue {
        synchronized(generation) {
            if (generation.get() != startedIn) {
                PluginLogger.Service.debug("Models fetched before the cache was cleared are not kept")
                return fetched
            }
            catalogue = fetched
        }
        PluginLogger.Service.info("Successfully cached ${fetched.all.size} models")
        return fetched
    }

    /**
     * Get current favorite models as full model objects
     * @return List of favorite model info objects
     */
    fun getFavoriteModels(): List<OpenRouterModelInfo> {
        val favoriteIds = settings.favoriteModelsManager.getFavoriteModels()
        val cached = textOutputModels
        return favoriteIds.map { modelId ->
            // Try to find full model info from cache - a pair's is its model's, under the pair's
            // own id - otherwise create minimal object
            cached?.find { it.id == modelId }
                ?: cached?.find { it.id == PresetPair.modelOf(modelId) }?.copy(id = modelId)
                ?: createMinimalModelInfo(modelId)
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
        return textOutputModels?.find { it.id == modelId }
    }

    /**
     * Get cached models without triggering a fetch
     * @return Cached models or null if cache is empty
     */
    fun getCachedModels(): List<OpenRouterModelInfo>? {
        return textOutputModels
    }

    /**
     * Every model the selected Data Region serves, whatever it outputs, without triggering a
     * fetch - or null while it has not loaded. Unlike [getCachedModels], a model missing from it
     * is not served.
     */
    fun getCachedCatalogue(): List<OpenRouterModelInfo>? {
        return catalogue?.all
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
        synchronized(generation) {
            generation.incrementAndGet()
            catalogue = null
        }
        PluginLogger.Service.debug("Models cache cleared")
    }

    /**
     * Get total count of available models from OpenRouter
     * Uses output_modalities=all to include all model types (not just text)
     */
    @Suppress("TooGenericExceptionCaught")
    suspend fun getModelsCount(): Int? {
        return try {
            PluginLogger.Settings.debug("Fetching models count from /models/count?output_modalities=all")
            val result = routerService.getModelsCount()
            when (result) {
                is ApiResult.Success -> {
                    val count = result.data.data.count
                    PluginLogger.Settings.debug("Successfully fetched models count: $count")
                    count
                }
                is ApiResult.Error -> {
                    PluginLogger.Settings.warn(
                        "Failed to fetch models count: ${result.message} (statusCode=${result.statusCode})"
                    )
                    null
                }
            }
        } catch (e: java.io.IOException) {
            PluginLogger.Settings.warn("Network error fetching models count: ${e.message}")
            null
        } catch (e: Throwable) {
            PluginLogger.Settings.warn("Unexpected error fetching models count: ${e.message}")
            null
        }
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

/**
 * Whether the pickers list [model]: what OpenRouter's own `/models` lists unless asked for every
 * output modality. A model that says nothing about its output is taken to answer in text.
 */
private fun outputsText(model: OpenRouterModelInfo): Boolean =
    model.architecture?.outputModalities?.any { it.equals("text", ignoreCase = true) } ?: true
