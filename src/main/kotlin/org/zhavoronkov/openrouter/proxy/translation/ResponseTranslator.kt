package org.zhavoronkov.openrouter.proxy.translation

import org.zhavoronkov.openrouter.models.ChatCompletionResponse
import org.zhavoronkov.openrouter.models.ProvidersResponse
import org.zhavoronkov.openrouter.proxy.models.*
import org.zhavoronkov.openrouter.utils.PluginLogger
import java.util.*

/**
 * Translates OpenRouter API responses to OpenAI API format
 */
object ResponseTranslator {

    /**
     * Converts OpenRouter chat completion response to OpenAI format
     */
    fun translateChatCompletionResponse(
        openRouterResponse: ChatCompletionResponse,
        requestId: String = generateRequestId()
    ): OpenAIChatCompletionResponse {
        PluginLogger.Service.debug("Translating OpenRouter response to OpenAI format")
        
        return OpenAIChatCompletionResponse(
            id = requestId,
            created = System.currentTimeMillis() / 1000,
            model = mapOpenRouterModelToOpenAI(openRouterResponse.model ?: "unknown"),
            choices = openRouterResponse.choices?.mapIndexed { index, choice ->
                OpenAIChatChoice(
                    index = index,
                    message = OpenAIChatMessage(
                        role = choice.message?.role ?: "assistant",
                        content = choice.message?.content ?: ""
                    ),
                    finish_reason = choice.finishReason
                )
            } ?: emptyList(),
            usage = openRouterResponse.usage?.let { usage ->
                OpenAIUsage(
                    prompt_tokens = usage.promptTokens ?: 0,
                    completion_tokens = usage.completionTokens ?: 0,
                    total_tokens = usage.totalTokens ?: 0
                )
            }
        )
    }

    /**
     * Converts OpenRouter providers response to OpenAI models format
     */
    fun translateModelsResponse(
        providersResponse: ProvidersResponse
    ): OpenAIModelsResponse {
        PluginLogger.Service.debug("Translating OpenRouter providers to OpenAI models format")

        // Focus on core OpenAI models that AI Assistant recognizes
        // Use minimal, clean model list for better compatibility
        val coreModels = listOf(
            OpenAIModel(
                id = "gpt-4",
                created = 1687882411,
                owned_by = "openai",
                permission = listOf(createDefaultPermission()),
                root = "gpt-4",
                parent = null
            ),
            OpenAIModel(
                id = "gpt-4-turbo",
                created = 1712361441,
                owned_by = "openai",
                permission = listOf(createDefaultPermission()),
                root = "gpt-4-turbo",
                parent = null
            ),
            OpenAIModel(
                id = "gpt-3.5-turbo",
                created = 1677610602,
                owned_by = "openai",
                permission = listOf(createDefaultPermission()),
                root = "gpt-3.5-turbo",
                parent = null
            ),
            OpenAIModel(
                id = "gpt-4o",
                created = 1715367049,
                owned_by = "openai",
                permission = listOf(createDefaultPermission()),
                root = "gpt-4o",
                parent = null
            ),
            OpenAIModel(
                id = "gpt-4o-mini",
                created = 1721172741,
                owned_by = "openai",
                permission = listOf(createDefaultPermission()),
                root = "gpt-4o-mini",
                parent = null
            )
        )

        PluginLogger.Service.debug("Returning ${coreModels.size} core OpenAI models for AI Assistant compatibility")

        return OpenAIModelsResponse(
            `object` = "list",
            data = coreModels
        )
    }

    /**
     * Creates an error response in OpenAI format
     */
    fun createErrorResponse(
        message: String,
        type: String = "invalid_request_error",
        code: String? = null
    ): OpenAIErrorResponse {
        return OpenAIErrorResponse(
            error = OpenAIError(
                message = message,
                type = type,
                code = code
            )
        )
    }

