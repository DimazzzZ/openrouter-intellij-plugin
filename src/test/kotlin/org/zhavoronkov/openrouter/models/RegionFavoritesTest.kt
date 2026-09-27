package org.zhavoronkov.openrouter.models

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * The subtle part is variant matching. A variant suffix is a routing instruction rather than a
 * separate model, so comparing full ids would report every favorite carrying one as missing and
 * overstate what a region costs - which is exactly the number the settings page shows someone
 * before they commit to the change. The wording around that number lives in the settings layer;
 * this object only counts.
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
        @DisplayName("favorites the region does not list are reported, in the order they were favorited")
        fun `missing favorites are reported in order`() {
            val missing = RegionFavorites.unavailable(
                favoriteIds = listOf("x-ai/grok-4", "openai/gpt-4o", "meta-llama/llama-4"),
                regionModelIds = euModels
            )

            assertEquals(listOf("x-ai/grok-4", "meta-llama/llama-4"), missing)
        }

        @Test
        @DisplayName("a favorite carrying a variant matches the base model the region serves")
        fun `a variant matches its base model`() {
            val missing = RegionFavorites.unavailable(
                favoriteIds = listOf("openai/gpt-4o:nitro", "anthropic/claude-sonnet-4:floor"),
                regionModelIds = euModels
            )

            assertTrue(missing.isEmpty(), "a routing variant is not a different model")
        }

        @Test
        @DisplayName("a catalogue entry that itself carries a variant still matches a plain favorite")
        fun `a variant in the catalogue matches a plain favorite`() {
            val missing = RegionFavorites.unavailable(
                favoriteIds = listOf("google/gemini-2.5-flash-lite"),
                regionModelIds = euModels
            )

            assertTrue(missing.isEmpty())
        }

        @Test
        @DisplayName("nothing is reported when there are no favorites")
        fun `no favorites reports nothing`() {
            assertEquals(emptyList<String>(), RegionFavorites.unavailable(emptyList(), euModels))
        }

        @Test
        @DisplayName("an empty catalogue reports nothing rather than declaring every favorite lost")
        fun `an empty catalogue reports nothing`() {
            assertEquals(
                emptyList<String>(),
                RegionFavorites.unavailable(listOf("openai/gpt-4o"), emptyList()),
                "an unanswered catalogue is not evidence that anything is missing"
            )
        }
    }
}
