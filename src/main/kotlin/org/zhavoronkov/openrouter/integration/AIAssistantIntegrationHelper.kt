package org.zhavoronkov.openrouter.integration

import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import org.zhavoronkov.openrouter.proxy.OpenRouterProxyServer
import org.zhavoronkov.openrouter.services.OpenRouterProxyService
import org.zhavoronkov.openrouter.services.OpenRouterSettingsService
import org.zhavoronkov.openrouter.utils.PluginLogger

/**
 * Helper utility for AI Assistant integration
 */

object AIAssistantIntegrationHelper {

    private const val DEFAULT_PROXY_PORT = 8080
    private const val AI_ASSISTANT_PLUGIN_ID = "com.intellij.ml.llm"

    data class AIAssistantInfo(val isAvailable: Boolean, val version: String?)

    /**
     * Gets AI Assistant plugin information
     */
    @Suppress("TooGenericExceptionCaught")
    fun getAIAssistantInfo(): AIAssistantInfo {
        return try {
            val pluginId = PluginId.getId(AI_ASSISTANT_PLUGIN_ID)
            val plugin = PluginManagerCore.getPlugin(pluginId) ?: return AIAssistantInfo(false, null)

            // Use recommended JetBrains API for checking if plugin is enabled
            // isDisabled() returns true if disabled, so we negate it to get isEnabled
            val isAvailable = !PluginManagerCore.isDisabled(pluginId)
            AIAssistantInfo(isAvailable, plugin.version)
        } catch (e: IllegalArgumentException) {
            PluginLogger.Service.debug("Invalid plugin ID format: $AI_ASSISTANT_PLUGIN_ID", e)
            AIAssistantInfo(false, null)
        } catch (e: RuntimeException) {
            // Catching RuntimeException as JetBrains plugin API may throw various unchecked exceptions
            PluginLogger.Service.debug("Runtime error checking AI Assistant", e)
            AIAssistantInfo(false, null)
        }
    }

    /**
     * Checks if JetBrains AI Assistant plugin is installed and enabled
     */
    fun isAIAssistantAvailable(): Boolean = getAIAssistantInfo().isAvailable

    /**
     * Gets the AI Assistant plugin version if available
     */
    fun getAIAssistantVersion(): String? = getAIAssistantInfo().version

    /**
     * Gets the current integration status
     */
    fun getIntegrationStatus(): IntegrationStatus {
        return when {
            !isAIAssistantAvailable() -> IntegrationStatus.AI_ASSISTANT_NOT_AVAILABLE
            !OpenRouterSettingsService.getInstance().isConfigured() -> IntegrationStatus.OPENROUTER_NOT_CONFIGURED
            !OpenRouterProxyService.getInstance().isReady() -> IntegrationStatus.PROXY_SERVER_NOT_RUNNING
            else -> IntegrationStatus.READY
        }
    }

    /**
     * Shows a comprehensive setup wizard dialog
     */
    fun showSetupWizard(project: Project?) {
        ApplicationManager.getApplication().invokeLater {
            val status = getIntegrationStatus()
            val message = getWizardMessage(status)
            val yesButtonText = when (status) {
                IntegrationStatus.AI_ASSISTANT_NOT_AVAILABLE -> "Open Marketplace"
                IntegrationStatus.OPENROUTER_NOT_CONFIGURED -> "Open Settings"
                IntegrationStatus.PROXY_SERVER_NOT_RUNNING -> "Start Proxy"
                IntegrationStatus.READY -> "Show Instructions"
            }

            val result = Messages.showYesNoDialog(
                project,
                message,
                "OpenRouter AI Assistant Integration",
                yesButtonText,
                "Cancel",
                Messages.getInformationIcon()
            )

            if (result == Messages.YES) {
                handleWizardAction(project, status)
            }
        }
    }

    /**
     * Get the wizard message based on integration status
     */
    private fun getWizardMessage(status: IntegrationStatus): String = when (status) {
        IntegrationStatus.AI_ASSISTANT_NOT_AVAILABLE -> """
            JetBrains AI Assistant plugin is not installed or enabled.

            To use OpenRouter with AI Assistant:
            1. Install the "AI Assistant" plugin from the JetBrains Marketplace
            2. Enable the plugin and restart your IDE
            3. Return to OpenRouter settings to complete the integration

            Would you like to open the plugin marketplace?
        """.trimIndent()
        IntegrationStatus.OPENROUTER_NOT_CONFIGURED -> """
            OpenRouter is not configured yet.

            To complete the integration:
            1. Configure your OpenRouter Provisioning Key
            2. Start the proxy server
            3. Configure AI Assistant to use the proxy URL

            Would you like to open OpenRouter settings?
        """.trimIndent()
        IntegrationStatus.PROXY_SERVER_NOT_RUNNING -> {
            val proxyService = OpenRouterProxyService.getInstance()
            val serverStatus = proxyService.getServerStatus()
            val defaultUrl = OpenRouterProxyServer.buildProxyUrl(DEFAULT_PROXY_PORT)

            """
                OpenRouter is configured but the proxy server is not running.

                Current status: ${if (serverStatus.isRunning) "Running on port ${serverStatus.port}" else "Stopped"}

                To complete the integration:
                1. Start the proxy server (click "Start Proxy Server" below)
                2. Configure AI Assistant with the proxy URL: ${serverStatus.url ?: defaultUrl}

                Would you like to start the proxy server now?
            """.trimIndent()
        }
        IntegrationStatus.READY -> {
            val proxyService = OpenRouterProxyService.getInstance()
            val serverStatus = proxyService.getServerStatus()

            """
                ✅ Integration is ready!

                Proxy server is running on: ${serverStatus.url}
                AI Assistant plugin: ${getAIAssistantVersion() ?: "Installed"}

                To configure AI Assistant:
                1. Go to Settings > Tools > AI Assistant > Models
                2. Add "Other OpenAI-compatible service"
                3. Set URL to: ${serverStatus.url}
                4. Leave API key empty
                5. Test connection and apply

                Would you like to see detailed configuration instructions?
            """.trimIndent()
        }
    }

