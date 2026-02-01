package org.zhavoronkov.openrouter.toolwindow

import com.intellij.openapi.project.Project
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
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
class OpenRouterToolWindowContent(
    private val project: Project,
    private val settingsService: OpenRouterSettingsService = OpenRouterSettingsService.getInstance(),
    private val openRouterService: OpenRouterService = OpenRouterService.getInstance()
) {

    companion object {
        // UI Dimensions
        private const val MAIN_PANEL_BORDER = 10
        private const val TITLE_FONT_SIZE = 16f
        private const val CONTENT_SPACING = 5
        private const val LABEL_SPACING_SMALL = 3
        private const val LABEL_SPACING_MEDIUM = 4
        private const val LABEL_SPACING_LARGE = 20
        private const val CONFIGURATION_PANEL_BORDER = 10

        // Configuration section vertical offset
        private const val CONFIGURATION_SECTION_OFFSET = 3

        // Percentage constants
        private const val PERCENTAGE_MULTIPLIER = 100
    }

    private val contentPanel: JPanel
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
        panel.border = JBUI.Borders.empty(MAIN_PANEL_BORDER)

        // Header
        val headerPanel = JPanel(BorderLayout())
        val titleLabel = JBLabel("OpenRouter API Status")
        titleLabel.font = titleLabel.font.deriveFont(TITLE_FONT_SIZE)
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
        gbc.insets = JBUI.insets(CONTENT_SPACING)

        // Status
        gbc.gridx = 0
        gbc.gridy = 0
        panel.add(JBLabel("Status:"), gbc)
        gbc.gridx = 1
        panel.add(statusLabel, gbc)

        // Model
        gbc.gridx = 0
        gbc.gridy = LABEL_SPACING_SMALL
        panel.add(JBLabel("Default Model:"), gbc)
        gbc.gridx = 1
        panel.add(modelLabel, gbc)

        // Quota
        gbc.gridx = 0
        gbc.gridy = LABEL_SPACING_MEDIUM
        panel.add(JBLabel("Quota:"), gbc)
        gbc.gridx = 1
        panel.add(quotaLabel, gbc)

        // Usage
        gbc.gridx = 0
        gbc.gridy = LABEL_SPACING_MEDIUM + 1
        panel.add(JBLabel("Usage:"), gbc)
        gbc.gridx = 1
        panel.add(usageLabel, gbc)

        // Activity
        gbc.gridx = 0
        gbc.gridy = LABEL_SPACING_MEDIUM + 2
        panel.add(JBLabel("Activity:"), gbc)
        gbc.gridx = 1
        panel.add(activityLabel, gbc)

        // Configuration section
        gbc.gridx = 0
        gbc.gridy = LABEL_SPACING_MEDIUM + CONFIGURATION_SECTION_OFFSET
        gbc.gridwidth = 2
        gbc.fill = GridBagConstraints.HORIZONTAL
        gbc.insets = JBUI.insets(LABEL_SPACING_LARGE, CONTENT_SPACING, CONTENT_SPACING, CONTENT_SPACING)

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
            JBUI.Borders.empty(CONFIGURATION_PANEL_BORDER)
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
            setUnconfiguredState()
            return
        }

        statusLabel.text = "Checking..."
        // NOTE: Future version - Default model selection
        // modelLabel.text = settingsService.getDefaultModel()
        modelLabel.text = "N/A" // Placeholder until model selection is implemented
        activityLabel.text = "Loading..."

        val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
        scope.launch { loadConnectionStatus() }
        scope.launch { loadQuotaInfo() }
        scope.launch { loadActivityInfo() }
    }

    private fun setUnconfiguredState() {
        statusLabel.text = "Not configured"
        modelLabel.text = "N/A"
        quotaLabel.text = "N/A"
        usageLabel.text = "N/A"
        activityLabel.text = "N/A"
    }

    private suspend fun loadConnectionStatus() {
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

    private suspend fun loadQuotaInfo() {
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
                        ((quota.used ?: 0.0) / (quota.total ?: 1.0)) * PERCENTAGE_MULTIPLIER
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

    private suspend fun loadActivityInfo() {
        val activityResult = openRouterService.getActivity()
        SwingUtilities.invokeLater {
            when (activityResult) {
                is ApiResult.Success -> {
                    val activityResponse = activityResult.data
                    if (activityResponse.data.isNotEmpty()) {
                        val totalRequests = activityResponse.data.sumOf { (it.requests ?: 0).toLong() }
                        val totalUsage = activityResponse.data.sumOf { it.usage ?: 0.0 }
                        val usageStr = String.format(Locale.US, "%.4f", totalUsage)
                        activityLabel.text = "$totalRequests requests, $$usageStr"
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

    fun getContentPanel(): JPanel = contentPanel

    internal fun getStatusTextForTest(): String = statusLabel.text

    internal fun getQuotaTextForTest(): String = quotaLabel.text

    internal fun getUsageTextForTest(): String = usageLabel.text

    internal fun getActivityTextForTest(): String = activityLabel.text
}
