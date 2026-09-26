package org.zhavoronkov.openrouter.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.options.ShowSettingsUtil
import org.zhavoronkov.openrouter.icons.OpenRouterIcons

/**
 * Action to open OpenRouter settings
 */
class OpenSettingsAction : AnAction("Settings...", "Open OpenRouter settings", OpenRouterIcons.SETTINGS) {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return

        ShowSettingsUtil.getInstance().showSettingsDialog(project, "OpenRouter")
    }

    /**
     * [update] only reads [AnActionEvent.getProject], which is safe off the EDT, so this
     * declares BGT rather than leaving the platform to warn about an unspecified thread.
     */
    override fun getActionUpdateThread() = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabled = e.project != null
    }
}
