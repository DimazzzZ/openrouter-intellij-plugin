
package org.zhavoronkov.openrouter.settings

import com.intellij.icons.AllIcons
import com.intellij.openapi.Disposable
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.ui.SearchTextField
import com.intellij.ui.TableSpeedSearch
import com.intellij.ui.ToolbarDecorator
import com.intellij.ui.components.JBLoadingPanel
import com.intellij.ui.dsl.builder.Align
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.AlignY
import com.intellij.ui.dsl.builder.RightGap
import com.intellij.ui.dsl.builder.RowLayout
import com.intellij.ui.dsl.builder.TopGap
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.table.TableView
import com.intellij.util.ui.ColumnInfo
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.ListTableModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.zhavoronkov.openrouter.models.OpenRouterModelInfo
import org.zhavoronkov.openrouter.services.FavoriteModelsService
import org.zhavoronkov.openrouter.services.OpenRouterSettingsService
import org.zhavoronkov.openrouter.utils.ModelProviderUtils
import org.zhavoronkov.openrouter.utils.PluginLogger
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.JCheckBox
import javax.swing.JComboBox
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
        private const val TABLE_PREFERRED_WIDTH = 300
        private const val TABLE_PREFERRED_HEIGHT = 250
        private const val TABLE_ROW_HEIGHT = 22
        private const val CAPABILITIES_COLUMN_WIDTH = 100
    }

    private val settingsService = OpenRouterSettingsService.getInstance()
    private val favoriteModelsService = FavoriteModelsService.getInstance()
    private val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private var keyPresent: Boolean = false
    private val availableTableModel = createAvailableTableModel()
    private val favoriteTableModel = createFavoriteTableModel()
    private val availableTable = TableView(availableTableModel)
    private val favoriteTable = TableView(favoriteTableModel)
    private val searchField = SearchTextField()
    private var searchDebounceTimer: Timer? = null
    private val loadingPanel = JBLoadingPanel(BorderLayout(), this)

    private val favoriteTableManager = FavoriteModelsTableManager(
        favoriteTableModel,
        favoriteTable
    ) {
        filterAvailableModels()
        updateStatusLabels()
    }

    private lateinit var modelsDataManager: ModelsDataManager
    private lateinit var modelsFilterManager: ModelsFilterManager

    private lateinit var providerComboBox: JComboBox<String>
    private lateinit var contextComboBox: JComboBox<String>
    private lateinit var visionCheckBox: JCheckBox
    private lateinit var audioCheckBox: JCheckBox
    private lateinit var toolsCheckBox: JCheckBox
    private lateinit var imageGenCheckBox: JCheckBox

    private var isLoading = false
    private var loadError: String? = null
    private var allAvailableModels: List<OpenRouterModelInfo> = emptyList()
    private var filteredAvailableModels: List<OpenRouterModelInfo> = emptyList()
    private var initialFavorites: List<String> = emptyList()
    private var availableStatusLabel: javax.swing.JLabel? = null
    private var favoritesStatusLabel: javax.swing.JLabel? = null

    init {
        checkProvisioningKey()
        setupTables()
        setupSearch()
        // Note: loadInitialData() is called in initializeManagers() after managers are created
        showGotItTooltips()
    }

    /**
     * Show GotIt tooltips for first-time users
     * Note: Tooltips are disabled for now to avoid memory leak issues
     */
    private fun showGotItTooltips() {
        // GotIt tooltips are disabled for now to avoid memory leak issues
        // They will be re-enabled in a future version with proper disposable management
        // See: https://jetbrains.org/intellij/sdk/docs/basics/disposers.html
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

                // Filters section
                row {
                    cell(createFiltersPanel())
                        .align(Align.FILL)
                        .resizableColumn()
                }.layout(RowLayout.PARENT_GRID).visible(keyPresent)

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

        // Initialize managers after UI components are created
        if (keyPresent) {
            initializeManagers()
            // Load initial data after managers are initialized
            loadInitialData()
        }

        return loadingPanel
    }

    private fun initializeManagers() {
        modelsDataManager = ModelsDataManager(
            favoriteModelsService = favoriteModelsService,
            coroutineScope = coroutineScope,
            onModelsLoaded = ::handleModelsLoaded,
            onLoadError = ::handleLoadError
        )

        val filterComponents = FilterComponents(
            searchField = searchField,
            providerComboBox = providerComboBox,
            contextComboBox = contextComboBox,
            visionCheckBox = visionCheckBox,
            audioCheckBox = audioCheckBox,
            toolsCheckBox = toolsCheckBox,
            imageGenCheckBox = imageGenCheckBox
        )

        modelsFilterManager = ModelsFilterManager(
            filterComponents = filterComponents,
            availableTableModel = availableTableModel,
            getCurrentFavoriteIds = ::getCurrentFavoriteIds
        )
    }

    /**
     * Create the filters panel
     */

    private fun createFiltersPanel(): JPanel {
        return panel {
            row {
                label("Filters:")
                    .bold()
            }.topGap(TopGap.NONE)

            row("Provider:") {
                providerComboBox = comboBox(listOf("All Providers"))
                    .applyToComponent {
                        addActionListener { onFilterChanged() }
                    }
                    .component

                label("Context:")
                    .gap(RightGap.SMALL)

                contextComboBox = comboBox(
                    ModelProviderUtils.ContextRange.entries.map { it.displayName }
                )
                    .applyToComponent {
                        addActionListener { onFilterChanged() }
                    }
                    .component
            }.layout(RowLayout.PARENT_GRID).topGap(TopGap.SMALL)

            row("Capabilities:") {
                visionCheckBox = checkBox("Vision")
                    .applyToComponent { addActionListener { onFilterChanged() } }
                    .component
                audioCheckBox = checkBox("Audio")
                    .applyToComponent { addActionListener { onFilterChanged() } }
                    .component
                toolsCheckBox = checkBox("Tools")
                    .applyToComponent { addActionListener { onFilterChanged() } }
                    .component
                imageGenCheckBox = checkBox("Image Gen")
                    .applyToComponent { addActionListener { onFilterChanged() } }
                    .component
            }.layout(RowLayout.PARENT_GRID).topGap(TopGap.SMALL)

            row("Quick Add:") {
                button("Popular") { addPresetToFavorites(ModelPresets.POPULAR_MODELS) }
                    .applyToComponent { toolTipText = "Add popular models for coding and general tasks" }
                button("OpenAI") { addPresetToFavorites(ModelPresets.OPENAI_MODELS) }
                    .applyToComponent { toolTipText = "Add all OpenAI GPT models" }
                button("Anthropic") { addPresetToFavorites(ModelPresets.ANTHROPIC_MODELS) }
                    .applyToComponent { toolTipText = "Add all Anthropic Claude models" }
                button("Google") { addPresetToFavorites(ModelPresets.GOOGLE_MODELS) }
                    .applyToComponent { toolTipText = "Add all Google Gemini models" }
                button("Cost-Effective") { addPresetToFavorites(ModelPresets.COST_EFFECTIVE_MODELS) }
                    .applyToComponent { toolTipText = "Add cost-effective models" }
            }.layout(RowLayout.PARENT_GRID).topGap(TopGap.SMALL)

            row {
                button("Clear Filters") {
                    clearFilters()
                }
            }.topGap(TopGap.SMALL)
        }
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
                    .setPreferredSize(Dimension(TABLE_PREFERRED_WIDTH, TABLE_PREFERRED_HEIGHT))

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
                    .setPreferredSize(Dimension(TABLE_PREFERRED_WIDTH, TABLE_PREFERRED_HEIGHT))

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
     * Create table model for available models with capabilities column
     */
    private fun createAvailableTableModel(): ListTableModel<OpenRouterModelInfo> {
        val modelColumn = object : ColumnInfo<OpenRouterModelInfo, String>("Model ID") {
            override fun valueOf(item: OpenRouterModelInfo): String = item.id
            override fun getPreferredStringValue(): String = "anthropic/claude-3.5-sonnet-20241022"
        }
        val capabilitiesColumn = object : ColumnInfo<OpenRouterModelInfo, String>("Capabilities") {
            override fun valueOf(item: OpenRouterModelInfo): String =
                ModelProviderUtils.getCapabilitiesString(item)
            override fun getPreferredStringValue(): String = "Vision, Audio"
            override fun getWidth(table: javax.swing.JTable?): Int = JBUI.scale(CAPABILITIES_COLUMN_WIDTH)
        }
        return ListTableModel(arrayOf(modelColumn, capabilitiesColumn), mutableListOf())
    }

    /**
     * Create table model for favorite models with capabilities column
     */
    private fun createFavoriteTableModel(): ListTableModel<OpenRouterModelInfo> {
        val modelColumn = object : ColumnInfo<OpenRouterModelInfo, String>("Model ID") {
            override fun valueOf(item: OpenRouterModelInfo): String = item.id
            override fun getPreferredStringValue(): String = "anthropic/claude-3.5-sonnet-20241022"
        }
        val capabilitiesColumn = object : ColumnInfo<OpenRouterModelInfo, String>("Capabilities") {
            override fun valueOf(item: OpenRouterModelInfo): String =
                ModelProviderUtils.getCapabilitiesString(item)
            override fun getPreferredStringValue(): String = "Vision, Audio"
            override fun getWidth(table: javax.swing.JTable?): Int = JBUI.scale(CAPABILITIES_COLUMN_WIDTH)
        }
        return ListTableModel(arrayOf(modelColumn, capabilitiesColumn), mutableListOf())
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
        availableTable.rowHeight = JBUI.scale(TABLE_ROW_HEIGHT)

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
        favoriteTable.rowHeight = JBUI.scale(TABLE_ROW_HEIGHT)

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

        // Add KeyListener to handle Enter key and prevent dialog from closing
        searchField.textEditor.addKeyListener(object : KeyAdapter() {
            override fun keyPressed(e: KeyEvent?) {
                if (e?.keyCode == KeyEvent.VK_ENTER) {
                    // Consume the Enter key event to prevent dialog from closing
                    e.consume()
                    // Immediately trigger search without debouncing on Enter
                    if (keyPresent) {
                        filterAvailableModels()
                    }
                }
            }
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
        loadError = null
        loadingPanel.startLoading()

        modelsDataManager.loadInitialData()
    }

    private fun handleModelsLoaded(models: List<OpenRouterModelInfo>?) {
        PluginLogger.Settings.debug("EDT callback executing...")
        loadingPanel.stopLoading()

        if (models != null) {
            PluginLogger.Settings.debug("Loading ${models.size} models into UI")
            allAvailableModels = models
            modelsFilterManager.setAllModels(models)
            modelsFilterManager.updateProviderDropdown()
            PluginLogger.Settings.debug("Set allAvailableModels, now calling filterAvailableModels()")
            modelsFilterManager.filterModels()
            PluginLogger.Settings.debug(
                "After filterAvailableModels(), table has ${availableTableModel.rowCount} rows"
            )
            loadFavorites()
            initialFavorites = getCurrentFavoriteIds()
            PluginLogger.Settings.debug("Initial data load complete")
        } else {
            loadError = "Failed to load models from API"
            showErrorState()
            PluginLogger.Settings.warn("Models response was null")
        }
    }

    private fun handleLoadError(message: String, throwable: Throwable) {
        loadingPanel.stopLoading()
        loadError = message
        showErrorState()
        PluginLogger.Settings.error(message, throwable)
    }

    /**
     * Refresh available models from API (bypass cache)
     */
    private fun refreshAvailableModels() {
        if (!keyPresent) return

        loadError = null
        loadingPanel.startLoading()

        modelsDataManager.refreshAvailableModels { models ->
            loadingPanel.stopLoading()

            if (models != null) {
                allAvailableModels = models
                modelsFilterManager.setAllModels(models)
                modelsFilterManager.updateProviderDropdown()
                modelsFilterManager.filterModels()
                updateFavoriteAvailability(models)
            } else {
                loadError = "Failed to refresh models"
                showErrorState()
            }
        }
    }

    /**
     * Filter available models based on all filter criteria
     */
    private fun filterAvailableModels() {
        modelsFilterManager.filterModels()
        filteredAvailableModels = modelsFilterManager.getFilteredModels()
        updateStatusLabels()
    }

    /**
     * Handle filter changes
     */
    private fun onFilterChanged() {
        modelsFilterManager.onFilterChanged()
        filteredAvailableModels = modelsFilterManager.getFilteredModels()
        updateStatusLabels()
    }

    /**
     * Clear all filters
     */
    private fun clearFilters() {
        modelsFilterManager.clearFilters()
        filteredAvailableModels = modelsFilterManager.getFilteredModels()
        updateStatusLabels()
    }

    /**
     * Add preset models to favorites
     */
    private fun addPresetToFavorites(presetModelIds: List<String>) {
        if (!keyPresent) return
        favoriteTableManager.addPresetModels(presetModelIds, allAvailableModels)
    }

    /**
     * Update provider dropdown with unique providers from available models
     */
    private fun updateProviderDropdown() {
        modelsFilterManager.updateProviderDropdown()
    }

    /**
     * Load favorites from settings
     */
    private fun loadFavorites() {
        val favoriteIds = settingsService.favoriteModelsManager.getFavoriteModels()
        val favoriteModels = favoriteIds.map { id ->
            allAvailableModels.find { it.id == id }
                ?: OpenRouterModelInfo(id = id, name = id, created = 0L) // Unavailable model
        }
        favoriteTableManager.setItems(favoriteModels)
        updateStatusLabels()
    }

    /**
     * Update favorite models availability after refresh
     */
    private fun updateFavoriteAvailability(availableModels: List<OpenRouterModelInfo>) {
        favoriteTableManager.updateAvailability(availableModels)
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
        favoriteTableManager.addModels(selectedModels)
    }

    /**
     * Add all filtered models to favorites
     */
    private fun addAllFilteredToFavorites() {
        if (!keyPresent) return
        favoriteTableManager.addModels(filteredAvailableModels)
    }

    /**
     * Remove selected models from favorites
     */
    private fun removeSelectedFromFavorites() {
        if (!keyPresent) return
        favoriteTableManager.removeSelectedModels()
    }

    /**
     * Clear all favorites with confirmation
     */
    private fun clearAllFavorites() {
        if (!keyPresent) return
        favoriteTableManager.clearAll()
    }

    /**
     * Move selected favorite up
     */
    private fun moveFavoriteUp() {
        if (!keyPresent) return
        favoriteTableManager.moveUp()
    }

    /**
     * Move selected favorite down
     */
    private fun moveFavoriteDown() {
        if (!keyPresent) return
        favoriteTableManager.moveDown()
    }

    /**
     * Get current favorite model IDs
     */
    private fun getCurrentFavoriteIds(): List<String> {
        return favoriteTableManager.getCurrentIds()
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
        settingsService.favoriteModelsManager.setFavoriteModels(favoriteIds)
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
