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
     * Check if model has a specific capability
     */
    fun hasCapability(model: OpenRouterModelInfo, capability: Capability): Boolean =
        when (capability) {
            Capability.VISION -> {
                val inputModalities = model.architecture?.inputModalities ?: emptyList()
                inputModalities.any { it.equals("image", ignoreCase = true) }
            }
            Capability.AUDIO -> {
                val inputModalities = model.architecture?.inputModalities ?: emptyList()
                inputModalities.any { it.equals("audio", ignoreCase = true) }
            }
            Capability.TOOLS -> {
                val supportedParams = model.supportedParameters ?: emptyList()
                supportedParams.any {
                    it.equals("tools", ignoreCase = true) ||
                        it.equals("functions", ignoreCase = true)
                }
            }
            Capability.IMAGE_GENERATION -> {
                val outputModalities = model.architecture?.outputModalities ?: emptyList()
                outputModalities.any { it.equals("image", ignoreCase = true) }
            }
        }

    /**
     * Capability types for models
     */
    enum class Capability {
        VISION,
        AUDIO,
        TOOLS,
        IMAGE_GENERATION
    }

    /**
     * Get all capabilities for a model as a list of strings
     */
    fun getCapabilities(model: OpenRouterModelInfo): List<String> {
        val capabilities = mutableListOf<String>()

        if (hasCapability(model, Capability.VISION)) capabilities.add("Vision")
        if (hasCapability(model, Capability.AUDIO)) capabilities.add("Audio")
        if (hasCapability(model, Capability.TOOLS)) capabilities.add("Tools")
        if (hasCapability(model, Capability.IMAGE_GENERATION)) capabilities.add("Image Gen")

        return capabilities
    }

    /**
     * Get capabilities as a formatted string
     */
    fun getCapabilitiesString(model: OpenRouterModelInfo): String {
        val capabilities = getCapabilities(model)
        return capabilities.joinToString(", ").ifEmpty { "—" }
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
                return entries.find { it.displayName == name } ?: ANY
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
            (!requireVision || hasCapability(model, Capability.VISION)) &&
                (!requireAudio || hasCapability(model, Capability.AUDIO)) &&
                (!requireTools || hasCapability(model, Capability.TOOLS)) &&
                (!requireImageGen || hasCapability(model, Capability.IMAGE_GENERATION))
        }
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
        if (criteria.provider != "All Providers") {
            filtered = filtered.filter { extractProvider(it.id) == criteria.provider }
        }

        // Apply context range filter
        if (criteria.contextRange != ContextRange.ANY) {
            filtered = filtered.filter { matchesContextRange(it, criteria.contextRange) }
        }

        // Apply capability filters
        filtered = filtered.filter { model ->
            (!criteria.requireVision || hasCapability(model, Capability.VISION)) &&
                (!criteria.requireAudio || hasCapability(model, Capability.AUDIO)) &&
                (!criteria.requireTools || hasCapability(model, Capability.TOOLS)) &&
                (!criteria.requireImageGen || hasCapability(model, Capability.IMAGE_GENERATION))
        }

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
}
