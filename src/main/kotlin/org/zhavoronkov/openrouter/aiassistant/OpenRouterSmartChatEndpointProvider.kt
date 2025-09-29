package org.zhavoronkov.openrouter.aiassistant

import com.intellij.openapi.project.Project
import org.zhavoronkov.openrouter.services.OpenRouterService
import org.zhavoronkov.openrouter.services.OpenRouterSettingsService
import org.zhavoronkov.openrouter.utils.PluginLogger

/**
 * Provides smart chat endpoints for AI Assistant integration with OpenRouter
 * This integrates with the AI Assistant's smartChatEndpointProvider extension point
 */
class OpenRouterSmartChatEndpointProvider {
    
    private val settingsService = OpenRouterSettingsService.getInstance()
    private val openRouterService = OpenRouterService.getInstance()
    
    companion object {
        private const val PROVIDER_ID = "openrouter"
        private const val PROVIDER_NAME = "OpenRouter"
        private const val PROVIDER_DESCRIPTION = "Access 400+ AI models through OpenRouter.ai"
        private const val BASE_URL = "https://openrouter.ai/api/v1"
    }
    
    /**
     * Gets the provider identifier for AI Assistant
     */
    fun getProviderId(): String = PROVIDER_ID
    
    /**
     * Gets the provider display name shown in AI Assistant settings
     */
    fun getProviderName(): String = PROVIDER_NAME
    
    /**
     * Gets the provider description
     */
    fun getProviderDescription(): String = PROVIDER_DESCRIPTION
    
    /**
     * Checks if the provider is available and properly configured
     */
    fun isAvailable(project: Project?): Boolean {
        val configured = settingsService.isConfigured()
        PluginLogger.Settings.debug("OpenRouter provider availability check: configured=$configured")
        return configured
    }
    
    /**
     * Gets the chat endpoint URL for OpenRouter
     */
    fun getChatEndpoint(): String {
        return "$BASE_URL/chat/completions"
    }
    
    /**
     * Gets the API key for authentication
     */
    fun getApiKey(): String? {
        val apiKey = settingsService.getApiKey()
        return if (apiKey.isNotBlank()) apiKey else null
    }
    
    /**
     * Gets available models from OpenRouter
     */
    fun getAvailableModels(project: Project?): List<ChatEndpointModel> {
        PluginLogger.Settings.debug("Getting available OpenRouter models for AI Assistant")
        
        if (!settingsService.isConfigured()) {
            PluginLogger.Settings.debug("OpenRouter not configured, returning empty model list")
            return emptyList()
        }
        
        // Return a curated list of popular OpenRouter models
        return listOf(
            ChatEndpointModel(
                id = "openai/gpt-4o",
                name = "GPT-4o",
                description = "OpenAI's most advanced model",
                provider = PROVIDER_NAME,
                contextLength = 128000,
                supportsStreaming = true
            ),
            ChatEndpointModel(
                id = "openai/gpt-4o-mini",
                name = "GPT-4o Mini",
                description = "Fast and cost-effective GPT-4 model",
                provider = PROVIDER_NAME,
                contextLength = 128000,
                supportsStreaming = true
            ),
            ChatEndpointModel(
                id = "anthropic/claude-3-sonnet",
                name = "Claude 3 Sonnet",
                description = "Anthropic's balanced model",
                provider = PROVIDER_NAME,
                contextLength = 200000,
                supportsStreaming = true
            ),
            ChatEndpointModel(
                id = "anthropic/claude-3-haiku",
                name = "Claude 3 Haiku",
                description = "Fast and efficient Claude model",
                provider = PROVIDER_NAME,
                contextLength = 200000,
                supportsStreaming = true
            ),
            ChatEndpointModel(
                id = "google/gemini-pro",
                name = "Gemini Pro",
                description = "Google's advanced language model",
                provider = PROVIDER_NAME,
                contextLength = 30720,
                supportsStreaming = true
            ),
            ChatEndpointModel(
                id = "meta-llama/llama-3.1-8b-instruct",
                name = "Llama 3.1 8B Instruct",
                description = "Meta's open-source instruction model",
                provider = PROVIDER_NAME,
                contextLength = 131072,
                supportsStreaming = true
            ),
            ChatEndpointModel(
                id = "mistralai/mistral-7b-instruct",
                name = "Mistral 7B Instruct",
                description = "Mistral's instruction-following model",
                provider = PROVIDER_NAME,
                contextLength = 32768,
                supportsStreaming = true
            )
        )
    }
    
    /**
     * Gets the default model for this provider
     */
    fun getDefaultModel(): String {
        return "openai/gpt-4o-mini"
    }
    
    /**
     * Gets provider-specific headers for API requests
     */
    fun getHeaders(): Map<String, String> {
        val headers = mutableMapOf<String, String>()
        headers["Content-Type"] = "application/json"
        
        val apiKey = getApiKey()
        if (apiKey != null) {
            headers["Authorization"] = "Bearer $apiKey"
        }
        
        // OpenRouter-specific headers
        headers["HTTP-Referer"] = "https://github.com/DimazzzZ/openrouter-intellij-plugin"
        headers["X-Title"] = "OpenRouter IntelliJ Plugin"
        
        return headers
    }
    
    /**
     * Validates the provider configuration
     */
    fun validateConfiguration(project: Project?): ValidationResult {
        PluginLogger.Settings.debug("Validating OpenRouter configuration for AI Assistant")
        
        if (!settingsService.isConfigured()) {
            return ValidationResult(
                isValid = false,
                message = "OpenRouter is not configured. Please set your provisioning key in Settings → Tools → OpenRouter.",
                details = mapOf("needsSetup" to true)
            )
        }
        
        val apiKey = getApiKey()
        if (apiKey.isNullOrBlank()) {
            return ValidationResult(
                isValid = false,
                message = "No API key available. Please configure OpenRouter in Settings → Tools → OpenRouter.",
                details = mapOf("needsSetup" to true)
            )
        }
        
        return ValidationResult(
            isValid = true,
            message = "OpenRouter is configured and ready to use.",
            details = mapOf("needsSetup" to false)
        )
    }
}

/**
 * Represents a chat endpoint model
 */
data class ChatEndpointModel(
    val id: String,
    val name: String,
    val description: String,
    val provider: String,
    val contextLength: Int,
    val supportsStreaming: Boolean,
    val supportsImages: Boolean = false,
    val supportsFunctions: Boolean = false
)

