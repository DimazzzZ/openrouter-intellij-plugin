package org.zhavoronkov.openrouter.settings

import com.intellij.icons.AllIcons
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.ui.Messages
import com.intellij.ui.SearchTextField
import com.intellij.ui.TableSpeedSearch
import com.intellij.ui.ToolbarDecorator
import com.intellij.ui.components.JBLoadingPanel
import com.intellij.ui.dsl.builder.*
import com.intellij.ui.table.TableView
import com.intellij.util.ui.ColumnInfo
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.ListTableModel
import org.zhavoronkov.openrouter.models.OpenRouterModelInfo
import org.zhavoronkov.openrouter.services.FavoriteModelsService
import org.zhavoronkov.openrouter.services.OpenRouterSettingsService
import org.zhavoronkov.openrouter.utils.PluginLogger
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.Timer
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener

/**
 * Compact Favorite Models settings panel with provisioning key guard
 * Uses Kotlin UI DSL v2 with single-column tables for space efficiency
 */
class FavoriteModelsSettingsPanel : Disposable {

    companion object {
        private const val SEARCH_DEBOUNCE_MS = 300
        private const val MIN_DIALOG_WIDTH = 760
        private const val MIN_DIALOG_HEIGHT = 480
        private const val SEARCH_FIELD_HEIGHT = 28
        private const val BUTTON_COLUMN_WIDTH = 90
        private const val CACHE_DURATION_MS = 300000L // 5 minutes
    }

    private val settingsService = OpenRouterSettingsService.getInstance()
    private val favoriteModelsService = FavoriteModelsService.getInstance()

    // Provisioning key state
    private var keyPresent: Boolean = false

    // Table models - single column "Model ID"
    private val availableTableModel = createAvailableTableModel()
    private val favoriteTableModel = createFavoriteTableModel()

    // Tables
    private val availableTable = TableView(availableTableModel)
    private val favoriteTable = TableView(favoriteTableModel)

    // Search
    private val searchField = SearchTextField()
    private var searchDebounceTimer: Timer? = null

    // Loading state
    private val loadingPanel = JBLoadingPanel(BorderLayout(), this)
    private var isLoading = false
    private var loadError: String? = null

    // Data
    private var allAvailableModels: List<OpenRouterModelInfo> = emptyList()
    private var filteredAvailableModels: List<OpenRouterModelInfo> = emptyList()

    // Modified state tracking
    private var initialFavorites: List<String> = emptyList()

    // Status labels (need to be updated dynamically)
    private var availableStatusLabel: javax.swing.JLabel? = null
    private var favoritesStatusLabel: javax.swing.JLabel? = null

    init {
        checkProvisioningKey()
        setupTables()
        setupSearch()
        if (keyPresent) {
            loadInitialData()
        }
    }

    /**
     * Check if provisioning key is present and valid
     */
    private fun checkProvisioningKey() {
        keyPresent = settingsService.isConfigured()
        PluginLogger.Settings.debug("Provisioning key present: $keyPresent")
    }

    /**
     * Create the main panel using UI DSL v2
     */
    fun createPanel(): JPanel {
        val panel = panel {
            // Warning banner when key is missing
            if (!keyPresent) {
                row {
                    icon(AllIcons.General.Warning)
                        .gap(RightGap.SMALL)
                    label("To manage favorite models, add your Provisioning Key in Tools → OpenRouter → Settings.")
                    button("Open Settings") {
                        openMainSettings()
                    }
                }.topGap(TopGap.NONE)
            }

            // Main content - disabled when key is missing
            group("Favorite Models Management") {
                row {
                    comment("Only favorite models are shown in AI Assistant model selection")
                }.topGap(TopGap.NONE).visible(keyPresent)

                row {
                    cell(createAvailableModelsPanel())
                        .align(Align.FILL)
                        .resizableColumn()

                    cell(createPickerButtonsColumn())
                        .align(AlignY.CENTER)

                    cell(createFavoritesPanel())
                        .align(Align.FILL)
                        .resizableColumn()
                }.layout(RowLayout.PARENT_GRID).resizableRow().visible(keyPresent)
            }
        }

        panel.minimumSize = Dimension(MIN_DIALOG_WIDTH, MIN_DIALOG_HEIGHT)
        loadingPanel.add(panel, BorderLayout.CENTER)
        return loadingPanel
    }

