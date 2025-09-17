package org.zhavoronkov.openrouter.toolwindow

import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory

/**
 * Factory for creating OpenRouter tool window
 */
class OpenRouterToolWindowFactory : ToolWindowFactory {

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val toolWindowContent = OpenRouterToolWindowContent(project)
        val content = ContentFactory.getInstance().createContent(
            toolWindowContent.getContentPanel(),
            "",
            false
        )
        toolWindow.contentManager.addContent(content)
    }
}
