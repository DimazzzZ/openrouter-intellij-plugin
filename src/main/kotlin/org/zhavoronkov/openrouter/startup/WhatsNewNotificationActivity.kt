
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
        // NOTE: This is hand-synced with `gradle.properties` (`pluginVersion`) and
        // `src/main/resources/META-INF/plugin.xml` change-notes. When you bump the
        // plugin version, update this constant too, or the "What's New" notification
        // will not fire for the new release. (Follow-up: derive from
        // `PluginManagerCore.getPlugin(PluginId.getId(...))?.version`.)
        private const val CURRENT_VERSION = "0.7.0"
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
                <b>📋 Requests Tab:</b><br/>
                • <b>Every Request</b> - From the chat and from tools using the proxy, with cost and provider<br/>
                • <b>Warnings</b> - A failed or cut-off request is announced in a balloon<br/>
                <br/>
                <b>🧭 Routers Hub:</b><br/>
                • <b>First-Class Routers</b> - <code>openrouter/auto</code>, <code>fusion</code>, <code>pareto-code</code> and more in every tool's model list<br/>
                • <b>Router Defaults</b> - Set each router's parameter once; replies say where they were routed<br/>
                <br/>
                <b>🧩 Models Paired With Presets:</b><br/>
                • <b>&lt;model&gt;@preset/&lt;slug&gt;</b> - A preset's settings for tools that can only pick a model<br/>
                <br/>
                <b>📊 Status Tab Redesign:</b><br/>
                • <b>Real Balance</b> - Spend trend, days left and spend by model; works with an ordinary API key<br/>
                <br/>
                <b>🚦 Clear Errors for Tools Using the Proxy</b><br/>
                <br/>
                <b>🔎 Web Search &amp; Structured Output:</b><br/>
                • <b>In the Chat</b> - Per-message web search, JSON or a saved schema<br/>
                <br/>
                <b>💬 Chat Redesign &amp; 🌍 In-Region Routing</b>
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
