
package org.zhavoronkov.openrouter.statusbar

import com.intellij.icons.AllIcons
import com.intellij.ide.BrowserUtil
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.ListSeparator
import com.intellij.openapi.ui.popup.PopupStep
import com.intellij.openapi.ui.popup.util.BaseListPopupStep
import com.intellij.openapi.wm.StatusBar
import com.intellij.openapi.wm.StatusBarWidget
import com.intellij.openapi.wm.impl.status.EditorBasedWidget
import com.intellij.ui.awt.RelativePoint
import com.intellij.util.Consumer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import org.zhavoronkov.openrouter.listeners.OpenRouterSettingsListener
import org.zhavoronkov.openrouter.models.ActivityData
import org.zhavoronkov.openrouter.models.ApiResult
import org.zhavoronkov.openrouter.models.ConnectionStatus
import org.zhavoronkov.openrouter.services.OpenRouterService
import org.zhavoronkov.openrouter.services.OpenRouterSettingsService
import org.zhavoronkov.openrouter.ui.OpenRouterStatsPopup
import java.awt.event.MouseEvent
import java.io.IOException
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.swing.Icon

/**
 * Enhanced status bar widget with comprehensive popup menu for OpenRouter
 */

@Suppress("TooManyFunctions")
class OpenRouterStatusBarWidget(project: Project) : EditorBasedWidget(project), StatusBarWidget.IconPresentation {

    private val openRouterService = OpenRouterService.getInstance()
    private val settingsService = OpenRouterSettingsService.getInstance()

    private var connectionStatus = ConnectionStatus.NOT_CONFIGURED
    private var currentText = "Status: Not Configured"
    private var currentTooltip = """
        <html>
        <table border='0' cellpadding='1' cellspacing='0'>
          <tr><td colspan='2'><b>Connection</b></td></tr>
          <tr height='2'><td></td></tr>
          <tr><td>Status:</td><td align='right' style='padding-left: 30px;'>Not Configured</td></tr>
          <tr><td colspan='2' height='8'></td></tr>
          <tr><td colspan='2'><i>API key not set.</i></td></tr>
        </table>
        </html>
    """.trimIndent()

    companion object {
        const val ID = "OpenRouterStatusBar"
        const val STATUS_MENU_TEXT = "Status: "
        const val QUOTA_MENU_TEXT = "View Quota Usage"
        const val LOGIN_MENU_TEXT = "Login to OpenRouter.ai"
        const val LOGOUT_MENU_TEXT = "Logout from OpenRouter.ai"
        const val SETTINGS_MENU_TEXT = "Settings"
        const val DOCUMENTATION_MENU_TEXT = "View OpenRouter Documentation..."
        const val FEEDBACK_MENU_TEXT = "View Feedback Repository..."
        private const val DAYS_IN_WEEK = 7
        private const val DATE_STRING_LENGTH = 10

        // Time conversion: milliseconds per second
        private const val MILLIS_PER_SECOND = 1000L

        // Percentage calculation multiplier
        private const val PERCENTAGE_MULTIPLIER = 100
    }

    override fun ID(): String = ID

    override fun getPresentation(): StatusBarWidget.WidgetPresentation = this

    override fun getTooltipText(): String = currentTooltip

    override fun getClickConsumer(): Consumer<MouseEvent>? {
        return Consumer { event ->
            // Always show the comprehensive popup menu on any click
            showPopupMenu(event)
        }
    }

    override fun getIcon(): Icon = connectionStatus.icon

    private fun showPopupMenu(event: MouseEvent) {
        val popupStep = OpenRouterPopupStep()
        val popup = JBPopupFactory.getInstance().createListPopup(popupStep)

        // Position popup above the status bar widget
        // Use the component from the event as the anchor
        val component = event.component

        // Get the preferred size of the popup to calculate proper positioning
        val popupContent = popup.content
        val popupSize = popupContent.preferredSize

        // Get component's screen location
        val componentLocationOnScreen = component.locationOnScreen

        // Calculate position: popup should appear above the component
        // Position the popup's bottom-left corner at the component's top-left corner
        val popupX = componentLocationOnScreen.x
        val popupY = componentLocationOnScreen.y - popupSize.height

        // Create the point and show the popup
        val popupPoint = java.awt.Point(popupX, popupY)
        popup.show(RelativePoint.fromScreen(popupPoint))
    }

