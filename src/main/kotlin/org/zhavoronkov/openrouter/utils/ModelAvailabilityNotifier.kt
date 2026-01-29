package org.zhavoronkov.openrouter.utils

import com.intellij.ide.BrowserUtil
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import org.zhavoronkov.openrouter.settings.OpenRouterConfigurable
import java.util.concurrent.ConcurrentHashMap

/**
 * Utility for notifying users about model availability issues
 *
 * Features:
 * - Shows balloon notifications when models become unavailable
 * - Prevents duplicate notifications for the same model
 * - Provides actionable suggestions (view models, open settings)
 * - Tracks notified models to avoid spam
 */
object ModelAvailabilityNotifier {

    // Track which models we've already notified about (to avoid spam)
    private val notifiedModels = ConcurrentHashMap.newKeySet<String>()

    // Reset interval (clear notification history after 1 hour)
    private const val RESET_INTERVAL_MS = 60 * 60 * 1000L
    private var lastResetTime = System.currentTimeMillis()

    /**
     * Notify user that a model is unavailable
     *
     * @param modelName The name of the unavailable model
     * @param errorMessage The error message from OpenRouter
     */
    fun notifyModelUnavailable(modelName: String, errorMessage: String) {
        // Reset notification history if enough time has passed
        val now = System.currentTimeMillis()
        if (now - lastResetTime > RESET_INTERVAL_MS) {
            notifiedModels.clear()
            lastResetTime = now
        }

        // Don't notify if we've already notified about this model
        if (!notifiedModels.add(modelName)) {
            PluginLogger.Service.debug("Skipping duplicate notification for model: $modelName")
            return
        }

        PluginLogger.Service.info("Showing model unavailability notification for: $modelName")

        // Get the current project (or use default project if none is open)
        val project = getCurrentProject()

        ApplicationManager.getApplication().invokeLater {
            showNotification(project, modelName, errorMessage)
        }
    }

    /**
     * Show the notification balloon
     */
    private fun showNotification(project: Project?, modelName: String, errorMessage: String) {
        val reason = extractUnavailabilityReason(errorMessage)

        NotificationGroupManager.getInstance()
            .getNotificationGroup("OpenRouter Errors")
            .createNotification(
                "Model Unavailable: $modelName",
                buildNotificationContent(modelName, reason),
                NotificationType.WARNING
            )
            .addAction(object : NotificationAction("View Available Models") {
                override fun actionPerformed(e: AnActionEvent, notification: com.intellij.notification.Notification) {
                    BrowserUtil.browse("https://openrouter.ai/models")
                    notification.expire()
                }
            })
            .addAction(object : NotificationAction("Open Settings") {
                override fun actionPerformed(e: AnActionEvent, notification: com.intellij.notification.Notification) {
                    ShowSettingsUtil.getInstance().showSettingsDialog(project, OpenRouterConfigurable::class.java)
                    notification.expire()
                }
            })
            .notify(project)
    }

    /**
     * Build the notification content message
     */
    private fun buildNotificationContent(modelName: String, reason: String): String {
        return """
            <b>$modelName</b> is currently unavailable on OpenRouter.
            <br/><br/>
            <b>Reason:</b> $reason
            <br/><br/>
            <b>Suggested alternatives:</b>
            <ul>
                <li><b>openai/gpt-4o-mini</b> - Fast and affordable</li>
                <li><b>anthropic/claude-3.5-sonnet</b> - High quality</li>
                <li><b>google/gemini-pro-1.5</b> - Large context window</li>
            </ul>
            Click "View Available Models" to see all current options.
        """.trimIndent()
    }

    /**
     * Extract the reason for unavailability from the error message
     */
    private fun extractUnavailabilityReason(errorMessage: String): String {
        return when {
            errorMessage.contains("deprecated", ignoreCase = true) ->
                "Model has been deprecated"
            errorMessage.contains("period has ended", ignoreCase = true) ||
                errorMessage.contains("migrate to", ignoreCase = true) ->
                "Free period has ended - migrate to paid version"
            errorMessage.contains("free tier", ignoreCase = true) ||
                errorMessage.contains("free", ignoreCase = true) ->
                "Free tier temporarily unavailable"
            errorMessage.contains("providers", ignoreCase = true) ->
                "All providers are currently down"
            else ->
                "No endpoints available"
        }
    }

    /**
     * Get the current project, or null if no project is open
     */
    private fun getCurrentProject(): Project? {
        val projectManager = ProjectManager.getInstance()
        val openProjects = projectManager.openProjects

        return when {
            openProjects.isNotEmpty() -> openProjects[0]
            else -> projectManager.defaultProject
        }
    }

    /**
     * Clear the notification history (useful for testing)
     */
    fun clearNotificationHistory() {
        notifiedModels.clear()
        lastResetTime = System.currentTimeMillis()
        PluginLogger.Service.debug("Cleared model unavailability notification history")
    }

    /**
     * Check if we've already notified about a specific model
     */
    fun hasNotified(modelName: String): Boolean {
        return notifiedModels.contains(modelName)
    }
}
