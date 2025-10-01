package org.zhavoronkov.openrouter.startup

import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import org.zhavoronkov.openrouter.services.OpenRouterProxyService
import org.zhavoronkov.openrouter.services.OpenRouterSettingsService
import org.zhavoronkov.openrouter.utils.PluginLogger

/**
 * Startup activity to auto-start the proxy server when a project opens
 */
class ProxyServerStartupActivity : ProjectActivity {

    override suspend fun execute(project: Project) {
        try {
            PluginLogger.Service.debug("OpenRouter plugin startup activity executing")

            val settingsService = OpenRouterSettingsService.getInstance()
            val proxyService = OpenRouterProxyService.getInstance()

            // Check if running with --proxy-server argument for testing
            val args = System.getProperty("idea.additional.args") ?: ""
            val forceStartProxy = args.contains("--proxy-server") ||
                                 System.getProperty("openrouter.force.proxy", "false").toBoolean()

            if (forceStartProxy) {
                PluginLogger.Service.info("Force starting proxy server for testing (--proxy-server argument detected)")

                // Force start the proxy server even without configuration
                proxyService.forceStartServer().thenAccept { success ->
                    if (success) {
                        val status = proxyService.getServerStatus()
                        PluginLogger.Service.info("OpenRouter proxy server FORCE STARTED on port ${status.port}")
                        PluginLogger.Service.info("AI Assistant can connect to: ${status.url}")
                        PluginLogger.Service.info("Note: Running in test mode - some features may be limited without proper configuration")
                    } else {
                        PluginLogger.Service.warn("Failed to force-start OpenRouter proxy server")
                    }
                }.exceptionally { throwable ->
                    PluginLogger.Service.error("Error during proxy server force-start", throwable)
                    null
                }
            } else if (settingsService.isConfigured()) {
                PluginLogger.Service.info("OpenRouter is configured, attempting to auto-start proxy server")

                // Start the proxy server asynchronously
                proxyService.autoStartIfConfigured().thenAccept { success ->
                    if (success) {
                        val status = proxyService.getServerStatus()
                        PluginLogger.Service.info("OpenRouter proxy server started successfully on port ${status.port}")
                        PluginLogger.Service.info("AI Assistant can connect to: ${status.url}")
                    } else {
                        PluginLogger.Service.warn("Failed to auto-start OpenRouter proxy server")
                    }
                }.exceptionally { throwable ->
                    PluginLogger.Service.error("Error during proxy server auto-start", throwable)
                    null
                }
            } else {
                PluginLogger.Service.debug("OpenRouter not configured, skipping proxy server auto-start")
            }

            // Note: AI Assistant integration check removed - plugin works independently

        } catch (e: Exception) {
            PluginLogger.Service.error("Error in OpenRouter startup activity", e)
        }
    }
}