    private inner class OpenRouterPopupStep : BaseListPopupStep<PopupMenuItem>("OpenRouter", createMenuItems()) {

        override fun getTextFor(value: PopupMenuItem): String = value.text

        override fun getIconFor(value: PopupMenuItem): Icon? = value.icon

        override fun hasSubstep(selectedValue: PopupMenuItem): Boolean = selectedValue.hasSubmenu

        override fun getSeparatorAbove(value: PopupMenuItem): ListSeparator? {
            // Add separator only before "View Quota Usage" and before documentation items
            return when (value.text) {
                "Settings" -> ListSeparator()
                "View OpenRouter Documentation..." -> ListSeparator()
                else -> null
            }
        }

        override fun isSelectable(value: PopupMenuItem): Boolean =
            value.action != null

        override fun onChosen(selectedValue: PopupMenuItem, finalChoice: Boolean): PopupStep<*>? {
            if (selectedValue.hasSubmenu) {
                return selectedValue.createSubmenu()
            } else {
                // Defer dialog display to avoid focus issues with popup menus
                selectedValue.action?.let { action ->
                    ApplicationManager.getApplication().invokeLater { action() }
                }
                return FINAL_CHOICE
            }
        }

        override fun getDefaultOptionIndex(): Int = -1
    }

    private fun createMenuItems(): List<PopupMenuItem> {
        val items = mutableListOf<PopupMenuItem>()
        val statusText = STATUS_MENU_TEXT + connectionStatus.displayName
        items.add(PopupMenuItem(statusText, connectionStatus.icon, null))

        val isExtended = settingsService.apiKeyManager.authScope == org.zhavoronkov.openrouter.models.AuthScope.EXTENDED
        val quotaAction = if (settingsService.isConfigured() && isExtended) { { showQuotaUsage() } } else { null }
        val quotaItem = PopupMenuItem(QUOTA_MENU_TEXT, AllIcons.General.Information, quotaAction)
        if (!isExtended) {
            // Add a comment or change text to indicate it's disabled
            items.add(quotaItem.copy(text = "$QUOTA_MENU_TEXT (Monitoring Disabled)"))
        } else {
            items.add(quotaItem)
        }

        if (settingsService.isConfigured()) {
            items.add(PopupMenuItem(LOGOUT_MENU_TEXT, AllIcons.Actions.Exit, { logout() }))
        } else {
            items.add(PopupMenuItem(LOGIN_MENU_TEXT, AllIcons.Actions.Execute, { openSettings() }))
        }

        items.add(PopupMenuItem(SETTINGS_MENU_TEXT, AllIcons.General.Settings, { openSettings() }))
        items.add(PopupMenuItem(DOCUMENTATION_MENU_TEXT, AllIcons.Actions.Help, { openDocumentation() }))
        items.add(PopupMenuItem(FEEDBACK_MENU_TEXT, AllIcons.Vcs.Vendors.Github, { openFeedbackRepository() }))

        return items
    }

    // Action methods
    private fun showQuotaUsage() {
        // Show quota usage in a modal dialog
        val statsPopup = OpenRouterStatsPopup(project, openRouterService, settingsService)
        statsPopup.showDialog()
    }

    private fun logout() {
        // Defer dialog display to avoid focus issues with popup menus
        ApplicationManager.getApplication().invokeLater {
            val result = Messages.showYesNoDialog(
                project,
                "Are you sure you want to logout from OpenRouter.ai?\nThis will clear your API key.",
                "Logout Confirmation",
                Messages.getQuestionIcon()
            )

            if (result == Messages.YES) {
                settingsService.apiKeyManager.setApiKey("")
                settingsService.apiKeyManager.setProvisioningKey("")
                updateConnectionStatus()
                Messages.showInfoMessage(project, "Successfully logged out from OpenRouter.ai", "Logout")
            }
        }
    }

    private fun openSettings() {
        ApplicationManager.getApplication().invokeLater {
            ShowSettingsUtil.getInstance().showSettingsDialog(project, "OpenRouter")
        }
    }

    private fun openDocumentation() {
        BrowserUtil.browse("https://openrouter.ai/docs")
    }

