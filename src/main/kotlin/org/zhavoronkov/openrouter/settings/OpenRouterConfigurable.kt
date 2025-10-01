package org.zhavoronkov.openrouter.settings

import com.intellij.openapi.options.Configurable
import com.intellij.openapi.ui.Messages
import org.zhavoronkov.openrouter.services.OpenRouterService
import org.zhavoronkov.openrouter.services.OpenRouterSettingsService
import javax.swing.JComponent

/**
 * Configurable for OpenRouter plugin settings
 */
class OpenRouterConfigurable : Configurable {

    private var settingsPanel: OpenRouterSettingsPanel? = null
    private val settingsService = OpenRouterSettingsService.getInstance()
    private val openRouterService = OpenRouterService.getInstance()

    override fun getDisplayName(): String = "OpenRouter"

    override fun createComponent(): JComponent? {
        settingsPanel = OpenRouterSettingsPanel()

        // Load current settings into the panel BEFORE refreshing API keys
        val panel = settingsPanel ?: return null
        panel.setProvisioningKey(settingsService.getProvisioningKey())
        // TODO: Future version - Default model selection
        // panel.setDefaultModel(settingsService.getDefaultModel())
        panel.setAutoRefresh(settingsService.isAutoRefreshEnabled())
        panel.setRefreshInterval(settingsService.getRefreshInterval())
        panel.setShowCosts(settingsService.shouldShowCosts())
        // Convert favorite model IDs to model objects
        val favoriteModelIds = settingsService.getFavoriteModels()
        val favoriteModels = favoriteModelIds.map { modelId ->
            org.zhavoronkov.openrouter.models.OpenRouterModelInfo(
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
        panel.setFavoriteModels(favoriteModels)

        // Now refresh API keys with the loaded provisioning key
        panel.refreshApiKeys()

        return panel.getPanel()
    }

    override fun isModified(): Boolean {
        val panel = settingsPanel ?: return false

        return panel.getProvisioningKey() != settingsService.getProvisioningKey() ||
            // TODO: Future version - Default model selection
            // panel.getDefaultModel() != settingsService.getDefaultModel() ||
            panel.isAutoRefreshEnabled() != settingsService.isAutoRefreshEnabled() ||
            panel.getRefreshInterval() != settingsService.getRefreshInterval() ||
            panel.shouldShowCosts() != settingsService.shouldShowCosts() ||
            panel.isFavoriteModelsModified()
    }

    override fun apply() {
        val panel = settingsPanel ?: return

        val oldProvisioningKey = settingsService.getProvisioningKey()
        val newProvisioningKey = panel.getProvisioningKey()

        // Update settings
        settingsService.setProvisioningKey(newProvisioningKey)
        // TODO: Future version - Default model selection
        // settingsService.setDefaultModel(panel.getDefaultModel())
        settingsService.setAutoRefresh(panel.isAutoRefreshEnabled())
        settingsService.setRefreshInterval(panel.getRefreshInterval())
        settingsService.setShowCosts(panel.shouldShowCosts())
        // Convert favorite models to model IDs for storage
        val favoriteModelIds = panel.getFavoriteModels().map { it.id }
        settingsService.setFavoriteModels(favoriteModelIds)

        // Refresh API keys table if provisioning key changed
        if (oldProvisioningKey != newProvisioningKey) {
            panel.refreshApiKeys()

            // If this is the first time setting a provisioning key, the refreshApiKeys will automatically
            // create the IntelliJ IDEA Plugin API key if it doesn't exist
        }

        // Test connection if provisioning key changed
        if (oldProvisioningKey != newProvisioningKey && newProvisioningKey.isNotBlank()) {
            testConnection()
        }
    }

    override fun reset() {
        val panel = settingsPanel ?: return

        panel.setProvisioningKey(settingsService.getProvisioningKey())
        // TODO: Future version - Default model selection
        // panel.setDefaultModel(settingsService.getDefaultModel())
        panel.setAutoRefresh(settingsService.isAutoRefreshEnabled())
        panel.setRefreshInterval(settingsService.getRefreshInterval())
        panel.setShowCosts(settingsService.shouldShowCosts())
        // Convert favorite model IDs to model objects
        val favoriteModelIds = settingsService.getFavoriteModels()
        val favoriteModels = favoriteModelIds.map { modelId ->
            org.zhavoronkov.openrouter.models.OpenRouterModelInfo(
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
        panel.setFavoriteModels(favoriteModels)
        panel.refreshApiKeys() // Refresh API keys when resetting
    }

    override fun disposeUIResources() {
        settingsPanel = null
    }

    private fun testConnection() {
        openRouterService.testConnection().thenAccept { success ->
            if (success) {
                Messages.showInfoMessage(
                    "Successfully connected to OpenRouter API!",
                    "Connection Test"
                )
            } else {
                Messages.showErrorDialog(
                    "Failed to connect to OpenRouter API. Please check your API key.",
                    "Connection Test Failed"
                )
            }
        }
    }
}
