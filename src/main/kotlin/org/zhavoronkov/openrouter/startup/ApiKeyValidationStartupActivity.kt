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
import java.io.IOException

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

    companion object {
        private const val UNAUTHORIZED_STATUS_CODE = 401
    }

    override suspend fun execute(project: Project) {
        try {
            val settingsService = OpenRouterSettingsService.getInstance()
            val openRouterService = OpenRouterService.getInstance()

            if (!shouldValidateApiKey(settingsService)) {
                return
            }

            val storedApiKey = settingsService.apiKeyManager.getStoredApiKey()
            if (storedApiKey.isNullOrBlank()) {
                PluginLogger.Startup.info("No API key stored - will be created when settings panel loads")
                return
            }

            validateAndHandleApiKey(storedApiKey, openRouterService)
        } catch (e: IllegalStateException) {
            PluginLogger.Startup.error("Error during API key validation on startup - service not available", e)
        } catch (e: IOException) {
            PluginLogger.Startup.error("Network error during API key validation on startup", e)
        }
    }

    private fun shouldValidateApiKey(settingsService: OpenRouterSettingsService): Boolean {
        val usesExtendedScope = settingsService.apiKeyManager.authScope == AuthScope.EXTENDED
        if (!usesExtendedScope) {
            PluginLogger.Startup.debug("Skipping API key validation - not using EXTENDED auth scope")
        }

        val hasProvisioningKey = settingsService.getProvisioningKey().isNotBlank()
        if (!hasProvisioningKey) {
            PluginLogger.Startup.debug("Skipping API key validation - no provisioning key configured")
        }

        return usesExtendedScope && hasProvisioningKey
    }

    private suspend fun validateAndHandleApiKey(apiKey: String, openRouterService: OpenRouterService) {
        val maskedKey = KeyValidator.maskApiKey(apiKey)
        PluginLogger.Startup.info("Validating stored API key on startup: $maskedKey")

        withContext(Dispatchers.IO) {
            val result = openRouterService.testApiKey(apiKey)

            when (result) {
                is ApiResult.Success<*> -> {
                    PluginLogger.Startup.info("✅ Stored API key is valid")
                }
                is ApiResult.Error -> {
                    handleValidationError(result)
                }
            }
        }
    }

    private suspend fun handleValidationError(error: ApiResult.Error) {
        val statusCode = error.statusCode ?: 0
        if (statusCode == UNAUTHORIZED_STATUS_CODE) {
            PluginLogger.Startup.warn(
                "⚠️ Stored API key is invalid (401 Unauthorized) - regenerating automatically"
            )
            regenerateApiKey()
        } else {
            PluginLogger.Startup.warn(
                "⚠️ API key validation failed with status $statusCode: " +
                    "${error.message} - keeping existing key (may be temporary network issue)"
            )
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
            } catch (e: IllegalStateException) {
                PluginLogger.Startup.error("Failed to regenerate API key on startup - service not available", e)
            } catch (e: IOException) {
                PluginLogger.Startup.error("Network error regenerating API key on startup", e)
            }
        }
    }
}
