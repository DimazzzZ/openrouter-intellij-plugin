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
        private const val CURRENT_VERSION = "0.2.0"
        private const val CHANGELOG_URL = "https://github.com/DimazzzZ/openrouter-intellij-plugin/blob/main/CHANGELOG.md"
    }

    override suspend fun execute(project: Project) {
        try {
            val settingsService = OpenRouterSettingsService.getInstance()
            val settings = settingsService.getState()
            val lastSeenVersion = settings.lastSeenVersion

            // Only show notification if this is a new version
            if (lastSeenVersion != CURRENT_VERSION && lastSeenVersion.isNotEmpty()) {
                PluginLogger.Service.info("Showing What's New notification for version $CURRENT_VERSION (last seen: $lastSeenVersion)")
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
        } catch (e: Exception) {
            PluginLogger.Service.error("Error in What's New notification activity", e)
        }
    }

    private fun showWhatsNewNotification(project: Project) {
        NotificationGroupManager.getInstance()
            .getNotificationGroup("OpenRouter Updates")
            .createNotification(
                "OpenRouter Plugin Updated to v$CURRENT_VERSION",
                """
                <b>🎉 New Features:</b><br/>
                • <b>AI Assistant Proxy</b> - Connect AI Assistant to 400+ OpenRouter models<br/>
                • <b>Favorite Models</b> - Quick access to your preferred models<br/>
                • <b>Enhanced Quality</b> - 207+ tests, zero critical code smells<br/>
                <br/>
                Click below to explore the new features!
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

