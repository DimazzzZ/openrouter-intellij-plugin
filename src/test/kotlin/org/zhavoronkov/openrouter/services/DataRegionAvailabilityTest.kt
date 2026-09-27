package org.zhavoronkov.openrouter.services

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.zhavoronkov.openrouter.models.ApiResult
import org.zhavoronkov.openrouter.models.DataRegion
import org.zhavoronkov.openrouter.models.KeyData
import org.zhavoronkov.openrouter.models.KeyInfoResponse

/**
 * The rule under test is small but easy to get wrong in a way nobody would notice: a key that
 * could not be read must not subtract a region. Getting that backwards would quietly strip a
 * paid-for region whenever the network hiccuped, and the user would see the same thing as a real
 * plan downgrade.
 */
@DisplayName("DataRegionAvailability")
class DataRegionAvailabilityTest {

    private fun keyInfo(vararg regions: String) = ApiResult.Success(
        KeyInfoResponse(KeyData(label = "key", allowedDataRegions = regions.toList())),
        200
    )

    private fun availability(
        answers: Map<String, ApiResult<KeyInfoResponse>>,
        managementKey: String = "mgmt",
        apiKey: String = "api"
    ) = DataRegionAvailability(
        fetchKeyInfo = { key -> answers[key] ?: ApiResult.Error("no answer for $key") },
        managementKeyProvider = { managementKey },
        apiKeyProvider = { apiKey }
    )

    @Test
    @DisplayName("only the regions both keys allow are offered")
    fun `only the intersection is offered`() = runTest {
        val available = availability(
            mapOf(
                "mgmt" to keyInfo("global", "europe", "us"),
                "api" to keyInfo("global", "europe")
            )
        ).load()

        assertEquals(listOf(DataRegion.GLOBAL, DataRegion.EUROPE), available)
    }

    @Test
    @DisplayName("an account without the entitlement is offered the global region alone")
    fun `no entitlement leaves only global`() = runTest {
        val available = availability(
            mapOf("mgmt" to keyInfo("global"), "api" to keyInfo("global"))
        ).load()

        assertEquals(listOf(DataRegion.GLOBAL), available)
    }

    @Test
    @DisplayName("a key whose lookup failed does not subtract a region the other key allows")
    fun `a failed lookup does not subtract`() = runTest {
        val available = availability(
            mapOf(
                "mgmt" to keyInfo("global", "europe"),
                "api" to ApiResult.Error("network unreachable")
            )
        ).load()

        assertEquals(listOf(DataRegion.GLOBAL, DataRegion.EUROPE), available)
    }

    @Test
    @DisplayName("a key that is not configured is skipped without a request being made for it")
    fun `a blank key is never fetched`() = runTest {
        val asked = mutableListOf<String>()
        val availability = DataRegionAvailability(
            fetchKeyInfo = { key ->
                asked += key
                keyInfo("global", "europe", "us")
            },
            managementKeyProvider = { "mgmt" },
            apiKeyProvider = { "" }
        )

        val available = availability.load()

        assertEquals(listOf("mgmt"), asked)
        assertEquals(listOf(DataRegion.GLOBAL, DataRegion.EUROPE, DataRegion.US), available)
    }

    @Test
    @DisplayName("when neither key can be read the answer is the global region, never an empty list")
    fun `nothing readable still answers global`() = runTest {
        val available = availability(emptyMap()).load()

        assertEquals(listOf(DataRegion.GLOBAL), available)
    }
}
