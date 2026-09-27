package org.zhavoronkov.openrouter.toolwindow.status

import org.zhavoronkov.openrouter.models.ApiKeyInfo
import org.zhavoronkov.openrouter.models.ApiKeysListResponse

/**
 * Decides whether the API keys' spend cap is worth showing at all, and if so what `used` figure
 * pairs with it.
 *
 * This is NOT [org.zhavoronkov.openrouter.services.OpenRouterService.getQuotaInfo], which this
 * plan replaces. `getQuotaInfo()` computed `totalLimit` as
 * `enabledKeys.mapNotNull { it.limit }.sum()` while computing `totalUsed` as
 * `enabledKeys.sumOf { it.usage }` - two different sets of keys. If one key is capped at $10 and
 * another is uncapped, the old code paired the $10 cap with BOTH keys' combined usage, so it
 * could render "used $15.00 of a $10.00 cap" - a nonsense pairing that still looks like a real
 * reading. [from] sums `used` and `limit` over exactly the same set of keys: the enabled ones
 * that actually carry a positive limit. An uncapped key contributes to neither sum.
 *
 * Pure: only the plugin's own model types and the Kotlin stdlib, so this runs in the fast
 * headless `test` task rather than needing a platform runner.
 */
object KeyLimit {

    /** @param used spend against [limit], summed over the same capped-key set as [limit]. */
    data class Reading(val used: Double, val limit: Double)

    /**
     * Returns `null` when there is no cap worth showing: no keys at all, every key disabled,
     * every key uncapped (`limit == null`), or every key's limit is `0.0` - which the API uses
     * the same way `null` is used elsewhere in this codebase, not as a real "spend nothing" cap.
     * A caller must render nothing (never a fabricated `$0.00 of $0.00`) for a `null` result.
     */
    fun from(keys: ApiKeysListResponse?): Reading? {
        val capped = keys?.data.orEmpty().filter { isCapped(it) }
        if (capped.isEmpty()) return null

        return Reading(
            used = capped.sumOf { it.usage },
            limit = capped.sumOf { checkNotNull(it.limit) { "isCapped guarantees a non-null limit" } }
        )
    }

    private fun isCapped(key: ApiKeyInfo): Boolean = !key.disabled && (key.limit ?: 0.0) > 0.0
}
