package org.zhavoronkov.openrouter.ui

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.intellij.util.ui.JBUI
import org.zhavoronkov.openrouter.icons.OpenRouterIcons
import org.zhavoronkov.openrouter.models.ActivityData
import org.zhavoronkov.openrouter.models.ActivityResponse
import org.zhavoronkov.openrouter.models.ApiKeysListResponse
import org.zhavoronkov.openrouter.models.CreditsResponse
import org.zhavoronkov.openrouter.services.OpenRouterService
import org.zhavoronkov.openrouter.services.OpenRouterSettingsService
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Font
import java.time.LocalDate
import javax.swing.Action
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JProgressBar
import javax.swing.JSeparator

/**
 * Dialog that displays OpenRouter usage statistics and information
 */
@Suppress("TooManyFunctions")
class OpenRouterStatsPopup(private val project: Project) : DialogWrapper(project) {

    init {
        title = "OpenRouter Statistics"
        init()
    }

    // Test-friendly constructor
    constructor(
        project: Project,
        openRouterService: OpenRouterService?,
        settingsService: OpenRouterSettingsService?
    ) : this(
        project
    ) {
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
    private val dataLoader: StatsDataLoader by lazy {
        StatsDataLoader(settingsService, openRouterService)
    }

    private val openRouterService: OpenRouterService?
        get() = openRouterServiceField ?: try {
            OpenRouterService.getInstance()
        } catch (e: IllegalStateException) {
            // Service not available in test environment or initialization error
            org.zhavoronkov.openrouter.utils.PluginLogger.Service.warn(
                "OpenRouterService not available: ${e.message}",
                e
            )
            null
        } catch (e: NoClassDefFoundError) {
            // Service class not found
            org.zhavoronkov.openrouter.utils.PluginLogger.Service.warn(
                "OpenRouterService class not found: ${e.message}",
                e
            )
            null
        }

    private val settingsService: OpenRouterSettingsService?
        get() = settingsServiceField ?: try {
            OpenRouterSettingsService.getInstance()
        } catch (e: IllegalStateException) {
            // Service not available in test environment or initialization error
            org.zhavoronkov.openrouter.utils.PluginLogger.Service.warn(
                "OpenRouterSettingsService not available: ${e.message}",
                e
            )
            null
        } catch (e: NoClassDefFoundError) {
            // Service class not found
            org.zhavoronkov.openrouter.utils.PluginLogger.Service.warn(
                "OpenRouterSettingsService class not found: ${e.message}",
                e
            )
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

        private const val NO_RECENT_MODELS_HTML = "<html>Recent Models:<br/>• None</html>"
        private const val LOADING_MODELS_HTML = "<html>Recent Models:<br/>• Loading...</html>"

        // UI Dimensions
        private const val MAIN_PANEL_WIDTH = 450
        private const val MAIN_PANEL_HEIGHT = 350
        private const val MAIN_PANEL_MIN_HEIGHT = 300
        private const val STATS_PANEL_WIDTH = 400
        private const val STATS_PANEL_MIN_HEIGHT = 200
        private const val STATS_PANEL_HEIGHT = 250
        private const val MAIN_PANEL_BORDER = 12
        private const val HEADER_ICON_GAP = 8
        private const val STATS_PANEL_BORDER_TOP = 12
        private const val FONT_SIZE_TITLE = 14f
        private const val FONT_SIZE_LABEL = 12f
        private const val PROGRESS_BAR_HEIGHT = 4
        private const val LABEL_SPACING = 8
        private const val ACTIVITY_SECTION_SPACING = 4

        // Timing constants
        private const val DIALOG_SHOW_DELAY_MS = 100
        private const val ACTIVITY_DAYS_WEEK = 7
        private const val ACTIVITY_DISPLAY_LIMIT = 5

        // Percentage constants
        private const val PERCENTAGE_MULTIPLIER = 100

        // Currency formatting
        private const val CURRENCY_DECIMAL_PLACES = 4

        // Progress bar maximum value
        private const val PROGRESS_BAR_MAX = 100

        // Date string length for date-only format (YYYY-MM-DD)
        private const val DATE_ONLY_LENGTH = 10
    }

    private lateinit var tierLabel: JBLabel
    private lateinit var totalCreditsLabel: JBLabel
    private lateinit var creditsUsageLabel: JBLabel
    private lateinit var creditsRemainingLabel: JBLabel

    // TEMPORARILY COMMENTED OUT - NOTE: Re-enable when local activity tracking is ready
    // private lateinit var recentCostLabel: JBLabel
    // private lateinit var recentTokensLabel: JBLabel
    // private lateinit var generationCountLabel: JBLabel
    private lateinit var activity24hLabel: JBLabel
    private lateinit var activityWeekLabel: JBLabel
    private lateinit var activityModelsLabel: JBLabel
    private lateinit var progressBar: JProgressBar

    /**
     * Sets all labels to loading state
     */
    private enum class LabelState {
        LOADING, NOT_CONFIGURED, ERROR, NO_ACTIVITY
    }

    private fun setLabelsState(state: LabelState) {
        when (state) {
            LabelState.LOADING -> {
                tierLabel.text = "Account: $DEFAULT_LOADING_TEXT"
                totalCreditsLabel.text = "Total Credits: $DEFAULT_LOADING_TEXT"
                creditsUsageLabel.text = "Credits Used: $DEFAULT_LOADING_TEXT"
                creditsRemainingLabel.text = "Credits Remaining: $DEFAULT_LOADING_TEXT"
                activity24hLabel.text = "Last 24 hours: $DEFAULT_LOADING_TEXT"
                activityWeekLabel.text = "Last week: $DEFAULT_LOADING_TEXT"
                activityModelsLabel.text = LOADING_MODELS_HTML
            }
            LabelState.NOT_CONFIGURED -> {
                tierLabel.text = "Account: $NOT_CONFIGURED_MESSAGE"
                totalCreditsLabel.text = "Total Credits: $NOT_CONFIGURED_TEXT"
                creditsUsageLabel.text = "Credits Used: $NOT_CONFIGURED_TEXT"
                creditsRemainingLabel.text = "Credits Remaining: $NOT_CONFIGURED_TEXT"
                activity24hLabel.text = "Last 24 hours: $NOT_CONFIGURED_TEXT"
                activityWeekLabel.text = "Last week: $NOT_CONFIGURED_TEXT"
                activityModelsLabel.text = "<html>Recent Models:<br/>• $NOT_CONFIGURED_TEXT</html>"
            }
            LabelState.ERROR -> {
                tierLabel.text = "Account: $ERROR_MESSAGE"
                totalCreditsLabel.text = "Total Credits: $ERROR_TEXT"
                creditsUsageLabel.text = "Credits Used: $ERROR_TEXT"
                creditsRemainingLabel.text = "Credits Remaining: $ERROR_TEXT"
                activity24hLabel.text = "Last 24 hours: $ERROR_TEXT"
                activityWeekLabel.text = "Last week: $ERROR_TEXT"
                activityModelsLabel.text = "<html>Recent Models:<br/>• $ERROR_TEXT</html>"
            }
            LabelState.NO_ACTIVITY -> {
                activity24hLabel.text = "Last 24 hours: $NO_ACTIVITY_TEXT"
                activityWeekLabel.text = "Last week: $NO_ACTIVITY_TEXT"
                activityModelsLabel.text = NO_RECENT_MODELS_HTML
            }
        }
    }

    /**
     * Sets progress bar to a specific state
     */
    private fun setProgressBarState(value: Int = 0, text: String, indeterminate: Boolean = false) {
        progressBar.value = value
        progressBar.string = text
        progressBar.isIndeterminate = indeterminate
    }

    fun showDialog() {
        show()
    }

    override fun show() {
        // Start data loading in a separate thread immediately after show() is called
        // This avoids the EDT blocking issue with super.show()
        Thread {
            // Wait a moment for dialog to be fully displayed
            Thread.sleep(DIALOG_SHOW_DELAY_MS.toLong())
            loadData()
        }.start()

        super.show()
    }

    override fun createCenterPanel(): JComponent {
        return createMainPanel()
    }

    override fun createActions(): Array<Action> {
        // Include Refresh, Settings, and Close buttons in the same line
        return arrayOf(
            createDialogAction("Refresh") { loadData() },
            createDialogAction("Settings") {
                close(OK_EXIT_CODE)
                openSettings()
            },
            createDialogAction("Close") { close(OK_EXIT_CODE) }
        )
    }

    private fun createDialogAction(name: String, action: () -> Unit): Action {
        return object : DialogWrapperAction(name) {
            override fun doAction(e: java.awt.event.ActionEvent?) {
                action()
            }
        }
    }

    private fun createMainPanel(): JPanel {
        val mainPanel = JBPanel<JBPanel<*>>(BorderLayout()).apply {
            // Set minimum size but allow content to expand
            preferredSize = Dimension(MAIN_PANEL_WIDTH, MAIN_PANEL_HEIGHT)
            minimumSize = Dimension(MAIN_PANEL_WIDTH, MAIN_PANEL_MIN_HEIGHT)
            border = JBUI.Borders.empty(MAIN_PANEL_BORDER)
        }

        // Header with icon and title
        val headerPanel = createHeaderPanel()
        mainPanel.add(headerPanel, BorderLayout.NORTH)

        // Stats content
        val statsPanel = createStatsPanel()
        mainPanel.add(statsPanel, BorderLayout.CENTER)

        // Buttons are now in dialog actions (bottom bar)

        return mainPanel
    }

    private fun createHeaderPanel(): JPanel {
        val headerPanel = JBPanel<JBPanel<*>>(FlowLayout(FlowLayout.LEFT, 0, 0))

        val iconLabel = JBLabel(OpenRouterIcons.STATUS_BAR)
        val titleLabel = JBLabel("OpenRouter API").apply {
            font = font.deriveFont(Font.BOLD, FONT_SIZE_TITLE)
        }

        headerPanel.add(iconLabel)
        headerPanel.add(Box.createHorizontalStrut(HEADER_ICON_GAP))
        headerPanel.add(titleLabel)

        return headerPanel
    }

    private fun createStatsPanel(): JPanel {
        val statsPanel = JBPanel<JBPanel<*>>().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            border = JBUI.Borders.empty(STATS_PANEL_BORDER_TOP, 0)
            minimumSize = Dimension(STATS_PANEL_WIDTH, STATS_PANEL_MIN_HEIGHT)
            preferredSize = Dimension(STATS_PANEL_WIDTH, STATS_PANEL_HEIGHT)
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
        progressBar = JProgressBar(0, PROGRESS_BAR_MAX).apply {
            isStringPainted = true
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
        statsPanel.add(Box.createVerticalStrut(LABEL_SPACING))

        // Credits section
        statsPanel.add(totalCreditsLabel)
        statsPanel.add(Box.createVerticalStrut(ACTIVITY_SECTION_SPACING))
        statsPanel.add(creditsUsageLabel)
        statsPanel.add(Box.createVerticalStrut(ACTIVITY_SECTION_SPACING))
        statsPanel.add(creditsRemainingLabel)
        statsPanel.add(Box.createVerticalStrut(LABEL_SPACING))

        statsPanel.add(progressBar)
        statsPanel.add(Box.createVerticalStrut(PROGRESS_BAR_HEIGHT.toInt()))

        // Add separator
        val separator = JSeparator()
        statsPanel.add(separator)
        statsPanel.add(Box.createVerticalStrut(LABEL_SPACING))

        // Recent activity section
        val recentLabel = JBLabel("Recent Activity").apply {
            font = font.deriveFont(Font.BOLD, FONT_SIZE_LABEL)
        }
        statsPanel.add(recentLabel)
        statsPanel.add(Box.createVerticalStrut(ACTIVITY_SECTION_SPACING))
        statsPanel.add(activity24hLabel)
        statsPanel.add(Box.createVerticalStrut(2)) // Small spacing between activity items
        statsPanel.add(activityWeekLabel)
        statsPanel.add(Box.createVerticalStrut(ACTIVITY_SECTION_SPACING))
        statsPanel.add(activityModelsLabel)
        statsPanel.add(Box.createVerticalStrut(LABEL_SPACING))

        // Local tracking section - TEMPORARILY COMMENTED OUT
        // NOTE: Re-enable when local activity tracking is ready
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

    private fun loadData() {
        setLoadingState()
        dataLoader.loadData { result ->
            when (result) {
                is StatsDataLoader.LoadResult.Success -> {
                    updateWithApiKeysList(result.data.apiKeysResponse)
                    updateWithCredits(result.data.creditsResponse)
                    updateWithActivity(result.data.activityResponse)
                }
                is StatsDataLoader.LoadResult.Error -> {
                    showErrorState(LabelState.ERROR, result.message)
                }
                is StatsDataLoader.LoadResult.NotConfigured -> {
                    showErrorState(LabelState.NOT_CONFIGURED, NOT_CONFIGURED_MESSAGE)
                }
                is StatsDataLoader.LoadResult.ProvisioningKeyMissing -> {
                    showProvisioningKeyError()
                }
            }
        }
    }

    private fun setLoadingState() {
        setLabelsState(LabelState.LOADING)
        setProgressBarState(text = DEFAULT_LOADING_TEXT, indeterminate = true)
    }

    private fun updateWithApiKeysList(apiKeysResponse: ApiKeysListResponse) {
        val enabledKeys = apiKeysResponse.data.filter { !it.disabled }
        tierLabel.text = "Account: ${enabledKeys.size} API Key${if (enabledKeys.size != 1) "s" else ""} Active"

        // Update tracking information - TEMPORARILY COMMENTED OUT
        // updateTrackingInfo() // NOTE: Re-enable when local activity tracking is ready
    }

    // TEMPORARILY COMMENTED OUT - NOTE: Re-enable when local activity tracking is ready
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
        val credits = creditsResponse.data
        val totalCredits = credits.totalCredits
        val usedCredits = credits.totalUsage
        val remainingCredits = totalCredits - usedCredits

        totalCreditsLabel.text = "Total Credits: $${formatCurrency(totalCredits)}"
        creditsUsageLabel.text = "Credits Used: $${formatCurrency(usedCredits)}"
        creditsRemainingLabel.text = "Credits Remaining: $${formatCurrency(remainingCredits)}"

        // Update progress bar with credits information
        if (totalCredits > 0) {
            val percentage = ((usedCredits / totalCredits) * PERCENTAGE_MULTIPLIER).toInt()
            setProgressBarState(
                percentage,
                "$percentage% used ($${formatCurrency(usedCredits)}/$${formatCurrency(totalCredits)})"
            )
        } else {
            setProgressBarState(text = "No credits available")
        }
    }

    private fun updateWithActivity(activityResponse: ActivityResponse?) {
        if (activityResponse == null || activityResponse.data.isEmpty()) {
            setLabelsState(LabelState.NO_ACTIVITY)
            return
        }

        val activities = activityResponse.data
        val today = LocalDate.now(java.time.ZoneId.of("UTC"))
        val yesterday = today.minusDays(1L)
        val weekAgo = today.minusDays((ACTIVITY_DAYS_WEEK - 1).toLong()) // Last 7 days including today

        // Filter activities by time periods
        val last24h = filterActivitiesByTime(activities, today, yesterday, weekAgo, isLast24h = true)
        val lastWeek = filterActivitiesByTime(activities, today, yesterday, weekAgo, isLast24h = false)

        // Calculate and display stats
        val (requests24h, usage24h) = calculateActivityStats(last24h)
        val (requestsWeek, usageWeek) = calculateActivityStats(lastWeek)

        activity24hLabel.text = "Last 24 hours: ${formatActivityText(requests24h, usage24h)}"
        activityWeekLabel.text = "Last week: ${formatActivityText(requestsWeek, usageWeek)}"

        // Get and display recent models
        val recentModelNames = extractRecentModelNames(lastWeek)
        activityModelsLabel.text = buildModelsHtmlList(recentModelNames)
    }

    private fun showErrorState(state: LabelState, message: String) {
        setLabelsState(state)
        setProgressBarState(text = message)
    }

    private fun showProvisioningKeyError() {
        tierLabel.text = "Account: Provisioning Key Required"
        totalCreditsLabel.text = "Total Credits: Configure provisioning key in settings"
        creditsUsageLabel.text = "Credits Used: Configure provisioning key in settings"
        creditsRemainingLabel.text = "Credits Remaining: Configure provisioning key in settings"
        activity24hLabel.text = "Last 24 hours: Configure provisioning key in settings"
        activityWeekLabel.text = "Last week: Configure provisioning key in settings"
        activityModelsLabel.text = buildString {
            append("<html>Recent Models:<br/>")
            append("• Go to Settings → OpenRouter<br/>")
            append("• Add your Provisioning Key<br/>")
            append("• Get it from openrouter.ai/keys</html>")
        }
        setProgressBarState(text = "Provisioning Key Required - Click Settings")
    }

    private fun openSettings() {
        ApplicationManager.getApplication().invokeLater {
            com.intellij.openapi.options.ShowSettingsUtil.getInstance()
                .showSettingsDialog(project, "OpenRouter")
        }
    }

    /**
     * Formats a currency value as a string with dollar sign and appropriate precision
     */
    private fun formatCurrency(value: Double, decimals: Int = 3): String {
        return String.format(java.util.Locale.US, "%." + decimals + "f", value)
    }

    /**
     * Creates formatted activity text for requests/usage
     */
    private fun formatActivityText(requests: Long, usage: Double): String {
        return "$requests requests, $${formatCurrency(usage, CURRENCY_DECIMAL_PLACES)} spent"
    }

    /**
     * Builds HTML-formatted recent models list
     */
    private fun buildModelsHtmlList(models: List<String>): String {
        return when {
            models.isEmpty() -> NO_RECENT_MODELS_HTML
            else -> {
                val displayModels = models.take(ACTIVITY_DISPLAY_LIMIT) // Show up to 5 models
                val bullets = displayModels.joinToString("<br/>") { "• $it" }
                val moreText = if (models.size > ACTIVITY_DISPLAY_LIMIT) {
                    "<br/>• +${models.size - ACTIVITY_DISPLAY_LIMIT} more"
                } else {
                    ""
                }
                "<html>Recent Models:<br/>$bullets$moreText</html>"
            }
        }
    }

    /**
     * Calculates total requests and usage from activity list
     */
    private fun calculateActivityStats(activities: List<ActivityData>): Pair<Long, Double> {
        val requests = activities.sumOf { (it.requests ?: 0).toLong() }
        val usage = activities.sumOf { it.usage ?: 0.0 }
        return Pair(requests, usage)
    }

    /**
     * Filters activities by time period
     */
    private fun filterActivitiesByTime(
        activities: List<ActivityData>,
        today: LocalDate,
        yesterday: LocalDate,
        weekAgo: LocalDate,
        isLast24h: Boolean
    ): List<ActivityData> {
        return if (isLast24h) {
            activities.filter { activity ->
                val dateStr = activity.date
                if (dateStr != null) {
                    val activityDate = parseActivityDate(dateStr)
                    activityDate?.let { date ->
                        date.isEqual(today) || date.isEqual(yesterday)
                    } ?: false
                } else {
                    false
                }
            }
        } else {
            activities.filter { activity ->
                val dateStr = activity.date
                if (dateStr != null) {
                    val activityDate = parseActivityDate(dateStr)
                    activityDate?.let { date ->
                        !date.isBefore(weekAgo) && !date.isAfter(today)
                    } ?: false
                } else {
                    false
                }
            }
        }
    }

    /**
     * Extracts model names sorted by most recent usage
     */
    private fun extractRecentModelNames(activities: List<ActivityData>): List<String> {
        return activities
            .filter { it.model != null && it.date != null }
            .groupBy { it.model!! }
            .mapValues { (_, activities) -> activities.maxOf { it.date!! } }
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
            if (dateString.length == DATE_ONLY_LENGTH) {
                LocalDate.parse(dateString)
            } else {
                // Extract just the date part from datetime string
                val datePart = dateString.substring(0, DATE_ONLY_LENGTH)
                LocalDate.parse(datePart)
            }
        } catch (e: java.time.format.DateTimeParseException) {
            // Log the problematic date format for debugging
            org.zhavoronkov.openrouter.utils.PluginLogger.Service.warn(
                "Failed to parse activity date: '$dateString' - ${e.message}",
                e
            )
            null
        } catch (_: StringIndexOutOfBoundsException) {
            // Date string too short
            org.zhavoronkov.openrouter.utils.PluginLogger.Service.warn(
                "Failed to parse activity date: '$dateString' - string too short"
            )
            null
        } catch (e: IllegalArgumentException) {
            // Invalid date format
            org.zhavoronkov.openrouter.utils.PluginLogger.Service.warn(
                "Failed to parse activity date: '$dateString' - ${e.message}",
                e
            )
            null
        }
    }
}
