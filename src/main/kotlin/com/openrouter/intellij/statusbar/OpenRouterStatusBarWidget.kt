package com.openrouter.intellij.statusbar

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.StatusBar
import com.intellij.openapi.wm.StatusBarWidget
import com.intellij.openapi.wm.impl.status.EditorBasedWidget
import com.intellij.util.Consumer
import com.openrouter.intellij.icons.OpenRouterIcons
import com.openrouter.intellij.services.OpenRouterService
import com.openrouter.intellij.services.OpenRouterSettingsService
import java.awt.event.MouseEvent
import javax.swing.Icon

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
                    // Left click - open settings
                    openSettings()
                }
            }
        }
    }
    
    override fun getIcon(): Icon = OpenRouterIcons.STATUS_BAR
    
    private fun showContextMenu(event: MouseEvent) {
        // TODO: Implement context menu with refresh, settings, etc.
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
        
        openRouterService.getQuotaInfo().thenAccept { quotaInfo ->
            ApplicationManager.getApplication().invokeLater {
                if (quotaInfo != null) {
                    val used = quotaInfo.used ?: 0.0
                    val total = quotaInfo.total ?: 0.0
                    val remaining = quotaInfo.remaining ?: 0.0
                    
                    currentText = if (settingsService.shouldShowCosts()) {
                        "OpenRouter: $${String.format("%.2f", used)}/$${String.format("%.2f", total)}"
                    } else {
                        "OpenRouter: ${String.format("%.1f", (used/total)*100)}% used"
                    }
                    
                    currentTooltip = buildString {
                        append("OpenRouter API Usage\n")
                        append("Used: $${String.format("%.2f", used)}\n")
                        append("Total: $${String.format("%.2f", total)}\n")
                        append("Remaining: $${String.format("%.2f", remaining)}\n")
                        quotaInfo.resetDate?.let { 
                            append("Resets: $it\n")
                        }
                        append("\nClick to open settings")
                    }
                } else {
                    currentText = "OpenRouter: Error"
                    currentTooltip = "OpenRouter - Failed to load quota info. Click to open settings."
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
