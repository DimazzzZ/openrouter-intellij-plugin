@file:Suppress("TooGenericExceptionCaught")

package org.zhavoronkov.openrouter.listeners

import com.intellij.ide.plugins.DynamicPluginListener
import com.intellij.ide.plugins.IdeaPluginDescriptor
import org.zhavoronkov.openrouter.proxy.servlets.RequestValidator
import org.zhavoronkov.openrouter.services.OpenRouterProxyService
import org.zhavoronkov.openrouter.services.OpenRouterSettingsService
import org.zhavoronkov.openrouter.utils.ModelAvailabilityNotifier
import org.zhavoronkov.openrouter.utils.PluginLogger

/**
 * Listener for plugin lifecycle events (load/unload)
 * Ensures proper cleanup when plugin is dynamically unloaded
 *
 * This enables the plugin to be installed, updated, and uninstalled
 * without requiring IDE restart (dynamic plugin support)
 */
class PluginLifecycleListener : DynamicPluginListener {

    companion object {
        private const val PLUGIN_ID = "org.zhavoronkov.openrouter"
    }

    /**
     * Called before plugin is unloaded
     * Perform cleanup to prevent memory leaks
     */
    override fun beforePluginUnload(pluginDescriptor: IdeaPluginDescriptor, isUpdate: Boolean) {
        if (pluginDescriptor.pluginId.idString != PLUGIN_ID) {
            return
        }

        val action = if (isUpdate) "update" else "uninstall"
        PluginLogger.Service.info("OpenRouter plugin unloading ($action)")

        try {
            // Clear static state from singleton objects to prevent memory leaks
            ModelAvailabilityNotifier.clearNotificationHistory()
            PluginLogger.Service.debug("Cleared ModelAvailabilityNotifier state")

            // Clear RequestValidator cache
            val settingsService = OpenRouterSettingsService.getInstance()
            val requestValidator = RequestValidator(settingsService)
            requestValidator.clearRecentRequests()
            PluginLogger.Service.debug("Cleared RequestValidator state")

            // Check if proxy server is still running
            val proxyService = OpenRouterProxyService.getInstance()
            val status = proxyService.getServerStatus()

            if (status.isRunning) {
                PluginLogger.Service.warn(
                    "Proxy server still running during plugin unload - will be stopped by dispose()"
                )
            }

            PluginLogger.Service.info("Plugin unload preparation completed")
        } catch (e: Exception) {
            PluginLogger.Service.error("Error during plugin unload preparation", e)
        }
    }

    /**
     * Called after plugin is loaded
     * Log plugin version and initialization status
     */
    override fun pluginLoaded(pluginDescriptor: IdeaPluginDescriptor) {
        if (pluginDescriptor.pluginId.idString != PLUGIN_ID) {
            return
        }

        PluginLogger.Service.info("OpenRouter plugin loaded: version ${pluginDescriptor.version}")
        PluginLogger.Service.info("Dynamic plugin support: enabled")
    }
}
