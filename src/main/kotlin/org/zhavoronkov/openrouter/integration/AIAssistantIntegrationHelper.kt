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

    private const val AI_ASSISTANT_PLUGIN_ID = "com.intellij.ml.llm"
    private const val AI_ASSISTANT_PLUGIN_NAME = "AI Assistant"

    /**
     * Checks if JetBrains AI Assistant plugin is installed and enabled
     */
    fun isAIAssistantAvailable(): Boolean {
        return try {
            val pluginId = PluginId.getId(AI_ASSISTANT_PLUGIN_ID)
            val plugin = PluginManagerCore.getPlugin(pluginId)
            plugin != null && plugin.isEnabled
        } catch (e: Exception) {
            PluginLogger.Service.debug("Error checking AI Assistant availability", e)
            false
        }
    }

    /**
     * Gets the AI Assistant plugin version if available
     */
    fun getAIAssistantVersion(): String? {
        return try {
            val pluginId = PluginId.getId(AI_ASSISTANT_PLUGIN_ID)
            val plugin = PluginManagerCore.getPlugin(pluginId)
            plugin?.version
        } catch (e: Exception) {
            PluginLogger.Service.debug("Error getting AI Assistant version", e)
            null
        }
    }

    /**
     * Checks if OpenRouter is properly configured for AI Assistant integration
     */
    fun isOpenRouterConfiguredForIntegration(): Boolean {
        val settingsService = OpenRouterSettingsService.getInstance()
        return settingsService.isConfigured()
    }

    /**
     * Checks if the proxy server is running and ready
     */
    fun isProxyServerReady(): Boolean {
        val proxyService = OpenRouterProxyService.getInstance()
        return proxyService.isReady()
    }

    /**
     * Gets the current integration status
     */
    fun getIntegrationStatus(): IntegrationStatus {
        return when {
            !isAIAssistantAvailable() -> IntegrationStatus.AI_ASSISTANT_NOT_AVAILABLE
            !isOpenRouterConfiguredForIntegration() -> IntegrationStatus.OPENROUTER_NOT_CONFIGURED
            !isProxyServerReady() -> IntegrationStatus.PROXY_SERVER_NOT_RUNNING
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
            val yesButtonText = getYesButtonText(status)

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
    private fun getWizardMessage(status: IntegrationStatus): String {
        return when (status) {
            IntegrationStatus.AI_ASSISTANT_NOT_AVAILABLE -> getAIAssistantNotAvailableMessage()
            IntegrationStatus.OPENROUTER_NOT_CONFIGURED -> getOpenRouterNotConfiguredMessage()
            IntegrationStatus.PROXY_SERVER_NOT_RUNNING -> getProxyServerNotRunningMessage()
            IntegrationStatus.READY -> getIntegrationReadyMessage()
        }
    }

    /**
     * Get message when AI Assistant is not available
     */
    private fun getAIAssistantNotAvailableMessage(): String {
        return """
            JetBrains AI Assistant plugin is not installed or enabled.

            To use OpenRouter with AI Assistant:
            1. Install the "AI Assistant" plugin from the JetBrains Marketplace
            2. Enable the plugin and restart your IDE
            3. Return to OpenRouter settings to complete the integration

            Would you like to open the plugin marketplace?
        """.trimIndent()
    }

    /**
     * Get message when OpenRouter is not configured
     */
    private fun getOpenRouterNotConfiguredMessage(): String {
        return """
            OpenRouter is not configured yet.

            To complete the integration:
            1. Configure your OpenRouter Provisioning Key
            2. Start the proxy server
            3. Configure AI Assistant to use the proxy URL

            Would you like to open OpenRouter settings?
        """.trimIndent()
    }

    /**
     * Get message when proxy server is not running
     */
    private fun getProxyServerNotRunningMessage(): String {
        val proxyService = OpenRouterProxyService.getInstance()
        val serverStatus = proxyService.getServerStatus()
        val defaultUrl = OpenRouterProxyServer.buildProxyUrl(8080)

        return """
            OpenRouter is configured but the proxy server is not running.

            Current status: ${if (serverStatus.isRunning) "Running on port ${serverStatus.port}" else "Stopped"}

            To complete the integration:
            1. Start the proxy server (click "Start Proxy Server" below)
            2. Configure AI Assistant with the proxy URL: ${serverStatus.url ?: defaultUrl}

            Would you like to start the proxy server now?
        """.trimIndent()
    }

    /**
     * Get message when integration is ready
     */
    private fun getIntegrationReadyMessage(): String {
        val proxyService = OpenRouterProxyService.getInstance()
        val serverStatus = proxyService.getServerStatus()

        return """
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

    /**
     * Get the yes button text based on integration status
     */
    private fun getYesButtonText(status: IntegrationStatus): String {
        return when (status) {
            IntegrationStatus.AI_ASSISTANT_NOT_AVAILABLE -> "Open Marketplace"
            IntegrationStatus.OPENROUTER_NOT_CONFIGURED -> "Open Settings"
            IntegrationStatus.PROXY_SERVER_NOT_RUNNING -> "Start Proxy"
            IntegrationStatus.READY -> "Show Instructions"
        }
    }

    private fun handleWizardAction(project: Project?, status: IntegrationStatus) {
        when (status) {
            IntegrationStatus.AI_ASSISTANT_NOT_AVAILABLE -> {
                // Open plugin marketplace
                try {
                    val action = com.intellij.ide.actions.ShowPluginManagerAction()
                    val dataContext = if (project != null) {
                        com.intellij.openapi.actionSystem.impl.SimpleDataContext.getProjectContext(project)
                    } else {
                        com.intellij.openapi.actionSystem.impl.SimpleDataContext.EMPTY_CONTEXT
                    }
                    action.actionPerformed(
                        com.intellij.openapi.actionSystem.AnActionEvent.createFromDataContext(
                            "",
                            null,
                            dataContext
                        )
                    )
                } catch (e: Exception) {
                    PluginLogger.Service.error("Failed to open plugin marketplace", e)
                    Messages.showErrorDialog(
                        project,
                        "Failed to open plugin marketplace. Please install AI Assistant manually.",
                        "Error"
                    )
                }
            }
            IntegrationStatus.OPENROUTER_NOT_CONFIGURED -> {
                // Open OpenRouter settings
                com.intellij.openapi.options.ShowSettingsUtil.getInstance()
                    .showSettingsDialog(project, "OpenRouter")
            }
            IntegrationStatus.PROXY_SERVER_NOT_RUNNING -> {
                // Start proxy server
                val proxyService = OpenRouterProxyService.getInstance()
                proxyService.startServer().thenAccept { success ->
                    ApplicationManager.getApplication().invokeLater {
                        if (success) {
                            val serverStatus = proxyService.getServerStatus()
                            Messages.showInfoMessage(
                                project,
                                "Proxy server started successfully!\n\nURL: ${serverStatus.url}\n\n" +
                                "Now configure AI Assistant to use this URL.",
                                "Proxy Server Started"
                            )
                        } else {
                            Messages.showErrorDialog(
                                project,
                                "Failed to start proxy server. Please check the logs and try again.",
                                "Proxy Server Error"
                            )
                        }
                    }
                }
            }
            IntegrationStatus.READY -> {
                // Show detailed instructions
                val proxyService = OpenRouterProxyService.getInstance()
                val instructions = proxyService.getAIAssistantConfigurationInstructions()
                Messages.showInfoMessage(project, instructions, "AI Assistant Configuration")
            }
        }
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