    @Suppress("TooGenericExceptionCaught")
    private fun handleWizardAction(project: Project?, status: IntegrationStatus) {
        when (status) {
            IntegrationStatus.AI_ASSISTANT_NOT_AVAILABLE -> {
                try {
                    val actionManager = com.intellij.openapi.actionSystem.ActionManager.getInstance()
                    val action = actionManager.getAction("WelcomeScreen.Plugins")
                        ?: actionManager.getAction("ShowPluginManager")

                    if (action != null) {
                        actionManager.tryToExecute(
                            action,
                            com.intellij.openapi.ui.playback.commands.ActionCommand.getInputEvent("ShowPluginManager"),
                            null,
                            null,
                            true
                        )
                    } else {
                        error("Plugin manager action not found")
                    }
                } catch (e: IllegalStateException) {
                    PluginLogger.Service.error("Plugin marketplace action not available", e)
                    showErrorDialog(project, "Failed to open plugin marketplace. Please install AI Assistant manually.")
                } catch (e: RuntimeException) {
                    // Catching RuntimeException as IntelliJ action API may throw various unchecked exceptions
                    PluginLogger.Service.error("Runtime error opening plugin marketplace", e)
                    showErrorDialog(project, "Failed to open plugin marketplace. Please install AI Assistant manually.")
                }
            }
            IntegrationStatus.OPENROUTER_NOT_CONFIGURED -> {
                com.intellij.openapi.options.ShowSettingsUtil.getInstance()
                    .showSettingsDialog(project, "OpenRouter")
            }
            IntegrationStatus.PROXY_SERVER_NOT_RUNNING -> {
                val proxyService = OpenRouterProxyService.getInstance()
                proxyService.startServer().thenAccept { success ->
                    ApplicationManager.getApplication().invokeLater {
                        if (success) {
                            val serverStatus = proxyService.getServerStatus()
                            showInfoDialog(
                                project,
                                "Proxy server started successfully!\n\nURL: ${serverStatus.url}\n\n" +
                                    "Now configure AI Assistant to use this URL.",
                                "Proxy Server Started"
                            )
                        } else {
                            showErrorDialog(
                                project,
                                "Failed to start proxy server. Please check the logs and try again.",
                                "Proxy Server Error"
                            )
                        }
                    }
                }
            }
            IntegrationStatus.READY -> {
                val proxyService = OpenRouterProxyService.getInstance()
                val instructions = proxyService.getAIAssistantConfigurationInstructions()
                Messages.showInfoMessage(project, instructions, "AI Assistant Configuration")
            }
        }
    }

    private fun showErrorDialog(project: Project?, message: String, title: String = "Error") {
        Messages.showErrorDialog(project, message, title)
    }

    private fun showInfoDialog(project: Project?, message: String, title: String) {
        Messages.showInfoMessage(project, message, title)
    }

    /**
     * Quick integration check with notification
     */
    fun checkAndNotifyIntegrationStatus(project: Project?) {
        val status = getIntegrationStatus()
        if (status != IntegrationStatus.READY) {
            ApplicationManager.getApplication().invokeLater {
                val message = when (status) {
                    IntegrationStatus.AI_ASSISTANT_NOT_AVAILABLE ->
                        "AI Assistant plugin not found. Install it to use OpenRouter models in AI Assistant."
                    IntegrationStatus.OPENROUTER_NOT_CONFIGURED ->
                        "OpenRouter not configured. Configure it to enable AI Assistant integration."
                    IntegrationStatus.PROXY_SERVER_NOT_RUNNING ->
                        "Proxy server not running. Start it to enable AI Assistant integration."
                    else -> ""
                }

                if (message.isNotEmpty()) {
                    val result = Messages.showYesNoDialog(
                        project,
                        "$message\n\nWould you like to run the setup wizard?",
                        "OpenRouter AI Assistant Integration",
                        "Setup",
                        "Later",
                        Messages.getInformationIcon()
                    )

                    if (result == Messages.YES) {
                        showSetupWizard(project)
                    }
                }
            }
        }
    }

    /**
     * Integration status enumeration
     */
    enum class IntegrationStatus {
        AI_ASSISTANT_NOT_AVAILABLE,
        OPENROUTER_NOT_CONFIGURED,
        PROXY_SERVER_NOT_RUNNING,
        READY
    }
}
