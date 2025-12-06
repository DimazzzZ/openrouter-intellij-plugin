package org.zhavoronkov.openrouter.proxy.models

import com.google.gson.annotations.SerializedName

/**
 * OpenAI API compatible models for the proxy server
 * These models ensure compatibility with JetBrains AI Assistant
 */

// Chat Completion Request Models
data class OpenAIChatCompletionRequest(
    val model: String,
    val messages: List<OpenAIChatMessage>,
    val temperature: Double? = null,
    @SerializedName("max_tokens") val maxTokens: Int? = null,
    @SerializedName("top_p") val topP: Double? = null,
    @SerializedName("frequency_penalty") val frequencyPenalty: Double? = null,
    @SerializedName("presence_penalty") val presencePenalty: Double? = null,
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
    @SerializedName("finish_reason") val finishReason: String? = null
)

data class OpenAIUsage(
    @SerializedName("prompt_tokens") val promptTokens: Int,
    @SerializedName("completion_tokens") val completionTokens: Int,
    @SerializedName("total_tokens") val totalTokens: Int
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
    @SerializedName("owned_by") val ownedBy: String,
    val permission: List<OpenAIPermission> = emptyList(),
    val root: String? = null,
    val parent: String? = null
)

data class OpenAIPermission(
    val id: String,
    val `object`: String = "model_permission",
    val created: Long,
    @SerializedName("allow_create_engine") val allowCreateEngine: Boolean = false,
    @SerializedName("allow_sampling") val allowSampling: Boolean = true,
    @SerializedName("allow_logprobs") val allowLogprobs: Boolean = true,
    @SerializedName("allow_search_indices") val allowSearchIndices: Boolean = false,
    @SerializedName("allow_view") val allowView: Boolean = true,
    @SerializedName("allow_fine_tuning") val allowFineTuning: Boolean = false,
    val organization: String = "*",
    val group: String? = null,
    @SerializedName("is_blocking") val isBlocking: Boolean = false
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
    @SerializedName("finish_reason") val finishReason: String? = null
)
