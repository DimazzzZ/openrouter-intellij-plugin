package org.zhavoronkov.openrouter.settings

import com.intellij.icons.AllIcons
import com.intellij.ide.BrowserUtil
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.ui.Messages
import com.intellij.ui.AnActionButton
import com.intellij.ui.SearchTextField
import com.intellij.ui.ToolbarDecorator
import com.intellij.ui.components.*
import com.intellij.ui.dsl.builder.*
import com.intellij.ui.table.JBTable
import com.intellij.ui.OnePixelSplitter

import com.intellij.ui.table.TableView
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.ColumnInfo
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import org.zhavoronkov.openrouter.models.ApiKeyInfo
import org.zhavoronkov.openrouter.models.OpenRouterModelInfo
import org.zhavoronkov.openrouter.services.OpenRouterService
import org.zhavoronkov.openrouter.services.OpenRouterSettingsService
import org.zhavoronkov.openrouter.services.OpenRouterProxyService
import org.zhavoronkov.openrouter.utils.PluginLogger
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.awt.event.KeyEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.util.Locale
import java.util.concurrent.TimeUnit
import javax.swing.*
import javax.swing.table.AbstractTableModel
import javax.swing.table.TableRowSorter

/**
 * Table model for API keys
 */
class ApiKeyTableModel : AbstractTableModel() {
    private val columnNames = arrayOf("Label", "Name", "Usage", "Limit", "Status")
    private val apiKeys = mutableListOf<ApiKeyInfo>()

    override fun getRowCount(): Int = apiKeys.size
    override fun getColumnCount(): Int = columnNames.size
    override fun getColumnName(column: Int): String = columnNames[column]

    override fun getValueAt(rowIndex: Int, columnIndex: Int): Any {
        val key = apiKeys[rowIndex]
        return when (columnIndex) {
            0 -> key.label
            1 -> key.name
            2 -> String.format(Locale.US, "$%.6f", key.usage)
            3 -> key.limit?.let { String.format(Locale.US, "$%.2f", it) } ?: "Unlimited"
            4 -> if (key.disabled) "Disabled" else "Enabled"
            else -> ""
        }
    }

    fun setApiKeys(keys: List<ApiKeyInfo>) {
        PluginLogger.Settings.debug("ApiKeysTableModel.setApiKeys called with ${keys.size} keys")

        // Ensure we're on EDT for UI updates
        if (!ApplicationManager.getApplication().isDispatchThread) {
            PluginLogger.Settings.debug("Not on EDT, scheduling setApiKeys on EDT")
            ApplicationManager.getApplication().invokeLater({
                setApiKeys(keys)
            }, com.intellij.openapi.application.ModalityState.defaultModalityState())
            return
        }

        PluginLogger.Settings.debug("On EDT, updating table model")
        apiKeys.clear()
        apiKeys.addAll(keys)
        PluginLogger.Settings.debug("ApiKeysTableModel internal list now has ${apiKeys.size} keys")
        fireTableDataChanged()
        PluginLogger.Settings.debug("fireTableDataChanged() called - table should now show ${apiKeys.size} rows")
        PluginLogger.Settings.debug("Table model getRowCount() returns: ${getRowCount()}")
        PluginLogger.Settings.debug("First key details: ${if (keys.isNotEmpty()) keys[0] else "No keys"}")
    }

    fun getApiKeyAt(rowIndex: Int): ApiKeyInfo? {
        return if (rowIndex >= 0 && rowIndex < apiKeys.size) apiKeys[rowIndex] else null
    }

    fun removeApiKey(rowIndex: Int) {
        if (rowIndex >= 0 && rowIndex < apiKeys.size) {
            apiKeys.removeAt(rowIndex)
            fireTableRowsDeleted(rowIndex, rowIndex)
        }
    }

    fun getApiKeys(): List<ApiKeyInfo> {
        return apiKeys.toList()
    }
}

/**
 * Table model for favorite models
 */
class FavoriteModelsTableModel : AbstractTableModel() {
    private val columnNames = arrayOf("Model ID")
    private val favoriteModels = mutableListOf<OpenRouterModelInfo>()

    override fun getRowCount(): Int = favoriteModels.size
    override fun getColumnCount(): Int = columnNames.size
    override fun getColumnName(column: Int): String = columnNames[column]

    override fun getValueAt(rowIndex: Int, columnIndex: Int): Any {
        val model = favoriteModels[rowIndex]
        return when (columnIndex) {
            0 -> model.id
            else -> ""
        }
    }