    /**
     * Create the available models panel (left side)
     */
    private fun createAvailableModelsPanel(): JPanel {
        return panel {
            row {
                label("Available Models")
                    .bold()
            }.topGap(TopGap.NONE)

            row {
                cell(searchField)
                    .align(AlignX.FILL)
                    .resizableColumn()
                    .applyToComponent {
                        preferredSize = Dimension(preferredSize.width, SEARCH_FIELD_HEIGHT)
                        maximumSize = Dimension(Int.MAX_VALUE, SEARCH_FIELD_HEIGHT)
                    }

                button("Refresh") {
                    refreshAvailableModels()
                }.applyToComponent {
                    preferredSize = Dimension(preferredSize.width, SEARCH_FIELD_HEIGHT)
                }
            }.topGap(TopGap.SMALL)

            row {
                val decorator = ToolbarDecorator.createDecorator(availableTable)
                    .disableAddAction()
                    .disableRemoveAction()
                    .disableUpDownActions()
                    .setPreferredSize(Dimension(300, 250))

                cell(decorator.createPanel())
                    .align(Align.FILL)
                    .resizableColumn()
            }.resizableRow().topGap(TopGap.SMALL)

            row {
                label(getAvailableModelsStatusText())
                    .applyToComponent {
                        name = "availableStatusLabel"
                        availableStatusLabel = this
                    }
            }.topGap(TopGap.SMALL)
        }
    }

    /**
     * Create the picker buttons column (middle)
     */
    private fun createPickerButtonsColumn(): JPanel {
        return panel {
            row {
                button("Add →") {
                    addSelectedToFavorites()
                }.applyToComponent {
                    toolTipText = "Add selected models to favorites (Enter)"
                    preferredSize = Dimension(BUTTON_COLUMN_WIDTH, preferredSize.height)
                }
            }.topGap(TopGap.NONE)

            row {
                button("Add All") {
                    addAllFilteredToFavorites()
                }.applyToComponent {
                    toolTipText = "Add all filtered models to favorites"
                    preferredSize = Dimension(BUTTON_COLUMN_WIDTH, preferredSize.height)
                }
            }.topGap(TopGap.SMALL)

            row {
                button("← Remove") {
                    removeSelectedFromFavorites()
                }.applyToComponent {
                    toolTipText = "Remove selected from favorites (Delete)"
                    preferredSize = Dimension(BUTTON_COLUMN_WIDTH, preferredSize.height)
                }
            }.topGap(TopGap.MEDIUM)

            row {
                button("Clear All") {
                    clearAllFavorites()
                }.applyToComponent {
                    toolTipText = "Remove all favorites"
                    preferredSize = Dimension(BUTTON_COLUMN_WIDTH, preferredSize.height)
                }
            }.topGap(TopGap.SMALL)
        }
    }

    /**
     * Create the favorites panel (right side)
     */
    private fun createFavoritesPanel(): JPanel {
        return panel {
            row {
                label("Favorite Models")
                    .bold()
            }.topGap(TopGap.NONE)

            row {
                comment("Drag to reorder or use Up/Down buttons")
            }.topGap(TopGap.SMALL)

            row {
                val decorator = ToolbarDecorator.createDecorator(favoriteTable)
                    .disableAddAction()
                    .disableRemoveAction()
                    .setMoveUpAction { moveFavoriteUp() }
                    .setMoveDownAction { moveFavoriteDown() }
                    .setPreferredSize(Dimension(300, 250))

                cell(decorator.createPanel())
                    .align(Align.FILL)
                    .resizableColumn()
            }.resizableRow().topGap(TopGap.SMALL)

            row {
                label(getFavoritesStatusText())
                    .applyToComponent {
                        name = "favoritesStatusLabel"
                        favoritesStatusLabel = this
                    }
            }.topGap(TopGap.SMALL)
        }
    }

    /**
     * Create table model for available models (single column)
     */
    private fun createAvailableTableModel(): ListTableModel<OpenRouterModelInfo> {
        val column = object : ColumnInfo<OpenRouterModelInfo, String>("Model ID") {
            override fun valueOf(item: OpenRouterModelInfo): String = item.id
            override fun getPreferredStringValue(): String = "anthropic/claude-3.5-sonnet-20241022"
        }
        return ListTableModel(arrayOf(column), mutableListOf())
    }

