package org.zhavoronkov.openrouter.services.settings

import org.zhavoronkov.openrouter.models.OpenRouterSettings

/**
 * Manages UI preferences and display settings
 */
class UIPreferencesManager(
    private val settings: OpenRouterSettings,
    private val onStateChanged: () -> Unit
) {

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

    var trackGenerations: Boolean
        get() = settings.trackGenerations
        set(value) {
            settings.trackGenerations = value
            onStateChanged()
        }

    var maxTrackedGenerations: Int
        get() = settings.maxTrackedGenerations
        set(value) {
            settings.maxTrackedGenerations = value
            onStateChanged()
        }

    var defaultMaxTokens: Int
        get() = settings.defaultMaxTokens
        set(value) {
            settings.defaultMaxTokens = value
            onStateChanged()
        }
}