    private fun openFeedbackRepository() {
        BrowserUtil.browse("https://github.com/DimazzzZ/openrouter-intellij-plugin/issues")
    }

    /**
     * Update the widget with current quota information
     */
    fun updateQuotaInfo() {
        updateConnectionStatus()

        if (!settingsService.isConfigured()) {
            return
        }

        if (settingsService.apiKeyManager.authScope == org.zhavoronkov.openrouter.models.AuthScope.REGULAR) {
            connectionStatus = ConnectionStatus.READY
            currentText = "Status: Ready"

            currentTooltip = """
                <html>
                <table border='0' cellpadding='1' cellspacing='0'>
                  <tr><td colspan='2'><b>Connection</b></td></tr>
                  <tr height='2'><td></td></tr>
                  <tr><td>Status:</td><td align='right' style='padding-left: 30px;'>Ready</td></tr>
                  <tr><td>Auth:</td><td align='right' style='padding-left: 30px;'>Regular Key</td></tr>
                  <tr><td>Monitoring:</td><td align='right' style='padding-left: 30px;'><i>Disabled</i></td></tr>
                </table>
                </html>
            """.trimIndent()

            updateStatusBar()
            return
        }

        connectionStatus = ConnectionStatus.CONNECTING
        updateStatusBar()

        // Launch coroutine to fetch credits information
        val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
        scope.launch {
            try {
                // Fetch credits and activity in parallel
                val creditsDeferred = async(Dispatchers.IO) { openRouterService.getCredits() }
                val activityDeferred = async(Dispatchers.IO) { openRouterService.getActivity() }

                val creditsResult = creditsDeferred.await()
                val activityResult = activityDeferred.await()

                ApplicationManager.getApplication().invokeLater {
                    when (creditsResult) {
                        is ApiResult.Success -> {
                            connectionStatus = ConnectionStatus.READY
                            val credits = creditsResult.data.data

                            val activityList = if (activityResult is ApiResult.Success) {
                                activityResult.data.data
                            } else {
                                null
                            }

                            currentText = formatStatusTextFromCredits(
                                credits.totalUsage,
                                credits.totalCredits
                            )
                            currentTooltip = formatStatusTooltipFromCredits(
                                credits.totalUsage,
                                credits.totalCredits,
                                activityList
                            )
                        }
                        is ApiResult.Error -> {
                            connectionStatus = ConnectionStatus.ERROR
                            currentText = "Status: Error"
                            currentTooltip = "OpenRouter Status: Error - Usage: ${creditsResult.message}"
                        }
                    }
                    updateStatusBar()
                }
            } catch (_: IllegalStateException) {
                ApplicationManager.getApplication().invokeLater {
                    connectionStatus = ConnectionStatus.ERROR
                    currentText = "Status: Error"
                    currentTooltip = "OpenRouter Status: Error - Invalid state"
                    updateStatusBar()
                }
            } catch (_: IOException) {
                ApplicationManager.getApplication().invokeLater {
                    connectionStatus = ConnectionStatus.ERROR
                    currentText = "Status: Error"
                    currentTooltip = "OpenRouter Status: Error - Network error"
                    updateStatusBar()
                }
            } catch (expectedError: Exception) {
                ApplicationManager.getApplication().invokeLater {
                    connectionStatus = ConnectionStatus.ERROR
                    currentText = "Status: Error"
                    currentTooltip = "OpenRouter Status: Error - Usage: ${expectedError.message}"
                    updateStatusBar()
                }
            }
        }
    }

    private fun updateConnectionStatus() {
        connectionStatus = if (settingsService.isConfigured()) {
            ConnectionStatus.READY
        } else {
            ConnectionStatus.NOT_CONFIGURED
        }

        // If configured with Regular key, ensure status is READY immediately
        // as we don't fetch credits for regular keys
        if (settingsService.isConfigured() &&
            settingsService.apiKeyManager.authScope == org.zhavoronkov.openrouter.models.AuthScope.REGULAR
        ) {
            connectionStatus = ConnectionStatus.READY
        }

        currentText = "Status: ${connectionStatus.displayName}"
        val isRegular = settingsService.apiKeyManager.authScope == org.zhavoronkov.openrouter.models.AuthScope.REGULAR
        val scopeText = if (isRegular) "Regular Key" else "Provisioning Key"
        val monitoringText = if (isRegular) "<i>Disabled</i>" else "Enabled"

        currentTooltip = """
            <html>
            <table border='0' cellpadding='1' cellspacing='0'>
              <tr><td colspan='2'><b>Connection</b></td></tr>
              <tr height='2'><td></td></tr>
              <tr><td>Status:</td><td align='right' style='padding-left: 30px;'>${connectionStatus.displayName}</td></tr>
              <tr><td>Auth:</td><td align='right' style='padding-left: 30px;'>$scopeText</td></tr>
              <tr><td>Monitoring:</td><td align='right' style='padding-left: 30px;'>$monitoringText</td></tr>
            </table>
            </html>
        """.trimIndent()

        updateStatusBar()
    }

