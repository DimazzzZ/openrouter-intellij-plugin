package org.zhavoronkov.openrouter.settings

import com.intellij.openapi.options.Configurable
import com.intellij.openapi.ui.Messages
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.zhavoronkov.openrouter.models.ApiResult
import org.zhavoronkov.openrouter.services.OpenRouterService
import org.zhavoronkov.openrouter.services.OpenRouterSettingsService
import javax.swing.JComponent

/**
 * Configurable for OpenRouter plugin settings
 */
class OpenRouterConfigurable : Configurable {

    companion object {
        private const val PREFERRED_DEFAULT_MAX_TOKENS = 8000
    }

    private var settingsPanel: OpenRouterSettingsPanel? = null
    private val settingsService = OpenRouterSettingsService.getInstance()
    private val openRouterService = OpenRouterService.getInstance()

    /**
     * Loads current settings from the service into the panel
     */
    private fun loadSettingsIntoPanel(panel: OpenRouterSettingsPanel) {
        panel.setProvisioningKey(settingsService.getProvisioningKey())
        panel.setAutoRefresh(settingsService.isAutoRefreshEnabled())
        panel.setRefreshInterval(settingsService.getRefreshInterval())
        panel.setShowCosts(settingsService.shouldShowCosts())
        setPanelDefaultMaxTokens(panel)
        setPanelProxySettings(panel)
    }

    /**
     * Saves settings from the panel to the service
     */
    private fun saveSettingsFromPanel(panel: OpenRouterSettingsPanel) {
        settingsService.setProvisioningKey(panel.getProvisioningKey())
        settingsService.setAutoRefresh(panel.isAutoRefreshEnabled())
        settingsService.setRefreshInterval(panel.getRefreshInterval())
        settingsService.setShowCosts(panel.shouldShowCosts())
        savePanelDefaultMaxTokens(panel)
        savePanelProxySettings(panel)
    }

    /**
     * Sets the panel's default max tokens configuration based on service state
     */
    private fun setPanelDefaultMaxTokens(panel: OpenRouterSettingsPanel) {
        val currentMaxTokens = settingsService.getDefaultMaxTokens()
        panel.setDefaultMaxTokensEnabled(currentMaxTokens > 0)
        panel.setDefaultMaxTokens(currentMaxTokens.takeIf { it > 0 } ?: PREFERRED_DEFAULT_MAX_TOKENS)
    }

    /**
     * Saves the default max tokens setting from panel to service
     */
    private fun savePanelDefaultMaxTokens(panel: OpenRouterSettingsPanel) {
        val maxTokensValue = if (panel.isDefaultMaxTokensEnabled()) {
            panel.getDefaultMaxTokens()
        } else {
            0 // 0 indicates disabled feature
        }
        settingsService.setDefaultMaxTokens(maxTokensValue)
    }

    /**
     * Sets the panel's proxy configuration based on service state
     */
    private fun setPanelProxySettings(panel: OpenRouterSettingsPanel) {
        panel.setProxyAutoStart(settingsService.isProxyAutoStartEnabled())

        val configuredPort = settingsService.getProxyPort()
        panel.setUseSpecificPort(configuredPort > 0)
        panel.setProxyPort(if (configuredPort > 0) configuredPort else 8080)

        panel.setProxyPortRangeStart(settingsService.getProxyPortRangeStart())
        panel.setProxyPortRangeEnd(settingsService.getProxyPortRangeEnd())
    }

    /**
     * Saves proxy settings from panel to service
     */
    private fun savePanelProxySettings(panel: OpenRouterSettingsPanel) {
        settingsService.setProxyAutoStart(panel.getProxyAutoStart())

        val port = if (panel.getUseSpecificPort()) {
            panel.getProxyPort()
        } else {
            0 // 0 means auto-select from range
        }
        settingsService.setProxyPort(port)

        settingsService.setProxyPortRange(
            panel.getProxyPortRangeStart(),
            panel.getProxyPortRangeEnd()
        )
    }

