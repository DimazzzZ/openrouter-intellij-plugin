package org.zhavoronkov.openrouter.ui

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.intellij.util.ui.JBUI
import org.zhavoronkov.openrouter.icons.OpenRouterIcons
import org.zhavoronkov.openrouter.models.ActivityData
import org.zhavoronkov.openrouter.models.ActivityResponse
import org.zhavoronkov.openrouter.models.ApiKeysListResponse
import org.zhavoronkov.openrouter.models.CreditsResponse
// import org.zhavoronkov.openrouter.services.OpenRouterGenerationTrackingService // TEMPORARILY COMMENTED OUT
import org.zhavoronkov.openrouter.services.OpenRouterService
import org.zhavoronkov.openrouter.services.OpenRouterSettingsService
import java.awt.BorderLayout
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

    // Test-friendly constructor
    constructor(project: Project, openRouterService: OpenRouterService?, settingsService: OpenRouterSettingsService?) : this(project) {
        // This constructor is only for testing - we'll use field injection
        if (openRouterService != null) {
            this.openRouterServiceField = openRouterService
        }
        if (settingsService != null) {
            this.settingsServiceField = settingsService
        }
    }

    private var openRouterServiceField: OpenRouterService? = null
    private var settingsServiceField: OpenRouterSettingsService? = null
    
    private val openRouterService: OpenRouterService?
        get() = openRouterServiceField ?: try {
            OpenRouterService.getInstance()
        } catch (e: Exception) {
            null
        }
    
    private val settingsService: OpenRouterSettingsService?
        get() = settingsServiceField ?: try {
            OpenRouterSettingsService.getInstance()
        } catch (e: Exception) {
            null
        }
    // private val trackingService = OpenRouterGenerationTrackingService.getInstance() // TEMPORARILY COMMENTED OUT

    companion object {
        private const val DEFAULT_LOADING_TEXT = "Loading..."
        private const val NOT_CONFIGURED_TEXT = "-"
        private const val ERROR_TEXT = "-"
        private const val NOT_CONFIGURED_MESSAGE = "Not configured"
        private const val ERROR_MESSAGE = "Error loading data"
        private const val NO_ACTIVITY_TEXT = "No recent activity"
        private const val NO_RECENT_MODELS_TEXT = "• None"
        private const val NO_RECENT_MODELS_HTML = "<html>Recent Models:<br/>• None</html>"
        private const val LOADING_MODELS_HTML = "<html>Recent Models:<br/>• Loading...</html>"
    }

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

    /**
     * Formats a currency value as a string with dollar sign and appropriate precision
     */
    private fun formatCurrency(value: Double, decimals: Int = 3): String {
        return String.format(Locale.US, "%." + decimals + "f", value)
    }

    /**
     * Formats a large integer value with comma separators
     */
    private fun formatLargeNumber(value: Long): String {
        return String.format(Locale.US, "%,d", value)
    }

    /**
     * Builds HTML-formatted recent models list
     */
    private fun buildModelsHtmlList(models: List<String>): String {
        return when {
            models.isEmpty() -> NO_RECENT_MODELS_HTML
            else -> {
                val displayModels = models.take(5) // Show up to 5 models
                val bullets = displayModels.joinToString("<br/>") { "• $it" }
                val moreText = if (models.size > 5) "<br/>• +${models.size - 5} more" else ""
                "<html>Recent Models:<br/>$bullets$moreText</html>"
            }
        }
    }

    /**
     * Sets all labels to loading state
     */
    private fun setLabelsToLoading() {
        tierLabel.text = "Account: $DEFAULT_LOADING_TEXT"
        totalCreditsLabel.text = "Total Credits: $DEFAULT_LOADING_TEXT"
        creditsUsageLabel.text = "Credits Used: $DEFAULT_LOADING_TEXT"
        creditsRemainingLabel.text = "Credits Remaining: $DEFAULT_LOADING_TEXT"
        activity24hLabel.text = "Last 24 hours: $DEFAULT_LOADING_TEXT"
        activityWeekLabel.text = "Last week: $DEFAULT_LOADING_TEXT"
        activityModelsLabel.text = LOADING_MODELS_HTML
        // recentCostLabel.text = "Recent Cost: $DEFAULT_LOADING_TEXT" // TEMPORARILY COMMENTED OUT
        // recentTokensLabel.text = "Recent Tokens: $DEFAULT_LOADING_TEXT" // TEMPORARILY COMMENTED OUT
        // generationCountLabel.text = "Tracked Calls: $DEFAULT_LOADING_TEXT" // TEMPORARILY COMMENTED OUT
    }

    /**
     * Sets all labels to not configured state
     */
    private fun setLabelsToNotConfigured() {
        tierLabel.text = "Account: $NOT_CONFIGURED_MESSAGE"
        totalCreditsLabel.text = "Total Credits: $NOT_CONFIGURED_TEXT"
        creditsUsageLabel.text = "Credits Used: $NOT_CONFIGURED_TEXT"
        creditsRemainingLabel.text = "Credits Remaining: $NOT_CONFIGURED_TEXT"
        activity24hLabel.text = "Last 24 hours: $NOT_CONFIGURED_TEXT"
        activityWeekLabel.text = "Last week: $NOT_CONFIGURED_TEXT"
        activityModelsLabel.text = "<html>Recent Models:<br/>• $NOT_CONFIGURED_TEXT</html>"
        // recentCostLabel.text = "Recent Cost: $NOT_CONFIGURED_TEXT" // TEMPORARILY COMMENTED OUT
        // recentTokensLabel.text = "Recent Tokens: $NOT_CONFIGURED_TEXT" // TEMPORARILY COMMENTED OUT
        // generationCountLabel.text = "Tracked Calls: $NOT_CONFIGURED_TEXT" // TEMPORARILY COMMENTED OUT
    }

    /**
     * Sets all labels to error state
     */
    private fun setLabelsToError() {
        tierLabel.text = "Account: $ERROR_MESSAGE"
        totalCreditsLabel.text = "Total Credits: $ERROR_TEXT"
        creditsUsageLabel.text = "Credits Used: $ERROR_TEXT"
        creditsRemainingLabel.text = "Credits Remaining: $ERROR_TEXT"
        activity24hLabel.text = "Last 24 hours: $ERROR_TEXT"
        activityWeekLabel.text = "Last week: $ERROR_TEXT"
        activityModelsLabel.text = "<html>Recent Models:<br/>• $ERROR_TEXT</html>"
        // recentCostLabel.text = "Recent Cost: $ERROR_TEXT" // TEMPORARILY COMMENTED OUT
        // recentTokensLabel.text = "Recent Tokens: $ERROR_TEXT" // TEMPORARILY COMMENTED OUT
        // generationCountLabel.text = "Tracked Calls: $ERROR_TEXT" // TEMPORARILY COMMENTED OUT
    }

    /**
     * Sets progress bar to a specific state
     */
    private fun setProgressBarState(value: Int = 0, text: String, indeterminate: Boolean = false) {
        progressBar.value = value
        progressBar.string = text
        progressBar.isIndeterminate = indeterminate
    }

    /**
     * Creates formatted activity text for requests/usage
     */
    private fun formatActivityText(requests: Long, usage: Double): String {
        return "$requests requests, $${formatCurrency(usage, 4)} spent"
    }

    /**
     * Sets activity state when no recent activity
     */
    private fun setActivityLabelsToNoActivity() {
        activity24hLabel.text = "Last 24 hours: $NO_ACTIVITY_TEXT"
        activityWeekLabel.text = "Last week: $NO_ACTIVITY_TEXT"
        activityModelsLabel.text = NO_RECENT_MODELS_HTML
    }

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
        val settings = settingsService
        val routerService = openRouterService
        
        if (settings == null || routerService == null) {
            // In test environment, services might be null
            showError()
            return
        }
        
        if (!settings.isConfigured()) {
            showNotConfigured()
            return
        }

        // Show loading state
        setLoadingState()

        // Fetch API keys, credits, and activity information
        val apiKeysFuture = routerService.getApiKeysList()
        val creditsFuture = routerService.getCredits()
        val activityFuture = routerService.getActivity()

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
        setLabelsToLoading()
        setProgressBarState(text = DEFAULT_LOADING_TEXT, indeterminate = true)
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

        totalCreditsLabel.text = "Total Credits: $${formatCurrency(totalCredits)}"
        creditsUsageLabel.text = "Credits Used: $${formatCurrency(usedCredits)}"
        creditsRemainingLabel.text = "Credits Remaining: $${formatCurrency(remainingCredits)}"

        // Update progress bar with credits information
        if (totalCredits > 0) {
            val percentage = ((usedCredits / totalCredits) * 100).toInt()
            setProgressBarState(percentage, "${percentage}% used ($${formatCurrency(usedCredits)}/$${formatCurrency(totalCredits)})")
        } else {
            setProgressBarState(text = "No credits available")
        }
    }

    private fun updateWithActivity(activityResponse: ActivityResponse?) {
        if (activityResponse == null || activityResponse.data.isEmpty()) {
            setActivityLabelsToNoActivity()
            return
        }

        val activities = activityResponse.data
        val today = LocalDate.now()
        val yesterday = today.minusDays(1)
        val weekAgo = today.minusDays(7)

        // Filter activities by time periods
        val last24h = filterActivitiesByTime(activities, today, yesterday, weekAgo, isLast24h = true)
        val lastWeek = filterActivitiesByTime(activities, today, yesterday, weekAgo, isLast24h = false)

        // Calculate and display stats
        val (requests24h, usage24h) = calculateActivityStats(last24h)
        activity24hLabel.text = "Last 24 hours: ${formatActivityText(requests24h, usage24h)}"

        val (requestsWeek, usageWeek) = calculateActivityStats(lastWeek)
        activityWeekLabel.text = "Last week: ${formatActivityText(requestsWeek, usageWeek)}"

        // Get and display recent models
        val recentModelNames = extractRecentModelNames(lastWeek)
        activityModelsLabel.text = buildModelsHtmlList(recentModelNames)
    }

    /**
     * Filters activities by time period
     */
    private fun filterActivitiesByTime(activities: List<ActivityData>,
                                       today: LocalDate, yesterday: LocalDate, weekAgo: LocalDate,
                                       isLast24h: Boolean): List<ActivityData> {
        return if (isLast24h) {
            activities.filter { activity ->
                val activityDate = parseActivityDate(activity.date)
                activityDate?.let { date ->
                    date.isEqual(today) || date.isEqual(yesterday)
                } ?: false
            }
        } else {
            activities.filter { activity ->
                val activityDate = parseActivityDate(activity.date)
                activityDate?.let { date ->
                    date.isAfter(weekAgo) || date.isEqual(weekAgo)
                } ?: false
            }
        }
    }

    /**
     * Calculates total requests and usage from activity list
     */
    private fun calculateActivityStats(activities: List<ActivityData>): Pair<Long, Double> {
        val requests = activities.sumOf { it.requests.toLong() }
        val usage = activities.sumOf { it.usage }
        return Pair(requests, usage)
    }

    /**
     * Extracts model names sorted by most recent usage
     */
    private fun extractRecentModelNames(activities: List<ActivityData>): List<String> {
        return activities
            .groupBy { it.model }
            .mapValues { (_, activities) -> activities.maxOf { it.date } }
            .toList()
            .sortedByDescending { it.second } // Sort by date descending (latest first)
            .map { it.first } // Extract just the model names
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
        setLabelsToNotConfigured()
        setProgressBarState(text = NOT_CONFIGURED_MESSAGE)
        refreshButton.isEnabled = false
    }

    private fun showError() {
        setLabelsToError()
        setProgressBarState(text = ERROR_MESSAGE)
        refreshButton.isEnabled = true
    }

    private fun openSettings() {
        ApplicationManager.getApplication().invokeLater {
            com.intellij.openapi.options.ShowSettingsUtil.getInstance()
                .showSettingsDialog(project, "OpenRouter")
        }
    }
}
