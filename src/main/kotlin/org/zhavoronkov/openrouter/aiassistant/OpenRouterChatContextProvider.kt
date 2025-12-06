package org.zhavoronkov.openrouter.aiassistant

import com.intellij.openapi.project.Project
import org.zhavoronkov.openrouter.services.OpenRouterSettingsService
import org.zhavoronkov.openrouter.utils.PluginLogger

/**
 * Provides context for AI Assistant chat conversations using OpenRouter
 * This integrates with the AI Assistant's chatContextProvider extension point
 */
class OpenRouterChatContextProvider {

    private val settingsService = OpenRouterSettingsService.getInstance()

    companion object {
        private const val PROVIDER_ID = "openrouter"
        private const val PROVIDER_NAME = "OpenRouter"
    }

    /**
     * Gets the provider identifier
     */
    fun getProviderId(): String = PROVIDER_ID

    /**
     * Gets the provider display name
     */
    fun getProviderName(): String = PROVIDER_NAME

    /**
     * Checks if the provider is available and configured
     */
    fun isAvailable(project: Project?): Boolean {
        return settingsService.isConfigured()
    }

    /**
     * Provides context information for chat conversations
     */
    fun getContextInfo(project: Project?): Map<String, Any> {
        PluginLogger.Settings.debug("Providing chat context for OpenRouter")

        return mapOf(
            "providerId" to PROVIDER_ID,
            "providerName" to PROVIDER_NAME,
            "isConfigured" to settingsService.isConfigured(),
            "hasProvisioningKey" to settingsService.getProvisioningKey().isNotBlank(),
            "hasApiKey" to settingsService.getApiKey().isNotBlank(),
            "supportedFeatures" to listOf(
                "chat",
                "completion",
                "streaming",
                "multiple_models"
            ),
            "modelCount" to 400 // OpenRouter supports 400+ models
        )
    }

    /**
     * Gets available models for context
     */
    fun getAvailableModels(): List<String> {
        return if (settingsService.isConfigured()) {
            // Return a selection of popular OpenRouter models
            listOf(
                "openai/gpt-4o",
                "openai/gpt-4o-mini",
                "anthropic/claude-3-sonnet",
                "anthropic/claude-3-haiku",
                "google/gemini-pro",
                "meta-llama/llama-3.1-8b-instruct",
                "mistralai/mistral-7b-instruct"
            )
        } else {
            emptyList()
        }
    }

    /**
     * Gets the default model for this provider
     */
    fun getDefaultModel(): String? {
        return if (settingsService.isConfigured()) {
            "openai/gpt-4o-mini" // Default to a cost-effective model
        } else {
            null
        }
    }
}
