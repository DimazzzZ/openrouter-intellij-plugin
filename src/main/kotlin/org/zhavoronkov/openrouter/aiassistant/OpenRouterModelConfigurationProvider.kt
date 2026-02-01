package org.zhavoronkov.openrouter.aiassistant

import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.Project
import org.zhavoronkov.openrouter.services.OpenRouterSettingsService
import org.zhavoronkov.openrouter.settings.OpenRouterConfigurable
import org.zhavoronkov.openrouter.utils.PluginLogger

/**
 * OpenRouter Model Configuration Provider for AI Assistant integration
 * Handles configuration aspects and settings integration
 */

@Suppress("TooManyFunctions")
class OpenRouterModelConfigurationProvider {

    private val settingsService = OpenRouterSettingsService.getInstance()
    private val modelProvider = OpenRouterModelProvider()

    companion object {
        private const val PROVIDER_ID = "openrouter"
        private const val PROVIDER_NAME = "OpenRouter"
    }

    /**
     * Get the unique provider identifier
     */
    fun getProviderId(): String = PROVIDER_ID

    /**
     * Get provider information (display name and description)
     */
    fun getProviderInfo(): ProviderInfo {
        return ProviderInfo(
            displayName = modelProvider.getProviderDisplayName(),
            description = modelProvider.getProviderDescription()
        )
    }

    /**
     * Check if the provider is properly configured
     */
    fun isConfigured(): Boolean {
        return settingsService.isConfigured() && modelProvider.isAvailable()
    }

    /**
     * Get current configuration status message
     */
    fun getConfigurationStatusMessage(): String {
        return modelProvider.getConfigurationStatus()
    }

    /**
     * Get configuration instructions for users
     */
    fun getConfigurationInstructions(): String {
        return """
            To configure OpenRouter for AI Assistant:

            1. Get a Provisioning Key from https://openrouter.ai/settings/provisioning-keys
            2. Open IntelliJ IDEA Settings → Tools → OpenRouter
            3. Enter your Provisioning Key
            4. Click "Test Connection" to verify setup
            5. OpenRouter models will appear in AI Assistant → Models

            The OpenRouter plugin will automatically create an API key for AI Assistant usage.
        """.trimIndent()
    }

    /**
     * Open OpenRouter settings when user needs to configure
     */
    fun openSettings(project: Project?) {
        try {
            ShowSettingsUtil.getInstance().showSettingsDialog(
                project,
                OpenRouterConfigurable::class.java
            )
        } catch (e: IllegalStateException) {
            PluginLogger.Service.error("Error opening OpenRouter settings", e)
        } catch (e: IllegalArgumentException) {
            PluginLogger.Service.error("Error opening OpenRouter settings", e)
        }
    }

    /**
     * Get available model configurations
     */
    fun getModelConfigurations(): List<ModelConfiguration> {
        return if (isConfigured()) {
            modelProvider.getAvailableModels().map { model ->
                ModelConfiguration(
                    id = model.id,
                    displayName = model.name,
                    description = model.description,
                    provider = PROVIDER_NAME,
                    capabilities = ModelCapabilities(
                        supportsChat = model.supportsChat,
                        supportsCompletion = model.supportsCompletion,
                        supportsStreaming = model.supportsStreaming,
                        maxContextLength = model.contextLength ?: 8192 // Default context length
                    ),
                    isAvailable = true,
                    configurationRequired = false
                )
            }
        } else {
            // Return empty list with a configuration prompt
            emptyList()
        }
    }

    /**
     * Get a specific model configuration by ID
     */
    fun getModelConfiguration(modelId: String): ModelConfiguration? {
        return getModelConfigurations().find { it.id == modelId }
    }

    /**
     * Validate a model configuration
     */
    fun validateModelConfiguration(modelId: String): ValidationResult {
        return try {
            when {
                !isConfigured() -> ValidationResult(
                    isValid = false,
                    message = "OpenRouter not configured. ${getConfigurationInstructions()}"
                )
                modelProvider.getModel(modelId) == null -> ValidationResult(
                    isValid = false,
                    message = "Model '$modelId' is not available through OpenRouter"
                )
                !modelProvider.testConnection() -> ValidationResult(
                    isValid = false,
                    message = "Connection test failed. Please check your API key."
                )
                else -> ValidationResult(isValid = true, message = "Model configuration is valid")
            }
        } catch (e: IllegalStateException) {
            PluginLogger.Service.error("Error validating model configuration", e)
            ValidationResult(
                isValid = false,
                message = "Validation error: ${e.message}"
            )
        } catch (e: IllegalArgumentException) {
            PluginLogger.Service.error("Error validating model configuration", e)
            ValidationResult(
                isValid = false,
                message = "Validation error: ${e.message}"
            )
        }
    }

    /**
     * Get provider-specific settings that might be shown in AI Assistant settings
     */
    fun getProviderSettings(): Map<String, Any> {
        return mapOf(
            "configured" to isConfigured(),
            "status" to getConfigurationStatusMessage(),
            "availableModels" to getModelConfigurations().size,
            "needsConfiguration" to !isConfigured(),
            "configurationUrl" to "settings://Tools/OpenRouter"
        )
    }

    /**
     * Handle provider activation/deactivation
     */
    fun onProviderStateChanged(activated: Boolean) {
        try {
            if (activated) {
                PluginLogger.Service.info("OpenRouter provider activated for AI Assistant")
                if (!isConfigured()) {
                    PluginLogger.Service.info("OpenRouter not configured - prompting user for setup")
                }
            } else {
                PluginLogger.Service.info("OpenRouter provider deactivated for AI Assistant")
            }
        } catch (e: IllegalStateException) {
            PluginLogger.Service.error("Error handling provider state change", e)
        } catch (e: IllegalArgumentException) {
            PluginLogger.Service.error("Error handling provider state change", e)
        }
    }
}

/**
 * Data classes for configuration
 */
data class ModelConfiguration(
    val id: String,
    val displayName: String,
    val description: String,
    val provider: String,
    val capabilities: ModelCapabilities,
    val isAvailable: Boolean,
    val configurationRequired: Boolean,
    val additionalInfo: Map<String, Any> = emptyMap()
)

data class ModelCapabilities(
    val supportsChat: Boolean,
    val supportsCompletion: Boolean,
    val supportsStreaming: Boolean,
    val maxContextLength: Int,
    val supportsImages: Boolean = false,
    val supportsCodeGeneration: Boolean = true,
    val supportsFunction: Boolean = false
)

data class ValidationResult(
    val isValid: Boolean,
    val message: String,
    val details: Map<String, Any> = emptyMap()
)

data class ProviderInfo(
    val displayName: String,
    val description: String
)