    /**
     * Creates an authentication error response matching OpenAI's exact format
     */
    fun createAuthErrorResponse(): OpenAIErrorResponse {
        return OpenAIErrorResponse(
            error = OpenAIError(
                message = "You didn't provide an API key. You need to provide your API key in an Authorization header using Bearer auth (i.e. Authorization: Bearer YOUR_KEY), or as the password field (with blank username) if you're accessing the API from your browser and are prompted for a username and password. You can obtain an API key from https://platform.openai.com/account/api-keys.",
                type = "invalid_request_error",
                code = "invalid_api_key"
            )
        )
    }

    /**
     * Maps OpenRouter model names back to OpenAI-compatible names
     */
    private fun mapOpenRouterModelToOpenAI(openRouterModel: String): String {
        return when {
            openRouterModel.contains("openai/gpt-4-turbo") -> "gpt-4-turbo"
            openRouterModel.contains("openai/gpt-4-vision") -> "gpt-4-vision-preview"
            openRouterModel.contains("openai/gpt-4-32k") -> "gpt-4-32k"
            openRouterModel.contains("openai/gpt-4") -> "gpt-4"
            openRouterModel.contains("openai/gpt-3.5-turbo-16k") -> "gpt-3.5-turbo-16k"
            openRouterModel.contains("openai/gpt-3.5-turbo") -> "gpt-3.5-turbo"
            openRouterModel.contains("anthropic/claude-3.5") -> "claude-3.5-sonnet"
            openRouterModel.contains("anthropic/claude-3") -> "claude-3-sonnet"
            openRouterModel.contains("anthropic/claude-2") -> "claude-2"
            openRouterModel.contains("google/gemini-pro") -> "gemini-pro"
            openRouterModel.contains("google/gemini-ultra") -> "gemini-ultra"
            else -> {
                // For unknown models, try to extract a reasonable name
                val parts = openRouterModel.split("/")
                if (parts.size >= 2) {
                    parts[1] // Return the model part without provider
                } else {
                    openRouterModel
                }
            }
        }
    }

    /**
     * Extracts the owner/provider from OpenRouter model ID
     */
    private fun extractOwner(modelId: String?): String {
        if (modelId == null) return "unknown"
        
        val parts = modelId.split("/")
        return if (parts.size >= 2) {
            when (parts[0]) {
                "openai" -> "openai"
                "anthropic" -> "anthropic"
                "google" -> "google"
                "meta" -> "meta"
                "mistral" -> "mistralai"
                else -> parts[0]
            }
        } else {
            "openrouter"
        }
    }

    /**
     * Creates default permission object for models
     */
    private fun createDefaultPermission(): OpenAIPermission {
        return OpenAIPermission(
            id = "perm-${generateRequestId()}",
            created = System.currentTimeMillis() / 1000,
            allow_create_engine = false,
            allow_sampling = true,
            allow_logprobs = true,
            allow_search_indices = false,
            allow_view = true,
            allow_fine_tuning = false,
            organization = "*",
            is_blocking = false
        )
    }

    /**
     * Generates a unique request ID in OpenAI format
     */
    private fun generateRequestId(): String {
        return "chatcmpl-${UUID.randomUUID().toString().replace("-", "").take(29)}"
    }

    /**
     * Validates that the translated response is valid OpenAI format
     */
    fun validateTranslatedResponse(response: OpenAIChatCompletionResponse): Boolean {
        return try {
            response.id.isNotBlank() &&
            response.model.isNotBlank() &&
            response.choices.isNotEmpty() &&
            response.choices.all { choice ->
                choice.message.role.isNotBlank() && choice.message.content.isNotBlank()
            }
        } catch (e: Exception) {
            PluginLogger.Service.error("Response validation failed", e)
            false
        }
    }

    /**
     * Creates a simple health check response
     */
    fun createHealthCheckResponse(): Map<String, Any> {
        return mapOf(
            "status" to "ok",
            "service" to "openrouter-proxy",
            "version" to "1.0.0",
            "timestamp" to System.currentTimeMillis()
        )
    }
}
