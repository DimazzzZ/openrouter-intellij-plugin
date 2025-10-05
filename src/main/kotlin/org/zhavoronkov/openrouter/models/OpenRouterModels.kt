package org.zhavoronkov.openrouter.models

import com.google.gson.annotations.SerializedName

/**
 * Data models for OpenRouter API responses
 */

data class UsageStats(
    @SerializedName("prompt_tokens") val promptTokens: Int = 0,
    @SerializedName("completion_tokens") val completionTokens: Int = 0,
    @SerializedName("total_tokens") val totalTokens: Int = 0
)

data class GenerationResponse(
    val id: String,
    val model: String,
    val usage: UsageStats?,
    val created: Long,
    @SerializedName("total_cost") val totalCost: Double? = null
)

data class ApiError(
    val code: Int,
    val message: String,
    val metadata: Map<String, Any>? = null
)

data class OpenRouterResponse<T>(
    val data: T? = null,
    val error: ApiError? = null
)

data class QuotaInfo(
    val remaining: Double? = null,
    val total: Double? = null,
    val used: Double? = null,
    val resetDate: String? = null
)

/**
 * Response from /api/v1/key endpoint
 */
data class KeyInfoResponse(
    val data: KeyData
)

data class KeyData(
    val label: String,
    val usage: Double, // Number of credits used
    val limit: Double?, // Credit limit for the key, or null if unlimited
    @SerializedName("is_free_tier") val isFreeTier: Boolean // Whether the user has paid for credits before
)

// New models for the correct API endpoint
data class ApiKeysListResponse(
    val data: List<ApiKeyInfo>
)

data class ApiKeyInfo(
    val name: String,
    val label: String,
    val limit: Double?,
    val usage: Double,
    val disabled: Boolean,
    @SerializedName("created_at") val createdAt: String,
    @SerializedName("updated_at") val updatedAt: String?,
    val hash: String
)

// API Key creation request and response
data class CreateApiKeyRequest(
    val name: String,
    val limit: Double? = null
)

data class CreateApiKeyResponse(
    val data: CreatedApiKeyInfo,
    val key: String // The actual API key value (only returned on creation) - at root level
)

data class CreatedApiKeyInfo(
    val name: String,
    val label: String,
    val limit: Double?,
    val usage: Double,
    val disabled: Boolean,
    @SerializedName("created_at") val createdAt: String,
    @SerializedName("updated_at") val updatedAt: String?,
    val hash: String
)

// API Key deletion response
data class DeleteApiKeyResponse(
    val deleted: Boolean
)

// Credits endpoint response
data class CreditsResponse(
    val data: CreditsData
)

data class CreditsData(
    @SerializedName("total_credits") val totalCredits: Double,
    @SerializedName("total_usage") val totalUsage: Double
)

// Providers endpoint response
data class ProvidersResponse(
    val data: List<ProviderInfo>
)

data class ProviderInfo(
    val name: String,
    val slug: String,
    @SerializedName("privacy_policy_url") val privacyPolicyUrl: String?,
    @SerializedName("terms_of_service_url") val termsOfServiceUrl: String?,
    @SerializedName("status_page_url") val statusPageUrl: String?
)

// OpenRouter Models API Response
data class OpenRouterModelsResponse(
    val data: List<OpenRouterModelInfo>
)

data class OpenRouterModelInfo(
    val id: String,
    val name: String,
    val created: Long,
    val description: String? = null,
    val architecture: ModelArchitecture? = null,
    @SerializedName("top_provider") val topProvider: TopProvider? = null,
    val pricing: ModelPricing? = null,
    @SerializedName("context_length") val contextLength: Int? = null,
    @SerializedName("per_request_limits") val perRequestLimits: Map<String, Any>? = null,
    @SerializedName("supported_parameters") val supportedParameters: List<String>? = null
)

data class ModelArchitecture(
    @SerializedName("input_modalities") val inputModalities: List<String>? = null,
    @SerializedName("output_modalities") val outputModalities: List<String>? = null,
    val tokenizer: String? = null,
    @SerializedName("instruct_type") val instructType: String? = null
)

