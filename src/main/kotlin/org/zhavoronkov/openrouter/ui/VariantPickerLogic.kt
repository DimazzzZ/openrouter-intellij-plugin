package org.zhavoronkov.openrouter.ui

import org.zhavoronkov.openrouter.models.OpenRouterModelInfo
import org.zhavoronkov.openrouter.utils.ModelProviderUtils

/**
 * Pure (UI-free) logic backing [VariantAwareFavoritesPickerDialog], extracted so
 * the grouping and selection rules can be unit-tested without a Swing environment.
 */
object VariantPickerLogic {

    /** Sentinel used to represent "base model, no variant" in the variant list. */
    const val BASE_SENTINEL = ""

    /**
     * Distinct base model IDs (variant suffix stripped), sorted alphabetically.
     */
    fun baseModelIds(availableModels: List<OpenRouterModelInfo>): List<String> {
        return availableModels
            .map { ModelProviderUtils.stripVariant(it.id) }
            .distinct()
            .sorted()
    }

    /**
     * Variant suffixes available for a given base model, always including the
     * [BASE_SENTINEL] (no-variant) option first, then known/unknown suffixes sorted.
     */
    fun variantsForBase(availableModels: List<OpenRouterModelInfo>, baseId: String): List<String> {
        val variants = mutableListOf(BASE_SENTINEL)
        availableModels
            .filter { ModelProviderUtils.stripVariant(it.id) == baseId }
            .forEach { model ->
                val parsed = ModelProviderUtils.parseModelId(model.id)
                when {
                    parsed.variant != null -> variants.add(parsed.variant.suffix)
                    parsed.unknownVariant != null -> variants.add(parsed.unknownVariant)
                }
            }
        return variants.distinct().sortedWith(compareBy({ it != BASE_SENTINEL }, { it }))
    }

    /**
     * Resolve a (base, variantSuffix) pair to a full model ID.
     * BASE_SENTINEL yields the base ID unchanged.
     */
    fun toModelId(baseId: String, variantSuffix: String): String {
        return if (variantSuffix == BASE_SENTINEL) baseId else "$baseId$variantSuffix"
    }
}
