package com.openrouter.intellij.settings

import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPasswordField
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.FormBuilder
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import javax.swing.*

/**
 * Settings panel for OpenRouter configuration
 */
class OpenRouterSettingsPanel {
    
    private val panel: JPanel
    private val apiKeyField: JBPasswordField
    private val provisioningKeyField: JBPasswordField
    private val defaultModelField: JBTextField
    private val autoRefreshCheckBox: JBCheckBox
    private val refreshIntervalSpinner: JSpinner
    private val showCostsCheckBox: JBCheckBox
    
    init {
        apiKeyField = JBPasswordField()
        apiKeyField.columns = 40

        provisioningKeyField = JBPasswordField()
        provisioningKeyField.columns = 40

        defaultModelField = JBTextField("openai/gpt-4o")
        defaultModelField.columns = 30
        
        autoRefreshCheckBox = JBCheckBox("Auto-refresh quota information")
        
        refreshIntervalSpinner = JSpinner(SpinnerNumberModel(300, 60, 3600, 60))
        
        showCostsCheckBox = JBCheckBox("Show costs in status bar")
        
        panel = FormBuilder.createFormBuilder()
            .addLabeledComponent(
                JBLabel("API Key:"),
                createApiKeyPanel(),
                1,
                false
            )
            .addLabeledComponent(
                JBLabel("Provisioning Key:"),
                createProvisioningKeyPanel(),
                1,
                false
            )
            .addLabeledComponent(
                JBLabel("Default Model:"),
                defaultModelField,
                1,
                false
            )
            .addComponent(autoRefreshCheckBox, 1)
            .addLabeledComponent(
                JBLabel("Refresh Interval (seconds):"),
                refreshIntervalSpinner,
                1,
                false
            )
            .addComponent(showCostsCheckBox, 1)
            .addComponentFillVertically(JPanel(), 0)
            .panel
        
        panel.border = JBUI.Borders.empty(10)
        
        // Set up listeners
        autoRefreshCheckBox.addActionListener {
            refreshIntervalSpinner.isEnabled = autoRefreshCheckBox.isSelected
        }
    }
    
    private fun createApiKeyPanel(): JPanel {
        val panel = JPanel(BorderLayout())
        panel.add(apiKeyField, BorderLayout.CENTER)

        val helpLabel = JBLabel("<html><small>Get your API key from <a href='https://openrouter.ai/keys'>openrouter.ai/keys</a></small></html>")
        panel.add(helpLabel, BorderLayout.SOUTH)

        return panel
    }

    private fun createProvisioningKeyPanel(): JPanel {
        val panel = JPanel(BorderLayout())
        panel.add(provisioningKeyField, BorderLayout.CENTER)

        val helpLabel = JBLabel("<html><small><b>Required for quota monitoring.</b> Get your provisioning key from <a href='https://openrouter.ai/settings/provisioning-keys'>openrouter.ai/settings/provisioning-keys</a></small></html>")
        panel.add(helpLabel, BorderLayout.SOUTH)

        return panel
    }
    
    fun getPanel(): JPanel = panel
    
    fun getApiKey(): String = String(apiKeyField.password)

    fun setApiKey(apiKey: String) {
        apiKeyField.text = apiKey
    }

    fun getProvisioningKey(): String = String(provisioningKeyField.password)

    fun setProvisioningKey(provisioningKey: String) {
        provisioningKeyField.text = provisioningKey
    }
    
    fun getDefaultModel(): String = defaultModelField.text
    
    fun setDefaultModel(model: String) {
        defaultModelField.text = model
    }
    
    fun isAutoRefreshEnabled(): Boolean = autoRefreshCheckBox.isSelected
    
    fun setAutoRefresh(enabled: Boolean) {
        autoRefreshCheckBox.isSelected = enabled
        refreshIntervalSpinner.isEnabled = enabled
    }
    
    fun getRefreshInterval(): Int = refreshIntervalSpinner.value as Int
    
    fun setRefreshInterval(interval: Int) {
        refreshIntervalSpinner.value = interval
    }
    
    fun shouldShowCosts(): Boolean = showCostsCheckBox.isSelected
    
    fun setShowCosts(show: Boolean) {
        showCostsCheckBox.isSelected = show
    }
}
