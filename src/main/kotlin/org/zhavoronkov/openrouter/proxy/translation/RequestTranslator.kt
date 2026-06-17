package org.zhavoronkov.openrouter.proxy.translation

import com.intellij.openapi.application.ApplicationManager
import org.zhavoronkov.openrouter.models.ChatCompletionRequest
import org.zhavoronkov.openrouter.models.ChatMessage
import org.zhavoronkov.openrouter.models.ChatTool
import org.zhavoronkov.openrouter.models.ChatToolCall
import org.zhavoronkov.openrouter.models.ChatToolCallFunction
import org.zhavoronkov.openrouter.models.ChatToolFunction
import org.zhavoronkov.openrouter.models.ReasoningConfig
import org.zhavoronkov.openrouter.models.ToolChoice
import org.zhavoronkov.openrouter.models.ToolChoiceFunction
import org.zhavoronkov.openrouter.proxy.models.OpenAIChatCompletionRequest
import org.zhavoronkov.openrouter.proxy.models.OpenAIChatMessage
import org.zhavoronkov.openrouter.proxy.models.OpenAIChatTool
import org.zhavoronkov.openrouter.proxy.models.OpenAIChatToolCall
import org.zhavoronkov.openrouter.proxy.models.OpenAIReasoningConfig
import org.zhavoronkov.openrouter.proxy.models.OpenAIToolChoice
import org.zhavoronkov.openrouter.services.OpenRouterSettingsService
import org.zhavoronkov.openrouter.utils.PluginLogger

/**
 * Translates OpenAI API requests to OpenRouter API format
 */
object RequestTranslator {

    private val settingsService by lazy {
        val application = ApplicationManager.getApplication()
        if (application == null) {
            PluginLogger.Service.debug("Application not available (test environment)")
            return@lazy null
        }

        try {
            OpenRouterSettingsService.getInstance()
        } catch (e: IllegalStateException) {
            // In test environment or when service not initialized yet
            PluginLogger.Service.debug("Settings service not initialized", e)
            null
        } catch (e: IllegalArgumentException) {
            // Service creation failed due to invalid state
            PluginLogger.Service.debug("Settings service not available", e)
            null
        }
    }

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
        val defaultMaxTokens = if ((settingsService?.uiPreferencesManager?.defaultMaxTokens ?: 0) > 0) {
            settingsService?.uiPreferencesManager?.defaultMaxTokens
        } else {
            null
        }

        return ChatCompletionRequest(
            model = openAIRequest.model, // Model name should already be normalized (e.g., "openai/gpt-4")
            messages = openAIRequest.messages.map { translateMessage(it) },
            temperature = openAIRequest.temperature ?: DEFAULT_TEMPERATURE,
            maxTokens = openAIRequest.maxTokens ?: defaultMaxTokens,
            topP = openAIRequest.topP,
            frequencyPenalty = openAIRequest.frequencyPenalty,
            presencePenalty = openAIRequest.presencePenalty,
            stop = openAIRequest.stop,
            stream = openAIRequest.stream ?: false, // Pass through streaming flag as-is
            reasoning = openAIRequest.reasoning?.let { translateReasoning(it) },
            verbosity = openAIRequest.verbosity,
            tools = openAIRequest.tools?.map { translateTool(it) },
            toolChoice = openAIRequest.toolChoice?.let { translateToolChoice(it) }
        )
    }

    /**
     * Converts OpenAI message to OpenRouter format
     */
    private fun translateMessage(openAIMessage: OpenAIChatMessage): ChatMessage {
        return ChatMessage(
            role = openAIMessage.role,
            content = openAIMessage.content,
            name = openAIMessage.name,
            toolCallId = openAIMessage.toolCallId,
            toolCalls = openAIMessage.toolCalls?.map { translateToolCall(it) }
        )
    }

    private fun translateTool(openAITool: OpenAIChatTool): ChatTool {
        return ChatTool(
            type = openAITool.type,
            function = ChatToolFunction(
                name = openAITool.function.name,
                description = openAITool.function.description,
                parameters = openAITool.function.parameters
            )
        )
    }

    private fun translateToolChoice(openAIToolChoice: OpenAIToolChoice): ToolChoice {
        return ToolChoice(
            type = openAIToolChoice.type,
            function = openAIToolChoice.function?.let {
                ToolChoiceFunction(name = it.name)
            }
        )
    }

    private fun translateToolCall(openAIToolCall: OpenAIChatToolCall): ChatToolCall {
        return ChatToolCall(
            id = openAIToolCall.id,
            type = openAIToolCall.type,
            function = ChatToolCallFunction(
                name = openAIToolCall.function.name,
                arguments = openAIToolCall.function.arguments
            )
        )
    }

    /**
     * Converts OpenAI reasoning config to OpenRouter format
     */
    private fun translateReasoning(openAIReasoning: OpenAIReasoningConfig): ReasoningConfig {
        return ReasoningConfig(
            effort = openAIReasoning.effort,
            maxTokens = openAIReasoning.maxTokens,
            exclude = openAIReasoning.exclude,
            enabled = openAIReasoning.enabled
        )
    }

    // REMOVED: Model mapping logic eliminated for simplicity
    // Models are now passed through exactly as requested

    /**
     * Validates that the translated request is valid for OpenRouter
     */
    fun validateTranslatedRequest(request: ChatCompletionRequest): Boolean {
        return try {
            request.model.isNotBlank() &&
                request.messages.isNotEmpty() &&
                request.messages.all { message ->
                    message.role.isNotBlank() && isValidContent(message.content)
                } &&
                (request.temperature == null || request.temperature in 0.0..2.0) &&
                (request.maxTokens == null || request.maxTokens > 0) &&
                (request.topP == null || request.topP in 0.0..1.0)
        } catch (e: IllegalArgumentException) {
            PluginLogger.Service.error("Request validation failed: invalid argument", e)
            false
        }
    }

    /**
     * Validates that content is either a non-blank string or a non-empty array
     */
    private fun isValidContent(content: com.google.gson.JsonElement): Boolean {
        return when {
            content.isJsonPrimitive && content.asJsonPrimitive.isString ->
                content.asString.isNotBlank()
            content.isJsonArray ->
                content.asJsonArray.size() > 0
            else -> false
        }
    }

    /**
     * Gets available model mappings for documentation/debugging
     */
    @Suppress("unused") // Public API for documentation/debugging
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
