package org.zhavoronkov.openrouter.settings

import com.intellij.openapi.options.Configurable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.zhavoronkov.openrouter.models.ApiResult
import org.zhavoronkov.openrouter.models.DataRegion
import org.zhavoronkov.openrouter.models.RegionFavorites
import org.zhavoronkov.openrouter.services.DataRegionAvailability
import org.zhavoronkov.openrouter.services.FavoriteModelsService
import org.zhavoronkov.openrouter.services.OpenRouterService
import org.zhavoronkov.openrouter.services.OpenRouterSettingsService
import org.zhavoronkov.openrouter.services.OpenRouterStatsCache
import org.zhavoronkov.openrouter.utils.PluginLogger
import javax.swing.JComponent

/**
 * Configurable for OpenRouter plugin settings
 */
@Suppress("TooManyFunctions")
class OpenRouterConfigurable : Configurable {

    companion object {
        private const val PREFERRED_DEFAULT_MAX_TOKENS = 8000

        // Fallback shown in the settings field when no port is configured yet.
        // Must match the runtime default (SetupWizardConfig.DEFAULT_PROXY_PORT /
        // OpenRouterSettings.proxyPortRangeStart = 8880), NOT the doc-only
        // OpenRouterConstants.DEFAULT_PROXY_PORT (8080).
        private const val DEFAULT_PROXY_PORT = 8880
    }

    internal var settingsPanel: OpenRouterSettingsPanel? = null
        private set
    private val settingsService = OpenRouterSettingsService.getInstance()

    // Cancelled in disposeUIResources: the lookup outlives nothing, and a settings page closed
    // mid-request must not come back to touch a panel that is gone.
    private val regionScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    /**
     * Asks both keys which data regions they allow and narrows the selector to the answer.
     *
     * Off the EDT because it makes two network calls, and back onto it to touch Swing. Failure is
     * not reported to the user: [DataRegionAvailability] already answers "Global only" when it
     * cannot tell, which is what the control would show anyway, and a modal complaint about a
     * paid feature the account probably does not have would be noise.
     */
    private fun loadAvailableDataRegions(panel: OpenRouterSettingsPanel) {
        regionScope.launch {
            val regions = DataRegionAvailability(OpenRouterService.getInstance(), settingsService).load()
            withContext(Dispatchers.Main) {
                panel.setAvailableDataRegions(regions)
            }
        }
    }

    /**
     * Stores the chosen region and drops everything cached against the old one.
     *
     * The caches are the reason this is not a plain setter. A region serves a different catalogue
     * - 66 models in the EU against 458 globally - and the model list, the analytics answers and
     * the status-bar figures are all cached for minutes. Without this, changing region would
     * leave the favorites page marking models unavailable, or available, according to the region
     * the user just left, and the status bar quoting figures fetched from it.
     *
     * Only on an actual change: clearing caches on every Apply would throw away work for nothing.
     *
     * The analytics cache is not cleared here because it is not reachable from here - each holder
     * builds its own AnalyticsService. It does not need to be: setDataRegion publishes the
     * settings-changed topic, the tool window refreshes the Status tab on it, and that refresh
     * invalidates the analytics cache itself.
     */
    private fun applyDataRegion(region: DataRegion) {
        if (region == settingsService.getDataRegion()) return

        settingsService.setDataRegion(region)
        FavoriteModelsService.getInstance().clearCache()
        OpenRouterStatsCache.getInstance().clearCache()
    }

    /**
     * Tells the user what the region they just picked would cost them in favorite models.
     *
     * The region's catalogue is asked through the global host rather than the regional one, so
     * this works before the choice is applied - which is the point. A failed fetch counts as
     * nothing missing rather than as an error: this is a hint attached to a decision in progress,
     * and an unanswered catalog is not evidence that anything is unavailable.
     */
    private fun loadFavoritesImpact(panel: OpenRouterSettingsPanel, region: DataRegion) {
        regionScope.launch {
            val favorites = settingsService.favoriteModelsManager.getFavoriteModels()
            val unavailable = when (val models = OpenRouterService.getInstance().getModelsInRegion(region)) {
                is ApiResult.Success -> RegionFavorites.unavailable(favorites, models.data.data.map { it.id }).size
                is ApiResult.Error -> 0
            }
            withContext(Dispatchers.Main) {
                panel.setDataRegionFavoritesImpact(region, unavailable, favorites.size)
            }
        }
    }

    /**
     * Synchronizes settings between panel and service
     */
    private fun syncSettings(panel: OpenRouterSettingsPanel, toService: Boolean) {
        if (toService) {
            settingsService.uiPreferencesManager.autoRefresh = panel.isAutoRefreshEnabled()
            settingsService.uiPreferencesManager.refreshInterval = panel.getRefreshInterval()
            settingsService.uiPreferencesManager.showCosts = panel.shouldShowCosts()
            settingsService.uiPreferencesManager.requestWarningBalloons = panel.requests.warningBalloons.isSelected
            settingsService.uiPreferencesManager.requestLogLimit = panel.requests.limit.number
            settingsService.uiPreferencesManager.balanceProviderEnabled = panel.isBalanceProviderEnabled()
            applyDataRegion(panel.getDataRegion())
        } else {
            panel.setAutoRefresh(settingsService.uiPreferencesManager.autoRefresh)
            panel.setRefreshInterval(settingsService.uiPreferencesManager.refreshInterval)
            panel.setShowCosts(settingsService.uiPreferencesManager.showCosts)
            panel.requests.warningBalloons.isSelected = settingsService.uiPreferencesManager.requestWarningBalloons
            panel.requests.limit.number = settingsService.uiPreferencesManager.requestLogLimit
            panel.setBalanceProviderEnabled(settingsService.uiPreferencesManager.balanceProviderEnabled)
            panel.setDataRegion(settingsService.getDataRegion())
            panel.onDataRegionChosen { region -> loadFavoritesImpact(panel, region) }
            loadAvailableDataRegions(panel)
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
            panel.requests.warningBalloons.isSelected != settingsService.uiPreferencesManager.requestWarningBalloons ||
            panel.requests.limit.number != settingsService.uiPreferencesManager.requestLogLimit ||
            panel.isBalanceProviderEnabled() != settingsService.uiPreferencesManager.balanceProviderEnabled ||
            isSettingModified(panel, SettingType.DEFAULT_MAX_TOKENS) ||
            isSettingModified(panel, SettingType.PROXY_SETTINGS) ||
            panel.getDataRegion() != settingsService.getDataRegion()
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
        regionScope.coroutineContext.cancelChildren()
        settingsPanel = null
    }
}
