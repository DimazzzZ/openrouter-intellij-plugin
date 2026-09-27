package org.zhavoronkov.openrouter.services

import org.zhavoronkov.openrouter.models.ApiResult
import org.zhavoronkov.openrouter.models.DataRegion
import org.zhavoronkov.openrouter.models.KeyInfoResponse

/**
 * Finds out which data regions the configured keys may use.
 *
 * Separate from [OpenRouterService] and from the settings UI because it is the only place that
 * knows the rule - ask both keys, intersect, never let a failed lookup subtract a region - and
 * that rule is worth testing without a network or a platform. Everything it needs arrives as a
 * function, so a test supplies canned answers.
 */
class DataRegionAvailability(
    private val fetchKeyInfo: suspend (String) -> ApiResult<KeyInfoResponse>,
    private val managementKeyProvider: () -> String,
    private val apiKeyProvider: () -> String
) {

    constructor(service: OpenRouterService, settings: OpenRouterSettingsService) : this(
        fetchKeyInfo = service::fetchKeyInfo,
        managementKeyProvider = settings::getProvisioningKey,
        apiKeyProvider = settings::getApiKey
    )

    /**
     * The regions both configured keys allow.
     *
     * A key that is not configured, or whose lookup failed, contributes nothing rather than
     * contributing "no regions": the difference matters, because reading a network error as a
     * refusal would take a region away from someone entitled to it, and they would have no way to
     * tell that from a genuine downgrade. With nothing to go on the answer is
     * [DataRegion.GLOBAL] alone, which is also the plugin's default.
     */
    suspend fun load(): List<DataRegion> = DataRegion.available(
        managementKeyRegions = regionsFor(managementKeyProvider()),
        apiKeyRegions = regionsFor(apiKeyProvider())
    )

    private suspend fun regionsFor(key: String): List<String>? {
        if (key.isBlank()) return null
        return when (val result = fetchKeyInfo(key)) {
            is ApiResult.Success -> result.data.data.allowedDataRegions
            is ApiResult.Error -> null
        }
    }
}
