package org.zhavoronkov.openrouter.settings

import com.intellij.ui.SearchTextField
import com.intellij.util.ui.ListTableModel
import org.zhavoronkov.openrouter.models.OpenRouterModelInfo
import org.zhavoronkov.openrouter.utils.ModelProviderUtils
import org.zhavoronkov.openrouter.utils.PluginLogger
import javax.swing.JCheckBox
import javax.swing.JComboBox

/**
 * Container for filter UI components
 */
data class FilterComponents(
    val searchField: SearchTextField,
    val providerComboBox: JComboBox<String>,
    val contextComboBox: JComboBox<String>,
    val visionCheckBox: JCheckBox,
    val audioCheckBox: JCheckBox,
    val toolsCheckBox: JCheckBox,
    val imageGenCheckBox: JCheckBox
)

/**
 * Manages filtering of available models based on various criteria
 */
class ModelsFilterManager(
    private val filterComponents: FilterComponents,
    private val availableTableModel: ListTableModel<OpenRouterModelInfo>,
    private val getCurrentFavoriteIds: () -> List<String>
) {

    private var allAvailableModels: List<OpenRouterModelInfo> = emptyList()
    private var filteredAvailableModels: List<OpenRouterModelInfo> = emptyList()
    private var currentFilterCriteria = ModelFilterCriteria.default()

    fun setAllModels(models: List<OpenRouterModelInfo>) {
        allAvailableModels = models
    }

    fun getAllModels(): List<OpenRouterModelInfo> = allAvailableModels

    fun getFilteredModels(): List<OpenRouterModelInfo> = filteredAvailableModels

    fun filterModels() {
        val searchText = filterComponents.searchField.text.trim()
        val criteria = currentFilterCriteria.copy(searchText = searchText)
        filteredAvailableModels = allAvailableModels.filter(criteria::matches)

        // Exclude already favorited models
        val favoriteIds = getCurrentFavoriteIds().toSet()
        val availableToAdd = filteredAvailableModels.filter { it.id !in favoriteIds }

        val filterDesc = currentFilterCriteria.describe()
        PluginLogger.Settings.debug(
            "Filtered models: ${availableToAdd.size} available " +
                "(from ${allAvailableModels.size} total, filters: $filterDesc)"
        )
        availableTableModel.items = availableToAdd
    }

    fun onFilterChanged() {
        // Update filter criteria from UI components
        val capabilities = buildSet {
            if (filterComponents.visionCheckBox.isSelected) add(ModelProviderUtils.Capability.VISION)
            if (filterComponents.audioCheckBox.isSelected) add(ModelProviderUtils.Capability.AUDIO)
            if (filterComponents.toolsCheckBox.isSelected) add(ModelProviderUtils.Capability.TOOLS)
            if (filterComponents.imageGenCheckBox.isSelected) add(ModelProviderUtils.Capability.IMAGE_GENERATION)
        }
        currentFilterCriteria = ModelFilterCriteria(
            provider = (filterComponents.providerComboBox.selectedItem as? String)
                ?.takeUnless { it == "All Providers" },
            contextRange = ModelProviderUtils.ContextRange.fromDisplayName(
                filterComponents.contextComboBox.selectedItem as? String ?: "Any"
            ),
            capabilities = capabilities,
            searchText = filterComponents.searchField.text.trim()
        )

        // Re-filter the available models
        filterModels()

        PluginLogger.Settings.debug("Filters changed: ${currentFilterCriteria.describe()}")
    }

    fun clearFilters() {
        filterComponents.providerComboBox.selectedItem = "All Providers"
        filterComponents.contextComboBox.selectedItem = ModelProviderUtils.ContextRange.ANY.displayName
        filterComponents.visionCheckBox.isSelected = false
        filterComponents.audioCheckBox.isSelected = false
        filterComponents.toolsCheckBox.isSelected = false
        filterComponents.imageGenCheckBox.isSelected = false
        filterComponents.searchField.text = ""

        currentFilterCriteria = ModelFilterCriteria.default()
        filterModels()

        PluginLogger.Settings.debug("Filters cleared")
    }

    fun updateProviderDropdown() {
        val providers = listOf("All Providers") + ModelProviderUtils.getUniqueProviders(allAvailableModels)
        val currentSelection = filterComponents.providerComboBox.selectedItem

        filterComponents.providerComboBox.removeAllItems()
        providers.forEach { filterComponents.providerComboBox.addItem(it) }

        // Restore selection if it still exists
        if (currentSelection in providers) {
            filterComponents.providerComboBox.selectedItem = currentSelection
        }
    }
}
