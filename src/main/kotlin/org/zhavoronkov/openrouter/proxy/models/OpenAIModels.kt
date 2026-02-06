package org.zhavoronkov.openrouter.proxy.models

import com.google.gson.JsonElement
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

/**
 * OpenAI chat message that supports both text-only and multimodal content
 * Content can be:
 * - A simple string for text-only messages
 * - An array of content parts for multimodal messages (text + images/files)
 */
data class OpenAIChatMessage(
    val role: String, // "system", "user", "assistant"
    val content: JsonElement, // Can be String or Array of ContentPart
    val name: String? = null
)

/**
 * Content part for multimodal messages
 * Used when content is an array
 * Supports all OpenRouter multimodal content types:
 * - text: Plain text content
 * - image_url: Images (URL or base64 data URI)
 * - input_audio: Audio files (base64 encoded)
 * - video_url: Videos (URL or base64 data URI)
 * - file: Files like PDFs (URL or base64 data URI)
 */
data class OpenAIContentPart(
    val type: String, // "text", "image_url", "input_audio", "video_url", "file"
    val text: String? = null, // For type="text"
    @SerializedName("image_url") val imageUrl: OpenAIImageUrl? = null, // For type="image_url"
    @SerializedName("input_audio") val inputAudio: OpenAIInputAudio? = null, // For type="input_audio"
    @SerializedName("video_url") val videoUrl: OpenAIVideoUrl? = null, // For type="video_url"
    val file: OpenAIFile? = null // For type="file" (PDFs, documents)
)

/**
 * Image URL content for vision models
 * Supports both direct URLs and base64 data URIs
 */
data class OpenAIImageUrl(
    val url: String, // URL or base64 data URI (e.g., "data:image/jpeg;base64,...")
    val detail: String? = null // "auto", "low", "high"
)

/**
 * Audio input content for audio models
 * Audio must be base64 encoded
 */
data class OpenAIInputAudio(
    val data: String, // Base64 encoded audio data
    val format: String // "wav", "mp3", etc.
)

/**
 * Video URL content for video-capable models
 * Supports both direct URLs (including YouTube) and base64 data URIs
 */
data class OpenAIVideoUrl(
    val url: String // URL or base64 data URI (e.g., "data:video/mp4;base64,...")
)

/**
 * File content for document processing (PDFs, etc.)
 * Supports both direct URLs and base64 data URIs
 */
data class OpenAIFile(
    val filename: String, // Original filename (e.g., "document.pdf")
    @SerializedName("file_data") val fileData: String // URL or base64 data URI
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
    val parent: String? = null,
    val type: String = "llm"
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
