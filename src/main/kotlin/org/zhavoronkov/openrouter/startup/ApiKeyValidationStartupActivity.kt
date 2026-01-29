package org.zhavoronkov.openrouter.startup

import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.zhavoronkov.openrouter.models.ApiResult
import org.zhavoronkov.openrouter.models.AuthScope
import org.zhavoronkov.openrouter.services.OpenRouterService
import org.zhavoronkov.openrouter.services.OpenRouterSettingsService
import org.zhavoronkov.openrouter.settings.IntellijApiKeyManager
import org.zhavoronkov.openrouter.utils.KeyValidator
import org.zhavoronkov.openrouter.utils.PluginLogger

/**
 * Startup activity that validates the stored API key on plugin startup
 * 
 * This prevents using stale/invalid API keys that were deleted from OpenRouter
 * but are still stored in the plugin settings. If the stored key is invalid,
 * it will be regenerated automatically.
 * 
 * This runs BEFORE the proxy server starts accepting requests to ensure
 * all requests use a valid API key.
 */
class ApiKeyValidationStartupActivity : ProjectActivity {

    override suspend fun execute(project: Project) {
        try {
            val settingsService = OpenRouterSettingsService.getInstance()
            val openRouterService = OpenRouterService.getInstance()

            // Only validate if using EXTENDED auth scope (provisioning key + auto-created API key)
            if (settingsService.apiKeyManager.authScope != AuthScope.EXTENDED) {
                PluginLogger.Startup.debug("Skipping API key validation - not using EXTENDED auth scope")
                return
            }

            // Only validate if provisioning key is configured
            val provisioningKey = settingsService.getProvisioningKey()
            if (provisioningKey.isBlank()) {
                PluginLogger.Startup.debug("Skipping API key validation - no provisioning key configured")
                return
            }

            // Get the stored API key
            val storedApiKey = settingsService.apiKeyManager.getStoredApiKey()
            if (storedApiKey.isNullOrBlank()) {
                PluginLogger.Startup.info("No API key stored - will be created when settings panel loads")
                return
            }

            val maskedKey = KeyValidator.maskApiKey(storedApiKey)
            PluginLogger.Startup.info("Validating stored API key on startup: $maskedKey")

            // Validate the stored API key by making a test request
            withContext(Dispatchers.IO) {
                val result = openRouterService.testApiKey(storedApiKey)

                when (result) {
                    is ApiResult.Success<*> -> {
                        PluginLogger.Startup.info("✅ Stored API key is valid")
                    }
                    is ApiResult.Error -> {
                        val statusCode = result.statusCode ?: 0
                        if (statusCode == 401) {
                            // 401 means the key is invalid (user not found, key deleted, etc.)
                            PluginLogger.Startup.warn(
                                "⚠️ Stored API key is invalid (401 Unauthorized) - regenerating automatically"
                            )
                            regenerateApiKey()
                        } else {
                            // Other errors (network, server errors) - don't regenerate
                            PluginLogger.Startup.warn(
                                "⚠️ API key validation failed with status $statusCode: ${result.message} - " +
                                    "keeping existing key (may be temporary network issue)"
                            )
                        }
                    }
                }
            }
        } catch (e: Exception) {
            PluginLogger.Startup.error("Error during API key validation on startup", e)
        }
    }
    
    /**
     * Regenerates the IntelliJ API key by calling the IntellijApiKeyManager
     */
    private suspend fun regenerateApiKey() {
        withContext(Dispatchers.IO) {
            try {
                val settingsService = OpenRouterSettingsService.getInstance()
                val openRouterService = OpenRouterService.getInstance()
                val intellijKeyManager = IntellijApiKeyManager(settingsService, openRouterService)

                PluginLogger.Startup.info("Regenerating IntelliJ API key...")

                // Use the IntellijApiKeyManager to recreate the key
                // This will delete the old key and create a new one
                intellijKeyManager.recreateIntellijApiKey()

                PluginLogger.Startup.info("✅ API key regenerated successfully")
            } catch (e: Exception) {
                PluginLogger.Startup.error("Failed to regenerate API key on startup", e)
            }
        }
    }
}

