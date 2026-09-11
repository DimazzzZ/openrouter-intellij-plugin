package org.zhavoronkov.openrouter.settings.favorites

import org.zhavoronkov.openrouter.models.OpenRouterModelInfo
import org.zhavoronkov.openrouter.settings.ModelFilterCriteria
import org.zhavoronkov.openrouter.settings.ModelPresets
import org.zhavoronkov.openrouter.utils.ModelProviderUtils

/**
 * View-model for the Favorite Models settings page.
 *
 * Pure Kotlin: no Swing, no IntelliJ platform, no logging, so the whole page
 * logic runs in the fast unit tier. The Swing panel renders from
 * [visibleRows], [statusText] and [emptyState] and forwards user actions here.
 *
 * The ordered [favorites] list is the load-bearing state: its order becomes the
 * AI Assistant dropdown order. Ticking appends to the end, unticking removes
 * positionally, and reordering is only offered in [Mode.FAVORITES_ONLY], where
 * row indices equal stored positions.
 */
class FavoriteModelsPageState(initialFavorites: List<String> = emptyList()) {

    enum class Mode { CATALOG, FAVORITES_ONLY }

    enum class EmptyState { NONE, NO_CATALOG, LOAD_FAILED, NO_MATCHES, NO_FAVORITES }

    data class PresetApplyResult(val added: Int, val alreadyPresent: Int, val notInCatalog: Int)

    private val favoriteIds = LinkedHashSet(initialFavorites)
    private var appliedFavorites: List<String> = initialFavorites.toList()

    var onChanged: (() -> Unit)? = null

    var catalog: List<OpenRouterModelInfo> = emptyList()
        private set

    var loadError: String? = null

    var mode: Mode = Mode.CATALOG
        set(value) {
            if (field == value) return
            field = value
            fireChanged()
        }

    var criteria: ModelFilterCriteria = ModelFilterCriteria.default()
        set(value) {
            if (field == value) return
            field = value
            fireChanged()
        }

    val favorites: List<String>
        get() = favoriteIds.toList()

    fun setCatalog(models: List<OpenRouterModelInfo>) {
        catalog = models
        loadError = null
        fireChanged()
    }

    // --- favorites -------------------------------------------------------------------------

    fun isFavorite(id: String): Boolean = id in favoriteIds

    /** Returns true when the list actually changed. */
    fun setFavorite(id: String, on: Boolean): Boolean {
        val changed = if (on) favoriteIds.add(id) else favoriteIds.remove(id)
        if (changed) fireChanged()
        return changed
    }

    /** Flips the favorite state of [id] and returns the new state. */
    fun toggleFavorite(id: String): Boolean {
        val newState = !isFavorite(id)
        setFavorite(id, newState)
        return newState
    }

    fun applyPreset(name: String): PresetApplyResult {
        val presetIds = ModelPresets.getPreset(name)?.modelIds ?: return PresetApplyResult(0, 0, 0)
        val catalogIds = catalog.mapTo(HashSet()) { it.id }
        var added = 0
        var alreadyPresent = 0
        var notInCatalog = 0
        for (id in presetIds) {
            when {
                id in favoriteIds -> alreadyPresent++
                id !in catalogIds -> notInCatalog++
                else -> {
                    favoriteIds.add(id)
                    added++
                }
            }
        }
        if (added > 0) fireChanged()
        return PresetApplyResult(added, alreadyPresent, notInCatalog)
    }

    // --- reordering ------------------------------------------------------------------------

    fun canReorder(): Boolean = mode == Mode.FAVORITES_ONLY

    /** Swaps two rows of the favorites list; returns false when refused or out of range. */
    fun exchange(a: Int, b: Int): Boolean {
        if (!canReorder()) return false
        val list = favoriteIds.toMutableList()
        if (a !in list.indices || b !in list.indices || a == b) return false
        val tmp = list[a]
        list[a] = list[b]
        list[b] = tmp
        favoriteIds.clear()
        favoriteIds.addAll(list)
        fireChanged()
        return true
    }

    /** Moves the row at [index] up by one; returns its new index or null when nothing moved. */
    fun moveUp(index: Int): Int? = shift(index, -1)

    /** Moves the row at [index] down by one; returns its new index or null when nothing moved. */
    fun moveDown(index: Int): Int? = shift(index, +1)

    private fun shift(index: Int, delta: Int): Int? {
        val target = index + delta
        return if (exchange(index, target)) target else null
    }

    // --- filtering -------------------------------------------------------------------------

    fun clearFilters() {
        criteria = ModelFilterCriteria.default()
    }

    fun providers(): List<String> = ModelProviderUtils.getUniqueProviders(catalog)

    // --- derived views ---------------------------------------------------------------------

    fun visibleRows(): List<OpenRouterModelInfo> = when (mode) {
        Mode.CATALOG -> catalog.filter(criteria::matches)
        Mode.FAVORITES_ONLY -> favoriteIds.map(::resolve)
    }

    /**
     * Resolve a favorite id to catalog data: exact id, else the base model with the
     * variant id kept (so pricing survives), else a bare placeholder.
     */
    fun resolve(id: String): OpenRouterModelInfo {
        catalog.firstOrNull { it.id == id }?.let { return it }
        val base = catalog.firstOrNull { it.id == ModelProviderUtils.stripVariant(id) }
        return base?.copy(id = id, name = id) ?: OpenRouterModelInfo(id = id, name = id, created = 0L)
    }

    fun isAvailable(id: String): Boolean {
        val baseId = ModelProviderUtils.stripVariant(id)
        return catalog.any { it.id == id || it.id == baseId }
    }

    fun statusText(): String {
        loadError?.let { return "Error: $it" }
        val favoritesText = pluralize(favoriteIds.size, "favorite")
        return when (mode) {
            Mode.FAVORITES_ONLY -> "$favoritesText · drag rows or use ↑ ↓ to reorder"
            Mode.CATALOG -> {
                val shown = visibleRows().size
                val total = catalog.size
                val modelsText = if (shown == total) {
                    pluralize(total, "model")
                } else {
                    "$shown of ${pluralize(total, "model")}"
                }
                "$modelsText · $favoritesText"
            }
        }
    }

    fun emptyState(): EmptyState = when {
        mode == Mode.FAVORITES_ONLY -> if (favoriteIds.isEmpty()) EmptyState.NO_FAVORITES else EmptyState.NONE
        loadError != null && catalog.isEmpty() -> EmptyState.LOAD_FAILED
        catalog.isEmpty() -> EmptyState.NO_CATALOG
        visibleRows().isEmpty() -> EmptyState.NO_MATCHES
        else -> EmptyState.NONE
    }

    // --- lifecycle -------------------------------------------------------------------------

    fun isModified(): Boolean = favorites != appliedFavorites

    /** Snapshot the current list as the persisted baseline and return it. */
    fun markApplied(): List<String> {
        appliedFavorites = favorites
        return appliedFavorites
    }

    fun reset(persisted: List<String>) {
        favoriteIds.clear()
        favoriteIds.addAll(persisted)
        appliedFavorites = persisted.toList()
        fireChanged()
    }

    private fun fireChanged() {
        onChanged?.invoke()
    }

    private fun pluralize(count: Int, noun: String): String =
        if (count == 1) "$count $noun" else "$count ${noun}s"
}
