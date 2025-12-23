
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
        private const val CURRENT_VERSION = "0.4.1"
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
                <b>🔧 Bug Fixes:</b><br/>
                • <b>Fixed AI Assistant Streaming</b> - SSE format now complies with specification<br/>
                • <b>Stream Termination</b> - All streams properly end with [DONE] marker<br/>
                • <b>Error Handling</b> - Fixed error response SSE format<br/>
                <br/>
                <b>📝 Logging Improvements:</b><br/>
                • 78% reduction in log noise • Request duration metrics • Standardized request IDs<br/>
                <br/>
                <b>🧪 Testing:</b><br/>
                • 11 new SSE format compliance tests • Regression tests for streaming bug
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
