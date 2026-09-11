package org.zhavoronkov.openrouter.settings.favorites

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.zhavoronkov.openrouter.settings.favorites.FavoriteModelsFixtures.CATALOG
import org.zhavoronkov.openrouter.settings.favorites.FavoriteModelsFixtures.GPT4O
import org.zhavoronkov.openrouter.settings.favorites.FavoriteModelsFixtures.SONNET
import org.zhavoronkov.openrouter.settings.favorites.FavoriteModelsPageState.Mode

class FavoriteModelsTableModelTest {

    private val state = FavoriteModelsPageState(listOf(GPT4O.id, SONNET.id)).also { it.setCatalog(CATALOG) }
    private val model = FavoriteModelsTableModel(FavoriteModelsTableColumns(state), state)

    @Test
    fun `only the favorite column is editable`() {
        model.items = state.visibleRows()

        assertTrue(model.isCellEditable(0, 0))
        (1 until model.columnCount).forEach { assertFalse(model.isCellEditable(0, it), "column $it") }
    }

    @Test
    fun `setValueAt on the favorite column toggles state`() {
        model.items = state.visibleRows()
        val row = model.items.indexOfFirst { it.id == SONNET.id }

        model.setValueAt(false, row, 0)

        assertEquals(listOf(GPT4O.id), state.favorites)
    }

    @Test
    fun `canExchangeRows mirrors state canReorder`() {
        assertFalse(model.canExchangeRows(0, 1))
        state.mode = Mode.FAVORITES_ONLY
        assertTrue(model.canExchangeRows(0, 1))
    }

    @Test
    fun `exchangeRows swaps both the state and the displayed items`() {
        state.mode = Mode.FAVORITES_ONLY
        model.items = state.visibleRows()

        model.exchangeRows(0, 1)

        assertEquals(listOf(SONNET.id, GPT4O.id), state.favorites)
        assertEquals(listOf(SONNET.id, GPT4O.id), model.items.map { it.id })
    }

    @Test
    fun `exchangeRows is refused in catalog mode`() {
        model.items = state.visibleRows()
        val before = model.items.map { it.id }

        model.exchangeRows(0, 1)

        assertEquals(listOf(GPT4O.id, SONNET.id), state.favorites)
        assertEquals(before, model.items.map { it.id })
    }

    @Test
    fun `addRow and removeRow are no-ops`() {
        model.items = state.visibleRows()
        val before = model.items.size

        model.addRow()
        model.removeRow(0)

        assertEquals(before, model.items.size)
    }
}
