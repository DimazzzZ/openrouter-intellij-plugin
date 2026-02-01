package org.zhavoronkov.openrouter.settings

import com.intellij.openapi.options.Configurable
import org.zhavoronkov.openrouter.services.OpenRouterSettingsService
import org.zhavoronkov.openrouter.utils.PluginLogger
import javax.swing.JComponent

/**
 * Configurable for OpenRouter plugin settings
 */
@Suppress("TooManyFunctions")
class OpenRouterConfigurable : Configurable {

    companion object {
        private const val PREFERRED_DEFAULT_MAX_TOKENS = 8000
        private const val DEFAULT_PROXY_PORT = 8080
    }

    private var settingsPanel: OpenRouterSettingsPanel? = null
    private val settingsService = OpenRouterSettingsService.getInstance()

    /**
     * Synchronizes settings between panel and service
     */
    private fun syncSettings(panel: OpenRouterSettingsPanel, toService: Boolean) {
        if (toService) {
            settingsService.uiPreferencesManager.autoRefresh = panel.isAutoRefreshEnabled()
            settingsService.uiPreferencesManager.refreshInterval = panel.getRefreshInterval()
            settingsService.uiPreferencesManager.showCosts = panel.shouldShowCosts()
        } else {
            panel.setAutoRefresh(settingsService.uiPreferencesManager.autoRefresh)
            panel.setRefreshInterval(settingsService.uiPreferencesManager.refreshInterval)
            panel.setShowCosts(settingsService.uiPreferencesManager.showCosts)
        }
        syncDefaultMaxTokens(panel, toService)
        syncProxySettings(panel, toService)
        syncAuthenticationSettings(panel, toService)
    }

    /**
     * Synchronizes authentication settings (API key or provisioning key) between panel and service
     */
    private fun syncAuthenticationSettings(panel: OpenRouterSettingsPanel, toService: Boolean) {
        if (!toService) {
            // Load authentication settings into panel
            // Note: setProvisioningKey() will automatically load API keys (with caching)
            val authScope = settingsService.apiKeyManager.authScope
            if (authScope == org.zhavoronkov.openrouter.models.AuthScope.REGULAR) {
                panel.setApiKey(settingsService.apiKeyManager.getApiKey())
            } else {
                panel.setProvisioningKey(settingsService.apiKeyManager.getProvisioningKey())
            }
        }
        // Note: We don't sync TO service here because authentication is managed through the Setup Wizard
        // and should not be changed directly in the settings panel
    }

    /**
     * Synchronizes default max tokens setting between panel and service
     */
    private fun syncDefaultMaxTokens(panel: OpenRouterSettingsPanel, toService: Boolean) {
        if (toService) {
            val maxTokensValue = if (panel.isDefaultMaxTokensEnabled()) {
                panel.getDefaultMaxTokens()
            } else {
                0 // 0 indicates disabled feature
            }
            settingsService.uiPreferencesManager.defaultMaxTokens = maxTokensValue
        } else {
            val currentMaxTokens = settingsService.uiPreferencesManager.defaultMaxTokens
            panel.setDefaultMaxTokensEnabled(currentMaxTokens > 0)
            panel.setDefaultMaxTokens(currentMaxTokens.takeIf { it > 0 } ?: PREFERRED_DEFAULT_MAX_TOKENS)
        }
    }

    /**
     * Synchronizes proxy settings between panel and service
     */
    private fun syncProxySettings(panel: OpenRouterSettingsPanel, toService: Boolean) {
        if (toService) {
            settingsService.proxyManager.setProxyAutoStart(panel.getProxyAutoStart())
            val port = if (panel.getUseSpecificPort()) panel.getProxyPort() else 0
            settingsService.proxyManager.setProxyPort(port)
            settingsService.proxyManager.setProxyPortRange(
                panel.getProxyPortRangeStart(),
                panel.getProxyPortRangeEnd()
            )
        } else {
            panel.setProxyAutoStart(settingsService.proxyManager.isProxyAutoStartEnabled())
            val configuredPort = settingsService.proxyManager.getProxyPort()
            panel.setUseSpecificPort(configuredPort > 0)
            panel.setProxyPort(if (configuredPort > 0) configuredPort else DEFAULT_PROXY_PORT)
            panel.setProxyPortRangeStart(settingsService.proxyManager.getProxyPortRangeStart())
            panel.setProxyPortRangeEnd(settingsService.proxyManager.getProxyPortRangeEnd())
        }
    }

