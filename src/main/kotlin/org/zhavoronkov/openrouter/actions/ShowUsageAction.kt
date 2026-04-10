package org.zhavoronkov.openrouter.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.wm.ToolWindowManager
import org.zhavoronkov.openrouter.icons.OpenRouterIcons

/**
 * Action to show OpenRouter usage statistics
 */
class ShowUsageAction : AnAction() {

    init {
        templatePresentation.text = "Show Usage Statistics"
        templatePresentation.description = "Display OpenRouter API usage statistics"
        templatePresentation.icon = OpenRouterIcons.TOOL_WINDOW
    }

    override fun getActionUpdateThread() = ActionUpdateThread.BGT

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return

        val toolWindowManager = project.getService(ToolWindowManager::class.java)
        val toolWindow = toolWindowManager.getToolWindow("OpenRouter")

        toolWindow?.let {
            it.activate(null)
            it.show(null)
        }
    }

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabled = e.project != null
    }
}
