package com.openrouter.intellij.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project
import com.openrouter.intellij.ui.OpenRouterChatWindow

/**
 * Action to open the OpenRouter chat window
 */
class OpenChatAction : AnAction("Open OpenRouter Chat", "Open chat window for OpenRouter models", null) {
    
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        openChatWindow(project)
    }
    
    override fun update(e: AnActionEvent) {
        e.presentation.isEnabled = e.project != null
    }
    
    companion object {
        fun openChatWindow(project: Project) {
            val chatWindow = OpenRouterChatWindow(project)
            chatWindow.show()
        }
    }
}
