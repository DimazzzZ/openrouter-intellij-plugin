package com.openrouter.intellij.statusbar

import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.StatusBar
import com.intellij.openapi.wm.StatusBarWidget
import com.intellij.openapi.wm.StatusBarWidgetFactory

/**
 * Factory for creating OpenRouter status bar widgets
 */
class OpenRouterStatusBarWidgetFactory : StatusBarWidgetFactory {
    
    override fun getId(): String = OpenRouterStatusBarWidget.ID
    
    override fun getDisplayName(): String = "OpenRouter"
    
    override fun isAvailable(project: Project): Boolean = true
    
    override fun createWidget(project: Project): StatusBarWidget {
        return OpenRouterStatusBarWidget(project)
    }
    
    override fun disposeWidget(widget: StatusBarWidget) {
        // Cleanup if needed
    }
    
    override fun canBeEnabledOn(statusBar: StatusBar): Boolean = true
}
