package org.zhavoronkov.openrouter.statusbar

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
import org.zhavoronkov.openrouter.listeners.OpenRouterSettingsListener
import org.zhavoronkov.openrouter.listeners.OpenRouterStatsListener
import org.zhavoronkov.openrouter.models.ActivityData
import org.zhavoronkov.openrouter.models.ConnectionStatus
import org.zhavoronkov.openrouter.models.CreditsData
import org.zhavoronkov.openrouter.services.OpenRouterService
import org.zhavoronkov.openrouter.services.OpenRouterSettingsService
import org.zhavoronkov.openrouter.services.OpenRouterStatsCache
import org.zhavoronkov.openrouter.ui.OpenRouterStatsPopup
import java.awt.event.MouseEvent
import javax.swing.Icon

/**
 * Enhanced status bar widget with comprehensive popup menu for OpenRouter
 *
 * This widget subscribes to [OpenRouterStatsListener.TOPIC] to receive updates
 * from the shared [OpenRouterStatsCache], ensuring the tooltip stays in sync
 * with data displayed in the Stats Popup dialog.
 */

@Suppress("TooManyFunctions")
class OpenRouterStatusBarWidget(project: Project) : EditorBasedWidget(project), StatusBarWidget.IconPresentation {

    private val settingsService = OpenRouterSettingsService.getInstance()
    private val statsCache = OpenRouterStatsCache.getInstance()

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

        // Time conversion: milliseconds per second
        private const val MILLIS_PER_SECOND = 1000L
    }

    override fun ID(): String = ID

    override fun getPresentation(): StatusBarWidget.WidgetPresentation = this

    override fun getTooltipText(): String = currentTooltip

    override fun getClickConsumer(): Consumer<MouseEvent> {
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
        val menuItems = StatusBarMenuBuilder.buildMenuItems(
            connectionStatus,
            settingsService.isConfigured(),
            settingsService.apiKeyManager.authScope
        )

        menuItems.forEach { item ->
            val action = when (item.text) {
                QUOTA_MENU_TEXT -> if (item.isActionEnabled) { { showQuotaUsage() } } else null
                "${QUOTA_MENU_TEXT} (Monitoring Disabled)" -> null
                LOGOUT_MENU_TEXT -> { { logout() } }
                LOGIN_MENU_TEXT -> { { openSettings() } }
                SETTINGS_MENU_TEXT -> { { openSettings() } }
                DOCUMENTATION_MENU_TEXT -> { { openDocumentation() } }
                FEEDBACK_MENU_TEXT -> { { openFeedbackRepository() } }
                else -> null
            }
            items.add(PopupMenuItem(item.text, item.icon, action))
        }

        return items
    }

    // Action methods
    private fun showQuotaUsage() {
        // Show quota usage in a modal dialog
        val openRouterService = OpenRouterService.getInstance()
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
                statsCache.clearCache()
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
     * Update the widget with current quota information.
     * This triggers a refresh of the shared stats cache, which will notify
     * all listeners (including this widget) when data is available.
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

        // Use the shared stats cache - it will notify us via the listener when data is ready
        statsCache.refresh()
    }

    /**
     * Called when stats data has been refreshed successfully from the shared cache.
     */
    private fun onStatsUpdated(credits: CreditsData, activity: List<ActivityData>?) {
        connectionStatus = ConnectionStatus.READY
        currentText = formatStatusTextFromCredits(credits.totalUsage, credits.totalCredits)
        currentTooltip = formatStatusTooltipFromCredits(credits.totalUsage, credits.totalCredits, activity)
        updateStatusBar()
    }

    /**
     * Called when stats loading has started.
     */
    private fun onStatsLoading() {
        connectionStatus = ConnectionStatus.CONNECTING
        updateStatusBar()
    }

    /**
     * Called when stats loading has failed.
     */
    private fun onStatsError(errorMessage: String) {
        connectionStatus = ConnectionStatus.ERROR
        currentText = "Status: Error"
        currentTooltip = "OpenRouter Status: Error - $errorMessage"
        updateStatusBar()
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

        // Subscribe to stats cache updates - this is the key to keeping tooltip in sync
        ApplicationManager.getApplication().messageBus.connect(this).subscribe(
            OpenRouterStatsListener.TOPIC,
            object : OpenRouterStatsListener {
                override fun onStatsUpdated(credits: CreditsData, activity: List<ActivityData>?) {
                    this@OpenRouterStatusBarWidget.onStatsUpdated(credits, activity)
                }

                override fun onStatsLoading() {
                    this@OpenRouterStatusBarWidget.onStatsLoading()
                }

                override fun onStatsError(errorMessage: String) {
                    this@OpenRouterStatusBarWidget.onStatsError(errorMessage)
                }
            }
        )

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

        // Initial update - trigger cache refresh
        updateQuotaInfo()

        // Set up auto-refresh if enabled
        if (settingsService.uiPreferencesManager.autoRefresh) {
            startAutoRefresh()
        }
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
                } catch (_: InterruptedException) {
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
        return StatusBarStatsFormatter.formatStatusTextFromCredits(
            used,
            total,
            settingsService.uiPreferencesManager.showCosts
        )
    }

    private fun formatStatusTooltipFromCredits(
        used: Double,
        total: Double,
        activityList: List<ActivityData>? = null
    ): String {
        return StatusBarStatsFormatter.formatStatusTooltipFromCredits(
            connectionStatus.displayName,
            used,
            total,
            activityList
        )
    }
}

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
                    return FINAL_CHOICE
                }
            }
        } else {
            null
        }
    }
}
