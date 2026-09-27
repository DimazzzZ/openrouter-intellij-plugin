package org.zhavoronkov.openrouter.models

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * The subtle part is variant matching. A variant suffix is a routing instruction rather than a
 * separate model, so comparing full ids would report every favourite carrying one as missing and
 * overstate what a region costs - which is exactly the number the settings page shows someone
 * before they commit to the change.
 */
@DisplayName("RegionFavorites")
class RegionFavoritesTest {

    private val euModels = listOf(
        "openai/gpt-4o",
        "anthropic/claude-sonnet-4",
        "google/gemini-2.5-flash-lite:batch"
    )

    @Nested
    @DisplayName("What a region cannot serve")
    inner class Unavailable {

        @Test
        @DisplayName("favourites the region does not list are reported, in the order they were favourited")
        fun `missing favourites are reported in order`() {
            val missing = RegionFavorites.unavailable(
                favoriteIds = listOf("x-ai/grok-4", "openai/gpt-4o", "meta-llama/llama-4"),
                regionModelIds = euModels
            )

            assertEquals(listOf("x-ai/grok-4", "meta-llama/llama-4"), missing)
        }

        @Test
        @DisplayName("a favourite carrying a variant matches the base model the region serves")
        fun `a variant matches its base model`() {
            val missing = RegionFavorites.unavailable(
                favoriteIds = listOf("openai/gpt-4o:nitro", "anthropic/claude-sonnet-4:floor"),
                regionModelIds = euModels
            )

            assertTrue(missing.isEmpty(), "a routing variant is not a different model")
        }

        @Test
        @DisplayName("a catalogue entry that itself carries a variant still matches a plain favourite")
        fun `a variant in the catalogue matches a plain favourite`() {
            val missing = RegionFavorites.unavailable(
                favoriteIds = listOf("google/gemini-2.5-flash-lite"),
                regionModelIds = euModels
            )

            assertTrue(missing.isEmpty())
        }

        @Test
        @DisplayName("nothing is reported when there are no favourites")
        fun `no favourites reports nothing`() {
            assertEquals(emptyList<String>(), RegionFavorites.unavailable(emptyList(), euModels))
        }

        @Test
        @DisplayName("an empty catalogue reports nothing rather than declaring every favourite lost")
        fun `an empty catalogue reports nothing`() {
            assertEquals(
                emptyList<String>(),
                RegionFavorites.unavailable(listOf("openai/gpt-4o"), emptyList()),
                "an unanswered catalogue is not evidence that anything is missing"
            )
        }
    }

    @Nested
    @DisplayName("The sentence shown in settings")
    inner class Summary {

        @Test
        @DisplayName("names how many of how many, and the region")
        fun `the summary names the counts and the region`() {
            val summary = RegionFavorites.impactSummary(
                region = DataRegion.EUROPE,
                favoriteIds = listOf("x-ai/grok-4", "openai/gpt-4o", "meta-llama/llama-4"),
                regionModelIds = euModels
            )

            assertEquals(
                "2 of your 3 favourite models are not available in European Union " +
                    "and will not be offered while it is selected.",
                summary
            )
        }

        @Test
        @DisplayName("says nothing about the global region, which serves everything")
        fun `the global region says nothing`() {
            assertNull(
                RegionFavorites.impactSummary(
                    region = DataRegion.GLOBAL,
                    favoriteIds = listOf("x-ai/grok-4"),
                    regionModelIds = euModels
                )
            )
        }

        @Test
        @DisplayName("says nothing when the region serves every favourite")
        fun `a region that serves everything says nothing`() {
            assertNull(
                RegionFavorites.impactSummary(
                    region = DataRegion.EUROPE,
                    favoriteIds = listOf("openai/gpt-4o"),
                    regionModelIds = euModels
                )
            )
        }

        @Test
        @DisplayName("says nothing when there are no favourites to lose")
        fun `no favourites says nothing`() {
            assertNull(RegionFavorites.impactSummary(DataRegion.EUROPE, emptyList(), euModels))
        }
    }
}
