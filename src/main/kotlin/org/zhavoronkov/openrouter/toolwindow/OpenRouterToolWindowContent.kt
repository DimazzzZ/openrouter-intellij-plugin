package org.zhavoronkov.openrouter.toolwindow

import com.intellij.openapi.project.Project
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.zhavoronkov.openrouter.models.ActivityResponse
import org.zhavoronkov.openrouter.models.ApiResult
import org.zhavoronkov.openrouter.services.OpenRouterService
import org.zhavoronkov.openrouter.services.OpenRouterSettingsService
import java.awt.BorderLayout
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.util.Locale
import javax.swing.JButton
import javax.swing.JPanel
import javax.swing.SwingUtilities

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
    private val activityLabel = JBLabel("Recent Activity: N/A")

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

        // Activity
        gbc.gridx = 0
        gbc.gridy = 4
        panel.add(JBLabel("Activity:"), gbc)
        gbc.gridx = 1
        panel.add(activityLabel, gbc)

        // Configuration section
        gbc.gridx = 0
        gbc.gridy = 5
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

        val messageLabel = JBLabel(
            "<html><b>OpenRouter not configured</b><br/>" +
                "Please configure your API key to use OpenRouter features.</html>"
        )
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
            activityLabel.text = "N/A"
            return
        }

        statusLabel.text = "Checking..."
        // TODO: Future version - Default model selection
        // modelLabel.text = settingsService.getDefaultModel()
        modelLabel.text = "N/A" // Placeholder until model selection is implemented
        activityLabel.text = "Loading..."

        val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

        // Test connection
        scope.launch {
            val result = openRouterService.testConnection()
            SwingUtilities.invokeLater {
                when (result) {
                    is ApiResult.Success -> {
                        statusLabel.text = if (result.data) "Connected" else "Connection failed"
                    }
                    is ApiResult.Error -> {
                        statusLabel.text = "Connection failed"
                    }
                }
            }
        }

        // Get quota info
        scope.launch {
            val quotaResult = openRouterService.getQuotaInfo()
            SwingUtilities.invokeLater {
                when (quotaResult) {
                    is ApiResult.Success -> {
                        val quota = quotaResult.data
                        quotaLabel.text = "$${String.format(Locale.US, "%.2f", quota.total ?: 0.0)}"
                        val usedAmount = String.format(Locale.US, "%.2f", quota.used ?: 0.0)
                        val percentage = String.format(
                            Locale.US,
                            "%.1f",
                            ((quota.used ?: 0.0) / (quota.total ?: 1.0)) * 100
                        )
                        usageLabel.text = "$$usedAmount ($percentage%)"
                    }
                    is ApiResult.Error -> {
                        quotaLabel.text = "Failed to load"
                        usageLabel.text = "Failed to load"
                    }
                }
            }
        }

        // Get activity info
        scope.launch {
            val activityResult = openRouterService.getActivity()
            SwingUtilities.invokeLater {
                when (activityResult) {
                    is ApiResult.Success -> {
                        val activityResponse = activityResult.data
                        if (activityResponse.data.isNotEmpty()) {
                            val totalRequests = activityResponse.data.sumOf { it.requests }
                            val totalUsage = activityResponse.data.sumOf { it.usage }
                            activityLabel.text = "$totalRequests requests, $${String.format(Locale.US, "%.4f", totalUsage)}"
                        } else {
                            activityLabel.text = "No recent activity"
                        }
                    }
                    is ApiResult.Error -> {
                        activityLabel.text = "No recent activity"
                    }
                }
            }
        }
    }

    fun getContentPanel(): JPanel = contentPanel
}
