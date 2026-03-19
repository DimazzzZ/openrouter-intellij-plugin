package org.zhavoronkov.openrouter.startup

import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import org.zhavoronkov.openrouter.services.CreditUsageHistoryService
import org.zhavoronkov.openrouter.services.OpenRouterSettingsService
import org.zhavoronkov.openrouter.utils.PluginLogger

/**
 * Startup activity to initialize the credit usage history service.
 *
 * This service tracks credit usage over time to enable accurate "today's spending"
 * calculations, since the OpenRouter API doesn't provide real-time daily data.
 */
class CreditHistoryStartupActivity : ProjectActivity {

    override suspend fun execute(project: Project) {
        try {
            val settingsService = OpenRouterSettingsService.getInstance()

            if (settingsService.isConfigured()) {
                PluginLogger.Service.info("Starting CreditUsageHistoryService snapshot timer")
                val historyService = CreditUsageHistoryService.getInstance()
                historyService.startSnapshotTimer()
                PluginLogger.Service.info(
                    "CreditUsageHistoryService initialized with ${historyService.getSnapshotCount()} existing snapshots"
                )
            } else {
                PluginLogger.Service.debug(
                    "CreditUsageHistoryService: Skipping startup - plugin not configured"
                )
            }
        } catch (e: IllegalStateException) {
            PluginLogger.Service.warn("Failed to initialize CreditUsageHistoryService: ${e.message}")
        }
    }
}
