package com.openrouter.intellij.settings

import com.intellij.openapi.options.Configurable
import com.intellij.openapi.ui.Messages
import com.openrouter.intellij.services.OpenRouterService
import com.openrouter.intellij.services.OpenRouterSettingsService
import javax.swing.JComponent

/**
 * Configurable for OpenRouter plugin settings
 */
class OpenRouterConfigurable : Configurable {
    
    private var settingsPanel: OpenRouterSettingsPanel? = null
    private val settingsService = OpenRouterSettingsService.getInstance()
    private val openRouterService = OpenRouterService.getInstance()
    
    override fun getDisplayName(): String = "OpenRouter"
    
    override fun createComponent(): JComponent? {
        settingsPanel = OpenRouterSettingsPanel()
        return settingsPanel?.getPanel()
    }
    
    override fun isModified(): Boolean {
        val panel = settingsPanel ?: return false
        
        return panel.getApiKey() != settingsService.getApiKey() ||
                panel.getDefaultModel() != settingsService.getDefaultModel() ||
                panel.isAutoRefreshEnabled() != settingsService.isAutoRefreshEnabled() ||
                panel.getRefreshInterval() != settingsService.getRefreshInterval() ||
                panel.shouldShowCosts() != settingsService.shouldShowCosts()
    }
    
    override fun apply() {
        val panel = settingsPanel ?: return
        
        val oldApiKey = settingsService.getApiKey()
        val newApiKey = panel.getApiKey()
        
        // Update settings
        settingsService.setApiKey(newApiKey)
        settingsService.setDefaultModel(panel.getDefaultModel())
        settingsService.setAutoRefresh(panel.isAutoRefreshEnabled())
        settingsService.setRefreshInterval(panel.getRefreshInterval())
        settingsService.setShowCosts(panel.shouldShowCosts())
        
        // Test connection if API key changed
        if (oldApiKey != newApiKey && newApiKey.isNotBlank()) {
            testConnection()
        }
    }
    
    override fun reset() {
        val panel = settingsPanel ?: return
        
        panel.setApiKey(settingsService.getApiKey())
        panel.setDefaultModel(settingsService.getDefaultModel())
        panel.setAutoRefresh(settingsService.isAutoRefreshEnabled())
        panel.setRefreshInterval(settingsService.getRefreshInterval())
        panel.setShowCosts(settingsService.shouldShowCosts())
    }
    
    override fun disposeUIResources() {
        settingsPanel = null
    }
    
    private fun testConnection() {
        openRouterService.testConnection().thenAccept { success ->
            if (success) {
                Messages.showInfoMessage(
                    "Successfully connected to OpenRouter API!",
                    "Connection Test"
                )
            } else {
                Messages.showErrorDialog(
                    "Failed to connect to OpenRouter API. Please check your API key.",
                    "Connection Test Failed"
                )
            }
        }
    }
}
