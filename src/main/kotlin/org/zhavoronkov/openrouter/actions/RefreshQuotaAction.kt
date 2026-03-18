package org.zhavoronkov.openrouter.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import org.zhavoronkov.openrouter.icons.OpenRouterIcons
import org.zhavoronkov.openrouter.services.OpenRouterStatsCache

/**
 * Action to refresh OpenRouter quota information.
 *
 * This action triggers a refresh of the shared [OpenRouterStatsCache],
 * which will notify all listeners (status bar widget, stats popup, etc.)
 * when fresh data is available.
 */
class RefreshQuotaAction : AnAction(
    "Refresh Quota",
    "Refresh OpenRouter API quota information",
    OpenRouterIcons.REFRESH
) {

    override fun actionPerformed(e: AnActionEvent) {
        // Trigger the shared stats cache refresh
        // All subscribed listeners will be notified when data is ready
        OpenRouterStatsCache.getInstance().refresh()
    }

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabled = e.project != null
    }
}
