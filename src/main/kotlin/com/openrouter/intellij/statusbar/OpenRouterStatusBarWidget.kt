package com.openrouter.intellij.statusbar

import com.intellij.icons.AllIcons
import com.intellij.ide.BrowserUtil
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.keymap.KeymapManager
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.ListPopup
import com.intellij.openapi.ui.popup.PopupStep
import com.intellij.openapi.ui.popup.util.BaseListPopupStep
import com.intellij.openapi.wm.StatusBar
import com.intellij.openapi.wm.StatusBarWidget
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.openapi.wm.impl.status.EditorBasedWidget
import com.intellij.util.Consumer
import com.openrouter.intellij.icons.OpenRouterIcons
import com.openrouter.intellij.models.ConnectionStatus
import com.openrouter.intellij.services.OpenRouterService
import com.openrouter.intellij.services.OpenRouterSettingsService
import com.openrouter.intellij.ui.OpenRouterChatWindow
import com.openrouter.intellij.ui.OpenRouterStatsPopup
import java.awt.event.MouseEvent
import javax.swing.Icon

/**
 * Enhanced status bar widget with comprehensive popup menu for OpenRouter
 */
class OpenRouterStatusBarWidget(project: Project) : EditorBasedWidget(project), StatusBarWidget.IconPresentation {

    private val openRouterService = OpenRouterService.getInstance()
    private val settingsService = OpenRouterSettingsService.getInstance()

    private var connectionStatus = ConnectionStatus.NOT_CONFIGURED
    private var currentText = "Status: Not Configured"
    private var currentTooltip = "OpenRouter - Click to view menu"

    companion object {
        const val ID = "OpenRouterStatusBar"
        const val CHAT_ACTION_ID = "OpenRouter.OpenChat"
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
        popup.showUnderneathOf(event.component)
    }

    private inner class OpenRouterPopupStep : BaseListPopupStep<PopupMenuItem>("OpenRouter", createMenuItems()) {

        override fun getTextFor(value: PopupMenuItem): String = value.text

        override fun getIconFor(value: PopupMenuItem): Icon? = value.icon

        override fun hasSubstep(selectedValue: PopupMenuItem): Boolean = selectedValue.hasSubmenu

        override fun onChosen(selectedValue: PopupMenuItem, finalChoice: Boolean): PopupStep<*>? {
            if (selectedValue.hasSubmenu) {
                return selectedValue.createSubmenu()
            } else {
                selectedValue.action?.invoke()
                return FINAL_CHOICE
            }
        }

        override fun getDefaultOptionIndex(): Int = -1
    }
    
    private fun createMenuItems(): List<PopupMenuItem> {
        return listOf(
            // Status Display
            PopupMenuItem(
                text = "Status: ${connectionStatus.displayName}",
                icon = connectionStatus.icon,
                action = null // Status display only
            ),
            PopupMenuItem.SEPARATOR,

            // Quota / Usage
            PopupMenuItem(
                text = "View Quota Usage",
                icon = AllIcons.General.Information,
                action = { showQuotaUsage() }
            ),

            // Authentication
            if (settingsService.isConfigured()) {
                PopupMenuItem(
                    text = "Logout from OpenRouter.ai",
                    icon = AllIcons.Actions.Exit,
                    action = { logout() }
                )
            } else {
                PopupMenuItem(
                    text = "Login to OpenRouter.ai",
                    icon = AllIcons.Actions.Execute,
                    action = { openSettings() }
                )
            },

            PopupMenuItem.SEPARATOR,

            // Chat / Console
            PopupMenuItem(
                text = "Open Chat",
                icon = AllIcons.Toolwindows.ToolWindowMessages,
                action = { openChat() },
                shortcut = "⇧⌃C"
            ),

            PopupMenuItem.SEPARATOR,

            // Settings submenu
            PopupMenuItem(
                text = "Settings",
                icon = AllIcons.General.Settings,
                hasSubmenu = true,
                submenuItems = listOf(
                    PopupMenuItem(
                        text = "Edit Settings...",
                        icon = AllIcons.General.Settings,
                        action = { openSettings() }
                    ),
                    PopupMenuItem(
                        text = "Show Keymap Settings...",
                        icon = AllIcons.General.Settings,
                        action = { openKeymapSettings() }
                    )
                )
            ),

            PopupMenuItem.SEPARATOR,

            // Documentation
            PopupMenuItem(
                text = "View OpenRouter Documentation...",
                icon = AllIcons.Actions.Help,
                action = { openDocumentation() }
            ),

            // Feedback
            PopupMenuItem(
                text = "View Feedback Repository...",
                icon = AllIcons.Vcs.Vendors.Github,
                action = { openFeedbackRepository() }
            )
        )
    }
    
