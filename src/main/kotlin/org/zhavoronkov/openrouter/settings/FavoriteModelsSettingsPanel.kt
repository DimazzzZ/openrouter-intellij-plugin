package org.zhavoronkov.openrouter.settings

import com.intellij.icons.AllIcons
import com.intellij.ide.DataManager
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.ActionToolbar
import com.intellij.openapi.actionSystem.CommonShortcuts
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.Separator
import com.intellij.openapi.actionSystem.toolbarLayout.ToolbarLayoutStrategy
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.ui.RowsDnDSupport
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.SearchTextField
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.TableSpeedSearch
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBLoadingPanel
import com.intellij.ui.dsl.builder.Align
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.RightGap
import com.intellij.ui.dsl.builder.TopGap
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.table.TableView
import com.intellij.util.ui.JBDimension
import com.intellij.util.ui.JBUI
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.zhavoronkov.openrouter.models.OpenRouterModelInfo
import org.zhavoronkov.openrouter.services.FavoriteModelsService
import org.zhavoronkov.openrouter.services.OpenRouterSettingsService
import org.zhavoronkov.openrouter.services.settings.FavoriteModelsManager
import org.zhavoronkov.openrouter.settings.favorites.CapabilitiesFilterAction
import org.zhavoronkov.openrouter.settings.favorites.ChoiceFilterAction
import org.zhavoronkov.openrouter.settings.favorites.ClearFiltersAction
import org.zhavoronkov.openrouter.settings.favorites.FavoriteModelsPageState
import org.zhavoronkov.openrouter.settings.favorites.FavoriteModelsPageState.EmptyState
import org.zhavoronkov.openrouter.settings.favorites.FavoriteModelsPageState.Mode
import org.zhavoronkov.openrouter.settings.favorites.FavoriteModelsTableColumns
import org.zhavoronkov.openrouter.settings.favorites.FavoriteModelsTableModel
import org.zhavoronkov.openrouter.settings.favorites.FavoritesOnlyToggleAction
import org.zhavoronkov.openrouter.settings.favorites.MoveFavoriteAction
import org.zhavoronkov.openrouter.settings.favorites.PresetsAction
import org.zhavoronkov.openrouter.settings.favorites.RefreshCatalogAction
import org.zhavoronkov.openrouter.settings.favorites.VariantLegend
import org.zhavoronkov.openrouter.utils.PluginLogger
import java.awt.BorderLayout
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.ListSelectionModel
import javax.swing.Timer
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener

/**
 * Favorite Models settings page: one catalog table with a favorite checkbox column.
 *
 * This class is a thin Swing adapter. All page logic lives in
 * [FavoriteModelsPageState]; the panel renders from it and forwards user
 * actions to it. Ticking a row appends it to the ordered favorites list,
 * "Favorites only" shows that list in stored order and enables reordering,
 * and the stored order is what AI Assistant displays.
 */
