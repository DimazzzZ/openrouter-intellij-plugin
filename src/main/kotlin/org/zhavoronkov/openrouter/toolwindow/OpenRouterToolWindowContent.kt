package org.zhavoronkov.openrouter.toolwindow

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTabbedPane
import com.intellij.util.ui.JBUI
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.zhavoronkov.openrouter.listeners.OpenRouterSettingsListener
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
@Suppress("TooManyFunctions")
class OpenRouterToolWindowContent(
    private val project: Project,
    private val settingsService: OpenRouterSettingsService = OpenRouterSettingsService.getInstance(),
    private val openRouterService: OpenRouterService = OpenRouterService.getInstance()
) : Disposable {

    companion object {
        // UI Dimensions
        private const val MAIN_PANEL_BORDER = 10
        private const val TITLE_FONT_SIZE = 16f
        private const val CONTENT_SPACING = 5
        private const val LABEL_SPACING_LARGE = 20
        private const val CONFIGURATION_PANEL_BORDER = 10
        private const val PERCENTAGE_MULTIPLIER = 100

        // Grid position constants
        private const val GRID_STATUS_ROW = 0
        private const val GRID_MODEL_ROW = 1
        private const val GRID_QUOTA_ROW = 2
        private const val GRID_USAGE_ROW = 3
        private const val GRID_ACTIVITY_ROW = 4
        private const val GRID_CONFIG_ROW = 5
    }

    private val mainPanel: JPanel
    private val tabbedPane: JBTabbedPane
    private val statusPanel: JPanel
    private var configurationPanel: JPanel? = null
    private val statusLabel = JBLabel("Loading...")
    private val quotaLabel = JBLabel("Quota: N/A")
    private val usageLabel = JBLabel("Usage: N/A")
    private val modelLabel = JBLabel("Model: N/A")
    private val activityLabel = JBLabel("Recent Activity: N/A")

    private val coroutineScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private lateinit var chatPanel: ChatPanel

    init {
        // Register for settings changes
        val connection = ApplicationManager.getApplication().messageBus.connect(this)
        connection.subscribe(
            OpenRouterSettingsListener.TOPIC,
            object : OpenRouterSettingsListener {
                override fun onSettingsChanged() {
                    SwingUtilities.invokeLater {
                        updateConfigurationPanelVisibility()
                        refreshData()
                    }
                }
            }
        )

        // Create main panel with tabs
        mainPanel = JPanel(BorderLayout())
        mainPanel.border = JBUI.Borders.empty(MAIN_PANEL_BORDER)

        // Create tabbed pane
        tabbedPane = JBTabbedPane()

        // Create status panel
        statusPanel = createStatusPanel()
        tabbedPane.addTab("Status", statusPanel)

        // Create chat panel
        chatPanel = ChatPanel(project, settingsService, openRouterService)
        tabbedPane.addTab("Chat", chatPanel.getPanel())

        // Select Chat tab by default
        tabbedPane.selectedIndex = 1

        mainPanel.add(tabbedPane, BorderLayout.CENTER)

        // Initial data refresh
        refreshData()
    }

    private fun createStatusPanel(): JPanel {
        val panel = JPanel(BorderLayout())

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
        gbc.gridy = GRID_STATUS_ROW
        panel.add(JBLabel("Status:"), gbc)
        gbc.gridx = 1
        panel.add(statusLabel, gbc)

        // Model
        gbc.gridx = 0
        gbc.gridy = GRID_MODEL_ROW
        panel.add(JBLabel("Default Model:"), gbc)
        gbc.gridx = 1
        panel.add(modelLabel, gbc)

        // Quota
        gbc.gridx = 0
        gbc.gridy = GRID_QUOTA_ROW
        panel.add(JBLabel("Quota:"), gbc)
        gbc.gridx = 1
        panel.add(quotaLabel, gbc)

        // Usage
        gbc.gridx = 0
        gbc.gridy = GRID_USAGE_ROW
        panel.add(JBLabel("Usage:"), gbc)
        gbc.gridx = 1
        panel.add(usageLabel, gbc)

        // Activity
        gbc.gridx = 0
        gbc.gridy = GRID_ACTIVITY_ROW
        panel.add(JBLabel("Activity:"), gbc)
        gbc.gridx = 1
        panel.add(activityLabel, gbc)

        // Configuration section (dynamic)
        gbc.gridx = 0
        gbc.gridy = GRID_CONFIG_ROW
        gbc.gridwidth = 2
        gbc.fill = GridBagConstraints.HORIZONTAL
        gbc.insets = JBUI.insets(LABEL_SPACING_LARGE, CONTENT_SPACING, CONTENT_SPACING, CONTENT_SPACING)

        configurationPanel = createConfigurationPanel()
        panel.add(configurationPanel, gbc)

        // Update visibility based on current state
        updateConfigurationPanelVisibility()

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

    private fun updateConfigurationPanelVisibility() {
        configurationPanel?.isVisible = !settingsService.isConfigured()
    }

    private fun refreshData() {
        updateConfigurationPanelVisibility()

        if (!settingsService.isConfigured()) {
            setUnconfiguredState()
            return
        }

        statusLabel.text = "Checking..."
        modelLabel.text = "N/A"
        activityLabel.text = "Loading..."

        coroutineScope.launch { loadConnectionStatus() }
        coroutineScope.launch { loadQuotaInfo() }
        coroutineScope.launch { loadActivityInfo() }
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

    fun getContentPanel(): JPanel = mainPanel

    override fun dispose() {
        coroutineScope.cancel()
    }

    internal fun getStatusTextForTest(): String = statusLabel.text
    internal fun getQuotaTextForTest(): String = quotaLabel.text
    internal fun getUsageTextForTest(): String = usageLabel.text
    internal fun getActivityTextForTest(): String = activityLabel.text
}
