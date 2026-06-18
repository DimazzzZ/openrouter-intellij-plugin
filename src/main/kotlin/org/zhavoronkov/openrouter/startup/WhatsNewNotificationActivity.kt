
package org.zhavoronkov.openrouter.startup

import com.intellij.ide.BrowserUtil
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import org.zhavoronkov.openrouter.services.OpenRouterSettingsService
import org.zhavoronkov.openrouter.settings.OpenRouterConfigurable
import org.zhavoronkov.openrouter.utils.PluginLogger
import java.io.IOException

/**
 * Startup activity to show "What's New" notification after plugin update
 *
 * This follows JetBrains best practices:
 * - Shows only once per version
 * - Non-intrusive balloon notification
 * - Dismissible by user
 * - Provides actionable links
 */
class WhatsNewNotificationActivity : ProjectActivity {

    companion object {
        private const val CURRENT_VERSION = "0.5.3"
        private const val CHANGELOG_URL =
            "https://github.com/DimazzzZ/openrouter-intellij-plugin/blob/main/CHANGELOG.md"
    }

    override suspend fun execute(project: Project) {
        try {
            val settingsService = OpenRouterSettingsService.getInstance()
            val settings = settingsService.getState()
            val lastSeenVersion = settings.lastSeenVersion

            // Only show notification if this is a new version
            if (lastSeenVersion != CURRENT_VERSION && lastSeenVersion.isNotEmpty()) {
                PluginLogger.Service.info(
                    "Showing What's New notification for version $CURRENT_VERSION (last seen: $lastSeenVersion)"
                )
                showWhatsNewNotification(project)

                // Update last seen version
                settings.lastSeenVersion = CURRENT_VERSION
            } else if (lastSeenVersion.isEmpty()) {
                // First install - just set the version without showing notification
                PluginLogger.Service.info("First install detected, setting version to $CURRENT_VERSION")
                settings.lastSeenVersion = CURRENT_VERSION
            } else {
                PluginLogger.Service.debug("Version $CURRENT_VERSION already seen, skipping What's New notification")
            }
        } catch (e: IllegalStateException) {
            PluginLogger.Service.error("Invalid state in What's New notification activity", e)
        } catch (e: IOException) {
            PluginLogger.Service.error("IO error in What's New notification activity", e)
        } catch (expectedError: Exception) {
            PluginLogger.Service.error("Error in What's New notification activity", expectedError)
        }
    }

    private fun showWhatsNewNotification(project: Project) {
        NotificationGroupManager.getInstance()
            .getNotificationGroup("OpenRouter Updates")
            .createNotification(
                "OpenRouter Plugin Updated to v$CURRENT_VERSION",
                """
                <b>💬 Reasoning & Verbosity Controls:</b><br/>
                • <b>Reasoning Dropdown</b> - Control reasoning effort for supported models<br/>
                • <b>Verbosity Dropdown</b> - Control response verbosity level<br/>
                • <b>Auto-Detection</b> - Model capabilities from OpenRouter API<br/>
                <br/>
                <b>🔧 Tool Calling Support:</b><br/>
                • <b>Proxy Translation</b> - Tool call handling in proxy layer<br/>
                • <b>Agent Foundation</b> - Infrastructure for AI Assistant workflows<br/>
                <br/>
                <b>🔒 Settings Reliability:</b><br/>
                • <b>Fixed Settings Loss</b> - API keys no longer lost on IDE restart<br/>
                • <b>Deprecated API Fix</b> - Updated CredentialAttributes usage<br/>
                <br/>
                <b>🎯 AI Assistant Improvements:</b><br/>
                • <b>Presets in Dropdown</b> - Custom presets now in model selection
                """.trimIndent(),
                NotificationType.INFORMATION
            )
            .addAction(object : NotificationAction("Open Settings") {
                override fun actionPerformed(e: AnActionEvent, notification: com.intellij.notification.Notification) {
                    ShowSettingsUtil.getInstance().showSettingsDialog(project, OpenRouterConfigurable::class.java)
                    notification.expire()
                }
            })
            .addAction(object : NotificationAction("View Changelog") {
                override fun actionPerformed(e: AnActionEvent, notification: com.intellij.notification.Notification) {
                    BrowserUtil.browse(CHANGELOG_URL)
                    notification.expire()
                }
            })
            .notify(project)
    }
}
