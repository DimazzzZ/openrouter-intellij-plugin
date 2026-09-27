package org.zhavoronkov.openrouter.models

import org.zhavoronkov.openrouter.utils.ModelProviderUtils

/**
 * Works out which favorites a region cannot serve.
 *
 * Curation is what a consumer's model dropdown shows, and a region serves far fewer models than
 * the global endpoint - at the time of writing, 66 in the EU and 115 in the US against 458
 * globally. Pinning a region therefore takes models out of that dropdown, and the user is told
 * how many BEFORE the change is applied rather than discovering it later with a shorter list.
 *
 * Counting only: the wording lives with the control that shows it, in the settings layer.
 */
object RegionFavorites {

    /**
     * The favorites a region does not serve, in the order they were favorited.
     *
     * Matching ignores the variant suffix. A variant such as `:nitro` or `:floor` is a routing
     * instruction rather than a separate model, so `openai/gpt-4o:nitro` is served wherever
     * `openai/gpt-4o` is; comparing the full id would report it missing and overstate the loss.
     * Variants that ARE listed as their own catalogue entry (`:batch`, for instance) still match,
     * because their base id is present too.
     */
    fun unavailable(favoriteIds: List<String>, regionModelIds: Collection<String>): List<String> {
        if (favoriteIds.isEmpty() || regionModelIds.isEmpty()) return emptyList()

        val servedBaseIds = regionModelIds.mapTo(mutableSetOf()) { ModelProviderUtils.stripVariant(it) }
        return favoriteIds.filterNot { ModelProviderUtils.stripVariant(it) in servedBaseIds }
    }
}
