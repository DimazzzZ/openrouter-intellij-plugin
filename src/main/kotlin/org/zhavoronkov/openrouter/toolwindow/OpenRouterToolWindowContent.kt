package org.zhavoronkov.openrouter.toolwindow

import com.intellij.openapi.project.Project
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import org.zhavoronkov.openrouter.services.OpenRouterService
import org.zhavoronkov.openrouter.services.OpenRouterSettingsService
import java.awt.BorderLayout
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import javax.swing.*

/**
 * Content for the OpenRouter tool window
 */
class OpenRouterToolWindowContent(private val project: Project) {
    
    private val contentPanel: JPanel
    private val settingsService = OpenRouterSettingsService.getInstance()
    private val openRouterService = OpenRouterService.getInstance()
    
    private val statusLabel = JBLabel("Loading...")
    private val quotaLabel = JBLabel("Quota: N/A")
    private val usageLabel = JBLabel("Usage: N/A")
    private val modelLabel = JBLabel("Model: N/A")
    
    init {
        contentPanel = createMainPanel()
        refreshData()
    }
    
    private fun createMainPanel(): JPanel {
        val panel = JPanel(BorderLayout())
        panel.border = JBUI.Borders.empty(10)
        
        // Header
        val headerPanel = JPanel(BorderLayout())
        val titleLabel = JBLabel("OpenRouter API Status")
        titleLabel.font = titleLabel.font.deriveFont(16f)
        headerPanel.add(titleLabel, BorderLayout.WEST)
        
        val refreshButton = JButton("Refresh")
        refreshButton.addActionListener { refreshData() }
        headerPanel.add(refreshButton, BorderLayout.EAST)
        
        panel.add(headerPanel, BorderLayout.NORTH)
        
        // Content
        val contentPanel = createContentPanel()
        val scrollPane = JBScrollPane(contentPanel)
        panel.add(scrollPane, BorderLayout.CENTER)
        
        return panel
    }
    
    private fun createContentPanel(): JPanel {
        val panel = JPanel(GridBagLayout())
        val gbc = GridBagConstraints()
        
        gbc.anchor = GridBagConstraints.WEST
        gbc.insets = JBUI.insets(5)
        
        // Status
        gbc.gridx = 0
        gbc.gridy = 0
        panel.add(JBLabel("Status:"), gbc)
        gbc.gridx = 1
        panel.add(statusLabel, gbc)
        
        // Model
        gbc.gridx = 0
        gbc.gridy = 1
        panel.add(JBLabel("Default Model:"), gbc)
        gbc.gridx = 1
        panel.add(modelLabel, gbc)
        
        // Quota
        gbc.gridx = 0
        gbc.gridy = 2
        panel.add(JBLabel("Quota:"), gbc)
        gbc.gridx = 1
        panel.add(quotaLabel, gbc)
        
        // Usage
        gbc.gridx = 0
        gbc.gridy = 3
        panel.add(JBLabel("Usage:"), gbc)
        gbc.gridx = 1
        panel.add(usageLabel, gbc)
        
        // Configuration section
        gbc.gridx = 0
        gbc.gridy = 4
        gbc.gridwidth = 2
        gbc.fill = GridBagConstraints.HORIZONTAL
        gbc.insets = JBUI.insets(20, 5, 5, 5)
        
        if (!settingsService.isConfigured()) {
            val configPanel = createConfigurationPanel()
            panel.add(configPanel, gbc)
        }
        
        return panel
    }
    
    private fun createConfigurationPanel(): JPanel {
        val panel = JPanel(BorderLayout())
        panel.border = JBUI.Borders.compound(
            JBUI.Borders.customLine(JBUI.CurrentTheme.ToolWindow.borderColor()),
            JBUI.Borders.empty(10)
        )
        
        val messageLabel = JBLabel("<html><b>OpenRouter not configured</b><br/>Please configure your API key to use OpenRouter features.</html>")
        panel.add(messageLabel, BorderLayout.CENTER)
        
        val configButton = JButton("Configure")
        configButton.addActionListener {
            com.intellij.openapi.options.ShowSettingsUtil.getInstance()
                .showSettingsDialog(project, "OpenRouter")
        }
        panel.add(configButton, BorderLayout.SOUTH)
        
        return panel
    }
    
    private fun refreshData() {
        if (!settingsService.isConfigured()) {
            statusLabel.text = "Not configured"
            modelLabel.text = "N/A"
            quotaLabel.text = "N/A"
            usageLabel.text = "N/A"
            return
        }
        
        statusLabel.text = "Checking..."
        modelLabel.text = settingsService.getDefaultModel()
        
        // Test connection
        openRouterService.testConnection().thenAccept { connected ->
            SwingUtilities.invokeLater {
                statusLabel.text = if (connected) "Connected" else "Connection failed"
            }
        }
        
        // Get quota info
        openRouterService.getQuotaInfo().thenAccept { quota ->
            SwingUtilities.invokeLater {
                if (quota != null) {
                    quotaLabel.text = "$${String.format("%.2f", quota.total ?: 0.0)}"
                    usageLabel.text = "$${String.format("%.2f", quota.used ?: 0.0)} (${String.format("%.1f", ((quota.used ?: 0.0) / (quota.total ?: 1.0)) * 100)}%)"
                } else {
                    quotaLabel.text = "Failed to load"
                    usageLabel.text = "Failed to load"
                }
            }
        }
    }
    
    fun getContentPanel(): JPanel = contentPanel
}