data class TopProvider(
    @SerializedName("is_moderated") val isModerated: Boolean? = null,
    @SerializedName("context_length") val contextLength: Int? = null,
    @SerializedName("max_completion_tokens") val maxCompletionTokens: Int? = null
)

data class ModelPricing(
    val prompt: String? = null,
    val completion: String? = null,
    val image: String? = null,
    val request: String? = null
)

// Legacy model info for backward compatibility
data class ModelInfo(
    val id: String,
    val name: String,
    val description: String? = null,
    val pricing: ModelPricing? = null
)

/**
 * Generation tracking data
 */
data class GenerationTrackingInfo(
    val generationId: String,
    val model: String,
    val timestamp: Long,
    val promptTokens: Int? = null,
    val completionTokens: Int? = null,
    val totalTokens: Int? = null,
    val totalCost: Double? = null
)

/**
 * Activity analytics data models for /api/v1/activity endpoint
 */
data class ActivityResponse(
    val data: List<ActivityData>
)

data class ActivityData(
    val date: String,
    val model: String,
    @SerializedName("model_permaslug") val modelPermaslug: String,
    @SerializedName("endpoint_id") val endpointId: String,
    @SerializedName("provider_name") val providerName: String,
    val usage: Double,
    @SerializedName("byok_usage_inference") val byokUsageInference: Double,
    val requests: Int,
    @SerializedName("prompt_tokens") val promptTokens: Int,
    @SerializedName("completion_tokens") val completionTokens: Int,
    @SerializedName("reasoning_tokens") val reasoningTokens: Int
)

/**
 * Chat completion request and response models
 */
data class ChatCompletionRequest(
    val model: String,
    val messages: List<ChatMessage>,
    val temperature: Double? = null,
    @SerializedName("max_tokens") val maxTokens: Int? = null,
    @SerializedName("top_p") val topP: Double? = null,
    @SerializedName("frequency_penalty") val frequencyPenalty: Double? = null,
    @SerializedName("presence_penalty") val presencePenalty: Double? = null,
    val stop: List<String>? = null,
    val stream: Boolean? = false
)

data class ChatMessage(
    val role: String, // "system", "user", "assistant"
    val content: String,
    val name: String? = null
)

data class ChatCompletionResponse(
    val id: String? = null,
    val `object`: String? = null,
    val created: Long? = null,
    val model: String? = null,
    val choices: List<ChatChoice>? = null,
    val usage: ChatUsage? = null
)

data class ChatChoice(
    val index: Int? = null,
    val message: ChatMessage? = null,
    @SerializedName("finish_reason") val finishReason: String? = null
)

data class ChatUsage(
    @SerializedName("prompt_tokens") val promptTokens: Int? = null,
    @SerializedName("completion_tokens") val completionTokens: Int? = null,
    @SerializedName("total_tokens") val totalTokens: Int? = null
)

/**
 * Settings data class
 */
data class OpenRouterSettings(
    var apiKey: String = "",
    var provisioningKey: String = "",
    var autoRefresh: Boolean = true,
    var refreshInterval: Int = 300, // seconds
    var showCosts: Boolean = true,
    var trackGenerations: Boolean = true,
    var maxTrackedGenerations: Int = 100,
    var favoriteModels: MutableList<String> = getDefaultFavoriteModels(),
    var lastSeenVersion: String = "", // Track last seen version for "What's New" notifications
    var hasSeenWelcome: Boolean = false, // Track if user has seen welcome notification
    var hasCompletedSetup: Boolean = false // Track if user has completed initial setup
)

/**
 * Default favorite models for new installations
 */
private fun getDefaultFavoriteModels(): MutableList<String> {
    return mutableListOf(
        "openai/gpt-5",
        "openai/gpt-4o",
        "openai/gpt-4o-mini",
        "openai/gpt-4-turbo",
        "openai/gpt-4",
        "anthropic/claude-sonnet-4.5",
        "anthropic/claude-sonnet-4",
        "anthropic/claude-3.5-sonnet",
        "anthropic/claude-3-opus",
        "anthropic/claude-3-haiku",
        "google/gemini-2.5-pro",
        "google/gemini-2.5-flash",
        "meta-llama/llama-3.1-70b-instruct",
        "mistralai/mixtral-8x7b-instruct",
        "cohere/command-r-plus"
    )
}
