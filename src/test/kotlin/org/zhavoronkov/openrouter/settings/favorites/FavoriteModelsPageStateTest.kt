package org.zhavoronkov.openrouter.settings.favorites

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.zhavoronkov.openrouter.settings.ModelFilterCriteria
import org.zhavoronkov.openrouter.settings.favorites.FavoriteModelsFixtures.BRAND_NEW
import org.zhavoronkov.openrouter.settings.favorites.FavoriteModelsFixtures.CATALOG
import org.zhavoronkov.openrouter.settings.favorites.FavoriteModelsFixtures.GPT4O
import org.zhavoronkov.openrouter.settings.favorites.FavoriteModelsFixtures.GPT4O_MINI
import org.zhavoronkov.openrouter.settings.favorites.FavoriteModelsFixtures.GROK
import org.zhavoronkov.openrouter.settings.favorites.FavoriteModelsFixtures.SONNET
import org.zhavoronkov.openrouter.settings.favorites.FavoriteModelsPageState.EmptyState
import org.zhavoronkov.openrouter.settings.favorites.FavoriteModelsPageState.Mode

@DisplayName("FavoriteModelsPageState")
class FavoriteModelsPageStateTest {

    private fun state(
        favorites: List<String> = emptyList(),
        catalog: List<org.zhavoronkov.openrouter.models.OpenRouterModelInfo> = CATALOG,
    ): FavoriteModelsPageState = FavoriteModelsPageState(favorites).also { it.setCatalog(catalog) }

    private fun visibleIds(state: FavoriteModelsPageState) = state.visibleRows().map { it.id }

    @Nested
    @DisplayName("toggling favorites")
    inner class Toggling {

        @Test
        fun `ticking a model appends it to the end of the ordered list`() {
            val s = state(favorites = listOf(GPT4O.id, SONNET.id))

            assertTrue(s.setFavorite(GROK.id, true))

            assertEquals(listOf(GPT4O.id, SONNET.id, GROK.id), s.favorites)
        }

        @Test
        fun `unticking removes only that id and keeps the others in order`() {
            val s = state(favorites = listOf(GPT4O.id, SONNET.id, GROK.id))

            assertTrue(s.setFavorite(SONNET.id, false))

            assertEquals(listOf(GPT4O.id, GROK.id), s.favorites)
        }

        @Test
        fun `toggle returns the new state and toggling twice restores the list`() {
            val s = state(favorites = listOf(GPT4O.id))

            assertTrue(s.toggleFavorite(GROK.id))
            assertFalse(s.toggleFavorite(GROK.id))

            assertEquals(listOf(GPT4O.id), s.favorites)
            assertFalse(s.isModified())
        }

        @Test
        fun `an id that is not in the catalog can still be removed`() {
            val s = state(favorites = listOf("gone/model", GPT4O.id))

            assertTrue(s.setFavorite("gone/model", false))

            assertEquals(listOf(GPT4O.id), s.favorites)
        }

        @Test
        fun `setting an existing favorite again is a no-op and does not notify`() {
            val s = state(favorites = listOf(GPT4O.id, SONNET.id))
            var notifications = 0
            s.onChanged = { notifications++ }

            assertFalse(s.setFavorite(GPT4O.id, true))
            assertFalse(s.setFavorite(GROK.id, false))

            assertEquals(listOf(GPT4O.id, SONNET.id), s.favorites)
            assertEquals(0, notifications)
        }

        @Test
        fun `isFavorite reflects the list`() {
            val s = state(favorites = listOf(GPT4O.id))

            assertTrue(s.isFavorite(GPT4O.id))
            assertFalse(s.isFavorite(GROK.id))
        }
    }

    @Nested
    @DisplayName("catalog view")
    inner class CatalogView {

        @Test
        fun `rows follow catalog order and favorites stay in place`() {
            val s = state(favorites = listOf(GROK.id, GPT4O.id))

            assertEquals(CATALOG.map { it.id }, visibleIds(s))
        }

        @Test
        fun `criteria filter the catalog`() {
            val s = state()
            s.criteria = ModelFilterCriteria(provider = "OpenAI")

            assertEquals(listOf(GPT4O.id, GPT4O_MINI.id), visibleIds(s))
        }

        @Test
        fun `search text narrows the catalog`() {
            val s = state()
            s.criteria = ModelFilterCriteria(searchText = "grok")

            assertEquals(listOf(GROK.id, "x-ai/grok-4-fast:free"), visibleIds(s))
        }

        @Test
        fun `favorites missing from the catalog are not shown in catalog mode`() {
            val s = state(favorites = listOf("gone/model"))

            assertFalse(visibleIds(s).contains("gone/model"))
        }

        @Test
        fun `providers are the sorted unique display names of the catalog`() {
            val s = state()

            assertEquals(
                listOf("Anthropic", "Google", "Meta", "Mistral", "OpenAI", "Some", "xAI"),
                s.providers()
            )
        }
    }

