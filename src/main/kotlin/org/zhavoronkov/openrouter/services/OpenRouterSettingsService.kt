package org.zhavoronkov.openrouter.services

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import org.zhavoronkov.openrouter.models.OpenRouterSettings
import org.zhavoronkov.openrouter.utils.EncryptionUtil
import org.zhavoronkov.openrouter.utils.PluginLogger

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

    fun getApiKey(): String {
        val encrypted = settings.apiKey
        val decrypted = if (EncryptionUtil.isEncrypted(encrypted)) {
            EncryptionUtil.decrypt(encrypted)
        } else {
            encrypted
        }
        PluginLogger.Service.info("getApiKey: encrypted.length=${encrypted.length}, encrypted.isEmpty=${encrypted.isEmpty()}, decrypted.length=${decrypted.length}, decrypted.isEmpty=${decrypted.isEmpty()}")
        return decrypted
    }

    fun setApiKey(apiKey: String) {
        PluginLogger.Service.info("setApiKey called: apiKey.length=${apiKey.length}, apiKey.isEmpty=${apiKey.isEmpty()}")

        val encryptedKey = if (apiKey.isNotBlank()) {
            EncryptionUtil.encrypt(apiKey)
        } else {
            apiKey
        }

        PluginLogger.Service.info("setApiKey: encrypted.length=${encryptedKey.length}, encrypted.isEmpty=${encryptedKey.isEmpty()}")

        // Modify the existing settings object directly (don't create a new copy)
        // This ensures the PersistentStateComponent properly detects the change
        val oldApiKey = settings.apiKey
        settings.apiKey = encryptedKey

        PluginLogger.Service.info("setApiKey: settings.apiKey changed from length ${oldApiKey.length} to ${settings.apiKey.length}")
        PluginLogger.Service.info("setApiKey: settings.apiKey.isEmpty=${settings.apiKey.isEmpty()}")

        notifyStateChanged()

        // Verify immediately after persistence
        val retrieved = getApiKey()
        PluginLogger.Service.info("setApiKey: verification after persistence - retrieved.length=${retrieved.length}, retrieved.isEmpty=${retrieved.isEmpty()}")
    }

    /**
     * Get the stored API key (used for endpoints that require API key authentication)
     * This is typically the "IntelliJ IDEA Plugin" API key created automatically
     */
    fun getStoredApiKey(): String? {
        val apiKey = getApiKey()
        return if (apiKey.isNotBlank()) apiKey else null
    }

    fun getProvisioningKey(): String {
        return if (EncryptionUtil.isEncrypted(settings.provisioningKey)) {
            EncryptionUtil.decrypt(settings.provisioningKey)
        } else {
            settings.provisioningKey
        }
    }

    fun setProvisioningKey(provisioningKey: String) {
        val encryptedKey = if (provisioningKey.isNotBlank()) {
            EncryptionUtil.encrypt(provisioningKey)
        } else {
            provisioningKey
        }

        // Modify the existing settings object directly (don't create a new copy)
        settings.provisioningKey = encryptedKey
        notifyStateChanged()
    }

    // TODO: Future version - Default model selection
    // fun getDefaultModel(): String = settings.defaultModel
    //
    // fun setDefaultModel(model: String) {
    //     settings.defaultModel = model
    // }

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

    fun isConfigured(): Boolean {
        val provisioningKey = getProvisioningKey()
        return provisioningKey.isNotBlank()
    }

    // Favorite Models Management
    fun getFavoriteModels(): List<String> {
        return settings.favoriteModels.toList()
    }

    fun addFavoriteModel(modelId: String) {
        if (!settings.favoriteModels.contains(modelId)) {
            settings.favoriteModels.add(modelId)
        }
    }

    fun removeFavoriteModel(modelId: String) {
        settings.favoriteModels.remove(modelId)
    }

    fun setFavoriteModels(models: List<String>) {
        settings.favoriteModels.clear()
        settings.favoriteModels.addAll(models)
    }

    fun isFavoriteModel(modelId: String): Boolean {
        return settings.favoriteModels.contains(modelId)
    }

    /**
     * Notify the platform that the state has changed and should be persisted.
     * Check if user has seen the welcome notification
     */
    fun hasSeenWelcome(): Boolean {
        return settings.hasSeenWelcome
    }

    /**
     * Mark that user has seen the welcome notification
     */
    fun setHasSeenWelcome(seen: Boolean) {
        settings.hasSeenWelcome = seen
        notifyStateChanged()
    }

    /**
     * Check if user has completed initial setup
     */
    fun hasCompletedSetup(): Boolean {
        return settings.hasCompletedSetup
    }

    /**
     * Mark that user has completed initial setup
     */
    fun setHasCompletedSetup(completed: Boolean) {
        settings.hasCompletedSetup = completed
        notifyStateChanged()
    }

    /**
     * This is necessary when settings are modified outside of the standard
     * Configurable apply flow (e.g., from dialogs or background operations).
     *
     * This method forces immediate synchronous persistence to ensure the state
     * is saved before any subsequent operations that might check for it.
     */
    private fun notifyStateChanged() {
        try {
            PluginLogger.Service.info("notifyStateChanged: About to call saveSettings()")
            // Force immediate state persistence
            // This is synchronous to ensure the state is saved before returning
            ApplicationManager.getApplication().saveSettings()
            PluginLogger.Service.info("Settings state persisted successfully")
        } catch (e: Exception) {
            PluginLogger.Service.warn("Failed to persist settings state", e)
        }
    }
}
