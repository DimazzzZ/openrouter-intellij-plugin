package com.openrouter.intellij.statusbar

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.wm.StatusBar
import com.intellij.openapi.wm.StatusBarWidget
import com.intellij.openapi.wm.impl.status.EditorBasedWidget
import com.intellij.util.Consumer
import com.openrouter.intellij.icons.OpenRouterIcons
import com.openrouter.intellij.services.OpenRouterService
import com.openrouter.intellij.services.OpenRouterSettingsService
import com.openrouter.intellij.ui.OpenRouterStatsPopup
import java.awt.event.MouseEvent
import javax.swing.Icon
import javax.swing.JMenuItem
import javax.swing.JPopupMenu

/**
 * Status bar widget that displays OpenRouter quota and usage information
 */
class OpenRouterStatusBarWidget(project: Project) : EditorBasedWidget(project), StatusBarWidget.IconPresentation {
    
    private val openRouterService = OpenRouterService.getInstance()
    private val settingsService = OpenRouterSettingsService.getInstance()
    
    private var currentText = "OpenRouter"
    private var currentTooltip = "OpenRouter - Click to view settings"
    
    companion object {
        const val ID = "OpenRouterStatusBar"
    }
    
    override fun ID(): String = ID
    
    override fun getPresentation(): StatusBarWidget.WidgetPresentation = this
    
    override fun getTooltipText(): String = currentTooltip
    
    override fun getClickConsumer(): Consumer<MouseEvent>? {
        return Consumer { event ->
            when {
                event.isPopupTrigger || event.button == MouseEvent.BUTTON3 -> {
                    // Right click - show context menu
                    showContextMenu(event)
                }
                else -> {
                    // Left click - show statistics popup
                    showStatsPopup(event)
                }
            }
        }
    }
    
    override fun getIcon(): Icon = OpenRouterIcons.STATUS_BAR
    
    private fun showStatsPopup(event: MouseEvent) {
        val statsPopup = OpenRouterStatsPopup(project)
        statsPopup.show(event.component)
    }

    private fun showContextMenu(event: MouseEvent) {
        val popupMenu = JPopupMenu()

        val refreshItem = JMenuItem("Refresh").apply {
            addActionListener { updateQuotaInfo() }
        }

        val settingsItem = JMenuItem("Settings").apply {
            addActionListener { openSettings() }
        }

        popupMenu.add(refreshItem)
        popupMenu.addSeparator()
        popupMenu.add(settingsItem)

        popupMenu.show(event.component, event.x, event.y)
    }
    
    private fun openSettings() {
        ApplicationManager.getApplication().invokeLater {
            // Open OpenRouter settings
            com.intellij.openapi.options.ShowSettingsUtil.getInstance()
                .showSettingsDialog(project, "OpenRouter")
        }
    }
    
    /**
     * Update the widget with current quota information
     */
    fun updateQuotaInfo() {
        if (!settingsService.isConfigured()) {
            currentText = "OpenRouter: Not configured"
            currentTooltip = "OpenRouter - Click to configure API key"
            updateStatusBar()
            return
        }

        openRouterService.getKeyInfo().thenAccept { keyInfo ->
            ApplicationManager.getApplication().invokeLater {
                if (keyInfo != null) {
                    val data = keyInfo.data
                    val used = data.usage
                    val limit = data.limit
                    val remaining = if (limit != null) limit - used else Double.MAX_VALUE

                    currentText = if (limit != null) {
                        if (settingsService.shouldShowCosts()) {
                            "OpenRouter: $${String.format("%.4f", used)}/$${String.format("%.2f", limit)}"
                        } else {
                            val percentage = (used / limit) * 100
                            "OpenRouter: ${String.format("%.1f", percentage)}% used"
                        }
                    } else {
                        "OpenRouter: $${String.format("%.4f", used)} (unlimited)"
                    }

                    currentTooltip = buildString {
                        append("OpenRouter API Usage\n")
                        append("Used: $${String.format("%.4f", used)}\n")
                        if (limit != null) {
                            append("Limit: $${String.format("%.2f", limit)}\n")
                            append("Remaining: $${String.format("%.4f", remaining)}\n")
                        } else {
                            append("Limit: Unlimited\n")
                        }
                        append("Tier: ${if (data.isFreeTier) "Free" else "Paid"}\n")
                        append("\nClick to view detailed statistics")
                    }
                } else {
                    currentText = "OpenRouter: Error"
                    currentTooltip = "OpenRouter - Failed to load usage info. Click to view details."
                }
                updateStatusBar()
            }
        }
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