    @Nested
    @DisplayName("favorites-only view")
    inner class FavoritesOnlyView {

        @Test
        fun `rows are favorites in stored order regardless of catalog order`() {
            val s = state(favorites = listOf(SONNET.id, GPT4O.id))
            s.mode = Mode.FAVORITES_ONLY

            assertEquals(listOf(SONNET.id, GPT4O.id), visibleIds(s))
        }

        @Test
        fun `filters and search are ignored`() {
            val s = state(favorites = listOf(SONNET.id, GPT4O.id))
            s.criteria = ModelFilterCriteria(provider = "xAI", searchText = "nothing")
            s.mode = Mode.FAVORITES_ONLY

            assertEquals(listOf(SONNET.id, GPT4O.id), visibleIds(s))
        }

        @Test
        fun `a variant favorite whose base is in the catalog inherits base pricing and keeps its id`() {
            val s = state(favorites = listOf("openai/gpt-4o:nitro"))
            s.mode = Mode.FAVORITES_ONLY

            val row = s.visibleRows().single()

            assertEquals("openai/gpt-4o:nitro", row.id)
            assertEquals(GPT4O.pricing, row.pricing)
            assertTrue(s.isAvailable("openai/gpt-4o:nitro"))
        }

        @Test
        fun `a favorite missing from the catalog is a placeholder marked unavailable`() {
            val s = state(favorites = listOf("gone/model"))
            s.mode = Mode.FAVORITES_ONLY

            val row = s.visibleRows().single()

            assertEquals("gone/model", row.id)
            assertNull(row.pricing)
            assertFalse(s.isAvailable("gone/model"))
        }

        @Test
        fun `unticking in favorites-only mode drops the row`() {
            val s = state(favorites = listOf(SONNET.id, GPT4O.id))
            s.mode = Mode.FAVORITES_ONLY

            s.setFavorite(SONNET.id, false)

            assertEquals(listOf(GPT4O.id), visibleIds(s))
        }
    }

    @Nested
    @DisplayName("reordering")
    inner class Reordering {

        @Test
        fun `canReorder only in favorites-only mode`() {
            val s = state(favorites = listOf(GPT4O.id, SONNET.id))

            assertFalse(s.canReorder())
            s.mode = Mode.FAVORITES_ONLY
            assertTrue(s.canReorder())
        }

        @Test
        fun `moveUp swaps with the previous row and returns the new index`() {
            val s = state(favorites = listOf("a/1", "a/2", "a/3")).apply { mode = Mode.FAVORITES_ONLY }

            assertEquals(1, s.moveUp(2))

            assertEquals(listOf("a/1", "a/3", "a/2"), s.favorites)
        }

        @Test
        fun `moveUp at the top is a no-op and returns null`() {
            val s = state(favorites = listOf("a/1", "a/2")).apply { mode = Mode.FAVORITES_ONLY }
            var notifications = 0
            s.onChanged = { notifications++ }

            assertNull(s.moveUp(0))

            assertEquals(listOf("a/1", "a/2"), s.favorites)
            assertEquals(0, notifications)
        }

        @Test
        fun `moveDown swaps with the next row and returns the new index`() {
            val s = state(favorites = listOf("a/1", "a/2", "a/3")).apply { mode = Mode.FAVORITES_ONLY }

            assertEquals(1, s.moveDown(0))

            assertEquals(listOf("a/2", "a/1", "a/3"), s.favorites)
        }

        @Test
        fun `moveDown at the bottom is a no-op`() {
            val s = state(favorites = listOf("a/1", "a/2")).apply { mode = Mode.FAVORITES_ONLY }

            assertNull(s.moveDown(1))

            assertEquals(listOf("a/1", "a/2"), s.favorites)
        }

        @Test
        fun `out of range indices are ignored`() {
            val s = state(favorites = listOf("a/1", "a/2")).apply { mode = Mode.FAVORITES_ONLY }

            assertNull(s.moveUp(-1))
            assertNull(s.moveDown(5))
            assertFalse(s.exchange(1, 2))

            assertEquals(listOf("a/1", "a/2"), s.favorites)
        }

        @Test
        fun `exchange swaps adjacent rows`() {
            val s = state(favorites = listOf("a/1", "a/2", "a/3")).apply { mode = Mode.FAVORITES_ONLY }

            assertTrue(s.exchange(0, 1))

            assertEquals(listOf("a/2", "a/1", "a/3"), s.favorites)
        }

        @Test
        fun `reordering is refused in catalog mode`() {
            val s = state(favorites = listOf("a/1", "a/2"))

            assertNull(s.moveUp(1))
            assertFalse(s.exchange(0, 1))

            assertEquals(listOf("a/1", "a/2"), s.favorites)
        }
    }

