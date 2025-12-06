package org.zhavoronkov.openrouter.utils

import org.zhavoronkov.openrouter.models.OpenRouterModelInfo

/**
 * Utility class for extracting provider information and capabilities from OpenRouter models
 */
object ModelProviderUtils {

    private const val PROVIDER_SEPARATOR = "/"
    private const val UNKNOWN_PROVIDER = "Other"
    private const val CONTEXT_32K = 32000
    private const val CONTEXT_128K = 128000
    private const val MILLION = 1000000
    private const val THOUSAND = 1000

    /**
     * Provider name mappings for known OpenRouter providers
     */
    private val KNOWN_PROVIDERS = mapOf(
        "openai" to "OpenAI",
        "anthropic" to "Anthropic",
        "google" to "Google",
        "meta-llama" to "Meta",
        "meta" to "Meta",
        "mistralai" to "Mistral",
        "mistral" to "Mistral",
        "cohere" to "Cohere",
        "ai21" to "AI21",
        "microsoft" to "Microsoft",
        "qwen" to "Qwen",
        "deepseek" to "DeepSeek",
        "x-ai" to "xAI",
        "xai" to "xAI",
        "perplexity" to "Perplexity",
        "databricks" to "Databricks",
        "nvidia" to "NVIDIA",
        "01-ai" to "01.AI"
    )

    /**
     * Extract provider name from model ID
     * Examples:
     * - "openai/gpt-4o" -> "OpenAI"
     * - "anthropic/claude-3.5-sonnet" -> "Anthropic"
     * - "google/gemini-pro-1.5" -> "Google"
     */
    fun extractProvider(modelId: String): String {
        if (!modelId.contains(PROVIDER_SEPARATOR)) {
            return UNKNOWN_PROVIDER
        }

        val providerKey = modelId.substringBefore(PROVIDER_SEPARATOR).lowercase()
        return KNOWN_PROVIDERS[providerKey]
            ?: providerKey.replaceFirstChar { it.uppercase() }
    }

    /**
     * Get all unique providers from a list of models
     */
    fun getUniqueProviders(models: List<OpenRouterModelInfo>): List<String> {
        return models
            .map { extractProvider(it.id) }
            .distinct()
            .sorted()
    }

    /**
     * Check if model has vision capability (can process images)
     */
    fun hasVisionCapability(model: OpenRouterModelInfo): Boolean {
        val inputModalities = model.architecture?.inputModalities ?: return false
        return inputModalities.any { it.equals("image", ignoreCase = true) }
    }

    /**
     * Check if model has audio capability (can process audio)
     */
    fun hasAudioCapability(model: OpenRouterModelInfo): Boolean {
        val inputModalities = model.architecture?.inputModalities ?: return false
        return inputModalities.any { it.equals("audio", ignoreCase = true) }
    }

    /**
     * Check if model has tools/function calling capability
     */
    fun hasToolsCapability(model: OpenRouterModelInfo): Boolean {
        val supportedParams = model.supportedParameters ?: return false
        return supportedParams.any {
            it.equals("tools", ignoreCase = true) ||
                it.equals("functions", ignoreCase = true)
        }
    }

    /**
     * Check if model has image generation capability
     */
    fun hasImageGenerationCapability(model: OpenRouterModelInfo): Boolean {
        val outputModalities = model.architecture?.outputModalities ?: return false
        return outputModalities.any { it.equals("image", ignoreCase = true) }
    }

    /**
     * Get all capabilities for a model as a list of strings
     */
    fun getCapabilities(model: OpenRouterModelInfo): List<String> {
        val capabilities = mutableListOf<String>()

        if (hasVisionCapability(model)) capabilities.add("Vision")
        if (hasAudioCapability(model)) capabilities.add("Audio")
        if (hasToolsCapability(model)) capabilities.add("Tools")
        if (hasImageGenerationCapability(model)) capabilities.add("Image Gen")

        return capabilities
    }

    /**
     * Get capabilities as a formatted string
     */
    fun getCapabilitiesString(model: OpenRouterModelInfo): String {
        val capabilities = getCapabilities(model)
        return if (capabilities.isNotEmpty()) {
            capabilities.joinToString(", ")
        } else {
            "—"
        }
    }

    /**
     * Context length range for filtering
     */
    enum class ContextRange(val displayName: String) {
        ANY("Any"),
        SMALL("< 32K"),
        MEDIUM("32K - 128K"),
        LARGE("> 128K");

