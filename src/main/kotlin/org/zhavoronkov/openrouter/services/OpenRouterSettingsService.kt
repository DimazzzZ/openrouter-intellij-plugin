@file:Suppress("TooGenericExceptionCaught")

package org.zhavoronkov.openrouter.services

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import org.zhavoronkov.openrouter.models.OpenRouterSettings
import org.zhavoronkov.openrouter.services.settings.ApiKeySettingsManager
import org.zhavoronkov.openrouter.services.settings.FavoriteModelsManager
import org.zhavoronkov.openrouter.services.settings.ProxySettingsManager
import org.zhavoronkov.openrouter.services.settings.SetupStateManager
import org.zhavoronkov.openrouter.services.settings.UIPreferencesManager
import org.zhavoronkov.openrouter.utils.PluginLogger

/**
 * Service for managing OpenRouter plugin settings
 * Implements Disposable for dynamic plugin support
 */
@State(
    name = "OpenRouterSettings",
    storages = [Storage("openrouter.xml")]
)
class OpenRouterSettingsService : PersistentStateComponent<OpenRouterSettings>, Disposable {

    private var settings = OpenRouterSettings()

    lateinit var apiKeyManager: ApiKeySettingsManager
        private set
    lateinit var proxyManager: ProxySettingsManager
        private set
    lateinit var uiPreferencesManager: UIPreferencesManager
        private set
    lateinit var setupStateManager: SetupStateManager
        private set
    lateinit var favoriteModelsManager: FavoriteModelsManager
        private set

    init {
        initializeManagers()
    }

    private fun initializeManagers() {
        apiKeyManager = ApiKeySettingsManager(settings) { notifyStateChanged() }
        proxyManager = ProxySettingsManager(settings) { notifyStateChanged() }
        uiPreferencesManager = UIPreferencesManager(settings) { notifyStateChanged() }
        setupStateManager = SetupStateManager(settings) { notifyStateChanged() }
        favoriteModelsManager = FavoriteModelsManager(settings) { notifyStateChanged() }
    }

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
        migrateSettingsIfNeeded()
        initializeManagers()
    }

    /**
     * Migrate settings from previous versions to ensure compatibility
     */
    private fun migrateSettingsIfNeeded() {
        // Migration for v0.4.0: Detect existing provisioning keys and set authScope to EXTENDED
        // This fixes the issue where users upgrading from v0.3.0 had their settings "reset"
        // because authScope defaulted to REGULAR
        if (settings.provisioningKey.isNotBlank() &&
            settings.authScope == org.zhavoronkov.openrouter.models.AuthScope.REGULAR
        ) {
            PluginLogger.Service.info("Migration: Detected existing provisioning key, setting authScope to EXTENDED")
            settings.authScope = org.zhavoronkov.openrouter.models.AuthScope.EXTENDED
        }
    }

    // Convenience methods for frequently used operations
    fun isConfigured(): Boolean = apiKeyManager.isConfigured()

    fun getApiKey(): String = apiKeyManager.getApiKey()

    fun getProvisioningKey(): String = apiKeyManager.getProvisioningKey()

    /**
     * This is necessary when settings are modified outside of the standard
     * Configurable apply flow (e.g., from dialogs or background operations).
     *
     * This method forces immediate synchronous persistence to ensure the state
     * is saved before any subsequent operations that might check for it.
     */
    private fun notifyStateChanged() {
        try {
            val application = ApplicationManager.getApplication()
            if (application != null) {
                PluginLogger.Service.info("notifyStateChanged: About to call saveSettings()")
                // Force immediate state persistence
                // This is synchronous to ensure the state is saved before returning
                application.saveSettings()
                PluginLogger.Service.info("Settings state persisted successfully")
            } else {
                PluginLogger.Service.info(
                    "notifyStateChanged: Application is null (likely test environment), skipping saveSettings()"
                )
            }

            // Notify listeners about settings change
            application?.messageBus?.syncPublisher(
                org.zhavoronkov.openrouter.listeners.OpenRouterSettingsListener.TOPIC
            )?.onSettingsChanged()
        } catch (e: IllegalStateException) {
            PluginLogger.Service.warn("Failed to persist settings state", e)
        } catch (e: Exception) {
            PluginLogger.Service.warn("Failed to persist settings state", e)
        }
    }

    /**
     * Dispose method for dynamic plugin support
     * Note: We don't call saveSettings() here because dispose() is called inside a write action
     * during plugin unload, and saveSettings() triggers AWT events which are not allowed.
     * The IntelliJ Platform automatically saves settings when needed.
     */
    override fun dispose() {
        PluginLogger.Service.info("Disposing OpenRouterSettingsService")
        // No explicit cleanup needed - settings are automatically persisted by the platform
        PluginLogger.Service.info("OpenRouterSettingsService disposed successfully")
    }
}
