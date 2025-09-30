package org.zhavoronkov.openrouter.settings

import com.intellij.icons.AllIcons
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.ui.Messages
import com.intellij.ui.components.JBLabel
import org.zhavoronkov.openrouter.services.OpenRouterProxyService
import org.zhavoronkov.openrouter.services.OpenRouterSettingsService
import org.zhavoronkov.openrouter.utils.PluginLogger
import javax.swing.JButton
import javax.swing.Timer

/**
 * Handles proxy server management operations for OpenRouter settings
 */
class ProxyServerManager(
    private val proxyService: OpenRouterProxyService,
    private val settingsService: OpenRouterSettingsService
) {
    
    companion object {
        private const val STATUS_UPDATE_DELAY_MS = 1000
        private const val BUTTON_UPDATE_DELAY_MS = 500
        private const val ICON_TEXT_GAP = 5
    }

    fun updateProxyStatusLabel(statusLabel: JBLabel) {
        val status = proxyService.getServerStatus()
        if (status.isRunning) {
            statusLabel.icon = AllIcons.General.InspectionsOK
            statusLabel.text = "Running on port ${status.port}"
            statusLabel.foreground = com.intellij.util.ui.UIUtil.getLabelForeground()
            statusLabel.iconTextGap = ICON_TEXT_GAP
        } else {
            statusLabel.icon = AllIcons.General.BalloonInformation
            statusLabel.text = "Stopped"
            statusLabel.foreground = com.intellij.util.ui.UIUtil.getLabelForeground()
            statusLabel.iconTextGap = ICON_TEXT_GAP
        }
    }

    fun updateProxyButtons(startButton: JButton, stopButton: JButton) {
        val status = proxyService.getServerStatus()
        val isConfigured = settingsService.isConfigured()

        startButton.isEnabled = !status.isRunning && isConfigured
        stopButton.isEnabled = status.isRunning

        if (!isConfigured) {
            startButton.text = "Start Proxy (Configure First)"
        } else {
            startButton.text = "Start Proxy"
        }
    }

    fun startProxyServer(statusLabel: JBLabel, startButton: JButton, stopButton: JButton) {
        if (!settingsService.isConfigured()) {
            Messages.showErrorDialog(
                "Please configure your Provisioning Key first.",
                "Configuration Required"
            )
            return
        }

        startButton.isEnabled = false
        startButton.text = "Starting..."

        ApplicationManager.getApplication().executeOnPooledThread {
            try {
                val success = proxyService.startServer().get()
                ApplicationManager.getApplication().invokeLater({
                    if (success) {
                        PluginLogger.Settings.info("Proxy server started successfully")
                        updateProxyStatusLabel(statusLabel)
                        updateProxyButtons(startButton, stopButton)
                    } else {
                        PluginLogger.Settings.error("Failed to start proxy server")
                        Messages.showErrorDialog(
                            "Failed to start proxy server. Check logs for details.",
                            "Proxy Start Failed"
                        )
                        startButton.isEnabled = true
                        startButton.text = "Start Proxy"
                    }
                }, com.intellij.openapi.application.ModalityState.any())
            } catch (e: Exception) {
                PluginLogger.Settings.error("Exception starting proxy server: ${e.message}", e)
                ApplicationManager.getApplication().invokeLater({
                    Messages.showErrorDialog(
                        "Failed to start proxy server: ${e.message}",
                        "Proxy Start Failed"
                    )
                    startButton.isEnabled = true
                    startButton.text = "Start Proxy"
                }, com.intellij.openapi.application.ModalityState.any())
            }
        }
    }

    fun stopProxyServer(statusLabel: JBLabel, startButton: JButton, stopButton: JButton) {
        stopButton.isEnabled = false
        stopButton.text = "Stopping..."

        ApplicationManager.getApplication().executeOnPooledThread {
            try {
                proxyService.stopServer()
                ApplicationManager.getApplication().invokeLater({
                    PluginLogger.Settings.info("Proxy server stopped successfully")
                    updateProxyStatusLabel(statusLabel)
                    updateProxyButtons(startButton, stopButton)
                    stopButton.text = "Stop Proxy"
                }, com.intellij.openapi.application.ModalityState.any())
            } catch (e: Exception) {
                PluginLogger.Settings.error("Exception stopping proxy server: ${e.message}", e)
                ApplicationManager.getApplication().invokeLater({
                    Messages.showErrorDialog(
                        "Failed to stop proxy server: ${e.message}",
                        "Proxy Stop Failed"
                    )
                    stopButton.isEnabled = true
                    stopButton.text = "Stop Proxy"
                }, com.intellij.openapi.application.ModalityState.any())
            }
        }
    }

    fun showConfigurationInstructions() {
        val instructions = proxyService.getAIAssistantConfigurationInstructions()
        Messages.showInfoMessage(instructions, "AI Assistant Configuration")
    }

    fun createPeriodicUpdater(statusLabel: JBLabel, startButton: JButton, stopButton: JButton): Timer {
        return Timer(STATUS_UPDATE_DELAY_MS) {
            updateProxyStatusLabel(statusLabel)
            updateProxyButtons(startButton, stopButton)
        }.apply {
            isRepeats = true
        }
    }

    fun scheduleButtonUpdate(startButton: JButton, stopButton: JButton) {
        Timer(BUTTON_UPDATE_DELAY_MS) {
            updateProxyButtons(startButton, stopButton)
        }.apply {
            isRepeats = false
            start()
        }
    }
}
