package org.zhavoronkov.openrouter.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.WindowManager
import org.zhavoronkov.openrouter.icons.OpenRouterIcons
import org.zhavoronkov.openrouter.statusbar.OpenRouterStatusBarWidget

/**
 * Action to refresh OpenRouter quota information
 */
class RefreshQuotaAction : AnAction(
    "Refresh Quota",
    "Refresh OpenRouter API quota information",
    OpenRouterIcons.REFRESH
) {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        refreshQuota(project)
    }

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabled = e.project != null
    }

    private fun refreshQuota(project: Project) {
        val statusBar = WindowManager.getInstance().getStatusBar(project)
        val widget = statusBar?.getWidget(OpenRouterStatusBarWidget.ID) as? OpenRouterStatusBarWidget
        widget?.updateQuotaInfo()
    }
}
