package org.zhavoronkov.openrouter.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.wm.ToolWindowManager
import org.zhavoronkov.openrouter.icons.OpenRouterIcons

/**
 * Action to show OpenRouter usage statistics
 */
class ShowUsageAction : AnAction("Show Usage Statistics", "Display OpenRouter API usage statistics", OpenRouterIcons.TOOL_WINDOW) {
    
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        
        val toolWindowManager = ToolWindowManager.getInstance(project)
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