    /**
     * Checks if proxy settings have been modified
     */
    private fun isProxySettingsModified(panel: OpenRouterSettingsPanel): Boolean {
        val currentAutoStart = settingsService.isProxyAutoStartEnabled()
        val currentPort = settingsService.getProxyPort()
        val currentRangeStart = settingsService.getProxyPortRangeStart()
        val currentRangeEnd = settingsService.getProxyPortRangeEnd()

        val panelAutoStart = panel.getProxyAutoStart()
        val panelUseSpecificPort = panel.getUseSpecificPort()
        val panelPort = if (panelUseSpecificPort) panel.getProxyPort() else 0
        val panelRangeStart = panel.getProxyPortRangeStart()
        val panelRangeEnd = panel.getProxyPortRangeEnd()

        return currentAutoStart != panelAutoStart ||
            currentPort != panelPort ||
            currentRangeStart != panelRangeStart ||
            currentRangeEnd != panelRangeEnd
    }

    override fun getDisplayName(): String = "OpenRouter"

    override fun createComponent(): JComponent? {
        settingsPanel = OpenRouterSettingsPanel()

        // Load current settings into the panel
        // Note: setProvisioningKey() will automatically load API keys (with caching)
        val panel = settingsPanel ?: return null
        loadSettingsIntoPanel(panel)

        return panel.getPanel()
    }

    override fun isModified(): Boolean {
        val panel = settingsPanel ?: return false

        // Check if max tokens setting changed
        val currentMaxTokensEnabled = settingsService.getDefaultMaxTokens() > 0
        val currentMaxTokensValue = settingsService.getDefaultMaxTokens()
        val panelMaxTokensEnabled = panel.isDefaultMaxTokensEnabled()
        val panelMaxTokensValue = panel.getDefaultMaxTokens()

        val maxTokensChanged = when {
            // Both enabled: check if value changed
            currentMaxTokensEnabled && panelMaxTokensEnabled -> panelMaxTokensValue != currentMaxTokensValue
            // Panel enabled but current disabled: always modified
            panelMaxTokensEnabled && !currentMaxTokensEnabled -> true
            // Current enabled but panel disabled: always modified
            !panelMaxTokensEnabled && currentMaxTokensEnabled -> true
            // Both disabled: no change
            else -> false
        }

        return panel.getProvisioningKey() != settingsService.getProvisioningKey() ||
            panel.isAutoRefreshEnabled() != settingsService.isAutoRefreshEnabled() ||
            panel.getRefreshInterval() != settingsService.getRefreshInterval() ||
            panel.shouldShowCosts() != settingsService.shouldShowCosts() ||
            maxTokensChanged ||
            isProxySettingsModified(panel)
    }

    override fun apply() {
        val panel = settingsPanel ?: return

        val oldProvisioningKey = settingsService.getProvisioningKey()
        val newProvisioningKey = panel.getProvisioningKey()

        // Update all settings from panel to service
        saveSettingsFromPanel(panel)

        // Refresh API keys table if provisioning key changed (force refresh to bypass cache)
        if (oldProvisioningKey != newProvisioningKey) {
            panel.refreshApiKeys(forceRefresh = true)

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

        // Load all current settings back into the panel
        loadSettingsIntoPanel(panel)

        // REMOVED: panel.refreshApiKeys() - No longer needed!
        // setProvisioningKey() already loads API keys with caching
    }

    override fun disposeUIResources() {
        settingsPanel = null
    }

    private fun testConnection() {
        val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
        scope.launch {
            val result = openRouterService.testConnection()
            when (result) {
                is ApiResult.Success -> {
                    if (result.data) {
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
                is ApiResult.Error -> {
                    Messages.showErrorDialog(
                        "Failed to connect to OpenRouter API: ${result.message}",
                        "Connection Test Failed"
                    )
                }
            }
        }
    }
}
