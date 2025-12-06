package org.zhavoronkov.openrouter.startup

import com.intellij.notification.Notification
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import org.zhavoronkov.openrouter.services.OpenRouterSettingsService
import org.zhavoronkov.openrouter.ui.SetupWizardDialog
import org.zhavoronkov.openrouter.utils.PluginLogger

/**
 * Startup activity that shows a welcome notification for first-time users
 */
class WelcomeNotificationActivity : ProjectActivity {

    override suspend fun execute(project: Project) {
        val settings = OpenRouterSettingsService.getInstance()

        // Only show welcome notification if user hasn't seen it yet
        if (!settings.setupStateManager.hasSeenWelcome()) {
            showWelcomeNotification(project)
            settings.setupStateManager.setHasSeenWelcome(true)
            PluginLogger.Startup.info("Welcome notification shown to first-time user")
        }
    }

    private fun showWelcomeNotification(project: Project) {
        val notification = NotificationGroupManager.getInstance()
            .getNotificationGroup("OpenRouter Notifications")
            .createNotification(
                "Welcome to OpenRouter!",
                "Get started in 3 easy steps:\n" +
                    "1. Add your Provisioning Key\n" +
                    "2. Select favorite models\n" +
                    "3. Start the proxy server",
                NotificationType.INFORMATION
            )

        // Add "Quick Setup" action
        notification.addAction(object : NotificationAction("Quick Setup") {
            override fun actionPerformed(e: AnActionEvent, notification: Notification) {
                // Open setup wizard
                SetupWizardDialog.show(project)
                notification.expire()
            }
        })

        // Add "Open Settings" action
        notification.addAction(object : NotificationAction("Open Settings") {
            override fun actionPerformed(e: AnActionEvent, notification: Notification) {
                // Open OpenRouter settings
                ShowSettingsUtil.getInstance().showSettingsDialog(
                    project,
                    "OpenRouter"
                )
                notification.expire()
            }
        })

        // Add "Dismiss" action
        notification.addAction(object : NotificationAction("Dismiss") {
            override fun actionPerformed(e: AnActionEvent, notification: Notification) {
                notification.expire()
            }
        })

        notification.notify(project)
    }
}
