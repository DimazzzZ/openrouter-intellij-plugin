package org.zhavoronkov.openrouter.ui

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.intellij.util.ui.JBUI
import org.zhavoronkov.openrouter.icons.OpenRouterIcons
import org.zhavoronkov.openrouter.models.ApiKeysListResponse
import org.zhavoronkov.openrouter.models.CreditsResponse
import org.zhavoronkov.openrouter.services.OpenRouterGenerationTrackingService
import org.zhavoronkov.openrouter.services.OpenRouterService
import org.zhavoronkov.openrouter.services.OpenRouterSettingsService
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Font
import java.util.Locale
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JButton
import javax.swing.JPanel
import javax.swing.JProgressBar
import javax.swing.JSeparator

/**
 * Popup that displays OpenRouter usage statistics and information
 */
class OpenRouterStatsPopup(private val project: Project) {

    private val openRouterService = OpenRouterService.getInstance()
    private val settingsService = OpenRouterSettingsService.getInstance()
    private val trackingService = OpenRouterGenerationTrackingService.getInstance()

    private lateinit var tierLabel: JBLabel
    private lateinit var totalCreditsLabel: JBLabel
    private lateinit var creditsUsageLabel: JBLabel
    private lateinit var creditsRemainingLabel: JBLabel
    private lateinit var recentCostLabel: JBLabel
    private lateinit var recentTokensLabel: JBLabel
    private lateinit var generationCountLabel: JBLabel
    private lateinit var progressBar: JProgressBar
    private lateinit var refreshButton: JButton
    private lateinit var settingsButton: JButton

    fun show(component: Component?) {
        val popup = createPopup()
        if (component != null) {
            popup.showUnderneathOf(component)
        } else {
            popup.showCenteredInCurrentWindow(project)
        }
        loadData()
    }

    fun showCenteredInCurrentWindow() {
        val popup = createPopup()
        popup.showCenteredInCurrentWindow(project)
        loadData()
    }

    private fun createPopup(): JBPopup {
        val panel = createMainPanel()

        return JBPopupFactory.getInstance()
            .createComponentPopupBuilder(panel, null)
            .setTitle("OpenRouter Statistics")
            .setResizable(false)
            .setMovable(false)
            .setRequestFocus(true)
            .createPopup()
    }

    private fun createMainPanel(): JPanel {
        val mainPanel = JBPanel<JBPanel<*>>(BorderLayout()).apply {
            preferredSize = Dimension(400, 400)
            border = JBUI.Borders.empty(12)
        }

        // Header with icon and title
        val headerPanel = createHeaderPanel()
        mainPanel.add(headerPanel, BorderLayout.NORTH)

        // Stats content
        val statsPanel = createStatsPanel()
        mainPanel.add(statsPanel, BorderLayout.CENTER)

        // Action buttons
        val buttonPanel = createButtonPanel()
        mainPanel.add(buttonPanel, BorderLayout.SOUTH)

        return mainPanel
    }

    private fun createHeaderPanel(): JPanel {
        val headerPanel = JBPanel<JBPanel<*>>(FlowLayout(FlowLayout.LEFT, 0, 0))

        val iconLabel = JBLabel(OpenRouterIcons.STATUS_BAR)
        val titleLabel = JBLabel("OpenRouter API").apply {
            font = font.deriveFont(Font.BOLD, 14f)
        }

        headerPanel.add(iconLabel)
        headerPanel.add(Box.createHorizontalStrut(8))
        headerPanel.add(titleLabel)

        return headerPanel
    }

    private fun createStatsPanel(): JPanel {
        val statsPanel = JBPanel<JBPanel<*>>().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            border = JBUI.Borders.empty(12, 0)
        }

        // Account information
        tierLabel = JBLabel("Account: Loading...").apply {
            foreground = JBUI.CurrentTheme.Label.foreground()
        }