    fun setFavoriteModels(models: List<OpenRouterModelInfo>) {
        favoriteModels.clear()
        favoriteModels.addAll(models)
        fireTableDataChanged()
    }

    fun getFavoriteModels(): List<OpenRouterModelInfo> = favoriteModels.toList()

    fun addModel(model: OpenRouterModelInfo) {
        if (!favoriteModels.contains(model)) {
            favoriteModels.add(model)
            fireTableRowsInserted(favoriteModels.size - 1, favoriteModels.size - 1)
        }
    }

    fun removeModel(index: Int) {
        if (index >= 0 && index < favoriteModels.size) {
            favoriteModels.removeAt(index)
            fireTableRowsDeleted(index, index)
        }
    }

    fun moveUp(index: Int) {
        if (index > 0 && index < favoriteModels.size) {
            val model = favoriteModels.removeAt(index)
            favoriteModels.add(index - 1, model)
            fireTableRowsUpdated(index - 1, index)
        }
    }

    fun moveDown(index: Int) {
        if (index >= 0 && index < favoriteModels.size - 1) {
            val model = favoriteModels.removeAt(index)
            favoriteModels.add(index + 1, model)
            fireTableRowsUpdated(index, index + 1)
        }
    }
}

/**
 * Table model for available models
 */
class AvailableModelsTableModel : AbstractTableModel() {
    private val columnNames = arrayOf("Model ID")
    private val availableModels = mutableListOf<OpenRouterModelInfo>()

    override fun getRowCount(): Int = availableModels.size
    override fun getColumnCount(): Int = columnNames.size
    override fun getColumnName(column: Int): String = columnNames[column]

    override fun getValueAt(rowIndex: Int, columnIndex: Int): Any {
        val model = availableModels[rowIndex]
        return when (columnIndex) {
            0 -> model.id
            else -> ""
        }
    }

    fun setAvailableModels(models: List<OpenRouterModelInfo>) {
        availableModels.clear()
        availableModels.addAll(models)
        fireTableDataChanged()
    }

    fun getModelAt(index: Int): OpenRouterModelInfo? {
        return if (index >= 0 && index < availableModels.size) availableModels[index] else null
    }

    fun getAvailableModels(): List<OpenRouterModelInfo> = availableModels.toList()
}

/**
 * Settings panel for OpenRouter configuration using IntelliJ UI DSL v2
 */
class OpenRouterSettingsPanel {

    private val panel: JPanel
    private val provisioningKeyField: JBPasswordField
    private val autoRefreshCheckBox: JBCheckBox
    private val refreshIntervalSpinner: JSpinner
    private val showCostsCheckBox: JBCheckBox
    private val apiKeyTableModel = ApiKeyTableModel()
    private val apiKeyTable = JBTable(apiKeyTableModel)
    private val openRouterService = OpenRouterService.getInstance()
    private val settingsService = OpenRouterSettingsService.getInstance()
    private val proxyService = OpenRouterProxyService.getInstance()

    // Favorite Models components
    private val favoriteModelsTableModel = FavoriteModelsTableModel()
    private val favoriteModelsTable = JBTable(favoriteModelsTableModel)
    private val availableModelsTableModel = AvailableModelsTableModel()
    private val availableModelsTable = JBTable(availableModelsTableModel)
    private val searchTextField = SearchTextField()
    private var allModels: List<OpenRouterModelInfo> = emptyList()

    // API Keys panel
    private val apiKeysPanel = JPanel(BorderLayout())

    // Splitter and panels for favorite models
    // false = horizontal split (side-by-side), true = vertical split (top/bottom)
    private val favoriteModelsSplitter = OnePixelSplitter(false, 0.5f).apply {
        preferredSize = Dimension(700, 400)
    }
    private val favoritesPanel = JPanel(BorderLayout())
    private val availablePanel = JPanel(BorderLayout())
    private lateinit var addToFavoritesButton: JButton
    private lateinit var searchButton: JButton

    // State tracking
    private var isCreatingApiKey = false

    // Helper classes for managing different aspects
    private val apiKeyManager: ApiKeyManager
    private val proxyServerManager: ProxyServerManager

    // Status components for AI Assistant Integration
    private val statusLabel = JBLabel()

    // Action buttons for AI Assistant Integration
    private lateinit var startServerButton: JButton
    private lateinit var stopServerButton: JButton
    private lateinit var copyUrlButton: JButton

    companion object {
        private const val INTELLIJ_API_KEY_NAME = "IntelliJ IDEA Plugin"
        private const val DEFAULT_REFRESH_INTERVAL = 30
        private const val MIN_REFRESH_INTERVAL = 10
        private const val MAX_REFRESH_INTERVAL = 300
        private const val REFRESH_INTERVAL_STEP = 10
        private const val LABEL_COLUMN_WIDTH = 170
    }