class FavoriteModelsSettingsPanel(
    private val favoriteModelsManager: FavoriteModelsManager =
        OpenRouterSettingsService.getInstance().favoriteModelsManager,
    private val isConfigured: () -> Boolean = { OpenRouterSettingsService.getInstance().isConfigured() },
    private val favoriteModelsServiceProvider: () -> FavoriteModelsService = { FavoriteModelsService.getInstance() },
    private val state: FavoriteModelsPageState = FavoriteModelsPageState(),
    private val autoLoad: Boolean = true,
) : Disposable {

    private companion object {
        const val SEARCH_DEBOUNCE_MS = 300
        const val PRESET_FEEDBACK_MS = 4000
        const val TABLE_ROW_HEIGHT = 20
        const val TABLE_PREFERRED_WIDTH = 640
        const val TABLE_PREFERRED_HEIGHT = 400
        const val TABLE_MIN_HEIGHT = 160
        const val PANEL_BORDER = 10
        const val MISSING_KEY_MESSAGE =
            "To manage favorite models, add your Provisioning Key in Tools → OpenRouter → Settings."
        const val PAGE_COMMENT =
            "Only favorite models are shown in AI Assistant. Their order here is the order there."
    }

    private val keyPresent = isConfigured()
    private val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val loadingPanel = JBLoadingPanel(BorderLayout(), this)
    private val searchField = SearchTextField()
    private val tableModel = FavoriteModelsTableModel(FavoriteModelsTableColumns(state), state)
    private val table = TableView(tableModel)
    private val statusLabel = JBLabel().apply { name = "favoritesStatusLabel" }
    private val toolbar: ActionToolbar by lazy { createToolbar() }
    private val modelsDataManager: ModelsDataManager by lazy {
        ModelsDataManager(favoriteModelsServiceProvider(), coroutineScope, ::onCatalogLoaded, ::onLoadError)
    }

    private var searchDebounceTimer: Timer? = null
    private var presetFeedbackTimer: Timer? = null
    private var presetFeedback: String? = null
    private var renderedMode: Mode? = null

    fun createPanel(): JPanel {
        state.reset(favoriteModelsManager.getFavoriteModels())
        state.onChanged = ::render
        setupTable()
        setupSearch()

        val content = panel {
            if (!keyPresent) {
                row {
                    icon(AllIcons.General.Warning).gap(RightGap.SMALL)
                    label(MISSING_KEY_MESSAGE)
                    button("Open Settings") { openMainSettings() }
                }.topGap(TopGap.NONE)
            }
            // The group keeps its own grid, so it needs resizableRow() as well:
            // without it the group claims only its preferred height and the
            // table row's resizableRow() has no spare space to hand out.
            group("Favorite Models") {
                row {
                    comment(PAGE_COMMENT)
                    cell(VariantLegend.createLabel())
                }.topGap(TopGap.NONE).visible(keyPresent)
                row {
                    cell(searchField).align(AlignX.FILL).resizableColumn()
                }.visible(keyPresent)
                row {
                    cell(toolbar.component).align(AlignX.FILL)
                }.topGap(TopGap.NONE).visible(keyPresent)
                row {
                    cell(tableScrollPane()).align(Align.FILL).resizableColumn()
                }.resizableRow().topGap(TopGap.NONE).visible(keyPresent)
                row {
                    cell(statusLabel)
                }.topGap(TopGap.SMALL).visible(keyPresent)
            }.resizableRow()
        }
        content.border = JBUI.Borders.empty(PANEL_BORDER)
        loadingPanel.add(content, BorderLayout.CENTER)

        if (keyPresent) {
            render()
            if (autoLoad) loadCatalog()
        }
        return loadingPanel
    }

    /** The table is the page's focus: give it a tall baseline and let it absorb spare height. */
    private fun tableScrollPane(): JComponent =
        ScrollPaneFactory.createScrollPane(table).apply {
            preferredSize = JBDimension(TABLE_PREFERRED_WIDTH, TABLE_PREFERRED_HEIGHT)
            minimumSize = JBDimension(TABLE_PREFERRED_WIDTH, TABLE_MIN_HEIGHT)
        }

    // --- construction ----------------------------------------------------------------------

    private fun setupTable() {
        table.setShowGrid(false)
        table.rowHeight = JBUI.scale(TABLE_ROW_HEIGHT)
        table.fillsViewportHeight = true
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION)
        table.putClientProperty("terminateEditOnFocusLost", true)
        TableSpeedSearch.installOn(table) { value, _ -> (value as? OpenRouterModelInfo)?.id }
        RowsDnDSupport.install(table, tableModel)
        table.dragEnabled = false
        table.addKeyListener(object : KeyAdapter() {
            override fun keyPressed(e: KeyEvent) {
                if (e.keyCode == KeyEvent.VK_SPACE) {
                    table.selectedObject?.let { state.toggleFavorite(it.id) }
                    e.consume()
                }
            }
        })
    }

    private fun setupSearch() {
        searchField.textEditor.emptyText.text = "Search models by id or name"
        searchField.addDocumentListener(object : DocumentListener {
            override fun insertUpdate(e: DocumentEvent?) = scheduleSearch()
            override fun removeUpdate(e: DocumentEvent?) = scheduleSearch()
            override fun changedUpdate(e: DocumentEvent?) = scheduleSearch()
        })
        // Consume Enter so the Settings dialog does not close; apply the search immediately.
        searchField.textEditor.addKeyListener(object : KeyAdapter() {
            override fun keyPressed(e: KeyEvent) {
                if (e.keyCode == KeyEvent.VK_ENTER) {
                    e.consume()
                    searchDebounceTimer?.stop()
                    applySearchText()
                }
            }
        })
    }

    private fun createToolbar(): ActionToolbar {
        val group = DefaultActionGroup(
            FavoritesOnlyToggleAction(state),
            Separator.getInstance(),
            ChoiceFilterAction.provider(state),
            ChoiceFilterAction.context(state),
            CapabilitiesFilterAction(state),
            ChoiceFilterAction.variant(state),
            ClearFiltersAction(state, ::clearFilters),
            Separator.getInstance(),
            PresetsAction(::applyPreset),
            RefreshCatalogAction({ modelsDataManager.isCurrentlyLoading() }, ::refreshCatalog),
            Separator.getInstance(),
            moveAction(up = true),
            moveAction(up = false),
        )
        val toolbar = ActionManager.getInstance().createActionToolbar(ActionPlaces.TOOLBAR, group, true)
        toolbar.targetComponent = table
        toolbar.setLayoutStrategy(ToolbarLayoutStrategy.AUTOLAYOUT_STRATEGY)
        return toolbar
    }

    private fun moveAction(up: Boolean): MoveFavoriteAction {
        val action = MoveFavoriteAction(state, up, { table.selectedRow }) { newRow ->
            table.setRowSelectionInterval(newRow, newRow)
        }
        action.registerCustomShortcutSet(if (up) CommonShortcuts.MOVE_UP else CommonShortcuts.MOVE_DOWN, table)
        return action
    }

    // --- rendering -------------------------------------------------------------------------

    private fun render() {
        if (table.isEditing) table.cellEditor?.stopCellEditing()
        val selectedId = table.selectedObject?.id
        val modeChanged = renderedMode != state.mode

        tableModel.isSortable = state.mode == Mode.CATALOG
        tableModel.items = state.visibleRows()
        if (modeChanged) {
            // JBTable.setModel re-evaluates the row sorter from isSortable.
            table.setModelAndUpdateColumns(tableModel)
            table.dragEnabled = state.canReorder()
            renderedMode = state.mode
        }
        restoreSelection(selectedId)

        searchField.isEnabled = state.mode == Mode.CATALOG
        refreshEmptyText()
        statusLabel.text = statusText()
        toolbar.updateActionsAsync()
    }

    private fun restoreSelection(selectedId: String?) {
        val modelRow = tableModel.items.indexOfFirst { it.id == selectedId }
        if (modelRow < 0) {
            table.clearSelection()
            return
        }
        val viewRow = table.convertRowIndexToView(modelRow)
        table.setRowSelectionInterval(viewRow, viewRow)
    }

    private fun refreshEmptyText() {
        val emptyText = table.emptyText.clear()
        val link = SimpleTextAttributes.LINK_PLAIN_ATTRIBUTES
        when (state.emptyState()) {
            EmptyState.NONE -> Unit
            EmptyState.NO_CATALOG -> emptyText.appendText("Loading models…")
            EmptyState.LOAD_FAILED -> emptyText.appendText("Failed to load models.")
                .appendSecondaryText("Retry", link) { refreshCatalog() }
            EmptyState.NO_MATCHES -> emptyText.appendText("No models match the filters.")
                .appendSecondaryText("Clear filters", link) { clearFilters() }
            EmptyState.NO_FAVORITES -> emptyText.appendText("No favorite models added.")
                .appendSecondaryText("Add from presets…", link) { showPresetsPopup() }
        }
    }

    private fun statusText(): String {
        val base = state.statusText()
        return presetFeedback?.let { "$base · $it" } ?: base
    }

    // --- user actions ----------------------------------------------------------------------

    private fun scheduleSearch() {
        if (!keyPresent) return
        searchDebounceTimer?.stop()
        searchDebounceTimer = Timer(SEARCH_DEBOUNCE_MS) { applySearchText() }.apply {
            isRepeats = false
            start()
        }
    }

    private fun applySearchText() {
        state.criteria = state.criteria.copy(searchText = searchField.text.trim())
    }

    private fun clearFilters() {
        searchDebounceTimer?.stop()
        searchField.text = ""
        state.clearFilters()
    }

    private fun applyPreset(name: String) {
        val result = state.applyPreset(name)
        val details = buildList {
            if (result.alreadyPresent > 0) add("${result.alreadyPresent} already favorites")
            if (result.notInCatalog > 0) add("${result.notInCatalog} not in catalog")
        }
        val suffix = if (details.isEmpty()) "" else " (${details.joinToString(", ")})"
        showPresetFeedback("Added ${result.added} from $name$suffix")
    }

    private fun showPresetFeedback(message: String) {
        presetFeedback = message
        statusLabel.text = statusText()
        presetFeedbackTimer?.stop()
        presetFeedbackTimer = Timer(PRESET_FEEDBACK_MS) {
            presetFeedback = null
            statusLabel.text = statusText()
        }.apply {
            isRepeats = false
            start()
        }
    }

    private fun showPresetsPopup() {
        JBPopupFactory.getInstance()
            .createActionGroupPopup(
                "Add from Preset",
                PresetsAction.presetsGroup(::applyPreset),
                DataManager.getInstance().getDataContext(table),
                JBPopupFactory.ActionSelectionAid.SPEEDSEARCH,
                false,
            )
            .showInCenterOf(table)
    }

    private fun openMainSettings() {
        ShowSettingsUtil.getInstance().showSettingsDialog(null, OpenRouterConfigurable::class.java)
    }

    // --- catalog loading -------------------------------------------------------------------

    private fun loadCatalog() {
        loadingPanel.startLoading()
        modelsDataManager.loadInitialData()
    }

    private fun refreshCatalog() {
        if (!keyPresent) return
        loadingPanel.startLoading()
        modelsDataManager.refreshAvailableModels { models -> onCatalogLoaded(models) }
    }

    /** Catalog callback; also the seam platform tests use to feed a fixture catalog. */
    internal fun onCatalogLoaded(models: List<OpenRouterModelInfo>?) {
        loadingPanel.stopLoading()
        if (models == null) {
            showLoadError("Failed to load models from API")
            return
        }
        PluginLogger.Settings.debug("Loaded ${models.size} models into the favorites catalog")
        state.setCatalog(models)
    }

    private fun onLoadError(message: String, throwable: Throwable) {
        loadingPanel.stopLoading()
        PluginLogger.Settings.error(message, throwable)
        showLoadError(message)
    }

    private fun showLoadError(message: String) {
        state.loadError = message
        render()
    }

    // --- Configurable contract -------------------------------------------------------------

    fun isModified(): Boolean = keyPresent && state.isModified()

    fun apply() {
        if (!keyPresent) return
        val favorites = state.markApplied()
        favoriteModelsManager.setFavoriteModels(favorites)
        PluginLogger.Settings.info("Applied ${favorites.size} favorite models")
    }

    fun reset() {
        if (!keyPresent) return
        state.reset(favoriteModelsManager.getFavoriteModels())
    }

    override fun dispose() {
        searchDebounceTimer?.stop()
        presetFeedbackTimer?.stop()
        coroutineScope.cancel()
    }
}
