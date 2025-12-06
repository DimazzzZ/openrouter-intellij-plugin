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
import kotlinx.coroutines.*
import org.zhavoronkov.openrouter.models.ApiResult
import org.zhavoronkov.openrouter.models.ConnectionStatus
import org.zhavoronkov.openrouter.services.OpenRouterProxyService
import org.zhavoronkov.openrouter.services.OpenRouterService
import org.zhavoronkov.openrouter.services.OpenRouterSettingsService
import org.zhavoronkov.openrouter.ui.OpenRouterStatsPopup
import java.awt.event.MouseEvent
import java.util.Locale
import javax.swing.Icon

/**
 * Enhanced status bar widget with comprehensive popup menu for OpenRouter
 */
class OpenRouterStatusBarWidget(project: Project) : EditorBasedWidget(project), StatusBarWidget.IconPresentation {

    private val openRouterService = OpenRouterService.getInstance()
    private val settingsService = OpenRouterSettingsService.getInstance()
    private val proxyService = OpenRouterProxyService.getInstance()

    private var connectionStatus = ConnectionStatus.NOT_CONFIGURED
    private var currentText = "Status: Not Configured"
    private var currentTooltip = "OpenRouter Status: Not Configured - Usage: Not available"

    companion object {
        const val ID = "OpenRouterStatusBar"
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

        // Status Display
        items.add(
            PopupMenuItem(
                text = "Status: ${connectionStatus.displayName}",
                icon = connectionStatus.icon,
                action = null // Status display only
            )
        )

        // Quota / Usage (always show, but disable when not configured)
        items.add(
            PopupMenuItem(
                text = "View Quota Usage",
                icon = AllIcons.General.Information,
                action = if (settingsService.isConfigured()) {
                    { showQuotaUsage() }
                } else {
                    null // Disabled when not configured
                }
            )
        )

        // Authentication
        if (settingsService.isConfigured()) {
            items.add(
                PopupMenuItem(
                    text = "Logout from OpenRouter.ai",
                    icon = AllIcons.Actions.Exit,
                    action = { logout() }
                )
            )
        } else {
            items.add(
                PopupMenuItem(
                    text = "Login to OpenRouter.ai",
                    icon = AllIcons.Actions.Execute,
                    action = { openSettings() }
                )
            )
        }

        // Settings (direct action, no submenu)
        items.add(
            PopupMenuItem(
                text = "Settings",
                icon = AllIcons.General.Settings,
                action = { openSettings() }
            )
        )

        // Documentation
        items.add(
            PopupMenuItem(
                text = "View OpenRouter Documentation...",
                icon = AllIcons.Actions.Help,
                action = { openDocumentation() }
            )
        )

        // Feedback
        items.add(
            PopupMenuItem(
                text = "View Feedback Repository...",
                icon = AllIcons.Vcs.Vendors.Github,
                action = { openFeedbackRepository() }
            )
        )

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
                settingsService.setApiKey("")
                settingsService.setProvisioningKey("")
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

        connectionStatus = ConnectionStatus.CONNECTING
        updateStatusBar()

        // Launch coroutine to fetch quota information
        val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
        scope.launch {
            try {
                val quotaResult = withContext(Dispatchers.IO) {
                    openRouterService.getQuotaInfo()
                }

                ApplicationManager.getApplication().invokeLater {
                    when (quotaResult) {
                        is ApiResult.Success -> {
                            connectionStatus = ConnectionStatus.READY
                            val quota = quotaResult.data

                            currentText = if ((quota.total ?: 0.0) > 0) {
                                if (settingsService.shouldShowCosts()) {
                                    val usedFormatted = String.format(Locale.US, "%.3f", quota.used ?: 0.0)
                                    val availableFormatted = String.format(Locale.US, "%.2f", quota.remaining ?: 0.0)
                                    "Status: Ready - $$usedFormatted/${quota.total} avail"
                                } else {
                                    val percentage = ((quota.used ?: 0.0) / (quota.total ?: 1.0)) * 100
                                    "Status: Ready - ${String.format(Locale.US, "%.1f", percentage)}% used"
                                }
                            } else {
                                "Status: Ready - $${String.format(Locale.US, "%.3f", quota.used ?: 0.0)} (no credits)"
                            }

                            currentTooltip = if ((quota.total ?: 0.0) > 0) {
                                "OpenRouter Status: ${connectionStatus.displayName} - Usage: $${String.format(Locale.US, "%.3f", quota.used ?: 0.0)} of $${String.format(Locale.US, "%.0f", quota.total ?: 0.0)}"
                            } else {
                                "OpenRouter Status: ${connectionStatus.displayName} - Usage: $${String.format(Locale.US, "%.3f", quota.used ?: 0.0)} (no credits)"
                            }
                        }
                        is ApiResult.Error -> {
                            connectionStatus = ConnectionStatus.ERROR
                            currentText = "Status: Error"
                            currentTooltip = "OpenRouter Status: Error - Usage: ${quotaResult.message}"
                        }
                    }
                    updateStatusBar()
                }
            } catch (e: Exception) {
                ApplicationManager.getApplication().invokeLater {
                    connectionStatus = ConnectionStatus.ERROR
                    currentText = "Status: Error"
                    currentTooltip = "OpenRouter Status: Error - Usage: ${e.message}"
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

        currentText = "Status: ${connectionStatus.displayName}"
        currentTooltip = "OpenRouter Status: ${connectionStatus.displayName} - Usage: Not available"
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
        if (settingsService.isAutoRefreshEnabled()) {
            startAutoRefresh()
        }
    }

    private fun startAutoRefresh() {
        val refreshInterval = settingsService.getRefreshInterval()
        ApplicationManager.getApplication().executeOnPooledThread {
            while (settingsService.isAutoRefreshEnabled()) {
                try {
                    Thread.sleep(refreshInterval * 1000L)
                    ApplicationManager.getApplication().invokeLater {
                        updateQuotaInfo()
                    }
                } catch (e: InterruptedException) {
                    Thread.currentThread().interrupt()
                    break
                } catch (e: IllegalStateException) {
                    // Log error but continue
                    com.intellij.openapi.diagnostic.Logger.getInstance(OpenRouterStatusBarWidget::class.java)
                        .warn("Error in auto-refresh loop", e)
                    break
                } catch (e: SecurityException) {
                    // Log error but continue
                    com.intellij.openapi.diagnostic.Logger.getInstance(OpenRouterStatusBarWidget::class.java)
                        .warn("Security error in auto-refresh loop", e)
                    break
                }
            }
        }
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
                    return PopupStep.FINAL_CHOICE
                }
            }
        } else {
            null
        }
    }
}
