package org.zhavoronkov.openrouter.listeners

import com.intellij.util.messages.Topic
import org.zhavoronkov.openrouter.models.ActivityData
import org.zhavoronkov.openrouter.models.CreditsData

/**
 * Listener for OpenRouter stats data updates.
 * This allows components (like status bar widget and stats popup) to react
 * to shared cache updates.
 */
interface OpenRouterStatsListener {
    companion object {
        val TOPIC: Topic<OpenRouterStatsListener> = Topic.create(
            "OpenRouter Stats Updated",
            OpenRouterStatsListener::class.java
        )
    }

    /**
     * Called when stats data has been refreshed successfully.
     *
     * @param credits The updated credits data
     * @param activity The updated activity data (may be null if not available)
     */
    fun onStatsUpdated(credits: CreditsData, activity: List<ActivityData>?)

    /**
     * Called when stats loading has started.
     */
    fun onStatsLoading() {}

    /**
     * Called when stats loading has failed.
     *
     * @param errorMessage The error message describing the failure
     */
    fun onStatsError(errorMessage: String) {}
}
