package org.zhavoronkov.openrouter.settings.favorites

import com.intellij.util.ui.ListTableModel
import org.zhavoronkov.openrouter.models.OpenRouterModelInfo

/**
 * Table model for the single favorites table.
 *
 * Row exchange (drag-and-drop via `RowsDnDSupport`, Move Up/Down actions) is
 * gated by [FavoriteModelsPageState.canReorder] and applied to the state first,
 * so the displayed rows and the stored favorites order never diverge.
 * Add/remove rows are no-ops: rows come from the catalog, not from the user.
 */
class FavoriteModelsTableModel(
    columns: FavoriteModelsTableColumns,
    private val state: FavoriteModelsPageState,
) : ListTableModel<OpenRouterModelInfo>(columns.asArray(), mutableListOf()) {

    override fun canExchangeRows(oldIndex: Int, newIndex: Int): Boolean = state.canReorder()

    override fun exchangeRows(oldIndex: Int, newIndex: Int) {
        if (state.exchange(oldIndex, newIndex)) {
            super.exchangeRows(oldIndex, newIndex)
        }
    }

    override fun addRow() = Unit

    override fun removeRow(idx: Int) = Unit
}
