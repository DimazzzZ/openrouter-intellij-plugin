package org.zhavoronkov.openrouter.models

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * [DataRegion] exists because OpenRouter spells the same region three ways, and [DataRegions]
 * because a region is only usable when BOTH of the plugin's keys allow it. Both are plain logic
 * over values the API reports, so they belong to the fast headless `test` task.
 */
@DisplayName("DataRegion")
class DataRegionTest {

    @Nested
    @DisplayName("Spellings")
    inner class Spellings {

        @Test
        @DisplayName("each region carries the three spellings OpenRouter actually uses")
        fun `each region carries its three spellings`() {
            assertEquals("europe", DataRegion.EUROPE.apiName)
            assertEquals("eu", DataRegion.EUROPE.queryValue)
            assertEquals("https://eu.openrouter.ai/api/v1", DataRegion.EUROPE.baseUrl)

            assertEquals("us", DataRegion.US.apiName)
            assertEquals("us", DataRegion.US.queryValue)
            assertEquals("https://us.openrouter.ai/api/v1", DataRegion.US.baseUrl)
        }

        @Test
        @DisplayName("the global region omits the query parameter and keeps the plain host")
        fun `global omits the query parameter`() {
            assertNull(DataRegion.GLOBAL.queryValue)
            assertEquals("https://openrouter.ai/api/v1", DataRegion.GLOBAL.baseUrl)
        }

        @Test
        @DisplayName("an api name resolves back to its region, case-insensitively")
        fun `an api name resolves back`() {
            assertEquals(DataRegion.EUROPE, DataRegion.fromApiName("europe"))
            assertEquals(DataRegion.EUROPE, DataRegion.fromApiName("EUROPE"))
            assertEquals(DataRegion.GLOBAL, DataRegion.fromApiName("global"))
        }

        @Test
        @DisplayName("a region this build has never heard of resolves to null rather than throwing")
        fun `an unknown region resolves to null`() {
            assertNull(DataRegion.fromApiName("apac"))
            assertNull(DataRegion.fromApiName(""))
        }
    }

    @Nested
    @DisplayName("Availability")
    inner class Availability {

        @Test
        @DisplayName("only the regions both keys allow are offered")
        fun `only the intersection is offered`() {
            val available = DataRegions.available(
                managementKeyRegions = listOf("global", "europe", "us"),
                apiKeyRegions = listOf("global", "europe")
            )

            assertEquals(listOf(DataRegion.GLOBAL, DataRegion.EUROPE), available)
        }

        @Test
        @DisplayName("an account with no in-region entitlement is offered the global region alone")
        fun `no entitlement leaves only global`() {
            val available = DataRegions.available(listOf("global"), listOf("global"))

            assertEquals(listOf(DataRegion.GLOBAL), available)
        }

        @Test
        @DisplayName("a key whose lookup failed is left out of the reckoning, not read as allowing nothing")
        fun `a failed lookup does not strip regions`() {
            val available = DataRegions.available(listOf("global", "europe"), null)

            assertEquals(listOf(DataRegion.GLOBAL, DataRegion.EUROPE), available)
        }

        @Test
        @DisplayName("when neither key could be read, only the global region is offered")
        fun `two failed lookups leave only global`() {
            assertEquals(listOf(DataRegion.GLOBAL), DataRegions.available(null, null))
            assertEquals(listOf(DataRegion.GLOBAL), DataRegions.available(emptyList(), emptyList()))
        }

        @Test
        @DisplayName("the global region is offered even when the account does not list it")
        fun `global is always offered`() {
            val available = DataRegions.available(listOf("europe"), listOf("europe"))

            assertTrue(DataRegion.GLOBAL in available)
            assertEquals(listOf(DataRegion.GLOBAL, DataRegion.EUROPE), available)
        }

        @Test
        @DisplayName("an unrecognised region name is ignored rather than breaking the intersection")
        fun `an unrecognised name is ignored`() {
            val available = DataRegions.available(
                managementKeyRegions = listOf("global", "europe", "apac"),
                apiKeyRegions = listOf("global", "europe", "apac")
            )

            assertEquals(listOf(DataRegion.GLOBAL, DataRegion.EUROPE), available)
        }

        @Test
        @DisplayName("regions are offered in a stable order, not the order the server listed them")
        fun `regions keep a stable order`() {
            val available = DataRegions.available(
                managementKeyRegions = listOf("us", "europe", "global"),
                apiKeyRegions = listOf("us", "global", "europe")
            )

            assertEquals(listOf(DataRegion.GLOBAL, DataRegion.EUROPE, DataRegion.US), available)
        }
    }

    @Nested
    @DisplayName("Re-checking a stored selection")
    inner class StoredSelection {

        @Test
        @DisplayName("a selection the account still allows survives")
        fun `a still-allowed selection survives`() {
            val available = DataRegions.available(listOf("global", "europe"), listOf("global", "europe"))

            assertTrue(DataRegions.isStillAvailable(DataRegion.EUROPE, available))
        }

        @Test
        @DisplayName("a selection the account has lost - a downgrade, a new policy - no longer holds")
        fun `a withdrawn selection no longer holds`() {
            val available = DataRegions.available(listOf("global"), listOf("global"))

            assertFalse(DataRegions.isStillAvailable(DataRegion.EUROPE, available))
            assertTrue(DataRegions.isStillAvailable(DataRegion.GLOBAL, available))
        }
    }
}
