package org.zhavoronkov.openrouter.models

import org.zhavoronkov.openrouter.constants.OpenRouterConstants

/**
 * A data region an OpenRouter request can be pinned to.
 *
 * The same region is spelled three different ways by OpenRouter, which is the whole reason this
 * type exists rather than a bare string:
 *
 * | Where | EU | US | no region |
 * |---|---|---|---|
 * | `allowed_data_regions` on `/api/v1/key` | `europe` | `us` | `global` |
 * | `region=` on `/api/v1/models` | `eu` | `us` | omit the parameter |
 * | host name | `eu.openrouter.ai` | `us.openrouter.ai` | `openrouter.ai` |
 *
 * [GLOBAL] is a real member rather than a null: `allowed_data_regions` lists it explicitly, so
 * "not pinned to a region" is a value the API itself names.
 */
enum class DataRegion(
    /** The spelling used by `allowed_data_regions`. */
    val apiName: String,
    /** The spelling used by the `region=` query parameter, or null when the parameter is omitted. */
    val queryValue: String?,
    /** Base URL for this region, including the `/api/v1` suffix. */
    val baseUrl: String,
    /** What to show a person. */
    val displayName: String
) {
    GLOBAL("global", null, OpenRouterConstants.BASE_URL, "Global (no region pinning)"),
    EUROPE("europe", "eu", "https://eu.openrouter.ai/api/v1", "European Union"),
    US("us", "us", "https://us.openrouter.ai/api/v1", "United States");

    companion object {
        /**
         * Resolves a value from `allowed_data_regions`, answering null for anything unrecognised.
         *
         * Unrecognised is expected, not exceptional: OpenRouter adds regions without asking, and a
         * build that has never heard of one should ignore it rather than fail. The cost is that a
         * new region stays invisible until this enum learns about it.
         */
        fun fromApiName(apiName: String): DataRegion? =
            entries.firstOrNull { it.apiName.equals(apiName, ignoreCase = true) }
    }
}

/**
 * Works out which regions a user may actually select.
 *
 * `allowed_data_regions` already folds in both the guardrail policy on the key and the account's
 * regional-routing entitlement, so asking the key is enough - there is no separate entitlement
 * check to make and no way to pick a region the server will then refuse. In-region routing is a
 * paid-plan feature, so for most accounts this answers [DataRegion.GLOBAL] alone.
 */
object DataRegions {

    /**
     * The regions available given what each key reports.
     *
     * Both keys are consulted because the plugin uses both: the Management Key reads the account
     * (credits, analytics, key management) and the API key carries inference. Pinning a region
     * points BOTH at the regional host, so a region only works if both keys allow it - hence the
     * intersection. A key that reports nothing is treated as unknown and left out of the
     * reckoning rather than read as "allows nothing", so one failed lookup cannot silently strip
     * a region the user is entitled to.
     *
     * [DataRegion.GLOBAL] is always present. It is the plugin's own default and the state a user
     * must always be able to return to; an account that somehow does not list it would otherwise
     * be stuck in a region it cannot leave.
     */
    fun available(managementKeyRegions: List<String>?, apiKeyRegions: List<String>?): List<DataRegion> {
        val reported = listOfNotNull(managementKeyRegions, apiKeyRegions)
            .filter { it.isNotEmpty() }
            .map { regions -> regions.mapNotNull(DataRegion::fromApiName).toSet() }

        val allowed = when {
            reported.isEmpty() -> setOf(DataRegion.GLOBAL)
            else -> reported.reduce { acc, next -> acc intersect next } + DataRegion.GLOBAL
        }

        return DataRegion.entries.filter { it in allowed }
    }

    /**
     * Whether a stored selection still holds.
     *
     * A selection can stop being valid without the user touching anything - a plan downgrade, a
     * new guardrail policy, a replaced key - so it is re-checked rather than trusted.
     */
    fun isStillAvailable(selected: DataRegion, available: List<DataRegion>): Boolean = selected in available
}