        // Credits information
        totalCreditsLabel = JBLabel("Total Credits: Loading...").apply {
            font = font.deriveFont(Font.BOLD)
            foreground = JBUI.CurrentTheme.Label.foreground()
        }
        creditsUsageLabel = JBLabel("Credits Used: Loading...").apply {
            foreground = JBUI.CurrentTheme.Label.foreground()
        }
        creditsRemainingLabel = JBLabel("Credits Remaining: Loading...").apply {
            foreground = JBUI.CurrentTheme.Label.foreground()
        }

        // Progress bar
        progressBar = JProgressBar(0, 100).apply {
            isStringPainted = true
            string = "Loading..."
        }

        // Recent activity information
        recentCostLabel = JBLabel("Recent Cost: Loading...").apply {
            foreground = JBUI.CurrentTheme.Label.foreground()
        }
        recentTokensLabel = JBLabel("Recent Tokens: Loading...").apply {
            foreground = JBUI.CurrentTheme.Label.foreground()
        }
        generationCountLabel = JBLabel("Tracked Calls: Loading...").apply {
            foreground = JBUI.CurrentTheme.Label.foreground()
        }

        statsPanel.add(tierLabel)
        statsPanel.add(Box.createVerticalStrut(8))

        // Credits section
        statsPanel.add(totalCreditsLabel)
        statsPanel.add(Box.createVerticalStrut(4))
        statsPanel.add(creditsUsageLabel)
        statsPanel.add(Box.createVerticalStrut(4))
        statsPanel.add(creditsRemainingLabel)
        statsPanel.add(Box.createVerticalStrut(8))

        statsPanel.add(progressBar)
        statsPanel.add(Box.createVerticalStrut(12))

        // Add separator
        val separator = JSeparator()
        statsPanel.add(separator)
        statsPanel.add(Box.createVerticalStrut(8))

        // Recent activity section
        val recentLabel = JBLabel("Recent Activity").apply {
            font = font.deriveFont(Font.BOLD, 12f)
        }
        statsPanel.add(recentLabel)
        statsPanel.add(Box.createVerticalStrut(4))
        statsPanel.add(recentCostLabel)
        statsPanel.add(Box.createVerticalStrut(4))
        statsPanel.add(recentTokensLabel)
        statsPanel.add(Box.createVerticalStrut(4))
        statsPanel.add(generationCountLabel)

