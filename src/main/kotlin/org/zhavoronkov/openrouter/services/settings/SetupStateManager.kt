package org.zhavoronkov.openrouter.services.settings

import org.zhavoronkov.openrouter.models.OpenRouterSettings

/**
 * Manages setup and onboarding state
 */
class SetupStateManager(
    private val settings: OpenRouterSettings,
    private val onStateChanged: () -> Unit
) {

    fun hasSeenWelcome(): Boolean {
        return settings.hasSeenWelcome
    }

    fun setHasSeenWelcome(seen: Boolean) {
        settings.hasSeenWelcome = seen
        onStateChanged()
    }

    fun hasCompletedSetup(): Boolean {
        return settings.hasCompletedSetup
    }

    fun setHasCompletedSetup(completed: Boolean) {
        settings.hasCompletedSetup = completed
        onStateChanged()
    }
}
