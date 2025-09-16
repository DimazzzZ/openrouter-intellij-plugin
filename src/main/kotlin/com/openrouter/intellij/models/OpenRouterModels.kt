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
 * Settings data class
 */
data class OpenRouterSettings(
    var apiKey: String = "",
    var defaultModel: String = "openai/gpt-4o",
    var autoRefresh: Boolean = true,
    var refreshInterval: Int = 300, // seconds
    var showCosts: Boolean = true
)
