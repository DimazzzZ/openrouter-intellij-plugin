package org.zhavoronkov.openrouter.settings.favorites

import org.zhavoronkov.openrouter.utils.ModelProviderUtils
import org.zhavoronkov.openrouter.utils.ModelProviderUtils.ModelVariant

/**
 * Single-select variant filter for the Favorite Models catalog.
 *
 * [ANY] disables the dimension, [BASE_ONLY] keeps ids carrying no marker at all,
 * one entry per known [ModelVariant] keeps exactly that suffix, [LATEST] keeps
 * Latest Models (`~author/family-latest`), and [OTHER] keeps ids whose suffix the
 * plugin does not recognise (including retired ones such as `:thinking`).
 *
 * [LATEST] is not a variant - the marker is a `~` prefix, not a suffix - but what
 * this filter offers is kinds of model rather than suffixes, and a model that
 * moves to its family's newest release is one such kind. A latest slug that also
 * carries a variant matches both that variant's entry and [LATEST].
 */
enum class VariantFilter(val displayName: String, val variant: ModelVariant?) {
    ANY("Any variant", null),
    BASE_ONLY("Base only", null),
    FREE(ModelVariant.FREE.displayName, ModelVariant.FREE),
    EXACTO(ModelVariant.EXACTO.displayName, ModelVariant.EXACTO),
    NITRO(ModelVariant.NITRO.displayName, ModelVariant.NITRO),
    FLOOR(ModelVariant.FLOOR.displayName, ModelVariant.FLOOR),
    BATCH(ModelVariant.BATCH.displayName, ModelVariant.BATCH),
    LATEST("Latest", null),
    OTHER("Other", null);

    fun matches(modelId: String): Boolean {
        if (this == ANY) return true
        val parsed = ModelProviderUtils.parseModelId(modelId)
        return when (this) {
            BASE_ONLY -> parsed.variant == null && parsed.unknownVariant == null && !parsed.latest
            LATEST -> parsed.latest
            OTHER -> parsed.unknownVariant != null
            else -> parsed.variant == variant
        }
    }

    companion object {
        fun forVariant(variant: ModelVariant): VariantFilter =
            entries.first { it.variant == variant }
    }
}