    private fun updateStatusBar() {
        myStatusBar?.updateWidget(ID)
    }

    override fun install(statusBar: StatusBar) {
        super.install(statusBar)
        // Initial update
        updateQuotaInfo()

        // Set up auto-refresh if enabled
        if (settingsService.uiPreferencesManager.autoRefresh) {
            startAutoRefresh()
        }

        // Subscribe to settings changes
        project.messageBus.connect(this).subscribe(
            OpenRouterSettingsListener.TOPIC,
            object : OpenRouterSettingsListener {
                override fun onSettingsChanged() {
                    ApplicationManager.getApplication().invokeLater {
                        updateConnectionStatus()
                        updateQuotaInfo()
                    }
                }
            }
        )
    }

    private fun startAutoRefresh() {
        val refreshInterval = settingsService.uiPreferencesManager.refreshInterval
        ApplicationManager.getApplication().executeOnPooledThread {
            var shouldContinueRefresh = true
            while (settingsService.uiPreferencesManager.autoRefresh && shouldContinueRefresh) {
                try {
                    Thread.sleep(refreshInterval * MILLIS_PER_SECOND)
                    ApplicationManager.getApplication().invokeLater {
                        updateQuotaInfo()
                    }
                } catch (e: InterruptedException) {
                    Thread.currentThread().interrupt()
                    shouldContinueRefresh = false
                } catch (e: IllegalStateException) {
                    // Log error but continue
                    com.intellij.openapi.diagnostic.Logger.getInstance(OpenRouterStatusBarWidget::class.java)
                        .warn("Error in auto-refresh loop", e)
                    shouldContinueRefresh = false
                } catch (e: SecurityException) {
                    // Log error but continue
                    com.intellij.openapi.diagnostic.Logger.getInstance(OpenRouterStatusBarWidget::class.java)
                        .warn("Security error in auto-refresh loop", e)
                    shouldContinueRefresh = false
                }
            }
        }
    }

    private fun formatStatusTextFromCredits(used: Double, total: Double): String {
        return if (total > 0) {
            if (settingsService.uiPreferencesManager.showCosts) {
                val usedFormatted = String.format(Locale.US, "%.3f", used)
                val totalFormatted = String.format(Locale.US, "%.2f", total)
                "Status: Ready - $$usedFormatted/$$totalFormatted"
            } else {
                val percentage = (used / total) * PERCENTAGE_MULTIPLIER
                "Status: Ready - ${String.format(Locale.US, "%.1f", percentage)}% used"
            }
        } else {
            "Status: Ready - $${String.format(Locale.US, "%.3f", used)} (no credits)"
        }
    }

    private fun formatStatusTooltipFromCredits(
        used: Double,
        total: Double,
        activityList: List<ActivityData>? = null
    ): String {
        val status = connectionStatus.displayName
        val usedText = "$${String.format(Locale.US, "%.3f", used)}"
        val totalText = if (total > 0) "$${String.format(Locale.US, "%.2f", total)}" else "Unlimited"

        val activityRows = if (activityList != null) {
            calculateActivityRows(activityList)
        } else {
            ""
        }

        return """
            <html>
            <table border='0' cellpadding='1' cellspacing='0'>
              <tr><td colspan='2'><b>Connection</b></td></tr>
              <tr height='2'><td></td></tr>
              <tr><td>Status:</td><td align='right' style='padding-left: 30px;'>$status</td></tr>
              <tr><td>Auth:</td><td align='right' style='padding-left: 30px;'>Provisioning Key</td></tr>
              <tr height='8'><td></td></tr>
              <tr><td colspan='2'><b>Credits</b></td></tr>
              <tr height='2'><td></td></tr>
              <tr><td>Used:</td><td align='right' style='padding-left: 30px;'>$usedText</td></tr>
              <tr><td>Total:</td><td align='right' style='padding-left: 30px;'>$totalText</td></tr>
              $activityRows
            </table>
            </html>
        """.trimIndent()
    }

