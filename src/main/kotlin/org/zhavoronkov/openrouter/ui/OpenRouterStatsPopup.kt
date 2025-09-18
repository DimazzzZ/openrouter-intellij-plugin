package org.zhavoronkov.openrouter.ui

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.intellij.util.ui.JBUI
import org.zhavoronkov.openrouter.icons.OpenRouterIcons
import org.zhavoronkov.openrouter.models.ActivityResponse
import org.zhavoronkov.openrouter.models.ApiKeysListResponse
import org.zhavoronkov.openrouter.models.CreditsResponse
// import org.zhavoronkov.openrouter.services.OpenRouterGenerationTrackingService // TEMPORARILY COMMENTED OUT
import org.zhavoronkov.openrouter.services.OpenRouterService
import org.zhavoronkov.openrouter.services.OpenRouterSettingsService
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Component
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Font
import java.time.LocalDate
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
    // private val trackingService = OpenRouterGenerationTrackingService.getInstance() // TEMPORARILY COMMENTED OUT

    private lateinit var tierLabel: JBLabel
    private lateinit var totalCreditsLabel: JBLabel
    private lateinit var creditsUsageLabel: JBLabel
    private lateinit var creditsRemainingLabel: JBLabel
    // TEMPORARILY COMMENTED OUT - TODO: Re-enable when local activity tracking is ready
    // private lateinit var recentCostLabel: JBLabel
    // private lateinit var recentTokensLabel: JBLabel
    // private lateinit var generationCountLabel: JBLabel
    private lateinit var activity24hLabel: JBLabel
    private lateinit var activityWeekLabel: JBLabel
    private lateinit var activityModelsLabel: JBLabel
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
            .setResizable(true)
            .setMovable(true)
            .setRequestFocus(true)
            .createPopup()
    }

    private fun createMainPanel(): JPanel {
        val mainPanel = JBPanel<JBPanel<*>>(BorderLayout()).apply {
            // Set minimum size but allow content to expand
            preferredSize = Dimension(450, 300)
            minimumSize = Dimension(450, 250)
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
            minimumSize = Dimension(400, 200)
            preferredSize = Dimension(400, 250)
        }

        // Account information
        tierLabel = JBLabel("Account: Loading...")

        // Credits information
        totalCreditsLabel = JBLabel("Total Credits: Loading...").apply {
            font = font.deriveFont(Font.BOLD)
        }
        creditsUsageLabel = JBLabel("Credits Used: Loading...")
        creditsRemainingLabel = JBLabel("Credits Remaining: Loading...")

        // Progress bar
        progressBar = JProgressBar(0, 100).apply {
            isStringPainted = false // temporarily disabled
            string = "Loading..."
        }

        // Recent activity information - TEMPORARILY COMMENTED OUT
        // recentCostLabel = JBLabel("Recent Cost: Loading...")
        // recentTokensLabel = JBLabel("Recent Tokens: Loading...")
        // generationCountLabel = JBLabel("Tracked Calls: Loading...")

        // Real OpenRouter activity data
        activity24hLabel = JBLabel("Last 24 hours: Loading...")
        activityWeekLabel = JBLabel("Last week: Loading...")
        activityModelsLabel = JBLabel("<html>Recent Models:<br/>• Loading...</html>").apply {
            verticalAlignment = JBLabel.TOP
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
        statsPanel.add(activity24hLabel)
        statsPanel.add(Box.createVerticalStrut(2))
        statsPanel.add(activityWeekLabel)
        statsPanel.add(Box.createVerticalStrut(4))
        statsPanel.add(activityModelsLabel)
        statsPanel.add(Box.createVerticalStrut(8))

        // Local tracking section - TEMPORARILY COMMENTED OUT
        // TODO: Re-enable when local activity tracking is ready
        /*
        val localLabel = JBLabel("Local Tracking").apply {
            font = font.deriveFont(Font.BOLD, 12f)
        }
        statsPanel.add(localLabel)
        statsPanel.add(Box.createVerticalStrut(4))
        statsPanel.add(recentCostLabel)
        statsPanel.add(Box.createVerticalStrut(4))
        statsPanel.add(recentTokensLabel)
        statsPanel.add(Box.createVerticalStrut(4))
        statsPanel.add(generationCountLabel)
        */

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

        // Fetch API keys, credits, and activity information
        val apiKeysFuture = openRouterService.getApiKeysList()
        val creditsFuture = openRouterService.getCredits()
        val activityFuture = openRouterService.getActivity()

        apiKeysFuture.thenAccept { apiKeysResponse ->
            creditsFuture.thenAccept { creditsResponse ->
                activityFuture.thenAccept { activityResponse ->
                    ApplicationManager.getApplication().invokeLater {
                        if (apiKeysResponse != null && creditsResponse != null) {
                            updateWithApiKeysList(apiKeysResponse)
                            updateWithCredits(creditsResponse)
                            updateWithActivity(activityResponse)
                            // updateTrackingInfo() // TEMPORARILY COMMENTED OUT - TODO: Re-enable when ready
                        } else {
                            showError()
                        }
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
        activity24hLabel.text = "Last 24 hours: Loading..."
        activityWeekLabel.text = "Last week: Loading..."
        activityModelsLabel.text = "<html>Recent Models:<br/>• Loading...</html>"
        // recentCostLabel.text = "Recent Cost: Loading..." // TEMPORARILY COMMENTED OUT
        // recentTokensLabel.text = "Recent Tokens: Loading..." // TEMPORARILY COMMENTED OUT
        // generationCountLabel.text = "Tracked Calls: Loading..." // TEMPORARILY COMMENTED OUT
        progressBar.string = "Loading..."
        progressBar.isIndeterminate = true
        refreshButton.isEnabled = false
    }

    private fun updateWithApiKeysList(apiKeysResponse: ApiKeysListResponse) {
        val enabledKeys = apiKeysResponse.data.filter { !it.disabled }
        tierLabel.text = "Account: ${enabledKeys.size} API Key${if (enabledKeys.size != 1) "s" else ""} Active"

        // Update tracking information - TEMPORARILY COMMENTED OUT
        // updateTrackingInfo() // TODO: Re-enable when local activity tracking is ready

        refreshButton.isEnabled = true
    }

    // TEMPORARILY COMMENTED OUT - TODO: Re-enable when local activity tracking is ready
    /*
    private fun updateTrackingInfo() {
        val recentCost = trackingService.getTotalRecentCost(50)
        val recentTokens = trackingService.getTotalRecentTokens(50)
        val generationCount = trackingService.getGenerationCount()

        recentCostLabel.text = "Recent Cost: $${String.format(Locale.US, "%.6f", recentCost)} (last 50 calls)"
        recentTokensLabel.text = "Recent Tokens: ${String.format(Locale.US, "%,d", recentTokens)} (last 50 calls)"
        generationCountLabel.text = "Tracked Calls: $generationCount total"
    }
    */

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

    private fun updateWithActivity(activityResponse: ActivityResponse?) {
        if (activityResponse == null || activityResponse.data.isEmpty()) {
            activity24hLabel.text = "Last 24 hours: No recent activity"
            activityWeekLabel.text = "Last week: No recent activity"
            activityModelsLabel.text = "<html>Recent Models:<br/>• None</html>"
            return
        }

        val activities = activityResponse.data
        val today = LocalDate.now()
        val yesterday = today.minusDays(1)
        val weekAgo = today.minusDays(7)

        // Filter activities by time periods
        val last24h = activities.filter { activity ->
            val activityDate = parseActivityDate(activity.date)
            activityDate?.let { date ->
                date.isEqual(today) || date.isEqual(yesterday)
            } ?: false
        }

        val lastWeek = activities.filter { activity ->
            val activityDate = parseActivityDate(activity.date)
            activityDate?.let { date ->
                date.isAfter(weekAgo) || date.isEqual(weekAgo)
            } ?: false
        }

        // Calculate 24h stats
        val requests24h = last24h.sumOf { it.requests }
        val usage24h = last24h.sumOf { it.usage }
        activity24hLabel.text = "Last 24 hours: $requests24h requests, $${String.format(Locale.US, "%.4f", usage24h)} spent"

        // Calculate week stats
        val requestsWeek = lastWeek.sumOf { it.requests }
        val usageWeek = lastWeek.sumOf { it.usage }
        activityWeekLabel.text = "Last week: $requestsWeek requests, $${String.format(Locale.US, "%.4f", usageWeek)} spent"

        // Get models from last week, sorted by latest usage (most recent date first)
        val modelsByDate = lastWeek
            .groupBy { it.model }
            .mapValues { (_, activities) -> activities.maxOf { it.date } }
            .toList()
            .sortedByDescending { it.second } // Sort by date descending (latest first)
            .map { it.first } // Extract just the model names

        val modelText = when {
            modelsByDate.isEmpty() -> "<html>Recent Models:<br/>• None</html>"
            else -> {
                val displayModels = modelsByDate.take(5) // Show up to 5 models
                val bullets = displayModels.joinToString("<br/>") { "• $it" }
                val moreText = if (modelsByDate.size > 5) "<br/>• +${modelsByDate.size - 5} more" else ""
                "<html>Recent Models:<br/>$bullets$moreText</html>"
            }
        }
        activityModelsLabel.text = modelText
    }

    /**
     * Parse activity date which can be in format "YYYY-MM-DD" or "YYYY-MM-DD HH:MM:SS"
     */
    private fun parseActivityDate(dateString: String): LocalDate? {
        return try {
            // Try parsing as date only first
            if (dateString.length == 10) {
                LocalDate.parse(dateString)
            } else {
                // Extract just the date part from datetime string
                val datePart = dateString.substring(0, 10)
                LocalDate.parse(datePart)
            }
        } catch (e: Exception) {
            // Log the problematic date format for debugging
            println("Failed to parse activity date: '$dateString' - ${e.message}")
            null
        }
    }

    private fun showNotConfigured() {
        tierLabel.text = "Account: Not configured"
        totalCreditsLabel.text = "Total Credits: -"
        creditsUsageLabel.text = "Credits Used: -"
        creditsRemainingLabel.text = "Credits Remaining: -"
        activity24hLabel.text = "Last 24 hours: -"
        activityWeekLabel.text = "Last week: -"
        activityModelsLabel.text = "<html>Recent Models:<br/>• -</html>"
        // recentCostLabel.text = "Recent Cost: -" // TEMPORARILY COMMENTED OUT
        // recentTokensLabel.text = "Recent Tokens: -" // TEMPORARILY COMMENTED OUT
        // generationCountLabel.text = "Tracked Calls: -" // TEMPORARILY COMMENTED OUT
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
        activity24hLabel.text = "Last 24 hours: -"
        activityWeekLabel.text = "Last week: -"
        activityModelsLabel.text = "<html>Recent Models:<br/>• -</html>"
        // recentCostLabel.text = "Recent Cost: -" // TEMPORARILY COMMENTED OUT
        // recentTokensLabel.text = "Recent Tokens: -" // TEMPORARILY COMMENTED OUT
        // generationCountLabel.text = "Tracked Calls: -" // TEMPORARILY COMMENTED OUT
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
