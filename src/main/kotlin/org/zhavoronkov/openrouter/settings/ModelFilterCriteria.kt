package org.zhavoronkov.openrouter.settings

import org.zhavoronkov.openrouter.models.OpenRouterModelInfo
import org.zhavoronkov.openrouter.settings.favorites.VariantFilter
import org.zhavoronkov.openrouter.utils.ModelProviderUtils
import org.zhavoronkov.openrouter.utils.ModelProviderUtils.Capability
import org.zhavoronkov.openrouter.utils.ModelProviderUtils.ContextRange

/**
 * Immutable filter criteria for the Favorite Models catalog.
 *
 * Every dimension is ANDed. [provider] is the display name from
 * [ModelProviderUtils.extractProvider]; `null` means all providers.
 * [capabilities] must all be present. [searchText] is matched
 * case-insensitively against id, name and description.
 */
data class ModelFilterCriteria(
    val provider: String? = null,
    val contextRange: ContextRange = ContextRange.ANY,
    val capabilities: Set<Capability> = emptySet(),
    val variant: VariantFilter = VariantFilter.ANY,
    val searchText: String = "",
) {

    fun matches(model: OpenRouterModelInfo): Boolean =
        matchesProvider(model) &&
            ModelProviderUtils.matchesContextRange(model, contextRange) &&
            capabilities.all { ModelProviderUtils.hasCapability(model, it) } &&
            variant.matches(model.id) &&
            matchesSearch(model)

    private fun matchesProvider(model: OpenRouterModelInfo): Boolean =
        provider == null || ModelProviderUtils.extractProvider(model.id) == provider

    private fun matchesSearch(model: OpenRouterModelInfo): Boolean {
        val needle = searchText.trim()
        if (needle.isEmpty()) return true
        return model.id.contains(needle, ignoreCase = true) ||
            model.name.contains(needle, ignoreCase = true) ||
            model.description?.contains(needle, ignoreCase = true) == true
    }

    /** True when any drop-down filter is set (search text excluded). */
    fun hasActiveFilters(): Boolean = activeFilterCount() > 0

    /** True when a filter or search text is set — drives the "Clear filters" action. */
    fun hasAnyInput(): Boolean = hasActiveFilters() || searchText.isNotBlank()

    /** Number of active filter dimensions (a multi-capability selection counts once). */
    fun activeFilterCount(): Int = activeParts().size

    fun describe(): String =
        activeParts().takeIf { it.isNotEmpty() }?.joinToString(" | ") ?: "No filters"

    private fun activeParts(): List<String> = buildList {
        provider?.let { add("Provider: $it") }
        if (contextRange != ContextRange.ANY) add("Context: ${contextRange.displayName}")
        if (capabilities.isNotEmpty()) {
            add("Capabilities: ${capabilities.sortedBy { it.ordinal }.joinToString(", ") { it.displayName }}")
        }
        if (variant != VariantFilter.ANY) add("Variant: ${variant.displayName}")
    }

    companion object {
        fun default(): ModelFilterCriteria = ModelFilterCriteria()
    }
}