    private fun calculateActivityRows(activityList: List<ActivityData>): String {
        val utcNow = LocalDate.now(ZoneId.of("UTC"))
        val yesterday = utcNow.minusDays(1)
        val lastWeekStart = utcNow.minusDays((DAYS_IN_WEEK - 1).toLong())

        val costs = ActivityCosts()

        activityList.forEach { activity ->
            processActivity(activity, utcNow, yesterday, lastWeekStart, costs)
        }

        return formatActivityRowsHtml(costs.today, costs.yesterday, costs.lastWeek)
    }

    private fun processActivity(
        activity: ActivityData,
        utcNow: LocalDate,
        yesterday: LocalDate,
        lastWeekStart: LocalDate,
        costs: ActivityCosts
    ) {
        try {
            val dateStr = activity.date ?: return
            val usage = activity.usage ?: 0.0
            val activityDate = parseActivityDate(dateStr)

            when {
                activityDate.isEqual(utcNow) -> costs.today += usage
                activityDate.isEqual(yesterday) -> costs.yesterday += usage
            }

            if (!activityDate.isBefore(lastWeekStart) && !activityDate.isAfter(utcNow)) {
                costs.lastWeek += usage
            }
        } catch (e: Exception) {
            com.intellij.openapi.diagnostic.Logger.getInstance(OpenRouterStatusBarWidget::class.java)
                .warn("Error parsing activity date: ${activity.date}", e)
        }
    }

    private fun parseActivityDate(dateStr: String): LocalDate {
        val datePart = if (dateStr.length > DATE_STRING_LENGTH) {
            dateStr.substring(0, DATE_STRING_LENGTH)
        } else {
            dateStr
        }
        return LocalDate.parse(datePart, DateTimeFormatter.ISO_DATE)
    }

    private fun formatActivityRowsHtml(todayCost: Double, yesterdayCost: Double, lastWeekCost: Double): String {
        val todayText = "$${String.format(Locale.US, "%.3f", todayCost)}"
        val yesterdayText = "$${String.format(Locale.US, "%.3f", yesterdayCost)}"
        val lastWeekText = "$${String.format(Locale.US, "%.3f", lastWeekCost)}"

        return """
          <tr height='8'><td></td></tr>
          <tr><td colspan='2'><b>Activity</b></td></tr>
          <tr height='2'><td></td></tr>
          <tr><td>Today:</td><td align='right' style='padding-left: 30px;'>$todayText</td></tr>
          <tr><td>Yesterday:</td><td align='right' style='padding-left: 30px;'>$yesterdayText</td></tr>
          <tr><td>7 Days:</td><td align='right' style='padding-left: 30px;'>$lastWeekText</td></tr>
        """.trimIndent()
    }
}

/**
 * Holds activity cost data for different time periods
 */
private data class ActivityCosts(
    var today: Double = 0.0,
    var yesterday: Double = 0.0,
    var lastWeek: Double = 0.0
)

/**
 * Represents a menu item in the popup menu
 */
data class PopupMenuItem(
    val text: String,
    val icon: Icon? = null,
    val action: (() -> Unit)? = null,
    val hasSubmenu: Boolean = false,
    val submenuItems: List<PopupMenuItem> = emptyList(),
    val shortcut: String? = null
) {
    fun createSubmenu(): PopupStep<PopupMenuItem>? {
        return if (hasSubmenu && submenuItems.isNotEmpty()) {
            object : BaseListPopupStep<PopupMenuItem>(text, submenuItems) {
                override fun getTextFor(value: PopupMenuItem): String = value.text
                override fun getIconFor(value: PopupMenuItem): Icon? = value.icon
                override fun onChosen(selectedValue: PopupMenuItem, finalChoice: Boolean): PopupStep<*>? {
                    selectedValue.action?.invoke()
                    return PopupStep.FINAL_CHOICE
                }
            }
        } else {
            null
        }
    }
}
