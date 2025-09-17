package com.openrouter.intellij.models

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
    @SerializedName("updated_at") val updatedAt: String,
    val hash: String
)

data class ModelInfo(
    val id: String,
    val name: String,
    val description: String? = null,
    val pricing: ModelPricing? = null
)

data class ModelPricing(
    val prompt: Double? = null,
    val completion: Double? = null
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
 * Settings data class
 */
data class OpenRouterSettings(
    var apiKey: String = "",
    var provisioningKey: String = "",
    var defaultModel: String = "openai/gpt-4o",
    var autoRefresh: Boolean = true,
    var refreshInterval: Int = 300, // seconds
    var showCosts: Boolean = true,
    var trackGenerations: Boolean = true,
    var maxTrackedGenerations: Int = 100
)
