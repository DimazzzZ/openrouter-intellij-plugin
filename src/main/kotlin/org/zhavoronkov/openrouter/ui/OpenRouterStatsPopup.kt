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
// import org.zhavoronkov.openrouter.services.OpenRouterGenerationTrackingService // TEMPORARILY COMMENTED OUT
import org.zhavoronkov.openrouter.services.OpenRouterService
import org.zhavoronkov.openrouter.services.OpenRouterSettingsService
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Font
import java.time.LocalDate
import java.util.Locale
import java.util.concurrent.CompletableFuture
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
class OpenRouterStatsPopup(private val project: Project) : DialogWrapper(project) {

    init {
        title = "OpenRouter Statistics"
        init()
    }

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


    // Utility methods are now in OpenRouterStatsUtils

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
        // Ensure dialog is showing before updating UI components
        if (!isShowing) return

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
        // Ensure dialog is showing before updating UI components
        if (!isShowing) return

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
     * Sets activity state when no recent activity
     */
    private fun setActivityLabelsToNoActivity() {
        // Ensure dialog is showing before updating UI components
        if (!isShowing) return

        activity24hLabel.text = "Last 24 hours: $NO_ACTIVITY_TEXT"
        activityWeekLabel.text = "Last week: $NO_ACTIVITY_TEXT"
        activityModelsLabel.text = NO_RECENT_MODELS_HTML
    }

    fun showDialog() {
        show()
        // Defer data loading until dialog is fully displayed to avoid component location issues
        ApplicationManager.getApplication().invokeLater {
            // Use double invokeLater to ensure dialog is fully painted
            javax.swing.SwingUtilities.invokeLater {
                loadData()
            }
        }
    }

    override fun createCenterPanel(): JComponent {
        return createMainPanel()
    }

    override fun createActions(): Array<Action> {
        // Include Refresh, Settings, and Close buttons in the same line
        return arrayOf(createRefreshAction(), createSettingsAction(), createCloseAction())
    }

    private fun createRefreshAction(): Action {
        return object : DialogWrapperAction("Refresh") {
            override fun doAction(e: java.awt.event.ActionEvent?) {
                loadData()
            }
        }
    }

    private fun createSettingsAction(): Action {
        return object : DialogWrapperAction("Settings") {
            override fun doAction(e: java.awt.event.ActionEvent?) {
                close(OK_EXIT_CODE)  // Close this dialog first
                openSettings()
            }
        }
    }

    private fun createCloseAction(): Action {
        return object : DialogWrapperAction("Close") {
            override fun doAction(e: java.awt.event.ActionEvent?) {
                close(OK_EXIT_CODE)
            }
        }
    }

    private fun createMainPanel(): JPanel {
        val mainPanel = JBPanel<JBPanel<*>>(BorderLayout()).apply {
            // Set minimum size but allow content to expand
            preferredSize = Dimension(450, 350)
            minimumSize = Dimension(450, 300)
            border = JBUI.Borders.empty(12)
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

        // Check if provisioning key is available (required for quota endpoints)
        val provisioningKey = settings.getProvisioningKey()
        if (provisioningKey.isBlank()) {
            showProvisioningKeyError()
            return
        }

        // Show loading state
        setLoadingState()
        // Fetch API keys, credits, and activity information
        val apiKeysFuture = routerService.getApiKeysList()
        val creditsFuture = routerService.getCredits()
        val activityFuture = routerService.getActivity()

        // Use CompletableFuture.allOf to wait for all results and handle them together
        CompletableFuture.allOf(apiKeysFuture, creditsFuture, activityFuture)
            .thenAccept {
                try {
                    val apiKeysResponse = apiKeysFuture.get()
                    val creditsResponse = creditsFuture.get()
                    val activityResponse = activityFuture.get()

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
                } catch (e: Exception) {
                    ApplicationManager.getApplication().invokeLater { showError() }
                }
            }
            .exceptionally { ex ->
                ApplicationManager.getApplication().invokeLater { showError() }
                null
            }
    }

    private fun setLoadingState() {

        setLabelsToLoading()
        setProgressBarState(text = DEFAULT_LOADING_TEXT, indeterminate = true)
    }

    private fun updateWithApiKeysList(apiKeysResponse: ApiKeysListResponse) {
        val enabledKeys = apiKeysResponse.data.filter { !it.disabled }
        tierLabel.text = "Account: ${enabledKeys.size} API Key${if (enabledKeys.size != 1) "s" else ""} Active"

        // Update tracking information - TEMPORARILY COMMENTED OUT
        // updateTrackingInfo() // TODO: Re-enable when local activity tracking is ready
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

        totalCreditsLabel.text = "Total Credits: $${OpenRouterStatsUtils.formatCurrency(totalCredits)}"
        creditsUsageLabel.text = "Credits Used: $${OpenRouterStatsUtils.formatCurrency(usedCredits)}"
        creditsRemainingLabel.text = "Credits Remaining: $${OpenRouterStatsUtils.formatCurrency(remainingCredits)}"

        // Update progress bar with credits information
        if (totalCredits > 0) {
            val percentage = ((usedCredits / totalCredits) * 100).toInt()
            setProgressBarState(percentage, "${percentage}% used ($${OpenRouterStatsUtils.formatCurrency(usedCredits)}/$${OpenRouterStatsUtils.formatCurrency(totalCredits)})")
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
        val last24h = OpenRouterStatsUtils.filterActivitiesByTime(activities, today, yesterday, weekAgo, isLast24h = true)
        val lastWeek = OpenRouterStatsUtils.filterActivitiesByTime(activities, today, yesterday, weekAgo, isLast24h = false)

        // Calculate and display stats
        val (requests24h, usage24h) = OpenRouterStatsUtils.calculateActivityStats(last24h)
        activity24hLabel.text = "Last 24 hours: ${OpenRouterStatsUtils.formatActivityText(requests24h, usage24h)}"

        val (requestsWeek, usageWeek) = OpenRouterStatsUtils.calculateActivityStats(lastWeek)
        activityWeekLabel.text = "Last week: ${OpenRouterStatsUtils.formatActivityText(requestsWeek, usageWeek)}"

        // Get and display recent models
        val recentModelNames = OpenRouterStatsUtils.extractRecentModelNames(lastWeek)
        activityModelsLabel.text = OpenRouterStatsUtils.buildModelsHtmlList(recentModelNames)
    }









    private fun showNotConfigured() {
        setLabelsToNotConfigured()
        setProgressBarState(text = NOT_CONFIGURED_MESSAGE)
    }

    private fun showError() {
        setLabelsToError()
        setProgressBarState(text = ERROR_MESSAGE)
    }

    private fun showProvisioningKeyError() {
        tierLabel.text = "Account: Provisioning Key Required"
        totalCreditsLabel.text = "Total Credits: Provisioning key needed"
        creditsUsageLabel.text = "Credits Used: Provisioning key needed"
        creditsRemainingLabel.text = "Credits Remaining: Provisioning key needed"
        activity24hLabel.text = "Last 24 hours: Provisioning key needed"
        activityWeekLabel.text = "Last week: Provisioning key needed"
        activityModelsLabel.text = "<html>Recent Models:<br/>• Provisioning key required for quota data</html>"
        setProgressBarState(text = "Provisioning Key Required")
    }

    private fun openSettings() {
        ApplicationManager.getApplication().invokeLater {
            com.intellij.openapi.options.ShowSettingsUtil.getInstance()
                .showSettingsDialog(project, "OpenRouter")
        }
    }
}