    override fun getDisplayName(): String = "OpenRouter"

    @Suppress("TooGenericExceptionCaught", "TooGenericExceptionThrown")
    override fun createComponent(): JComponent? {
        PluginLogger.Settings.info("OpenRouterConfigurable: createComponent called")
        try {
            settingsPanel = OpenRouterSettingsPanel()

            // Load current settings into the panel
            // Note: setProvisioningKey() will automatically load API keys (with caching)
            val panel = settingsPanel ?: return null
            syncSettings(panel, toService = false)

            PluginLogger.Settings.info("OpenRouterConfigurable: panel created successfully")
            return panel.getPanel()
        } catch (e: RuntimeException) {
            PluginLogger.Settings.error("OpenRouterConfigurable: failed to create component", e)
            throw e
        } catch (e: IllegalStateException) {
            PluginLogger.Settings.error("OpenRouterConfigurable: unexpected error", e)
            throw RuntimeException(e)
        }
    }

    override fun isModified(): Boolean {
        val panel = settingsPanel ?: return false

        return panel.isAutoRefreshEnabled() != settingsService.uiPreferencesManager.autoRefresh ||
            panel.getRefreshInterval() != settingsService.uiPreferencesManager.refreshInterval ||
            panel.shouldShowCosts() != settingsService.uiPreferencesManager.showCosts ||
            isSettingModified(panel, SettingType.DEFAULT_MAX_TOKENS) ||
            isSettingModified(panel, SettingType.PROXY_SETTINGS)
    }

    /**
     * Checks if a specific setting has been modified
     */
    private fun isSettingModified(panel: OpenRouterSettingsPanel, settingType: SettingType): Boolean {
        return when (settingType) {
            SettingType.DEFAULT_MAX_TOKENS -> {
                val currentMaxTokensEnabled = settingsService.uiPreferencesManager.defaultMaxTokens > 0
                val currentMaxTokensValue = settingsService.uiPreferencesManager.defaultMaxTokens
                val panelMaxTokensEnabled = panel.isDefaultMaxTokensEnabled()
                val panelMaxTokensValue = panel.getDefaultMaxTokens()

                when {
                    currentMaxTokensEnabled && panelMaxTokensEnabled -> panelMaxTokensValue != currentMaxTokensValue
                    panelMaxTokensEnabled -> true
                    !panelMaxTokensEnabled && currentMaxTokensEnabled -> true
                    else -> false
                }
            }
            SettingType.PROXY_SETTINGS -> {
                val currentAutoStart = settingsService.proxyManager.isProxyAutoStartEnabled()
                val currentPort = settingsService.proxyManager.getProxyPort()
                val currentRangeStart = settingsService.proxyManager.getProxyPortRangeStart()
                val currentRangeEnd = settingsService.proxyManager.getProxyPortRangeEnd()

                val panelAutoStart = panel.getProxyAutoStart()
                val panelUseSpecificPort = panel.getUseSpecificPort()
                val panelPort = if (panelUseSpecificPort) panel.getProxyPort() else 0
                val panelRangeStart = panel.getProxyPortRangeStart()
                val panelRangeEnd = panel.getProxyPortRangeEnd()

                currentAutoStart != panelAutoStart ||
                    currentPort != panelPort ||
                    currentRangeStart != panelRangeStart ||
                    currentRangeEnd != panelRangeEnd
            }
        }
    }

    /**
     * Enum for different setting types
     */
    private enum class SettingType {
        DEFAULT_MAX_TOKENS,
        PROXY_SETTINGS
    }

    override fun apply() {
        val panel = settingsPanel ?: return

        // Update all settings from panel to service
        syncSettings(panel, toService = true)

        // Update proxy status to reflect current configuration and server state
        panel.updateProxyStatus()
    }

    override fun reset() {
        val panel = settingsPanel ?: return

        // Load all current settings back into the panel
        syncSettings(panel, toService = false)

        // REMOVED: panel.refreshApiKeys() - No longer needed!
        // setProvisioningKey() already loads API keys with caching
    }

    override fun disposeUIResources() {
        settingsPanel = null
    }
}
