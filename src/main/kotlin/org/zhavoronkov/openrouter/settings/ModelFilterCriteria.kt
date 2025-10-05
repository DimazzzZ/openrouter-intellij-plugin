package org.zhavoronkov.openrouter.settings

import org.zhavoronkov.openrouter.utils.ModelProviderUtils

/**
 * Data class representing filter criteria for model selection
 */
data class ModelFilterCriteria(
    val provider: String = "All Providers",
    val contextRange: ModelProviderUtils.ContextRange = ModelProviderUtils.ContextRange.ANY,
    val requireVision: Boolean = false,
    val requireAudio: Boolean = false,
    val requireTools: Boolean = false,
    val requireImageGen: Boolean = false,
    val searchText: String = ""
) {
    /**
     * Check if any filters are active (excluding search text)
     */
    fun hasActiveFilters(): Boolean {
        return provider != "All Providers" ||
               contextRange != ModelProviderUtils.ContextRange.ANY ||
               requireVision ||
               requireAudio ||
               requireTools ||
               requireImageGen
    }

    /**
     * Get a human-readable description of active filters
     */
    fun getActiveFiltersDescription(): String {
        val filters = mutableListOf<String>()

        if (provider != "All Providers") {
            filters.add("Provider: $provider")
        }

        if (contextRange != ModelProviderUtils.ContextRange.ANY) {
            filters.add("Context: ${contextRange.displayName}")
        }

        val capabilities = mutableListOf<String>()
        if (requireVision) capabilities.add("Vision")
        if (requireAudio) capabilities.add("Audio")
        if (requireTools) capabilities.add("Tools")
        if (requireImageGen) capabilities.add("Image Gen")

        if (capabilities.isNotEmpty()) {
            filters.add("Capabilities: ${capabilities.joinToString(", ")}")
        }

        return if (filters.isNotEmpty()) {
            filters.joinToString(" | ")
        } else {
            "No filters"
        }
    }

    /**
     * Get count of active filters
     */
    fun getActiveFilterCount(): Int {
        var count = 0
        if (provider != "All Providers") count++
        if (contextRange != ModelProviderUtils.ContextRange.ANY) count++
        if (requireVision) count++
        if (requireAudio) count++
        if (requireTools) count++
        if (requireImageGen) count++
        return count
    }

    companion object {
        /**
         * Default filter criteria (no filters applied)
         */
        fun default(): ModelFilterCriteria {
            return ModelFilterCriteria()
        }

        /**
         * Create filter criteria for a specific provider
         */
        fun forProvider(provider: String): ModelFilterCriteria {
            return ModelFilterCriteria(provider = provider)
        }

        /**
         * Create filter criteria for multimodal models
         */
        fun forMultimodal(): ModelFilterCriteria {
            return ModelFilterCriteria(requireVision = true)
        }

        /**
         * Create filter criteria for coding models
         */
        fun forCoding(): ModelFilterCriteria {
            return ModelFilterCriteria(requireTools = true)
        }
    }
}

