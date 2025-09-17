package org.zhavoronkov.openrouter.services

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import org.zhavoronkov.openrouter.models.OpenRouterSettings

/**
 * Service for managing OpenRouter plugin settings
 */
@State(
    name = "OpenRouterSettings",
    storages = [Storage("openrouter.xml")]
)
class OpenRouterSettingsService : PersistentStateComponent<OpenRouterSettings> {

    private var settings = OpenRouterSettings()

    companion object {
        fun getInstance(): OpenRouterSettingsService {
            return ApplicationManager.getApplication().getService(OpenRouterSettingsService::class.java)
        }
    }

    override fun getState(): OpenRouterSettings {
        return settings
    }

    override fun loadState(state: OpenRouterSettings) {
        this.settings = state
    }

    fun getApiKey(): String = settings.apiKey

    fun setApiKey(apiKey: String) {
        settings.apiKey = apiKey
    }

    fun getProvisioningKey(): String = settings.provisioningKey

    fun setProvisioningKey(provisioningKey: String) {
        settings.provisioningKey = provisioningKey
    }

    fun getDefaultModel(): String = settings.defaultModel

    fun setDefaultModel(model: String) {
        settings.defaultModel = model
    }

    fun isAutoRefreshEnabled(): Boolean = settings.autoRefresh

    fun setAutoRefresh(enabled: Boolean) {
        settings.autoRefresh = enabled
    }

    fun getRefreshInterval(): Int = settings.refreshInterval

    fun setRefreshInterval(interval: Int) {
        settings.refreshInterval = interval
    }

    fun shouldShowCosts(): Boolean = settings.showCosts

    fun setShowCosts(show: Boolean) {
        settings.showCosts = show
    }

    fun isTrackingEnabled(): Boolean = settings.trackGenerations

    fun setTrackingEnabled(enabled: Boolean) {
        settings.trackGenerations = enabled
    }

    fun getMaxTrackedGenerations(): Int = settings.maxTrackedGenerations

    fun setMaxTrackedGenerations(max: Int) {
        settings.maxTrackedGenerations = max
    }

    fun isConfigured(): Boolean = settings.provisioningKey.isNotBlank()
}
