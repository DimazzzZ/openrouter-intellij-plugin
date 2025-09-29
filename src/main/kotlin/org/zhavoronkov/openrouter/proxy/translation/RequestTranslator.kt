package org.zhavoronkov.openrouter.proxy.translation

import org.zhavoronkov.openrouter.models.ChatCompletionRequest
import org.zhavoronkov.openrouter.models.ChatMessage
import org.zhavoronkov.openrouter.proxy.models.OpenAIChatCompletionRequest
import org.zhavoronkov.openrouter.proxy.models.OpenAIChatMessage
import org.zhavoronkov.openrouter.utils.PluginLogger

/**
 * Translates OpenAI API requests to OpenRouter API format
 */
object RequestTranslator {

    private const val DEFAULT_MAX_TOKENS = 1000
    private const val DEFAULT_TEMPERATURE = 0.7

    /**
     * Converts OpenAI chat completion request to OpenRouter format
     */
    fun translateChatCompletionRequest(
        openAIRequest: OpenAIChatCompletionRequest,
        openRouterModel: String? = null
    ): ChatCompletionRequest {
        PluginLogger.Service.debug("Translating OpenAI request to OpenRouter format")
        PluginLogger.Service.debug("Original model: ${openAIRequest.model}")
        PluginLogger.Service.debug("Target OpenRouter model: $openRouterModel")
        
        val targetModel = openRouterModel ?: mapOpenAIModelToOpenRouter(openAIRequest.model)
        
        // Force non-streaming for compatibility with current proxy implementation
        // Some clients (AI Assistant) may set stream=true by default; we disable it here
        if (openAIRequest.stream == true) {
            PluginLogger.Service.info("[Translate] Overriding stream=true to stream=false for compatibility")
        }
        val streamFlag = false

        return ChatCompletionRequest(
            model = targetModel,
            messages = openAIRequest.messages.map { translateMessage(it) },
            temperature = openAIRequest.temperature ?: DEFAULT_TEMPERATURE,
            maxTokens = openAIRequest.max_tokens ?: DEFAULT_MAX_TOKENS,
            topP = openAIRequest.top_p,
            frequencyPenalty = openAIRequest.frequency_penalty,
            presencePenalty = openAIRequest.presence_penalty,
            stop = openAIRequest.stop,
            stream = streamFlag
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

    /**
     * Maps OpenAI model names to OpenRouter model names
     * This provides intelligent model mapping for better compatibility
     */
    private fun mapOpenAIModelToOpenRouter(openAIModel: String): String {
        return when {
            // GPT-4 variants
            openAIModel.startsWith("gpt-4") -> {
                when {
                    openAIModel.contains("turbo") -> "openai/gpt-4-turbo"
                    openAIModel.contains("vision") -> "openai/gpt-4-vision-preview"
                    openAIModel.contains("32k") -> "openai/gpt-4-32k"
                    else -> "openai/gpt-4"
                }
            }
            
            // GPT-3.5 variants
            openAIModel.startsWith("gpt-3.5") -> {
                when {
                    openAIModel.contains("turbo") -> "openai/gpt-3.5-turbo"
                    openAIModel.contains("16k") -> "openai/gpt-3.5-turbo-16k"
                    else -> "openai/gpt-3.5-turbo"
                }
            }
            
            // Claude variants
            openAIModel.startsWith("claude") -> {
                when {
                    openAIModel.contains("3-5") -> "anthropic/claude-3.5-sonnet"
                    openAIModel.contains("3") -> "anthropic/claude-3-sonnet"
                    openAIModel.contains("2") -> "anthropic/claude-2"
                    else -> "anthropic/claude-3.5-sonnet"
                }
            }
            
            // Gemini variants
            openAIModel.startsWith("gemini") -> {
                when {
                    openAIModel.contains("pro") -> "google/gemini-pro"
                    openAIModel.contains("ultra") -> "google/gemini-ultra"
                    else -> "google/gemini-pro"
                }
            }
            
            // Default fallback - use the model as-is or default to GPT-3.5
            else -> {
                PluginLogger.Service.debug("Unknown model '$openAIModel', using as-is")
                if (openAIModel.contains("/")) {
                    // Already in OpenRouter format
                    openAIModel
                } else {
                    // Default to a reliable model
                    "openai/gpt-3.5-turbo"
                }
            }
        }
    }

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
