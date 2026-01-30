package org.zhavoronkov.openrouter.aiassistant

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.zhavoronkov.openrouter.models.ApiResult
import org.zhavoronkov.openrouter.services.OpenRouterService
import org.zhavoronkov.openrouter.services.OpenRouterSettingsService
import org.zhavoronkov.openrouter.utils.PluginLogger

/**
 * OpenRouter Model Provider for AI Assistant integration
 * This class serves as a bridge between OpenRouter API and IntelliJ AI Assistant plugin
 *
 * Note: This implementation uses educated guesses about the AI Assistant plugin's API
 * since the exact interface specifications are not publicly documented.
 */
@Suppress("TooGenericExceptionCaught")
class OpenRouterModelProvider {

    private val openRouterService = OpenRouterService.getInstance()
    private val settingsService = OpenRouterSettingsService.getInstance()

    companion object {
        private const val PROVIDER_NAME = "OpenRouter"
        private const val PROVIDER_DISPLAY_NAME = "OpenRouter (400+ AI Models)"
        private const val PROVIDER_DESCRIPTION = "Access to 400+ AI models through OpenRouter.ai unified API"

        // Connection test timeout (in milliseconds)
        private const val CONNECTION_TEST_TIMEOUT_MS = 10000L

        // Common OpenRouter models that work well for code assistance
        private val SUPPORTED_MODELS = listOf(
            "openai/gpt-4o",
            "openai/gpt-4o-mini",
            "anthropic/claude-3.5-sonnet",
            "anthropic/claude-3-haiku",
            "google/gemini-pro-1.5",
            "meta-llama/llama-3.1-70b-instruct",
            "microsoft/wizardlm-2-8x22b",
            "qwen/qwen-2.5-72b-instruct"
        )
    }

    /**
     * Get the provider name for AI Assistant
     */
    @Suppress("unused") // Public API method
    fun getProviderName(): String = PROVIDER_NAME

    /**
     * Get the provider display name for AI Assistant UI
     */
    fun getProviderDisplayName(): String = PROVIDER_DISPLAY_NAME

    /**
     * Get the provider description
     */
    fun getProviderDescription(): String = PROVIDER_DESCRIPTION

    /**
     * Check if the provider is available and configured
     */
    fun isAvailable(): Boolean {
        return try {
            settingsService.isConfigured() && settingsService.getApiKey().isNotBlank()
        } catch (e: IllegalStateException) {
            PluginLogger.Service.warn("Error checking OpenRouter availability", e)
            false
        } catch (e: IllegalArgumentException) {
            PluginLogger.Service.warn("Error checking OpenRouter availability", e)
            false
        }
    }

    /**
     * Get list of available models from OpenRouter
     * Returns a subset of popular models suitable for code assistance
     */
    fun getAvailableModels(): List<OpenRouterAIModel> {
        return if (isAvailable()) {
            SUPPORTED_MODELS.map { modelId ->
                OpenRouterAIModel(
                    id = modelId,
                    name = getModelDisplayName(modelId),
                    description = getModelDescription(modelId),
                    provider = "OpenRouter",
                    supportsChat = true,
                    supportsCompletion = true,
                    supportsStreaming = true
                )
            }
        } else {
            emptyList()
        }
    }

    /**
     * Get a specific model by ID
     */
    fun getModel(modelId: String): OpenRouterAIModel? {
        return getAvailableModels().find { it.id == modelId }
    }

    /**
     * Test if the provider is working correctly
     */
    fun testConnection(): Boolean {
        return try {
            if (!isAvailable()) return false

            // Use the existing OpenRouterService test connection method
            runBlocking {
                withTimeout(CONNECTION_TEST_TIMEOUT_MS) {
                    val result = openRouterService.testConnection()
                    when (result) {
                        is ApiResult.Success -> result.data
                        is ApiResult.Error -> false
                    }
                }
            }
        } catch (e: IllegalStateException) {
            PluginLogger.Service.warn("OpenRouter connection test failed", e)
            false
        } catch (e: IllegalArgumentException) {
            PluginLogger.Service.warn("OpenRouter connection test failed", e)
            false
        }
    }

    /**
     * Get configuration status for AI Assistant settings
     */
    fun getConfigurationStatus(): String {
        return when {
            !settingsService.isConfigured() -> "Not configured - Please set up OpenRouter API key"
            !testConnection() -> "Configuration issue - Please check API key"
            else -> "Ready - ${getAvailableModels().size} models available"
        }
    }

    private fun getModelDisplayName(modelId: String): String {
        return when {
            modelId.contains("gpt-4o-mini") -> "GPT-4o Mini"
            modelId.contains("gpt-4o") -> "GPT-4o"
            modelId.contains("claude-3.5-sonnet") -> "Claude 3.5 Sonnet"
            modelId.contains("claude-3-haiku") -> "Claude 3 Haiku"
            modelId.contains("gemini-pro-1.5") -> "Gemini Pro 1.5"
            modelId.contains("llama-3.1-70b") -> "Llama 3.1 70B"
            modelId.contains("wizardlm-2-8x22b") -> "WizardLM-2 8x22B"
            modelId.contains("qwen-2.5-72b") -> "Qwen 2.5 72B"
            else -> modelId.substringAfter("/").replace("-", " ").split(" ")
                .joinToString(" ") { it.replaceFirstChar { char -> char.uppercase() } }
        }
    }

    private fun getModelDescription(modelId: String): String {
        return when {
            modelId.contains("gpt-4o") -> "OpenAI's latest multimodal model with improved reasoning"
            modelId.contains("claude-3.5-sonnet") -> "Anthropic's most capable model for complex tasks"
            modelId.contains("claude-3-haiku") -> "Anthropic's fastest model for quick responses"
            modelId.contains("gemini-pro-1.5") -> "Google's advanced AI model with long context"
            modelId.contains("llama-3.1-70b") -> "Meta's open-source model with excellent performance"
            modelId.contains("wizardlm-2") -> "Microsoft's high-performance instruction-following model"
            modelId.contains("qwen-2.5-72b") -> "Alibaba's multilingual model with strong coding abilities"
            else -> "AI model available through OpenRouter"
        }
    }
}

/**
 * Data class representing an OpenRouter AI model for AI Assistant integration
 */
data class OpenRouterAIModel(
    val id: String,
    val name: String,
    val description: String,
    val provider: String,
    val supportsChat: Boolean = true,
    val supportsCompletion: Boolean = true,
    val supportsStreaming: Boolean = true,
    val contextLength: Int? = null,
    val pricing: ModelPricing? = null
) {
    data class ModelPricing(
        val inputCostPer1kTokens: Double,
        val outputCostPer1kTokens: Double
    )
}