        companion object {
            fun fromDisplayName(name: String): ContextRange {
                return values().find { it.displayName == name } ?: ANY
            }
        }
    }

    /**
     * Criteria for filtering models
     */
    data class FilterCriteria(
        val provider: String = "All Providers",
        val contextRange: ContextRange = ContextRange.ANY,
        val requireVision: Boolean = false,
        val requireAudio: Boolean = false,
        val requireTools: Boolean = false,
        val requireImageGen: Boolean = false,
        val searchText: String = ""
    )

    /**
     * Check if model's context length matches the specified range
     */
    fun matchesContextRange(model: OpenRouterModelInfo, range: ContextRange): Boolean {
        val contextLength = model.contextLength ?: return range == ContextRange.ANY

        return when (range) {
            ContextRange.ANY -> true
            ContextRange.SMALL -> contextLength < CONTEXT_32K
            ContextRange.MEDIUM -> contextLength in CONTEXT_32K..CONTEXT_128K
            ContextRange.LARGE -> contextLength > CONTEXT_128K
        }
    }

    /**
     * Format context length for display
     */
    fun formatContextLength(contextLength: Int?): String {
        if (contextLength == null) return "—"

        return when {
            contextLength >= MILLION -> "${contextLength / MILLION}M"
            contextLength >= THOUSAND -> "${contextLength / THOUSAND}K"
            else -> contextLength.toString()
        }
    }

    /**
     * Filter models by provider
     */
    fun filterByProvider(models: List<OpenRouterModelInfo>, provider: String): List<OpenRouterModelInfo> {
        if (provider == "All Providers") return models
        return models.filter { extractProvider(it.id) == provider }
    }

    /**
     * Filter models by capabilities
     * All specified capabilities must be present (AND logic)
     */
    fun filterByCapabilities(
        models: List<OpenRouterModelInfo>,
        requireVision: Boolean = false,
        requireAudio: Boolean = false,
        requireTools: Boolean = false,
        requireImageGen: Boolean = false
    ): List<OpenRouterModelInfo> {
        return models.filter { model ->
            (!requireVision || hasVisionCapability(model)) &&
                (!requireAudio || hasAudioCapability(model)) &&
                (!requireTools || hasToolsCapability(model)) &&
                (!requireImageGen || hasImageGenerationCapability(model))
        }
    }

    /**
     * Filter models by context length range
     */
    fun filterByContextRange(
        models: List<OpenRouterModelInfo>,
        range: ContextRange
    ): List<OpenRouterModelInfo> {
        if (range == ContextRange.ANY) return models
        return models.filter { matchesContextRange(it, range) }
    }

    /**
     * Apply all filters to a list of models
     */
    fun applyFilters(
        models: List<OpenRouterModelInfo>,
        criteria: FilterCriteria
    ): List<OpenRouterModelInfo> {
        var filtered = models

        // Apply provider filter
        filtered = filterByProvider(filtered, criteria.provider)

        // Apply context range filter
        filtered = filterByContextRange(filtered, criteria.contextRange)

        // Apply capability filters
        filtered = filterByCapabilities(
            filtered,
            criteria.requireVision,
            criteria.requireAudio,
            criteria.requireTools,
            criteria.requireImageGen
        )

        // Apply text search filter
        if (criteria.searchText.isNotBlank()) {
            filtered = filtered.filter { model ->
                model.id.contains(criteria.searchText, ignoreCase = true) ||
                    model.name.contains(criteria.searchText, ignoreCase = true) ||
                    model.description?.contains(criteria.searchText, ignoreCase = true) == true
            }
        }

        return filtered
    }

    /**
     * Apply all filters to a list of models (legacy method with individual parameters)
     * @deprecated Use applyFilters(models, FilterCriteria) instead
     */
    @Deprecated("Use applyFilters(models, FilterCriteria) instead")
    fun applyFilters(
        models: List<OpenRouterModelInfo>,
        provider: String = "All Providers",
        contextRange: ContextRange = ContextRange.ANY,
        requireVision: Boolean = false,
        requireAudio: Boolean = false,
        requireTools: Boolean = false,
        requireImageGen: Boolean = false,
        searchText: String = ""
    ): List<OpenRouterModelInfo> {
        val criteria = FilterCriteria(
            provider = provider,
            contextRange = contextRange,
            requireVision = requireVision,
            requireAudio = requireAudio,
            requireTools = requireTools,
            requireImageGen = requireImageGen,
            searchText = searchText
        )
        return applyFilters(models, criteria)
    }
}
