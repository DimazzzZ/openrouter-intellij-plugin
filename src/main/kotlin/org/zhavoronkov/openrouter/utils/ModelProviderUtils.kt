@file:OptIn(ExperimentalStdlibApi::class)

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
    val KNOWN_PROVIDERS = mapOf(
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
     * - "x-ai/grok-4-fast:free" -> "xAI"  (variant suffix is ignored)
     * - "@preset/email-copywriter" -> "@preset"
     */
    fun extractProvider(modelId: String): String {
        return parseModelId(modelId).provider
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
            Capability.REASONING -> {
                val supportedParams = model.supportedParameters ?: emptyList()
                supportedParams.any { it.equals("reasoning", ignoreCase = true) }
            }
            Capability.VERBOSITY -> {
                val supportedParams = model.supportedParameters ?: emptyList()
                supportedParams.any { it.equals("verbosity", ignoreCase = true) }
            }
        }

    /**
     * Capability types for models
     */
    enum class Capability {
        VISION,
        AUDIO,
        TOOLS,
        IMAGE_GENERATION,
        REASONING,
        VERBOSITY
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
        if (hasCapability(model, Capability.REASONING)) capabilities.add("Reasoning")
        if (hasCapability(model, Capability.VERBOSITY)) capabilities.add("Verbosity")

        return capabilities
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

    /**
     * Model variant types supported by OpenRouter.
     * Each variant has a suffix (e.g., ":free"), display name, and tooltip.
     */
    enum class ModelVariant(
        val suffix: String,
        val displayName: String,
        val tooltip: String,
    ) {
        FREE(":free", "Free", "Free tier — no cost"),
        EXTENDED(":extended", "Extended", "Extended context window"),
        EXACTO(":exacto", "Exacto", "Quality-first provider sorting"),
        THINKING(":thinking", "Thinking", "Extended reasoning capability"),
        ONLINE(":online", "Online", "Real-time web search integration"),
        NITRO(":nitro", "Nitro", "High-speed inference"),
        FLOOR(":floor", "Floor", "Lowest-cost inference");

        companion object {
            /**
             * Parse a variant suffix (e.g., ":free") into a ModelVariant.
             * Returns null if the suffix doesn't match any known variant.
             */
            fun fromSuffix(raw: String): ModelVariant? {
                return entries.find { it.suffix == raw }
            }
        }
    }

    /**
     * Parsed model ID with provider, base name, and optional variant.
     * If the variant is unknown to this plugin, it's captured in unknownVariant.
     */
    data class ModelId(
        val provider: String, // e.g., "OpenAI", "Anthropic", "@preset"
        val baseName: String, // e.g., "gpt-4o", "claude-3.5-sonnet"
        val variant: ModelVariant?, // Known variant or null
        val unknownVariant: String? // Raw suffix if it's not a known variant (e.g., ":brand-new")
    ) {
        /**
         * Reconstruct the full model ID string.
         */
        fun toFullId(): String {
            val base = if (provider == "@preset") {
                "@preset/$baseName"
            } else {
                "$provider/$baseName"
            }
            return when {
                variant != null -> base + variant.suffix
                unknownVariant != null -> base + unknownVariant
                else -> base
            }
        }
    }

    /**
     * Parse a model ID into provider, base name, and variant components.
     * Handles:
     * - "provider/name" → ModelId(provider, name, null, null)
     * - "provider/name:variant" → ModelId(provider, name, variant, null)
     * - "provider/name:unknown-suffix" → ModelId(provider, name, null, ":unknown-suffix")
     * - "@preset/slug" → ModelId("@preset", slug, null, null)
     * - bare "name" → ModelId("Other", name, null, null)
     */
    fun parseModelId(id: String): ModelId {
        if (id.isBlank()) {
            return ModelId(UNKNOWN_PROVIDER, id, null, null)
        }

        // Check for preset slug
        if (id.startsWith("@preset/")) {
            val slug = id.substringAfter("@preset/")
            return ModelId("@preset", slug, null, null)
        }

        // Split on "/" to separate provider from model+variant
        val slashIndex = id.indexOf(PROVIDER_SEPARATOR)
        if (slashIndex == -1) {
            // No slash — bare model name
            return ModelId(UNKNOWN_PROVIDER, id, null, null)
        }

        val providerKey = id.substring(0, slashIndex).lowercase()
        val displayProvider = KNOWN_PROVIDERS[providerKey]
            ?: providerKey.replaceFirstChar { it.uppercase() }

        val modelPart = id.substring(slashIndex + 1)

        // Split on ":" to separate base name from variant
        val colonIndex = modelPart.indexOf(':')
        return if (colonIndex == -1) {
            // No variant suffix
            ModelId(displayProvider, modelPart, null, null)
        } else {
            val baseName = modelPart.substring(0, colonIndex)
            val variantSuffix = modelPart.substring(colonIndex) // Includes the ":"
            val variant = ModelVariant.fromSuffix(variantSuffix)
            ModelId(displayProvider, baseName, variant, if (variant == null) variantSuffix else null)
        }
    }

    /**
     * Strip the variant suffix from a model ID, returning just the base model.
     * Examples:
     * - "x-ai/grok-4-fast:free" → "x-ai/grok-4-fast"
     * - "openai/gpt-4o" → "openai/gpt-4o"
     * - "@preset/email:thinking" → "@preset/email"
     */
    fun stripVariant(id: String): String {
        val colonIndex = id.indexOf(':')
        return if (colonIndex == -1) id else id.substring(0, colonIndex)
    }

    /**
     * Check if a model ID has a specific variant.
     * Examples:
     * - hasVariant("x-ai/grok-4-fast:free", ModelVariant.FREE) → true
     * - hasVariant("x-ai/grok-4-fast", ModelVariant.FREE) → false
     * - hasVariant("x-ai/grok-4-fast:unknown", ModelVariant.FREE) → false
     */
    fun hasVariant(id: String, variant: ModelVariant): Boolean {
        return parseModelId(id).variant == variant
    }
}
