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
        return when (providerKey) {
            "openai" -> "OpenAI"
            "anthropic" -> "Anthropic"
            "google" -> "Google"
            "meta-llama", "meta" -> "Meta"
            "mistralai", "mistral" -> "Mistral"
            "cohere" -> "Cohere"
            "ai21" -> "AI21"
            "microsoft" -> "Microsoft"
            "qwen" -> "Qwen"
            "deepseek" -> "DeepSeek"
            "x-ai", "xai" -> "xAI"
            "perplexity" -> "Perplexity"
            "databricks" -> "Databricks"
            "nvidia" -> "NVIDIA"
            "01-ai" -> "01.AI"
            else -> providerKey.replaceFirstChar { it.uppercase() }
        }
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
            contextLength >= 1000000 -> "${contextLength / 1000000}M"
            contextLength >= 1000 -> "${contextLength / 1000}K"
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
        provider: String = "All Providers",
        contextRange: ContextRange = ContextRange.ANY,
        requireVision: Boolean = false,
        requireAudio: Boolean = false,
        requireTools: Boolean = false,
        requireImageGen: Boolean = false,
        searchText: String = ""
    ): List<OpenRouterModelInfo> {
        var filtered = models

        // Apply provider filter
        filtered = filterByProvider(filtered, provider)

        // Apply context range filter
        filtered = filterByContextRange(filtered, contextRange)

        // Apply capability filters
        filtered = filterByCapabilities(
            filtered,
            requireVision,
            requireAudio,
            requireTools,
            requireImageGen
        )

        // Apply text search filter
        if (searchText.isNotBlank()) {
            filtered = filtered.filter { model ->
                model.id.contains(searchText, ignoreCase = true) ||
                model.name.contains(searchText, ignoreCase = true) ||
                model.description?.contains(searchText, ignoreCase = true) == true
            }
        }

        return filtered
    }
}