    init {
        // Initialize logging configuration
        PluginLogger.logConfiguration()
        PluginLogger.Settings.debug("Initializing OpenRouter Settings Panel")

        // Initialize components
        provisioningKeyField = JBPasswordField().apply {
            columns = 32
        }

        autoRefreshCheckBox = JBCheckBox("Auto-refresh quota information")
        showCostsCheckBox = JBCheckBox("Show costs in status bar")

        refreshIntervalSpinner = JSpinner(SpinnerNumberModel(
            DEFAULT_REFRESH_INTERVAL,
            MIN_REFRESH_INTERVAL,
            MAX_REFRESH_INTERVAL,
            REFRESH_INTERVAL_STEP
        ))

        // Configure API key table
        apiKeyTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION)
        apiKeyTable.autoResizeMode = JTable.AUTO_RESIZE_ALL_COLUMNS
        apiKeyTable.fillsViewportHeight = true

        // Configure favorite models tables
        configureFavoritesTable()
        configureAvailableModelsTable()

        // Setup API Keys panel
        setupApiKeysPanel()

        // Setup splitter and panels
        setupFavoriteModelsSplitter()

        // Configure search field
        searchTextField.addDocumentListener(object : javax.swing.event.DocumentListener {
            override fun insertUpdate(e: javax.swing.event.DocumentEvent?) = filterAvailableModels()
            override fun removeUpdate(e: javax.swing.event.DocumentEvent?) = filterAvailableModels()
            override fun changedUpdate(e: javax.swing.event.DocumentEvent?) = filterAvailableModels()
        })

        // Initialize helper classes
        apiKeyManager = ApiKeyManager(settingsService, openRouterService, apiKeyTable, apiKeyTableModel)
        proxyServerManager = ProxyServerManager(proxyService, settingsService)

        // Create the main panel using UI DSL v2
        panel = panel {
            // Provisioning group
            group("Provisioning") {
                row("Provisioning Key:") {
                    cell(provisioningKeyField)
                        .resizableColumn()
                        .columns(32)
                    button("Paste") { pasteFromClipboard() }
                }.layout(RowLayout.PARENT_GRID)

                row {
                    comment("Required for quota monitoring and API keys management.")
                }

                row {
                    link("Get your key from OpenRouter Provisioning Keys") {
                        BrowserUtil.browse("https://openrouter.ai/settings/provisioning-keys")
                    }
                }
            }

            // General Settings group
            group("General Settings") {
                row("Refresh interval (seconds):") {
                    cell(refreshIntervalSpinner)
                }.layout(RowLayout.PARENT_GRID)

                row {
                    cell(autoRefreshCheckBox)
                    cell(showCostsCheckBox)
                }.layout(RowLayout.PARENT_GRID)
            }

            // API Keys group
            group("API Keys") {
                row {
                    comment("Keys load automatically when Provisioning Key is configured.")
                }

                row {
                    cell(apiKeysPanel)
                        .align(Align.FILL)
                        .resizableColumn()
                }.resizableRow()
            }

            // Favorite Models group
            group("Favorite Models") {
                row {
                    comment("Manage your favorite models that will appear in AI Assistant. Only favorite models are shown to keep the list manageable.")
                }

                row {
                    cell(favoriteModelsSplitter)
                        .align(Align.FILL)
                        .resizableColumn()
                }.resizableRow()
            }

            // AI Assistant Integration group
            group("AI Assistant Integration") {
                row {
                    cell(statusLabel)
                }.layout(RowLayout.PARENT_GRID)

                row {
                    startServerButton = button("Start Proxy Server") { startProxyServer() }.component
                    stopServerButton = button("Stop Proxy Server") { stopProxyServer() }.component
                    copyUrlButton = button("Copy URL") { copyProxyUrl() }.component
                }.layout(RowLayout.PARENT_GRID)

                row {
                    comment("Copy the URL above and paste it as the Base URL in AI Assistant settings: Tools > AI Assistant > Models > Add Model > Custom OpenAI-compatible")
                }
            }
        }

        // Set up listeners
        autoRefreshCheckBox.addActionListener {
            refreshIntervalSpinner.isEnabled = autoRefreshCheckBox.isSelected
        }



        // Set up keyboard shortcuts for favorites table
        setupFavoritesKeyboardShortcuts()

        // Load initial data
        loadFavoriteModels()
        loadAvailableModels()

