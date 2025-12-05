package org.zhavoronkov.openrouter.proxy.models

/**
 * OpenAI API compatible models for the proxy server
 * These models ensure compatibility with JetBrains AI Assistant
 */

// Chat Completion Request Models
data class OpenAIChatCompletionRequest(
    val model: String,
    val messages: List<OpenAIChatMessage>,
    val temperature: Double? = null,
    val max_tokens: Int? = null,
    val top_p: Double? = null,
    val frequency_penalty: Double? = null,
    val presence_penalty: Double? = null,
    val stop: List<String>? = null,
    val stream: Boolean? = false,
    val user: String? = null
)

data class OpenAIChatMessage(
    val role: String, // "system", "user", "assistant"
    val content: String,
    val name: String? = null
)

// Chat Completion Response Models
data class OpenAIChatCompletionResponse(
    val id: String,
    val `object`: String = "chat.completion",
    val created: Long,
    val model: String,
    val choices: List<OpenAIChatChoice>,
    val usage: OpenAIUsage? = null
)

data class OpenAIChatChoice(
    val index: Int,
    val message: OpenAIChatMessage,
    val finish_reason: String? = null
)

data class OpenAIUsage(
    val prompt_tokens: Int,
    val completion_tokens: Int,
    val total_tokens: Int
)

// Models List Response
data class OpenAIModelsResponse(
    val `object`: String = "list",
    val data: List<OpenAIModel>
)

data class OpenAIModel(
    val id: String,
    val `object`: String = "model",
    val created: Long,
    val owned_by: String,
    val permission: List<OpenAIPermission> = emptyList(),
    val root: String? = null,
    val parent: String? = null
)

data class OpenAIPermission(
    val id: String,
    val `object`: String = "model_permission",
    val created: Long,
    val allow_create_engine: Boolean = false,
    val allow_sampling: Boolean = true,
    val allow_logprobs: Boolean = true,
    val allow_search_indices: Boolean = false,
    val allow_view: Boolean = true,
    val allow_fine_tuning: Boolean = false,
    val organization: String = "*",
    val group: String? = null,
    val is_blocking: Boolean = false
)

// Error Response Models
data class OpenAIErrorResponse(
    val error: OpenAIError
)

data class OpenAIError(
    val message: String,
    val type: String,
    val param: String? = null,
    val code: String? = null
)

// Streaming Response Models (for future use)
data class OpenAIStreamResponse(
    val id: String,
    val `object`: String = "chat.completion.chunk",
    val created: Long,
    val model: String,
    val choices: List<OpenAIStreamChoice>
)

data class OpenAIStreamChoice(
    val index: Int,
    val delta: OpenAIChatMessage,
    val finish_reason: String? = null
)
