package org.zhavoronkov.openrouter.aiassistant

import com.intellij.openapi.project.Project
import org.zhavoronkov.openrouter.services.OpenRouterSettingsService
import org.zhavoronkov.openrouter.utils.OpenRouterRequestBuilder
import org.zhavoronkov.openrouter.utils.PluginLogger

/**
 * Provides smart chat endpoints for AI Assistant integration with OpenRouter
 * This integrates with the AI Assistant's smartChatEndpointProvider extension point
 */

@Suppress("TooManyFunctions")
class OpenRouterSmartChatEndpointProvider {

    private val settingsService = OpenRouterSettingsService.getInstance()

    companion object {
        private const val PROVIDER_ID = "openrouter"
        private const val PROVIDER_NAME = "OpenRouter"
        private const val PROVIDER_DESCRIPTION = "Access 400+ AI models through OpenRouter.ai"
        private const val BASE_URL = "https://openrouter.ai/api/v1"
        private const val DEFAULT_MODEL = "openai/gpt-4o-mini"

        // Model context lengths
        private const val CONTEXT_128K = 128000
        private const val CONTEXT_200K = 200000
        private const val CONTEXT_30K = 30720
        private const val CONTEXT_131K = 131072
        private const val CONTEXT_32K = 32768

        // Curated model definitions
        private val CURATED_MODELS = listOf(
            ModelDef(
                "openai/gpt-4o",
                "GPT-4o",
                "OpenAI's most advanced model",
                CONTEXT_128K
            ),
            ModelDef(
                "openai/gpt-4o-mini",
                "GPT-4o Mini",
                "Fast and cost-effective GPT-4 model",
                CONTEXT_128K
            ),
            ModelDef(
                "anthropic/claude-3-sonnet",
                "Claude 3 Sonnet",
                "Anthropic's balanced model",
                CONTEXT_200K
            ),
            ModelDef(
                "anthropic/claude-3-haiku",
                "Claude 3 Haiku",
                "Fast and efficient Claude model",
                CONTEXT_200K
            ),
            ModelDef(
                "google/gemini-pro",
                "Gemini Pro",
                "Google's advanced language model",
                CONTEXT_30K
            ),
            ModelDef(
                "meta-llama/llama-3.1-8b-instruct",
                "Llama 3.1 8B Instruct",
                "Meta's open-source instruction model",
                CONTEXT_131K
            ),
            ModelDef(
                "mistralai/mistral-7b-instruct",
                "Mistral 7B Instruct",
                "Mistral's instruction-following model",
                CONTEXT_32K
            )
        )
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
     * @param project The project context (reserved for future project-specific configuration)
     */
    @Suppress("UnusedParameter")
    fun isAvailable(project: Project?): Boolean {
        // Project parameter reserved for future project-specific configuration
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
     * @param project The project context (reserved for future project-specific model lists)
     */
    @Suppress("UnusedParameter")
    fun getAvailableModels(project: Project?): List<ChatEndpointModel> {
        // Project parameter reserved for future project-specific model lists
        PluginLogger.Settings.debug("Getting available OpenRouter models for AI Assistant")

        if (!settingsService.isConfigured()) {
            PluginLogger.Settings.debug("OpenRouter not configured, returning empty model list")
            return emptyList()
        }

        return getCuratedModelList()
    }

    /**
     * Returns a curated list of popular OpenRouter models
     */
    private fun getCuratedModelList(): List<ChatEndpointModel> =
        CURATED_MODELS.map { model ->
            ChatEndpointModel(
                id = model.id,
                name = model.name,
                description = model.description,
                provider = PROVIDER_NAME,
                contextLength = model.contextLength,
                supportsStreaming = true
            )
        }

    /**
     * Temporary data class for model definitions
     */
    private data class ModelDef(val id: String, val name: String, val description: String, val contextLength: Int)

    /**
     * Gets the default model for this provider
     */
    fun getDefaultModel(): String = DEFAULT_MODEL

    /**
     * Gets provider-specific headers for API requests
     */
    fun getHeaders(): Map<String, String> {
        return OpenRouterRequestBuilder.getStandardHeaders(authToken = getApiKey())
    }

    /**
     * Validates the provider configuration
     * @param project The project context (reserved for future project-specific validation)
     */
    @Suppress("UnusedParameter")
    fun validateConfiguration(project: Project?): ValidationResult {
        // Project parameter reserved for future project-specific validation
        PluginLogger.Settings.debug("Validating OpenRouter configuration for AI Assistant")

        return when {
            !settingsService.isConfigured() -> ValidationResult(
                isValid = false,
                message = "OpenRouter is not configured. Please set your provisioning key in " +
                    "Settings → Tools → OpenRouter.",
                details = mapOf("needsSetup" to true)
            )
            getApiKey().isNullOrBlank() -> ValidationResult(
                isValid = false,
                message = "No API key available. Please configure OpenRouter in " +
                    "Settings → Tools → OpenRouter.",
                details = mapOf("needsSetup" to true)
            )
            else -> ValidationResult(
                isValid = true,
                message = "OpenRouter is configured and ready to use.",
                details = mapOf("needsSetup" to false)
            )
        }
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
