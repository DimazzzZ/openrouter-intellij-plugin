package org.zhavoronkov.openrouter.settings

import com.google.gson.JsonSyntaxException
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.zhavoronkov.openrouter.models.OpenRouterModelInfo
import org.zhavoronkov.openrouter.services.FavoriteModelsService
import org.zhavoronkov.openrouter.utils.PluginLogger
import java.io.IOException

/**
 * Manages loading and refreshing of models data
 */
class ModelsDataManager(
    private val favoriteModelsService: FavoriteModelsService,
    private val coroutineScope: CoroutineScope,
    private val onModelsLoaded: (List<OpenRouterModelInfo>?) -> Unit,
    private val onLoadError: (String, Throwable) -> Unit
) {

    @Volatile
    private var isLoading = false

    fun loadInitialData() {
        if (isLoading) {
            PluginLogger.Settings.debug("Already loading models, skipping duplicate request")
            return
        }

        isLoading = true
        PluginLogger.Settings.debug("Loading initial models data")

        coroutineScope.launch(Dispatchers.IO) {
            try {
                val models = favoriteModelsService.getAvailableModels(forceRefresh = false)

                ApplicationManager.getApplication().invokeLater({
                    isLoading = false
                    if (models != null) {
                        PluginLogger.Settings.debug("Successfully loaded ${models.size} models")
                        onModelsLoaded(models)
                    } else {
                        val error = Exception("Failed to load models from API")
                        PluginLogger.Settings.error("Failed to load models", error)
                        onLoadError("Failed to load models from API", error)
                    }
                }, ModalityState.any())
            } catch (e: IOException) {
                ApplicationManager.getApplication().invokeLater({
                    isLoading = false
                    PluginLogger.Settings.error("Network error loading models", e)
                    onLoadError("Network error loading models: ${e.message}", e)
                }, ModalityState.any())
            } catch (e: JsonSyntaxException) {
                ApplicationManager.getApplication().invokeLater({
                    isLoading = false
                    PluginLogger.Settings.error("JSON parsing error loading models", e)
                    onLoadError("Failed to parse models data: ${e.message}", e)
                }, ModalityState.any())
            }
        }
    }

    fun refreshAvailableModels(onRefreshComplete: (List<OpenRouterModelInfo>?) -> Unit) {
        if (isLoading) {
            PluginLogger.Settings.debug("Already loading models, skipping refresh request")
            return
        }

        isLoading = true
        PluginLogger.Settings.debug("Refreshing models from OpenRouter API")

        coroutineScope.launch(Dispatchers.IO) {
            try {
                val models = favoriteModelsService.getAvailableModels(forceRefresh = true)

                ApplicationManager.getApplication().invokeLater({
                    isLoading = false
                    if (models != null) {
                        PluginLogger.Settings.debug("Successfully refreshed ${models.size} models")
                        onRefreshComplete(models)
                    } else {
                        PluginLogger.Settings.error("Failed to refresh models")
                        onRefreshComplete(null)
                    }
                }, ModalityState.any())
            } catch (e: IOException) {
                ApplicationManager.getApplication().invokeLater({
                    isLoading = false
                    PluginLogger.Settings.error("Network error refreshing models", e)
                    onRefreshComplete(null)
                }, ModalityState.any())
            } catch (e: JsonSyntaxException) {
                ApplicationManager.getApplication().invokeLater({
                    isLoading = false
                    PluginLogger.Settings.error("JSON parsing error refreshing models", e)
                    onRefreshComplete(null)
                }, ModalityState.any())
            }
        }
    }

    fun isCurrentlyLoading(): Boolean = isLoading
}
