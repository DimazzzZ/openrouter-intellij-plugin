package org.zhavoronkov.openrouter.services

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import org.zhavoronkov.openrouter.models.DataRegion
import org.zhavoronkov.openrouter.models.OpenRouterSettings
import org.zhavoronkov.openrouter.models.PresetPair
import org.zhavoronkov.openrouter.services.settings.ApiKeySettingsManager
import org.zhavoronkov.openrouter.services.settings.FavoriteModelsManager
import org.zhavoronkov.openrouter.services.settings.OutputSchemasManager
import org.zhavoronkov.openrouter.services.settings.PresetsManager
import org.zhavoronkov.openrouter.services.settings.ProviderRoutingManager
import org.zhavoronkov.openrouter.services.settings.ProxySettingsManager
import org.zhavoronkov.openrouter.services.settings.RouterDefaultsManager
import org.zhavoronkov.openrouter.services.settings.SetupStateManager
import org.zhavoronkov.openrouter.services.settings.UIPreferencesManager
import org.zhavoronkov.openrouter.services.settings.WebSearchSettingsManager
import org.zhavoronkov.openrouter.utils.ExcludeFromCoverage
import org.zhavoronkov.openrouter.utils.ModelProviderUtils
import org.zhavoronkov.openrouter.utils.PasswordSafeKeyStorage
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
        @ExcludeFromCoverage(BYPASSED_SETTER)
        private set
    lateinit var proxyManager: ProxySettingsManager
        @ExcludeFromCoverage(BYPASSED_SETTER)
        private set
    lateinit var uiPreferencesManager: UIPreferencesManager
        @ExcludeFromCoverage(BYPASSED_SETTER)
        private set
    lateinit var setupStateManager: SetupStateManager
        @ExcludeFromCoverage(BYPASSED_SETTER)
        private set
    lateinit var favoriteModelsManager: FavoriteModelsManager
        @ExcludeFromCoverage(BYPASSED_SETTER)
        private set
    lateinit var presetsManager: PresetsManager

    lateinit var providerRoutingManager: ProviderRoutingManager
        @ExcludeFromCoverage(BYPASSED_SETTER)
        private set

    lateinit var routerDefaultsManager: RouterDefaultsManager
        @ExcludeFromCoverage(BYPASSED_SETTER)
        private set

    lateinit var webSearchManager: WebSearchSettingsManager
        @ExcludeFromCoverage(BYPASSED_SETTER)
        private set

    lateinit var outputSchemasManager: OutputSchemasManager
        @ExcludeFromCoverage(BYPASSED_SETTER)
        private set

    init {
        initializeManagers()
        // Warm the key cache in the background, before the first widget asks on the EDT
        PasswordSafeKeyStorage.preloadKeys()
    }

    private fun initializeManagers() {
        apiKeyManager = ApiKeySettingsManager(settings) { notifyStateChanged() }
        proxyManager = ProxySettingsManager(settings) { notifyStateChanged() }
        uiPreferencesManager = UIPreferencesManager(settings) { notifyStateChanged() }
        setupStateManager = SetupStateManager(settings) { notifyStateChanged() }
        favoriteModelsManager = FavoriteModelsManager(settings) { notifyStateChanged() }
        presetsManager = PresetsManager(settings) { notifyStateChanged() }
        providerRoutingManager = ProviderRoutingManager(settings) { notifyStateChanged() }
        routerDefaultsManager = RouterDefaultsManager(settings) { notifyStateChanged() }
        webSearchManager = WebSearchSettingsManager(settings) { notifyStateChanged() }
        outputSchemasManager = OutputSchemasManager(settings) { notifyStateChanged() }
    }

    companion object {
        fun getInstance(): OpenRouterSettingsService {
            return ApplicationManager.getApplication().getService(OpenRouterSettingsService::class.java)
        }

        private const val PROFILE_MARKER = "@profile/"

        /** The class writes these fields directly, so their generated private setters never run. */
        private const val BYPASSED_SETTER = "a private setter the class bypasses, writing the field directly"
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

        dropProfileFavorites()
        migrateDeprecatedFavoriteVariants()
    }

    /**
     * Development builds paired a model with a Profile, `<model>@profile/<name>`; presets replaced
     * Profiles, and OpenRouter has no such syntax, so those favourites cannot be sent.
     */
    private fun dropProfileFavorites() {
        val kept = settings.favoriteModels.filterNot { PROFILE_MARKER in it }
        if (kept.size == settings.favoriteModels.size) return
        PluginLogger.Service.info(
            "Migration: dropped ${settings.favoriteModels.size - kept.size} favorites paired with a Profile"
        )
        settings.favoriteModels = kept.toMutableList()
    }

    /**
     * Migration for v0.5.4: strip OpenRouter's retired variant suffixes
     * (:extended, :thinking, :online) from saved favorites. These suffixes are no
     * longer valid model IDs, so favorites carrying them are rewritten to the base
     * model. Duplicates that collapse into an existing favorite are dropped.
     */
    private fun migrateDeprecatedFavoriteVariants() {
        val favorites = settings.favoriteModels
        if (favorites.isEmpty()) {
            return
        }

        val migrated = LinkedHashSet<String>()
        var changed = false
        for (modelId in favorites) {
            // A pair's suffix follows its model's variant, so the model part is what is migrated
            val pair = PresetPair.parse(modelId)
            val model = ModelProviderUtils.stripDeprecatedVariant(pair?.model ?: modelId)
            val stripped = pair?.copy(model = model)?.id ?: model
            if (stripped != modelId) {
                changed = true
            }
            migrated.add(stripped)
        }

        if (changed) {
            PluginLogger.Service.info(
                "Migration: stripped deprecated variant suffixes from favorites " +
                    "(${favorites.size} → ${migrated.size} entries)"
            )
            settings.favoriteModels = migrated.toMutableList()
        }
    }

    // Convenience methods for frequently used operations
    fun isConfigured(): Boolean = apiKeyManager.isConfigured()

    fun getApiKey(): String = apiKeyManager.getApiKey()

    fun getProvisioningKey(): String = apiKeyManager.getProvisioningKey()

    /**
     * The data region every OpenRouter call is pinned to.
     *
     * Anything unrecognised - an older build reading a newer settings file, a hand-edited value,
     * a region OpenRouter has since renamed - reads as [DataRegion.GLOBAL]. Falling back to the
     * unpinned endpoint keeps the plugin working; the alternative, refusing to start over a
     * settings string, is worse for everyone except the one case where the region was load-bearing,
     * and that case surfaces immediately in the settings UI as "Global".
     */
    fun getDataRegion(): DataRegion =
        DataRegion.fromApiName(settings.dataRegion) ?: DataRegion.GLOBAL

    /**
     * Stores the region and tells everyone, which is what makes the change take effect.
     *
     * [notifyStateChanged] persists the settings and publishes the settings-changed topic; the
     * tool window answers that by refreshing the Status tab, and that refresh drops the analytics
     * query cache. Without the notification a region change would be a value nobody acted on
     * until the next restart.
     */
    fun setDataRegion(region: DataRegion) {
        if (settings.dataRegion == region.apiName) return

        settings.dataRegion = region.apiName
        notifyStateChanged()
    }

    /** Base URL for every OpenRouter call, resolved from the selected region. */
    fun getApiBaseUrl(): String = getDataRegion().baseUrl

    /**
     * This is necessary when settings are modified outside of the standard
     * Configurable apply flow (e.g., from dialogs or background operations).
     *
     * This method forces immediate synchronous persistence to ensure the state
     * is saved before any subsequent operations that might check for it.
     */
    private fun notifyStateChanged() = persistingQuietly {
        val application = ApplicationManager.getApplication()
        if (application != null) {
            PluginLogger.Service.info("notifyStateChanged: About to call saveSettings()")
            // Force immediate state persistence
            // This is synchronous to ensure the state is saved before returning
            application.saveSettings()
            PluginLogger.Service.info("Settings state persisted successfully")
            application.messageBus
                .syncPublisher(org.zhavoronkov.openrouter.listeners.OpenRouterSettingsListener.TOPIC)
                .onSettingsChanged()
        } else {
            PluginLogger.Service.info(
                "notifyStateChanged: Application is null (likely test environment), skipping saveSettings()"
            )
        }
    }

    /** Runs [persist], logging rather than throwing when the platform fails to save. */
    @ExcludeFromCoverage("only catches the platform's own save failing, which no test can make it do")
    private fun persistingQuietly(persist: () -> Unit) {
        try {
            persist()
        } catch (e: IllegalStateException) {
            PluginLogger.Service.warn("Failed to persist settings state", e)
        } catch (e: IllegalArgumentException) {
            PluginLogger.Service.warn("Failed to persist settings state", e)
        } catch (e: java.io.IOException) {
            PluginLogger.Service.warn("IO error persisting settings state", e)
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
