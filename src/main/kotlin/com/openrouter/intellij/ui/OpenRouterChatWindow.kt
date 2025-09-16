package com.openrouter.intellij.ui

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.Messages
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.JBUI
import com.openrouter.intellij.services.OpenRouterService
import com.openrouter.intellij.services.OpenRouterSettingsService
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.event.ActionEvent
import javax.swing.*

/**
 * Chat window for interacting with OpenRouter models
 */
class OpenRouterChatWindow(private val project: Project) : DialogWrapper(project) {
    
    private val openRouterService = OpenRouterService.getInstance()
    private val settingsService = OpenRouterSettingsService.getInstance()
    
    private val chatArea = JBTextArea().apply {
        isEditable = false
        lineWrap = true
        wrapStyleWord = true
        text = "Welcome to OpenRouter Chat!\nType your message below and press Enter or click Send.\n\n"
    }
    
    private val inputField = JBTextField().apply {
        toolTipText = "Type your message here..."
    }
    
    private val sendButton = JButton("Send").apply {
        addActionListener { sendMessage() }
    }
    
    private val clearButton = JButton("Clear").apply {
        addActionListener { clearChat() }
    }
    
    init {
        title = "OpenRouter Chat"
        init()
        
        // Set up Enter key for input field
        inputField.addActionListener { sendMessage() }
    }
    
    override fun createCenterPanel(): JComponent {
        val panel = JPanel(BorderLayout()).apply {
            preferredSize = Dimension(600, 400)
            border = JBUI.Borders.empty(10)
        }
        
        // Chat display area
        val scrollPane = JBScrollPane(chatArea).apply {
            preferredSize = Dimension(580, 300)
        }
        panel.add(scrollPane, BorderLayout.CENTER)
        
        // Input area
        val inputPanel = JPanel(BorderLayout()).apply {
            border = JBUI.Borders.emptyTop(10)
        }
        
        val buttonPanel = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.X_AXIS)
            add(Box.createHorizontalGlue())
            add(clearButton)
            add(Box.createHorizontalStrut(5))
            add(sendButton)
        }
        
        inputPanel.add(JLabel("Message:"), BorderLayout.WEST)
        inputPanel.add(inputField, BorderLayout.CENTER)
        inputPanel.add(buttonPanel, BorderLayout.EAST)
        
        panel.add(inputPanel, BorderLayout.SOUTH)
        
        return panel
    }
    
    override fun createActions(): Array<Action> {
        return arrayOf(cancelAction)
    }
    
    private fun sendMessage() {
        val message = inputField.text.trim()
        if (message.isEmpty()) return
        
        if (!settingsService.isConfigured()) {
            Messages.showErrorDialog(
                project,
                "Please configure your OpenRouter API key in Settings > Tools > OpenRouter",
                "API Key Required"
            )
            return
        }
        
        // Add user message to chat
        appendToChat("You: $message\n")
        inputField.text = ""
        
        // Add loading indicator
        appendToChat("OpenRouter: Thinking...\n")
        
        // TODO: Implement actual API call to OpenRouter
        // For now, show a placeholder response
        SwingUtilities.invokeLater {
            // Remove "Thinking..." line
            val text = chatArea.text
            val lastThinkingIndex = text.lastIndexOf("OpenRouter: Thinking...")
            if (lastThinkingIndex != -1) {
                chatArea.text = text.substring(0, lastThinkingIndex)
            }
            
            // Add response
            appendToChat("OpenRouter: This is a placeholder response. Chat functionality will be implemented in a future version.\n\n")
        }
    }
    
    private fun clearChat() {
        chatArea.text = "Welcome to OpenRouter Chat!\nType your message below and press Enter or click Send.\n\n"
    }
    
    private fun appendToChat(text: String) {
        SwingUtilities.invokeLater {
            chatArea.append(text)
            chatArea.caretPosition = chatArea.document.length
        }
    }
}
