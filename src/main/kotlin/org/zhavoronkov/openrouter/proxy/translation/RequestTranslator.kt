package org.zhavoronkov.openrouter.proxy.translation

import org.zhavoronkov.openrouter.models.ChatCompletionRequest
import org.zhavoronkov.openrouter.models.ChatMessage
import org.zhavoronkov.openrouter.proxy.models.OpenAIChatCompletionRequest
import org.zhavoronkov.openrouter.proxy.models.OpenAIChatMessage
import org.zhavoronkov.openrouter.services.OpenRouterSettingsService
import org.zhavoronkov.openrouter.utils.PluginLogger

/**
 * Translates OpenAI API requests to OpenRouter API format
 */
object RequestTranslator {

    private val settingsService by lazy {
        try {
            OpenRouterSettingsService.getInstance()
        } catch (e: Exception) {
            // In test environment, return null or a mock
            null
        }
    }

    private const val DEFAULT_MAX_TOKENS = 1000
    private const val DEFAULT_TEMPERATURE = 0.7

    /**
     * Converts OpenAI chat completion request to OpenRouter format
     * Model names should already be normalized by the servlet before calling this
     */
    fun translateChatCompletionRequest(
        openAIRequest: OpenAIChatCompletionRequest
    ): ChatCompletionRequest {
        PluginLogger.Service.debug("Translating OpenAI request to OpenRouter format")
        PluginLogger.Service.debug("Model: ${openAIRequest.model}, Stream: ${openAIRequest.stream}")

        // Apply default max tokens only if feature is enabled (defaultMaxTokens > 0)
        val defaultMaxTokens = if (settingsService?.getDefaultMaxTokens() ?: 0 > 0) {
            settingsService?.getDefaultMaxTokens()
        } else {
            null
        }

        return ChatCompletionRequest(
            model = openAIRequest.model, // Model name should already be normalized (e.g., "openai/gpt-4")
            messages = openAIRequest.messages.map { translateMessage(it) },
            temperature = openAIRequest.temperature ?: DEFAULT_TEMPERATURE,
            maxTokens = openAIRequest.max_tokens ?: defaultMaxTokens,
            topP = openAIRequest.top_p,
            frequencyPenalty = openAIRequest.frequency_penalty,
            presencePenalty = openAIRequest.presence_penalty,
            stop = openAIRequest.stop,
            stream = openAIRequest.stream ?: false // Pass through streaming flag as-is
        )
    }

    /**
     * Converts OpenAI message to OpenRouter format
     */
    private fun translateMessage(openAIMessage: OpenAIChatMessage): ChatMessage {
        return ChatMessage(
            role = openAIMessage.role,
            content = openAIMessage.content,
            name = openAIMessage.name
        )
    }

    // REMOVED: Model mapping logic eliminated for simplicity
    // Models are now passed through exactly as requested

    /**
     * Validates that the translated request is valid for OpenRouter
     */
    fun validateTranslatedRequest(request: ChatCompletionRequest): Boolean {
        return try {
            // Basic validation
            request.model.isNotBlank() &&
                request.messages.isNotEmpty() &&
                request.messages.all { it.role.isNotBlank() && it.content.isNotBlank() } &&
                (request.temperature == null || request.temperature in 0.0..2.0) &&
                (request.maxTokens == null || request.maxTokens > 0) &&
                (request.topP == null || request.topP in 0.0..1.0)
        } catch (e: Exception) {
            PluginLogger.Service.error("Request validation failed", e)
            false
        }
    }

    /**
     * Gets available model mappings for documentation/debugging
     */
    fun getModelMappings(): Map<String, String> {
        return mapOf(
            "gpt-4" to "openai/gpt-4",
            "gpt-4o" to "openai/gpt-4o",
            "gpt-4o-mini" to "openai/gpt-4o-mini",
            "gpt-4-turbo" to "openai/gpt-4-turbo",
            "gpt-4-vision-preview" to "openai/gpt-4-vision-preview",
            "gpt-3.5-turbo" to "openai/gpt-3.5-turbo",
            "gpt-3.5-turbo-16k" to "openai/gpt-3.5-turbo-16k",
            "claude-3-sonnet" to "anthropic/claude-3-sonnet",
            "claude-3.5-sonnet" to "anthropic/claude-3.5-sonnet",
            "gemini-pro" to "google/gemini-pro"
        )
    }
}