        // Initialize status
        updateProxyStatus()
    }

    private fun createFavoritesToolbar(): ToolbarDecorator {
        return ToolbarDecorator.createDecorator(favoriteModelsTable)
            .setRemoveAction { removeSelectedFavorites() }
            .setMoveUpAction { moveFavoriteUp() }
            .setMoveDownAction { moveFavoriteDown() }
            .disableAddAction()
    }

    private fun configureFavoritesTable() {
        favoriteModelsTable.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION)
        favoriteModelsTable.autoResizeMode = JTable.AUTO_RESIZE_ALL_COLUMNS
        favoriteModelsTable.fillsViewportHeight = true
        favoriteModelsTable.dragEnabled = true
        favoriteModelsTable.dropMode = DropMode.INSERT_ROWS
        favoriteModelsTable.rowHeight = 28

        // Set accessible name for screen readers
        favoriteModelsTable.accessibleContext.accessibleName = "Favorites Table"
        favoriteModelsTable.accessibleContext.accessibleDescription = "Table showing your favorite models on the right side. Use Alt+Up/Alt+Down to reorder, Delete to remove."

        // Configure column widths: Model ID ~65%, Provider ~35%
        setupFavoritesTableColumns()
    }

    private fun configureAvailableModelsTable() {
        availableModelsTable.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION)
        availableModelsTable.autoResizeMode = JTable.AUTO_RESIZE_ALL_COLUMNS
        availableModelsTable.fillsViewportHeight = true
        availableModelsTable.rowHeight = 28

        // Set accessible name for screen readers
        availableModelsTable.accessibleContext.accessibleName = "Available Models Table"
        availableModelsTable.accessibleContext.accessibleDescription = "Table showing all available models on the left side. Double-click, press Enter, or use Add to Favorites button to add models to your favorites."

        // Enable sorting
        val sorter = TableRowSorter(availableModelsTableModel)
        availableModelsTable.rowSorter = sorter

        // Configure column widths: Model ID ~40%, Name ~40%, Provider ~20%
        setupAvailableModelsTableColumns()

        // Add double-click listener and Enter key support
        availableModelsTable.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                if (e.clickCount == 2) {
                    addSelectedToFavorites()
                }
            }
        })

        // Add Enter key support for adding to favorites
        val inputMap = availableModelsTable.getInputMap(JComponent.WHEN_FOCUSED)
        val actionMap = availableModelsTable.actionMap
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "addToFavorites")
        actionMap.put("addToFavorites", object : AbstractAction() {
            override fun actionPerformed(e: java.awt.event.ActionEvent?) {
                addSelectedToFavorites()
            }
        })
    }

    private fun setupApiKeysPanel() {
        // North: Toolbar with Add/Remove on left and Refresh on right
        val toolbar = createApiKeysToolbarWithRefresh()
        apiKeysPanel.add(toolbar, BorderLayout.NORTH)

        // Center: API Keys table in scroll pane
        val scrollPane = JScrollPane(apiKeyTable)
        apiKeysPanel.add(scrollPane, BorderLayout.CENTER)
    }

    private fun createApiKeysToolbarWithRefresh(): JPanel {
        // Create the main toolbar with Add/Remove
        val decorator = ToolbarDecorator.createDecorator(apiKeyTable)
            .setAddAction { addApiKey() }
            .setRemoveAction { removeSelectedApiKey() }
            .disableUpDownActions()

        val toolbarPanel = decorator.createPanel()

        // Create a wrapper panel to add Refresh button on the right
        val wrapperPanel = JPanel(BorderLayout())
        wrapperPanel.add(toolbarPanel, BorderLayout.CENTER)

        // Add Refresh button on the right
        val refreshButtonPanel = JPanel(BorderLayout())
        val refreshButton = JButton("Refresh")
        refreshButton.addActionListener { refreshApiKeysWithValidation() }
        refreshButton.accessibleContext.accessibleName = "Refresh API Keys"
        refreshButtonPanel.add(refreshButton, BorderLayout.EAST)
        wrapperPanel.add(refreshButtonPanel, BorderLayout.EAST)

        return wrapperPanel
    }

    private fun setupFavoriteModelsSplitter() {
        // Configure splitter - 50/50 split
        favoriteModelsSplitter.setHonorComponentsMinimumSize(true)
        favoriteModelsSplitter.proportion = 0.5f

        // Set minimum sizes for both panels to ensure they're visible
        availablePanel.minimumSize = Dimension(250, 300)
        favoritesPanel.minimumSize = Dimension(250, 300)

        // Set preferred sizes to ensure proper initial layout - equal widths
        availablePanel.preferredSize = Dimension(350, 400)
        favoritesPanel.preferredSize = Dimension(350, 400)

        // Setup left panel (Available models)
        setupAvailableModelsPanel()

        // Setup right panel (Favorites)
        setupFavoritesPanel()

        // Add panels to splitter - Available on left, Favorites on right
        favoriteModelsSplitter.firstComponent = availablePanel
        favoriteModelsSplitter.secondComponent = favoritesPanel

        // Setup tab order: Search field → Available table → Add to Favorites → Favorites table → toolbar buttons
        setupTabOrder()
    }

    private fun setupFavoritesPanel() {
        // North: header with "Favorites" label - fixed height to match Available panel header
        val headerPanel = JPanel(BorderLayout())
        val favoritesLabel = JLabel("Favorites")
        headerPanel.add(favoritesLabel, BorderLayout.WEST)

        // Set fixed preferred height to match the Available panel's header (label + search field height)
        // Search field is typically ~28-30px, so we set header to 32px for consistent alignment
        headerPanel.preferredSize = Dimension(headerPanel.preferredSize.width, 32)
        headerPanel.minimumSize = Dimension(0, 32)
        headerPanel.maximumSize = Dimension(Int.MAX_VALUE, 32)

        favoritesPanel.add(headerPanel, BorderLayout.NORTH)

        // Center: ToolbarDecorator panel (includes table + toolbar)
        // ToolbarDecorator.createPanel() returns a panel with the table and toolbar already combined
        val toolbar = createFavoritesToolbar()
        favoritesPanel.add(toolbar.createPanel(), BorderLayout.CENTER)
    }

    private fun setupAvailableModelsPanel() {
        // North: header with "Available" label, search field, and Search button - fixed height
        val headerPanel = JPanel(BorderLayout())
        val availableLabel = JLabel("Available")
        searchTextField.textEditor.emptyText.text = "Search models"
        searchTextField.accessibleContext.accessibleName = "Search Available Models"
        searchTextField.accessibleContext.accessibleDescription = "Filter available models by Model ID"

        // Create a panel for search field + button
        val searchPanel = JPanel(BorderLayout())
        searchPanel.add(searchTextField, BorderLayout.CENTER)

        searchButton = JButton("Search")
        searchButton.accessibleContext.accessibleName = "Search models"
        searchButton.addActionListener { filterAvailableModels() }
        searchPanel.add(searchButton, BorderLayout.EAST)

        // Prevent Enter key from closing Settings dialog
        searchTextField.textEditor.registerKeyboardAction(
            { filterAvailableModels() },
            KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0),
            JComponent.WHEN_FOCUSED
        )

        headerPanel.add(availableLabel, BorderLayout.WEST)
        headerPanel.add(searchPanel, BorderLayout.EAST)

        // Set fixed preferred height to match Favorites panel header
        headerPanel.preferredSize = Dimension(headerPanel.preferredSize.width, 32)
        headerPanel.minimumSize = Dimension(0, 32)
        headerPanel.maximumSize = Dimension(Int.MAX_VALUE, 32)

        availablePanel.add(headerPanel, BorderLayout.NORTH)

        // Center: table with toolbar-style button at bottom
        // Create a panel that combines the table and the "Add to Favorites" button
        // This ensures both panels have the same structure: header + (table + buttons)
        val tableWithButtonPanel = JPanel(BorderLayout())

        // Table in center
        val scrollPane = JScrollPane(availableModelsTable)
        tableWithButtonPanel.add(scrollPane, BorderLayout.CENTER)

        // "Add to Favorites" button at bottom (matching the toolbar height on Favorites side)
        val buttonPanel = JPanel(BorderLayout())
        addToFavoritesButton = JButton("Add to Favorites")
        addToFavoritesButton.addActionListener { addSelectedToFavorites() }
        addToFavoritesButton.accessibleContext.accessibleName = "Add to Favorites"
        addToFavoritesButton.accessibleContext.accessibleDescription = "Add selected models from the available models table to your favorites"
        addToFavoritesButton.isEnabled = false // Initially disabled
        buttonPanel.add(addToFavoritesButton, BorderLayout.EAST)
        tableWithButtonPanel.add(buttonPanel, BorderLayout.SOUTH)

        availablePanel.add(tableWithButtonPanel, BorderLayout.CENTER)

        // Add selection listener to enable/disable button
        availableModelsTable.selectionModel.addListSelectionListener {
            addToFavoritesButton.isEnabled = availableModelsTable.selectedRowCount > 0
        }

        // Setup search functionality
        searchTextField.addDocumentListener(object : javax.swing.event.DocumentListener {
            override fun insertUpdate(e: javax.swing.event.DocumentEvent?) = filterAvailableModels()
            override fun removeUpdate(e: javax.swing.event.DocumentEvent?) = filterAvailableModels()
            override fun changedUpdate(e: javax.swing.event.DocumentEvent?) = filterAvailableModels()
        })
    }

    private fun setupTabOrder() {
        // Set up proper tab order: Search field → Search button → Available table → Add to Favorites → Favorites table
        // The ToolbarDecorator buttons will be handled automatically
        searchTextField.setNextFocusableComponent(searchButton)
        searchButton.setNextFocusableComponent(availableModelsTable)
        availableModelsTable.setNextFocusableComponent(addToFavoritesButton)
        addToFavoritesButton.setNextFocusableComponent(favoriteModelsTable)
        // Favorites table will naturally tab to its toolbar buttons
    }

    private fun setupFavoritesTableColumns() {
        val columnModel = favoriteModelsTable.columnModel
        if (columnModel.columnCount >= 1) {
            // Single column - Model ID takes full width
            columnModel.getColumn(0).minWidth = 100
        }
    }

    private fun setupAvailableModelsTableColumns() {
        val columnModel = availableModelsTable.columnModel
        if (columnModel.columnCount >= 1) {
            // Single column - Model ID takes full width
            columnModel.getColumn(0).minWidth = 100
        }
    }

    private fun pasteFromClipboard() {
        try {
            val clipboard = Toolkit.getDefaultToolkit().systemClipboard
            val data = clipboard.getData(java.awt.datatransfer.DataFlavor.stringFlavor) as? String
            if (data != null) {
                provisioningKeyField.text = data.trim()
            }
        } catch (e: Exception) {
            PluginLogger.Settings.warn("Failed to paste from clipboard: ${e.message}")
        }
    }

    private fun updateProxyStatus() {
        proxyServerManager.updateProxyStatusLabel(statusLabel)

        // Update button states based on proxy status
        val isRunning = proxyService.getServerStatus().isRunning
        startServerButton.isEnabled = !isRunning
        stopServerButton.isEnabled = isRunning
        copyUrlButton.isEnabled = isRunning

        // Force UI repaint to show changes immediately
        statusLabel.repaint()
        statusLabel.revalidate()
        panel.repaint()
    }

    private fun loadFavoriteModels() {
        val favoriteModelIds = settingsService.getFavoriteModels()
        // Convert model IDs to OpenRouterModelInfo objects
        val favoriteModels = favoriteModelIds.map { modelId ->
            OpenRouterModelInfo(
                id = modelId,
                name = modelId,
                created = System.currentTimeMillis() / 1000,
                description = null,
                architecture = null,
                topProvider = null,
                pricing = null,
                contextLength = null,
                perRequestLimits = null
            )
        }
        favoriteModelsTableModel.setFavoriteModels(favoriteModels)
    }

    private fun loadAvailableModels() {
        openRouterService.getModels().thenAccept { modelsResponse ->
            ApplicationManager.getApplication().invokeLater {
                allModels = modelsResponse?.data ?: emptyList()
                filterAvailableModels()
            }
        }.exceptionally { throwable ->
            PluginLogger.Settings.warn("Failed to load available models: ${throwable.message}")
            null
        }
    }

    private fun filterAvailableModels() {
        val searchText = searchTextField.text.lowercase()
        val filteredModels = if (searchText.isBlank()) {
            allModels
        } else {
            allModels.filter { model ->
                model.id.lowercase().contains(searchText)
            }
        }
        availableModelsTableModel.setAvailableModels(filteredModels)
    }

    private fun addSelectedToFavorites() {
        val selectedRows = availableModelsTable.selectedRows
        val favorites = favoriteModelsTableModel.getFavoriteModels().toMutableList()

        for (row in selectedRows) {
            val model = availableModelsTableModel.getModelAt(row)
            if (model != null && !favorites.any { it.id == model.id }) {
                favoriteModelsTableModel.addModel(model)
                favorites.add(model)
            }
        }

        // Save to settings (convert to model IDs)
        val modelIds = favoriteModelsTableModel.getFavoriteModels().map { it.id }
        settingsService.setFavoriteModels(modelIds)
    }

    private fun removeSelectedFavorites() {
        val selectedRows = favoriteModelsTable.selectedRows.sortedDescending()
        for (row in selectedRows) {
            favoriteModelsTableModel.removeModel(row)
        }

        // Save to settings (convert to model IDs)
        val modelIds = favoriteModelsTableModel.getFavoriteModels().map { it.id }
        settingsService.setFavoriteModels(modelIds)
    }

    private fun moveFavoriteUp() {
        val selectedRow = favoriteModelsTable.selectedRow
        if (selectedRow > 0) {
            favoriteModelsTableModel.moveUp(selectedRow)
            favoriteModelsTable.setRowSelectionInterval(selectedRow - 1, selectedRow - 1)

            // Save to settings (convert to model IDs)
            val modelIds = favoriteModelsTableModel.getFavoriteModels().map { it.id }
            settingsService.setFavoriteModels(modelIds)
        }
    }

    private fun moveFavoriteDown() {
        val selectedRow = favoriteModelsTable.selectedRow
        if (selectedRow >= 0 && selectedRow < favoriteModelsTable.rowCount - 1) {
            favoriteModelsTableModel.moveDown(selectedRow)
            favoriteModelsTable.setRowSelectionInterval(selectedRow + 1, selectedRow + 1)

            // Save to settings (convert to model IDs)
            val modelIds = favoriteModelsTableModel.getFavoriteModels().map { it.id }
            settingsService.setFavoriteModels(modelIds)
        }
    }

    private fun setupFavoritesKeyboardShortcuts() {
        val inputMap = favoriteModelsTable.getInputMap(JComponent.WHEN_FOCUSED)
        val actionMap = favoriteModelsTable.actionMap

        // Alt+Up for move up
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_UP, KeyEvent.ALT_DOWN_MASK), "moveUp")
        actionMap.put("moveUp", object : AbstractAction() {
            override fun actionPerformed(e: java.awt.event.ActionEvent?) {
                moveFavoriteUp()
            }
        })

        // Alt+Down for move down
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, KeyEvent.ALT_DOWN_MASK), "moveDown")
        actionMap.put("moveDown", object : AbstractAction() {
            override fun actionPerformed(e: java.awt.event.ActionEvent?) {
                moveFavoriteDown()
            }
        })

        // Delete for remove
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0), "remove")
        actionMap.put("remove", object : AbstractAction() {
            override fun actionPerformed(e: java.awt.event.ActionEvent?) {
                removeSelectedFavorites()
            }
        })
    }

    private fun startProxyServer() {
        if (!settingsService.isConfigured()) {
            Messages.showErrorDialog(
                "Please configure your Provisioning Key first.",
                "Configuration Required"
            )
            return
        }

        ApplicationManager.getApplication().executeOnPooledThread {
            try {
                val success = proxyService.startServer().get()
                ApplicationManager.getApplication().invokeLater({
                    updateProxyStatus()
                    if (!success) {
                        Messages.showErrorDialog(
                            "Failed to start proxy server. Check logs for details.",
                            "Proxy Start Failed"
                        )
                    }
                }, com.intellij.openapi.application.ModalityState.any())
            } catch (e: Exception) {
                ApplicationManager.getApplication().invokeLater({
                    updateProxyStatus()
                    Messages.showErrorDialog(
                        "Failed to start proxy server: ${e.message}",
                        "Proxy Start Failed"
                    )
                }, com.intellij.openapi.application.ModalityState.any())
            }
        }
    }

    private fun stopProxyServer() {
        ApplicationManager.getApplication().executeOnPooledThread {
            try {
                proxyService.stopServer().get()
                ApplicationManager.getApplication().invokeLater({
                    updateProxyStatus()
                }, com.intellij.openapi.application.ModalityState.any())
            } catch (e: Exception) {
                ApplicationManager.getApplication().invokeLater({
                    updateProxyStatus()
                    Messages.showErrorDialog(
                        "Failed to stop proxy server: ${e.message}",
                        "Proxy Stop Failed"
                    )
                }, com.intellij.openapi.application.ModalityState.any())
            }
        }
    }

    private fun copyProxyUrl() {
        val status = proxyService.getServerStatus()
        if (status.isRunning && status.url != null) {
            try {
                val clipboard = Toolkit.getDefaultToolkit().systemClipboard
                val stringSelection = java.awt.datatransfer.StringSelection(status.url)
                clipboard.setContents(stringSelection, null)

                Messages.showInfoMessage(
                    "Proxy URL copied to clipboard: ${status.url}\n\n" +
                    "Paste this as the Base URL in AI Assistant settings:\n" +
                    "Tools > AI Assistant > Models > Add Model > Custom OpenAI-compatible",
                    "URL Copied"
                )
            } catch (e: Exception) {
                Messages.showErrorDialog(
                    "Failed to copy URL to clipboard: ${e.message}",
                    "Copy Failed"
                )
            }
        } else {
            Messages.showWarningDialog(
                "Proxy server is not running. Please start the proxy server first.",
                "Server Not Running"
            )
        }
    }

    private fun showConfigurationInstructions() {
        val instructions = proxyService.getAIAssistantConfigurationInstructions()
        Messages.showInfoMessage(instructions, "AI Assistant Configuration")
    }

    private fun addApiKey() {
        apiKeyManager.addApiKey()
    }

    private fun removeSelectedApiKey() {
        apiKeyManager.removeApiKey()
    }



    fun refreshApiKeys() {
        // Use loadApiKeysWithoutAutoCreate to avoid triggering validation dialogs
        // Only the manual "Refresh" button should trigger full validation
        apiKeyManager.loadApiKeysWithoutAutoCreate()
    }

    fun refreshApiKeysWithValidation() {
        // This method is for the manual "Refresh" button that should trigger validation
        apiKeyManager.refreshApiKeys()
    }



    // Public interface methods for the configurable

    // TODO: Future version - Providers list
    // /**
    //  * Load providers list from OpenRouter
    //  */
    // private fun loadProviders() {
    //     PluginLogger.Settings.debug("Loading providers list from OpenRouter API")
    //
    //     openRouterService.getProviders().thenAccept { response ->
    //         ApplicationManager.getApplication().invokeLater({
    //             if (response != null && response.data.isNotEmpty()) {
    //                 PluginLogger.Settings.info("Successfully loaded ${response.data.size} providers from OpenRouter")
    //                 PluginLogger.Settings.debug("About to call providersTableModel.setProviders with ${response.data.size} providers")
    //                 providersTableModel.setProviders(response.data)
    //                 PluginLogger.Settings.debug("After setProviders - providers table row count: ${providersTable.rowCount}")
    //                 PluginLogger.Settings.debug("After setProviders - providers model row count: ${providersTableModel.rowCount}")
    //             } else {
    //                 PluginLogger.Settings.warn("Failed to load providers - response was null or empty")
    //                 providersTableModel.setProviders(emptyList())
    //                 Messages.showWarningDialog(
    //                     "Failed to load providers list. Please check your internet connection and try again.",
    //                     "Load Failed"
    //                 )
    //             }
    //         }, com.intellij.openapi.application.ModalityState.any())
    //     }.exceptionally { throwable ->
    //         ApplicationManager.getApplication().invokeLater({
    //             PluginLogger.Settings.error("Exception while loading providers", throwable)
    //             providersTableModel.setProviders(emptyList())
    //             Messages.showErrorDialog(
    //                 "Error loading providers: ${throwable.message}",
    //                 "Load Error"
    //             )
    //         }, com.intellij.openapi.application.ModalityState.any())
    //         null
    //     }
    // }

    fun getPanel(): JPanel = panel

    fun getProvisioningKey(): String = String(provisioningKeyField.password)

    fun setProvisioningKey(provisioningKey: String) {
        provisioningKeyField.text = provisioningKey
        // Load API keys without triggering validation dialogs when setting provisioning key
        if (provisioningKey.isNotBlank()) {
            apiKeyManager.loadApiKeysWithoutAutoCreate()
        }
    }

    // TODO: Future version - Default model selection
    // fun getDefaultModel(): String = defaultModelField.text
    //
    // fun setDefaultModel(model: String) {
    //     defaultModelField.text = model
    // }

    fun isAutoRefreshEnabled(): Boolean = autoRefreshCheckBox.isSelected

    fun setAutoRefresh(enabled: Boolean) {
        autoRefreshCheckBox.isSelected = enabled
        refreshIntervalSpinner.isEnabled = enabled
    }

    fun getRefreshInterval(): Int = refreshIntervalSpinner.value as Int

    fun setRefreshInterval(interval: Int) {
        refreshIntervalSpinner.value = interval
    }

    fun shouldShowCosts(): Boolean = showCostsCheckBox.isSelected

    fun setShowCosts(show: Boolean) {
        showCostsCheckBox.isSelected = show
    }

    fun getFavoriteModels(): List<OpenRouterModelInfo> = favoriteModelsTableModel.getFavoriteModels()

    fun setFavoriteModels(models: List<OpenRouterModelInfo>) {
        favoriteModelsTableModel.setFavoriteModels(models)
    }

    fun isFavoriteModelsModified(): Boolean {
        val currentFavoriteIds = favoriteModelsTableModel.getFavoriteModels().map { it.id }
        val savedFavoriteIds = settingsService.getFavoriteModels()
        return currentFavoriteIds != savedFavoriteIds
    }

}



