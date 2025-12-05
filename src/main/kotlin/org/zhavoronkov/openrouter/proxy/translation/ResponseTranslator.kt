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
     * SIMPLIFIED: No model name translation - return exactly what was requested
     */
    fun translateChatCompletionResponse(
        openRouterResponse: ChatCompletionResponse,
        requestedModel: String,
        requestId: String = generateRequestId()
    ): OpenAIChatCompletionResponse {
        PluginLogger.Service.debug("Translating OpenRouter response to OpenAI format")
        PluginLogger.Service.debug("Requested model: $requestedModel, OpenRouter model: ${openRouterResponse.model}")

        return OpenAIChatCompletionResponse(
            id = requestId,
            created = System.currentTimeMillis() / 1000,
            model = requestedModel, // SIMPLIFIED: Return exactly what was requested
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
        @Suppress("UNUSED_PARAMETER") providersResponse: ProvidersResponse
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

    // REMOVED: Model mapping logic eliminated for simplicity
    // Models are now returned exactly as requested

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
