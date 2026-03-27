package org.zhavoronkov.openrouter.services

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.extensions.ExtensionPointName
import org.jetbrains.annotations.ApiStatus
import org.zhavoronkov.openrouter.api.BalanceData
import org.zhavoronkov.openrouter.api.BalanceProvider
import org.zhavoronkov.openrouter.utils.PluginLogger

/**
 * Service responsible for notifying registered [BalanceProvider] implementations
 * about balance updates, loading states, and errors.
 *
 * ## Security Features
 * - **Configurable**: Users can disable balance sharing in Settings → Tools → OpenRouter
 * - **Exception Isolation**: Exceptions from individual providers are caught and logged,
 *   preventing one misbehaving provider from affecting others
 * - **Immutable Data**: Only immutable [BalanceData] objects are shared with providers
 *
 * ## Usage
 * This service is used internally by [OpenRouterStatsCache] to push balance updates
 * to all registered extension point implementations.
 *
 * ```kotlin
 * val notifier = BalanceProviderNotifier.getInstance()
 * notifier.notifyBalanceUpdated(balanceData)
 * ```
 *
 * @see BalanceProvider
 * @see BalanceData
 * @since 0.6.0
 */
@ApiStatus.Internal
@Service(Service.Level.APP)
class BalanceProviderNotifier : Disposable {

    companion object {
        /**
         * Extension point name for balance providers.
         * Fully qualified name: org.zhavoronkov.openrouter.balanceProvider
         */
        private val EP_NAME = ExtensionPointName.create<BalanceProvider>(
            "org.zhavoronkov.openrouter.balanceProvider"
        )

        /**
         * Gets the singleton instance of this service.
         *
         * @return The BalanceProviderNotifier instance
         * @throws IllegalStateException if the application is not initialized
         */
        fun getInstance(): BalanceProviderNotifier {
            return ApplicationManager.getApplication().getService(BalanceProviderNotifier::class.java)
        }

        /**
         * Safely gets the instance, returning null if the application is not available.
         * Useful for testing and shutdown scenarios.
         *
         * @return The BalanceProviderNotifier instance, or null if unavailable
         */
        fun getInstanceOrNull(): BalanceProviderNotifier? {
            return try {
                val app = ApplicationManager.getApplication() ?: return null
                app.getService(BalanceProviderNotifier::class.java)
            } catch (expected: IllegalStateException) {
                PluginLogger.Service.debug("BalanceProviderNotifier not available: ${expected.message}")
                null
            }
        }
    }

    /**
     * Notifies all registered providers that balance data has been updated.
     *
     * This method:
     * - Checks if the feature is enabled in settings
     * - Iterates through all registered providers
     * - Catches and logs exceptions from individual providers
     *
     * @param data The updated balance data to send to providers
     */
    fun notifyBalanceUpdated(data: BalanceData) {
        if (!isEnabled()) {
            PluginLogger.Service.debug("Balance provider notifications disabled, skipping update")
            return
        }

        val providers = getProviders()
        if (providers.isEmpty()) {
            PluginLogger.Service.debug("No balance providers registered")
            return
        }

        PluginLogger.Service.info("Notifying ${providers.size} balance provider(s) of update")

        providers.forEach { provider ->
            safeInvoke(provider, "onBalanceUpdated") {
                provider.onBalanceUpdated(data)
            }
        }
    }

    /**
     * Notifies all registered providers that balance loading has started.
     */
    fun notifyLoading() {
        if (!isEnabled()) return

        getProviders().forEach { provider ->
            safeInvoke(provider, "onBalanceLoading") {
                provider.onBalanceLoading()
            }
        }
    }

    /**
     * Notifies all registered providers that balance loading has failed.
     *
     * @param error Human-readable error message
     */
    fun notifyError(error: String) {
        if (!isEnabled()) return

        val providers = getProviders()
        if (providers.isEmpty()) return

        PluginLogger.Service.debug("Notifying ${providers.size} balance provider(s) of error: $error")

        providers.forEach { provider ->
            safeInvoke(provider, "onBalanceError") {
                provider.onBalanceError(error)
            }
        }
    }

    /**
     * Checks if any balance providers are registered.
     * This method is intended for debugging and diagnostics.
     *
     * @return true if at least one provider is registered
     */
    @Suppress("unused") // API for debugging
    fun hasProviders(): Boolean {
        return try {
            EP_NAME.hasAnyExtensions()
        } catch (expected: IllegalStateException) {
            PluginLogger.Service.debug("Cannot check for providers: ${expected.message}")
            false
        }
    }

    /**
     * Gets the count of registered balance providers.
     * This method is intended for debugging and diagnostics.
     *
     * @return Number of registered providers
     */
    @Suppress("unused") // API for debugging
    fun getProviderCount(): Int {
        return getProviders().size
    }

    /**
     * Gets the list of registered provider class names.
     * This method is intended for debugging and diagnostics.
     *
     * @return List of provider class names
     */
    @Suppress("unused") // API for debugging
    fun getProviderNames(): List<String> {
        return getProviders().map { it.javaClass.name }
    }

    /**
     * Checks if balance provider notifications are enabled.
     *
     * The feature is controlled by the `balanceProviderEnabled` setting.
     * Defaults to enabled if settings are unavailable.
     *
     * @return true if notifications should be sent to providers
     */
    private fun isEnabled(): Boolean {
        return try {
            OpenRouterSettingsService.getInstance()
                .uiPreferencesManager.balanceProviderEnabled
        } catch (_: IllegalStateException) {
            PluginLogger.Service.debug("Settings service unavailable, defaulting to enabled")
            true // Default to enabled if settings service is not available
        }
    }

    /**
     * Gets the list of registered providers.
     *
     * @return List of BalanceProvider instances, empty if unavailable
     */
    private fun getProviders(): List<BalanceProvider> {
        return try {
            EP_NAME.extensionList
        } catch (_: IllegalStateException) {
            // This is expected during plugin shutdown or when extension points are not available
            PluginLogger.Service.debug("Cannot get extension list (expected during shutdown)")
            emptyList()
        }
    }

    /**
     * Safely invokes a method on a provider, catching and logging any exceptions.
     *
     * This ensures that one misbehaving provider cannot prevent other providers
     * from receiving notifications.
     *
     * @param provider The provider being invoked
     * @param methodName Name of the method (for logging)
     * @param block The code to execute
     */
    @Suppress("TooGenericExceptionCaught") // Intentional: isolate provider exceptions
    private inline fun safeInvoke(
        provider: BalanceProvider,
        methodName: String,
        block: () -> Unit
    ) {
        try {
            block()
        } catch (e: Throwable) {
            PluginLogger.Service.warn(
                "BalanceProvider ${provider.javaClass.name}.$methodName() threw exception: ${e.message}",
                e
            )
        }
    }

    override fun dispose() {
        PluginLogger.Service.info("BalanceProviderNotifier disposed")
    }
}