    /**
     * Create table model for favorite models (single column)
     */
    private fun createFavoriteTableModel(): ListTableModel<OpenRouterModelInfo> {
        val column = object : ColumnInfo<OpenRouterModelInfo, String>("Model ID") {
            override fun valueOf(item: OpenRouterModelInfo): String = item.id
            override fun getPreferredStringValue(): String = "anthropic/claude-3.5-sonnet-20241022"
        }
        return ListTableModel(arrayOf(column), mutableListOf())
    }

    /**
     * Open main OpenRouter settings
     */
    private fun openMainSettings() {
        ShowSettingsUtil.getInstance().showSettingsDialog(
            null,
            OpenRouterConfigurable::class.java
        )
    }

    /**
     * Setup table configurations
     */
    private fun setupTables() {
        // Available models table
        availableTable.setShowGrid(false)
        availableTable.setSelectionMode(javax.swing.ListSelectionModel.MULTIPLE_INTERVAL_SELECTION)
        availableTable.rowHeight = JBUI.scale(22)

        // Add speed search
        TableSpeedSearch.installOn(availableTable) { obj, _ ->
            if (obj is OpenRouterModelInfo) obj.id else null
        }

        // Double-click to add
        availableTable.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                if (e.clickCount == 2 && keyPresent) {
                    addSelectedToFavorites()
                }
            }
        })

        // Enter key to add
        availableTable.addKeyListener(object : KeyAdapter() {
            override fun keyPressed(e: KeyEvent) {
                if (e.keyCode == KeyEvent.VK_ENTER && keyPresent) {
                    addSelectedToFavorites()
                    e.consume()
                }
            }
        })

        // Favorite models table
        favoriteTable.setShowGrid(false)
        favoriteTable.setSelectionMode(javax.swing.ListSelectionModel.MULTIPLE_INTERVAL_SELECTION)
        favoriteTable.rowHeight = JBUI.scale(22)

        // Add speed search
        TableSpeedSearch.installOn(favoriteTable) { obj, _ ->
            if (obj is OpenRouterModelInfo) obj.id else null
        }

        // Double-click to remove
        favoriteTable.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                if (e.clickCount == 2 && keyPresent) {
                    removeSelectedFromFavorites()
                }
            }
        })

        // Delete key to remove
        favoriteTable.addKeyListener(object : KeyAdapter() {
            override fun keyPressed(e: KeyEvent) {
                if ((e.keyCode == KeyEvent.VK_DELETE || e.keyCode == KeyEvent.VK_BACK_SPACE) && keyPresent) {
                    removeSelectedFromFavorites()
                    e.consume()
                }
            }
        })
    }

    /**
     * Setup search field with debouncing
     */
    private fun setupSearch() {
        searchField.addDocumentListener(object : DocumentListener {
            override fun insertUpdate(e: DocumentEvent?) = scheduleSearch()
            override fun removeUpdate(e: DocumentEvent?) = scheduleSearch()
            override fun changedUpdate(e: DocumentEvent?) = scheduleSearch()
        })
    }

    /**
     * Schedule a debounced search operation
     */
    private fun scheduleSearch() {
        if (!keyPresent) return

        searchDebounceTimer?.stop()
        searchDebounceTimer = Timer(SEARCH_DEBOUNCE_MS) {
            filterAvailableModels()
        }.apply {
            isRepeats = false
            start()
        }
    }

    /**
     * Load initial data from API and settings
     */
    private fun loadInitialData() {
        if (!keyPresent) return

        PluginLogger.Settings.debug("Starting initial data load...")
        isLoading = true
        loadError = null
        loadingPanel.startLoading()

        // getAvailableModels() already returns a CompletableFuture that executes asynchronously
        favoriteModelsService.getAvailableModels(forceRefresh = false)
            .thenAccept { models ->
                PluginLogger.Settings.debug("Received models from service: ${models?.size ?: 0}")
                ApplicationManager.getApplication().invokeLater({
                    PluginLogger.Settings.debug("EDT callback executing...")
                    isLoading = false
                    loadingPanel.stopLoading()

                    if (models != null) {
                        PluginLogger.Settings.debug("Loading ${models.size} models into UI")
                        allAvailableModels = models
                        PluginLogger.Settings.debug("Set allAvailableModels, now calling filterAvailableModels()")
                        filterAvailableModels()
                        PluginLogger.Settings.debug("After filterAvailableModels(), table has ${availableTableModel.rowCount} rows")
                        loadFavorites()
                        initialFavorites = getCurrentFavoriteIds()
                        PluginLogger.Settings.debug("Initial data load complete")
                    } else {
                        loadError = "Failed to load models from API"
                        showErrorState()
                        PluginLogger.Settings.warn("Models response was null")
                    }
                }, ModalityState.any())
            }
            .exceptionally { throwable ->
                ApplicationManager.getApplication().invokeLater({
                    isLoading = false
                    loadingPanel.stopLoading()
                    loadError = "Error loading models: ${throwable.message}"
                    showErrorState()
                    PluginLogger.Settings.error("Failed to load models", throwable)
                }, ModalityState.any())
                null
            }
    }

    /**
     * Refresh available models from API (bypass cache)
     */
    private fun refreshAvailableModels() {
        if (!keyPresent) return

        isLoading = true
        loadError = null
        loadingPanel.startLoading()

        // getAvailableModels() already returns a CompletableFuture that executes asynchronously
        favoriteModelsService.getAvailableModels(forceRefresh = true)
            .thenAccept { models ->
                ApplicationManager.getApplication().invokeLater({
                    isLoading = false
                    loadingPanel.stopLoading()

                    if (models != null) {
                        allAvailableModels = models
                        filterAvailableModels()
                        updateFavoriteAvailability(models)
                    } else {
                        loadError = "Failed to refresh models"
                        showErrorState()
                    }
                }, ModalityState.any())
            }
            .exceptionally { throwable ->
                ApplicationManager.getApplication().invokeLater({
                    isLoading = false
                    loadingPanel.stopLoading()
                    loadError = "Error refreshing models: ${throwable.message}"
                    PluginLogger.Settings.error("Failed to refresh models", throwable)
                }, ModalityState.any())
                null
            }
    }

    /**
     * Filter available models based on search text
     */
    private fun filterAvailableModels() {
        val searchText = searchField.text.trim()
        filteredAvailableModels = if (searchText.isEmpty()) {
            allAvailableModels
        } else {
            allAvailableModels.filter { model ->
                model.id.contains(searchText, ignoreCase = true) ||
                        model.name.contains(searchText, ignoreCase = true)
            }
        }

        // Exclude already favorited models
        val favoriteIds = getCurrentFavoriteIds().toSet()
        val availableToAdd = filteredAvailableModels.filter { it.id !in favoriteIds }

        PluginLogger.Settings.debug("Filtered models: ${availableToAdd.size} available (from ${allAvailableModels.size} total)")
        availableTableModel.items = availableToAdd

        // Update status label
        updateStatusLabels()
    }

    /**
     * Load favorites from settings
     */
    private fun loadFavorites() {
        val favoriteIds = settingsService.getFavoriteModels()
        val favoriteModels = favoriteIds.mapNotNull { id ->
            allAvailableModels.find { it.id == id }
                ?: OpenRouterModelInfo(id = id, name = id, created = 0L) // Unavailable model
        }
        favoriteTableModel.items = favoriteModels

        // Update status label
        updateStatusLabels()
    }

    /**
     * Update favorite models availability after refresh
     */
    private fun updateFavoriteAvailability(availableModels: List<OpenRouterModelInfo>) {
        val currentFavorites = favoriteTableModel.items
        val updatedFavorites = currentFavorites.map { favorite ->
            availableModels.find { it.id == favorite.id } ?: favorite
        }
        favoriteTableModel.items = updatedFavorites

        // Update status label
        updateStatusLabels()
    }

    /**
     * Add selected models to favorites
     */
    private fun addSelectedToFavorites() {
        if (!keyPresent) return

        val selectedRows = availableTable.selectedRows
        if (selectedRows.isEmpty()) return

        val selectedModels = selectedRows.map { availableTableModel.getItem(it) }
        val currentFavorites = favoriteTableModel.items.toMutableList()
        val currentIds = currentFavorites.map { it.id }.toSet()

        // Add only new models (prevent duplicates)
        val newModels = selectedModels.filter { it.id !in currentIds }
        currentFavorites.addAll(newModels)

        favoriteTableModel.items = currentFavorites
        filterAvailableModels() // Refresh to exclude newly added
    }

    /**
     * Add all filtered models to favorites
     */
    private fun addAllFilteredToFavorites() {
        if (!keyPresent) return

        val currentFavorites = favoriteTableModel.items.toMutableList()
        val currentIds = currentFavorites.map { it.id }.toSet()

        // Add all filtered models that aren't already favorites
        val newModels = filteredAvailableModels.filter { it.id !in currentIds }
        currentFavorites.addAll(newModels)

        favoriteTableModel.items = currentFavorites
        filterAvailableModels() // Refresh to exclude newly added
    }

    /**
     * Remove selected models from favorites
     */
    private fun removeSelectedFromFavorites() {
        if (!keyPresent) return

        val selectedRows = favoriteTable.selectedRows
        if (selectedRows.isEmpty()) return

        val selectedModels = selectedRows.map { favoriteTableModel.getItem(it) }
        val currentFavorites = favoriteTableModel.items.toMutableList()

        currentFavorites.removeAll(selectedModels.toSet())
        favoriteTableModel.items = currentFavorites
        filterAvailableModels() // Refresh to include removed models
    }

    /**
     * Clear all favorites with confirmation
     */
    private fun clearAllFavorites() {
        if (!keyPresent) return

        val result = Messages.showYesNoDialog(
            "Are you sure you want to remove all favorite models?",
            "Clear All Favorites",
            Messages.getQuestionIcon()
        )

        if (result == Messages.YES) {
            favoriteTableModel.items = emptyList()
            filterAvailableModels() // Refresh to include all models
        }
    }

    /**
     * Move selected favorite up
     */
    private fun moveFavoriteUp() {
        if (!keyPresent) return

        val selectedRow = favoriteTable.selectedRow
        if (selectedRow <= 0) return

        val items = favoriteTableModel.items.toMutableList()
        val temp = items[selectedRow]
        items[selectedRow] = items[selectedRow - 1]
        items[selectedRow - 1] = temp

        favoriteTableModel.items = items
        favoriteTable.setRowSelectionInterval(selectedRow - 1, selectedRow - 1)
    }

    /**
     * Move selected favorite down
     */
    private fun moveFavoriteDown() {
        if (!keyPresent) return

        val selectedRow = favoriteTable.selectedRow
        if (selectedRow < 0 || selectedRow >= favoriteTableModel.rowCount - 1) return

        val items = favoriteTableModel.items.toMutableList()
        val temp = items[selectedRow]
        items[selectedRow] = items[selectedRow + 1]
        items[selectedRow + 1] = temp

        favoriteTableModel.items = items
        favoriteTable.setRowSelectionInterval(selectedRow + 1, selectedRow + 1)
    }

    /**
     * Get current favorite model IDs
     */
    private fun getCurrentFavoriteIds(): List<String> {
        return favoriteTableModel.items.map { it.id }
    }

    /**
     * Show error state in UI
     */
    private fun showErrorState() {
        // Keep previous data if available, just show error message
        PluginLogger.Settings.warn("Error state: $loadError")

        // Update status label to show error
        updateStatusLabels()
    }

    /**
     * Get status text for available models
     */
    private fun getAvailableModelsStatusText(): String {
        return when {
            !keyPresent -> "Provisioning key required"
            isLoading -> "Loading models..."
            loadError != null -> "Error: $loadError"
            filteredAvailableModels.isEmpty() && searchField.text.isNotBlank() -> "No models match search"
            filteredAvailableModels.isEmpty() -> "No models available"
            else -> "${filteredAvailableModels.size} models available"
        }
    }

    /**
     * Get status text for favorites
     */
    private fun getFavoritesStatusText(): String {
        return when {
            !keyPresent -> "Provisioning key required"
            favoriteTableModel.rowCount == 0 -> "No favorites yet. Select models on the left and click 'Add'"
            else -> "${favoriteTableModel.rowCount} favorite models"
        }
    }

    /**
     * Update status labels with current state
     */
    private fun updateStatusLabels() {
        availableStatusLabel?.text = getAvailableModelsStatusText()
        favoritesStatusLabel?.text = getFavoritesStatusText()
    }

    /**
     * Check if settings have been modified
     */
    fun isModified(): Boolean {
        if (!keyPresent) return false
        return getCurrentFavoriteIds() != initialFavorites
    }

    /**
     * Apply changes to settings
     */
    fun apply() {
        if (!keyPresent) return

        val favoriteIds = getCurrentFavoriteIds()
        settingsService.setFavoriteModels(favoriteIds)
        initialFavorites = favoriteIds
        PluginLogger.Settings.info("Applied ${favoriteIds.size} favorite models")
    }

    /**
     * Reset to initial state
     */
    fun reset() {
        if (!keyPresent) return

        loadFavorites()
        filterAvailableModels()
    }

    override fun dispose() {
        searchDebounceTimer?.stop()
        searchDebounceTimer = null
    }
}

