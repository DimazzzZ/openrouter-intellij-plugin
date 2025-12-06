package org.zhavoronkov.openrouter.settings

import com.intellij.openapi.ui.Messages
import com.intellij.ui.table.TableView
import com.intellij.util.ui.ListTableModel
import org.zhavoronkov.openrouter.models.OpenRouterModelInfo
import org.zhavoronkov.openrouter.utils.PluginLogger

/**
 * Manages the favorites table operations
 */
class FavoriteModelsTableManager(
    private val favoriteTableModel: ListTableModel<OpenRouterModelInfo>,
    private val favoriteTable: TableView<OpenRouterModelInfo>,
    private val onFavoritesChanged: () -> Unit
) {

    fun addModels(models: List<OpenRouterModelInfo>) {
        val currentFavorites = favoriteTableModel.items.toMutableList()
        val currentIds = currentFavorites.map { it.id }.toSet()

        val newModels = models.filter { it.id !in currentIds }
        if (newModels.isEmpty()) return

        currentFavorites.addAll(newModels)
        favoriteTableModel.items = currentFavorites
        onFavoritesChanged()
    }

    fun addPresetModels(presetModelIds: List<String>, allAvailableModels: List<OpenRouterModelInfo>): Boolean {
        val currentFavorites = favoriteTableModel.items.toMutableList()
        val currentIds = currentFavorites.map { it.id }.toSet()

        val modelsToAdd = allAvailableModels.filter { model ->
            model.id in presetModelIds && model.id !in currentIds
        }

        if (modelsToAdd.isEmpty()) {
            Messages.showInfoMessage(
                "All models from this preset are already in your favorites.",
                "No Models Added"
            )
            return false
        }

        currentFavorites.addAll(modelsToAdd)
        favoriteTableModel.items = currentFavorites
        onFavoritesChanged()

        Messages.showInfoMessage(
            "Added ${modelsToAdd.size} model(s) to favorites.",
            "Models Added"
        )

        PluginLogger.Settings.debug("Added ${modelsToAdd.size} models from preset")
        return true
    }

    fun removeSelectedModels() {
        val selectedRows = favoriteTable.selectedRows
        if (selectedRows.isEmpty()) return

        val selectedModels = selectedRows.map { favoriteTableModel.getItem(it) }
        val currentFavorites = favoriteTableModel.items.toMutableList()

        currentFavorites.removeAll(selectedModels.toSet())
        favoriteTableModel.items = currentFavorites
        onFavoritesChanged()
    }

    fun clearAll(): Boolean {
        val result = Messages.showYesNoDialog(
            "Are you sure you want to remove all favorite models?",
            "Clear All Favorites",
            Messages.getQuestionIcon()
        )

        if (result == Messages.YES) {
            favoriteTableModel.items = emptyList()
            onFavoritesChanged()
            return true
        }
        return false
    }

    fun moveUp() {
        val selectedRow = favoriteTable.selectedRow
        if (selectedRow <= 0) return

        val items = favoriteTableModel.items.toMutableList()
        val temp = items[selectedRow]
        items[selectedRow] = items[selectedRow - 1]
        items[selectedRow - 1] = temp

        favoriteTableModel.items = items
        favoriteTable.setRowSelectionInterval(selectedRow - 1, selectedRow - 1)
    }

    fun moveDown() {
        val selectedRow = favoriteTable.selectedRow
        if (selectedRow < 0 || selectedRow >= favoriteTableModel.rowCount - 1) return

        val items = favoriteTableModel.items.toMutableList()
        val temp = items[selectedRow]
        items[selectedRow] = items[selectedRow + 1]
        items[selectedRow + 1] = temp

        favoriteTableModel.items = items
        favoriteTable.setRowSelectionInterval(selectedRow + 1, selectedRow + 1)
    }

    fun getCurrentIds(): List<String> {
        return favoriteTableModel.items.map { it.id }
    }

    fun setItems(items: List<OpenRouterModelInfo>) {
        favoriteTableModel.items = items
    }

    fun updateAvailability(availableModels: List<OpenRouterModelInfo>) {
        val currentFavorites = favoriteTableModel.items
        val updatedFavorites = currentFavorites.map { favorite ->
            availableModels.find { it.id == favorite.id } ?: favorite
        }
        favoriteTableModel.items = updatedFavorites
    }
}