    @Nested
    @DisplayName("presets")
    inner class Presets {

        @Test
        fun `applyPreset appends catalog-present ids in preset order after existing favorites`() {
            // OpenAI preset: gpt-4o, gpt-4o-mini, gpt-4-turbo, gpt-4, gpt-3.5-turbo — only two are in the catalog
            val s = state(favorites = listOf(SONNET.id, GPT4O_MINI.id))

            val result = s.applyPreset("OpenAI")

            assertEquals(listOf(SONNET.id, GPT4O_MINI.id, GPT4O.id), s.favorites)
            assertEquals(1, result.added)
            assertEquals(1, result.alreadyPresent)
            assertEquals(3, result.notInCatalog)
        }

        @Test
        fun `unknown preset changes nothing`() {
            val s = state(favorites = listOf(SONNET.id))

            val result = s.applyPreset("Nope")

            assertEquals(listOf(SONNET.id), s.favorites)
            assertEquals(FavoriteModelsPageState.PresetApplyResult(0, 0, 0), result)
        }

        @Test
        fun `a preset that is fully present adds nothing and does not modify`() {
            val s = state(favorites = listOf(GPT4O.id, GPT4O_MINI.id))

            val result = s.applyPreset("OpenAI")

            assertEquals(0, result.added)
            assertEquals(2, result.alreadyPresent)
            assertFalse(s.isModified())
        }
    }

    @Nested
    @DisplayName("modified, apply, reset")
    inner class Lifecycle {

        @Test
        fun `fresh state is not modified`() {
            assertFalse(state(favorites = listOf(GPT4O.id)).isModified())
        }

        @Test
        fun `reorder alone marks modified`() {
            val s = state(favorites = listOf("a/1", "a/2")).apply { mode = Mode.FAVORITES_ONLY }

            s.moveDown(0)

            assertTrue(s.isModified())
        }

        @Test
        fun `markApplied returns the current list and clears modified`() {
            val s = state(favorites = listOf(GPT4O.id))
            s.setFavorite(GROK.id, true)

            val applied = s.markApplied()

            assertEquals(listOf(GPT4O.id, GROK.id), applied)
            assertFalse(s.isModified())
        }

        @Test
        fun `reset replaces favorites and baseline`() {
            val s = state(favorites = listOf(GPT4O.id))
            s.setFavorite(GROK.id, true)

            s.reset(listOf(SONNET.id))

            assertEquals(listOf(SONNET.id), s.favorites)
            assertFalse(s.isModified())
        }

        @Test
        fun `setCatalog leaves favorites order intact`() {
            val s = state(favorites = listOf(SONNET.id, GPT4O.id))

            s.setCatalog(CATALOG.reversed())

            assertEquals(listOf(SONNET.id, GPT4O.id), s.favorites)
        }

        @Test
        fun `clearFilters resets criteria to default`() {
            val s = state()
            s.criteria = ModelFilterCriteria(provider = "OpenAI", searchText = "x")

            s.clearFilters()

            assertEquals(ModelFilterCriteria.default(), s.criteria)
        }
    }

    @Nested
    @DisplayName("status and empty state")
    inner class Status {

        @Test
        fun `unfiltered catalog status`() {
            val s = state(favorites = listOf(GPT4O.id, SONNET.id))

            assertEquals("9 models · 2 favorites", s.statusText())
        }

        @Test
        fun `filtered catalog status`() {
            val s = state(favorites = listOf(GPT4O.id))
            s.criteria = ModelFilterCriteria(provider = "OpenAI")

            assertEquals("2 of 9 models · 1 favorite", s.statusText())
        }

        @Test
        fun `favorites-only status`() {
            val s = state(favorites = listOf(GPT4O.id, SONNET.id)).apply { mode = Mode.FAVORITES_ONLY }

            assertEquals("2 favorites · drag rows or use ↑ ↓ to reorder", s.statusText())
        }

        @Test
        fun `error status`() {
            val s = state()
            s.loadError = "Network down"

            assertEquals("Error: Network down", s.statusText())
        }

        @Test
        fun `empty states`() {
            val empty = FavoriteModelsPageState()
            assertEquals(EmptyState.NO_CATALOG, empty.emptyState())

            empty.loadError = "boom"
            assertEquals(EmptyState.LOAD_FAILED, empty.emptyState())

            val filtered = state()
            filtered.criteria = ModelFilterCriteria(searchText = "zzz")
            assertEquals(EmptyState.NO_MATCHES, filtered.emptyState())

            val noFavorites = state().apply { mode = Mode.FAVORITES_ONLY }
            assertEquals(EmptyState.NO_FAVORITES, noFavorites.emptyState())

            assertEquals(EmptyState.NONE, state().emptyState())
            val withFavorites = state(favorites = listOf(GPT4O.id)).apply { mode = Mode.FAVORITES_ONLY }
            assertEquals(EmptyState.NONE, withFavorites.emptyState())
        }

        @Test
        fun `onChanged fires once per effective mutation`() {
            val s = state(favorites = listOf(GPT4O.id))
            var notifications = 0
            s.onChanged = { notifications++ }

            s.setFavorite(GROK.id, true)
            s.mode = Mode.FAVORITES_ONLY
            s.criteria = ModelFilterCriteria(searchText = "x")
            s.criteria = ModelFilterCriteria(searchText = "x")
            s.moveUp(1)
            s.moveUp(0)

            assertEquals(4, notifications)
        }

        @Test
        fun `BRAND_NEW fixture is visible so the Other variant filter has something to match`() {
            val s = state()
            s.criteria = ModelFilterCriteria(variant = VariantFilter.OTHER)

            assertEquals(listOf(BRAND_NEW.id), visibleIds(s))
        }
    }
}
