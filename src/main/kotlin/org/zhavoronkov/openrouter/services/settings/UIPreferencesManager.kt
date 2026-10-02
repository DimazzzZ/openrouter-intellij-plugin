package org.zhavoronkov.openrouter.services.settings

import org.zhavoronkov.openrouter.models.OpenRouterSettings

/**
 * Manages UI preferences and display settings
 */
class UIPreferencesManager(
    private val settings: OpenRouterSettings,
    private val onStateChanged: () -> Unit
) {

    /** How many requests the Requests tab keeps; never below one. */
    var requestLogLimit: Int
        get() = settings.requestLogLimit.coerceAtLeast(1)
        set(value) {
            settings.requestLogLimit = value.coerceAtLeast(1)
            onStateChanged()
        }

    /** Whether a Consumer's request that went wrong raises a balloon. */
    var requestWarningBalloons: Boolean
        get() = settings.requestWarningBalloons
        set(value) {
            settings.requestWarningBalloons = value
            onStateChanged()
        }

    /** Whether the Requests tab folds a burst of requests into one row. */
    var requestsGroupBursts: Boolean
        get() = settings.requestsGroupBursts
        set(value) {
            settings.requestsGroupBursts = value
            onStateChanged()
        }

    /** Whether every request's bodies are kept for the Requests tab; off unless the user turns it on. */
    var keepRequestBodies: Boolean
        get() = settings.keepRequestBodies
        set(value) {
            settings.keepRequestBodies = value
            onStateChanged()
        }

    var autoRefresh: Boolean
        get() = settings.autoRefresh
        set(value) {
            settings.autoRefresh = value
            onStateChanged()
        }

    var refreshInterval: Int
        get() = settings.refreshInterval
        set(value) {
            settings.refreshInterval = value
            onStateChanged()
        }

    var showCosts: Boolean
        get() = settings.showCosts
        set(value) {
            settings.showCosts = value
            onStateChanged()
        }

    var defaultMaxTokens: Int
        get() = settings.defaultMaxTokens
        set(value) {
            settings.defaultMaxTokens = value
            onStateChanged()
        }

    /**
     * Controls whether balance data is shared with other plugins via the extension point.
     *
     * When enabled (default), plugins that implement the BalanceProvider interface
     * will receive real-time balance updates. Users can disable this for privacy.
     *
     * @see org.zhavoronkov.openrouter.api.BalanceProvider
     */
    var balanceProviderEnabled: Boolean
        get() = settings.balanceProviderEnabled
        set(value) {
            settings.balanceProviderEnabled = value
            onStateChanged()
        }
}