    // Action methods
    private fun showQuotaUsage() {
        // Open the tool window instead of popup
        val toolWindowManager = ToolWindowManager.getInstance(project)
        val toolWindow = toolWindowManager.getToolWindow("OpenRouter")
        toolWindow?.show()
    }

    private fun logout() {
        val result = Messages.showYesNoDialog(
            project,
            "Are you sure you want to logout from OpenRouter.ai?\nThis will clear your API key.",
            "Logout Confirmation",
            Messages.getQuestionIcon()
        )

        if (result == Messages.YES) {
            settingsService.setApiKey("")
            updateConnectionStatus()
            Messages.showInfoMessage(project, "Successfully logged out from OpenRouter.ai", "Logout")
        }
    }

    private fun openChat() {
        val chatWindow = OpenRouterChatWindow(project)
        chatWindow.show()
    }

    private fun openSettings() {
        ApplicationManager.getApplication().invokeLater {
            ShowSettingsUtil.getInstance().showSettingsDialog(project, "OpenRouter")
        }
    }

    private fun openKeymapSettings() {
        ApplicationManager.getApplication().invokeLater {
            ShowSettingsUtil.getInstance().showSettingsDialog(project, "Keymap")
        }
    }

    private fun openDocumentation() {
        BrowserUtil.browse("https://openrouter.ai/docs")
    }

    private fun openFeedbackRepository() {
        BrowserUtil.browse("https://github.com/OpenRouterTeam/openrouter/issues")
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

        openRouterService.getKeyInfo().thenAccept { keyInfo ->
            ApplicationManager.getApplication().invokeLater {
                if (keyInfo != null) {
                    connectionStatus = ConnectionStatus.READY
                    val data = keyInfo.data
                    val used = data.usage
                    val limit = data.limit
                    val remaining = if (limit != null) limit - used else Double.MAX_VALUE

                    currentText = if (limit != null) {
                        if (settingsService.shouldShowCosts()) {
                            "Status: Ready - $${String.format("%.4f", used)}/$${String.format("%.2f", limit)}"
                        } else {
                            val percentage = (used / limit) * 100
                            "Status: Ready - ${String.format("%.1f", percentage)}% used"
                        }
                    } else {
                        "Status: Ready - $${String.format("%.4f", used)} (unlimited)"
                    }

                    currentTooltip = buildString {
                        append("OpenRouter API Status: ${connectionStatus.displayName}\n")
                        append("Used: $${String.format("%.4f", used)}\n")
                        if (limit != null) {
                            append("Limit: $${String.format("%.2f", limit)}\n")
                            append("Remaining: $${String.format("%.4f", remaining)}\n")
                        } else {
                            append("Limit: Unlimited\n")
                        }
                        append("Tier: ${if (data.isFreeTier) "Free" else "Paid"}\n")
                        append("\nClick to open menu")
                    }
                } else {
                    connectionStatus = ConnectionStatus.ERROR
                    currentText = "Status: Error"
                    currentTooltip = "OpenRouter - Failed to load usage info. Click to open menu."
                }
                updateStatusBar()
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
        currentTooltip = "OpenRouter - ${connectionStatus.description}. Click to open menu."
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
        // TODO: Implement periodic refresh using ApplicationManager.getApplication().executeOnPooledThread
        // with settingsService.getRefreshInterval()
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
    companion object {
        val SEPARATOR = PopupMenuItem("---")
    }

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
        } else null
    }
}
