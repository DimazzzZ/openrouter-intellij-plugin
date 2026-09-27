package org.zhavoronkov.openrouter.models

import org.zhavoronkov.openrouter.utils.ModelProviderUtils

/**
 * Works out which favourites a region cannot serve.
 *
 * Curation is what a consumer's model dropdown shows, and a region serves far fewer models than
 * the global endpoint - at the time of writing, 66 in the EU and 115 in the US against 458
 * globally. Pinning a region therefore takes models out of that dropdown, and the user is told
 * how many BEFORE the change is applied rather than discovering it later with a shorter list.
 */
object RegionFavorites {

    /**
     * The favourites a region does not serve, in the order they were favourited.
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

    /**
     * A sentence for the settings page, or null when there is nothing worth saying.
     *
     * Null covers three cases that all mean "do not alarm anyone": the global region, which
     * serves everything; no favourites to lose; and a region that serves all of them.
     */
    fun impactSummary(
        region: DataRegion,
        favoriteIds: List<String>,
        regionModelIds: Collection<String>
    ): String? {
        if (region == DataRegion.GLOBAL) return null

        val missing = unavailable(favoriteIds, regionModelIds)
        if (missing.isEmpty()) return null

        return "${missing.size} of your ${favoriteIds.size} favourite models are not available " +
            "in ${region.displayName} and will not be offered while it is selected."
    }
}