        return statsPanel
    }

    private fun createButtonPanel(): JPanel {
        val buttonPanel = JBPanel<JBPanel<*>>(FlowLayout(FlowLayout.RIGHT, 0, 0)).apply {
            border = JBUI.Borders.emptyTop(12)
        }

        refreshButton = JButton("Refresh").apply {
            addActionListener { loadData() }
        }

        settingsButton = JButton("Settings").apply {
            addActionListener { openSettings() }
        }

        buttonPanel.add(refreshButton)
        buttonPanel.add(Box.createHorizontalStrut(8))
        buttonPanel.add(settingsButton)

        return buttonPanel
    }

    private fun loadData() {
        if (!settingsService.isConfigured()) {
            showNotConfigured()
            return
        }

        // Show loading state
        setLoadingState()

        // Fetch both API keys and credits information
        val apiKeysFuture = openRouterService.getApiKeysList()
        val creditsFuture = openRouterService.getCredits()

        apiKeysFuture.thenAccept { apiKeysResponse ->
            creditsFuture.thenAccept { creditsResponse ->
                ApplicationManager.getApplication().invokeLater {
                    if (apiKeysResponse != null && creditsResponse != null) {
                        updateWithApiKeysList(apiKeysResponse)
                        updateWithCredits(creditsResponse)
                    } else {
                        showError()
                    }
                }
            }
        }
    }

    private fun setLoadingState() {
        tierLabel.text = "Account: Loading..."
        totalCreditsLabel.text = "Total Credits: Loading..."
        creditsUsageLabel.text = "Credits Used: Loading..."
        creditsRemainingLabel.text = "Credits Remaining: Loading..."
        recentCostLabel.text = "Recent Cost: Loading..."
        recentTokensLabel.text = "Recent Tokens: Loading..."
        generationCountLabel.text = "Tracked Calls: Loading..."
        progressBar.string = "Loading..."
        progressBar.isIndeterminate = true
        refreshButton.isEnabled = false
    }

    private fun updateWithApiKeysList(apiKeysResponse: ApiKeysListResponse) {
        val enabledKeys = apiKeysResponse.data.filter { !it.disabled }
        tierLabel.text = "Account: ${enabledKeys.size} API Key${if (enabledKeys.size != 1) "s" else ""} Active"

        // Update tracking information
        updateTrackingInfo()

        refreshButton.isEnabled = true
    }

    private fun updateTrackingInfo() {
        val recentCost = trackingService.getTotalRecentCost(50)
        val recentTokens = trackingService.getTotalRecentTokens(50)
        val generationCount = trackingService.getGenerationCount()

        recentCostLabel.text = "Recent Cost: $${String.format(Locale.US, "%.6f", recentCost)} (last 50 calls)"
        recentTokensLabel.text = "Recent Tokens: ${String.format(Locale.US, "%,d", recentTokens)} (last 50 calls)"
        generationCountLabel.text = "Tracked Calls: $generationCount total"
    }

    private fun updateWithCredits(creditsResponse: CreditsResponse) {
        val creditsData = creditsResponse.data
        val totalCredits = creditsData.totalCredits
        val usedCredits = creditsData.totalUsage
        val remainingCredits = totalCredits - usedCredits

        totalCreditsLabel.text = "Total Credits: $${String.format(Locale.US, "%.3f", totalCredits)}"
        creditsUsageLabel.text = "Credits Used: $${String.format(Locale.US, "%.3f", usedCredits)}"
        creditsRemainingLabel.text = "Credits Remaining: $${String.format(Locale.US, "%.3f", remainingCredits)}"

        // Update progress bar with credits information
        if (totalCredits > 0) {
            val percentage = ((usedCredits / totalCredits) * 100).toInt()
            progressBar.value = percentage
            progressBar.string = "${percentage}% used ($${String.format(Locale.US, "%.3f", usedCredits)}/$${String.format(Locale.US, "%.3f", totalCredits)})"
            progressBar.isIndeterminate = false
        } else {
            progressBar.value = 0
            progressBar.string = "No credits available"
            progressBar.isIndeterminate = false
        }
    }

    private fun showNotConfigured() {
        tierLabel.text = "Account: Not configured"
        totalCreditsLabel.text = "Total Credits: -"
        creditsUsageLabel.text = "Credits Used: -"
        creditsRemainingLabel.text = "Credits Remaining: -"
        recentCostLabel.text = "Recent Cost: -"
        recentTokensLabel.text = "Recent Tokens: -"
        generationCountLabel.text = "Tracked Calls: -"
        progressBar.value = 0
        progressBar.string = "Not configured"
        progressBar.isIndeterminate = false
        refreshButton.isEnabled = false
    }

    private fun showError() {
        tierLabel.text = "Account: Error loading data"
        totalCreditsLabel.text = "Total Credits: -"
        creditsUsageLabel.text = "Credits Used: -"
        creditsRemainingLabel.text = "Credits Remaining: -"
        recentCostLabel.text = "Recent Cost: -"
        recentTokensLabel.text = "Recent Tokens: -"
        generationCountLabel.text = "Tracked Calls: -"
        progressBar.value = 0
        progressBar.string = "Error"
        progressBar.isIndeterminate = false
        refreshButton.isEnabled = true
    }

    private fun openSettings() {
        ApplicationManager.getApplication().invokeLater {
            com.intellij.openapi.options.ShowSettingsUtil.getInstance()
                .showSettingsDialog(project, "OpenRouter")
        }
    }
}
