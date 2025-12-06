package org.zhavoronkov.openrouter.ui

import com.intellij.icons.AllIcons
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.JBColor
import com.intellij.ui.SearchTextField
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPasswordField
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.dsl.builder.*
import com.intellij.ui.table.JBTable
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import org.zhavoronkov.openrouter.models.ApiResult
import org.zhavoronkov.openrouter.models.OpenRouterModelInfo
import org.zhavoronkov.openrouter.proxy.OpenRouterProxyServer
import org.zhavoronkov.openrouter.services.FavoriteModelsService
import org.zhavoronkov.openrouter.services.OpenRouterProxyService
import org.zhavoronkov.openrouter.services.OpenRouterService
import org.zhavoronkov.openrouter.services.OpenRouterSettingsService
import org.zhavoronkov.openrouter.utils.EncryptionUtil
import org.zhavoronkov.openrouter.utils.PluginLogger
import java.awt.BorderLayout
import java.awt.CardLayout
import java.awt.Dimension
import java.awt.Font
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.SwingUtilities
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener
import javax.swing.table.AbstractTableModel

/**
 * Multi-step setup wizard for first-time users with validation and embedded model selection
 */
class SetupWizardDialog(private val project: Project?) : DialogWrapper(project) {

    private val settingsService = OpenRouterSettingsService.getInstance()
    private val openRouterService = OpenRouterService.getInstance()
    private val favoriteModelsService = FavoriteModelsService.getInstance()
    private val coroutineScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private val cardLayout = CardLayout()
    private val cardPanel = JPanel(cardLayout)

    private var currentStep = 0

    // Step 0: Welcome
    private val welcomePanel = createWelcomePanel()

    // Step 1: Provisioning Key
    private val provisioningKeyField = JBPasswordField()
    private val validationStatusLabel = JBLabel()
    private val validationIcon = JBLabel()
    private var isKeyValid = false
    private var isValidating = false
    private val provisioningKeyPanel = createProvisioningKeyPanel()

    // Step 2: Models
    private var allModels: List<OpenRouterModelInfo> = emptyList()
    private var filteredModels: List<OpenRouterModelInfo> = emptyList()
    private val selectedModels = mutableSetOf<String>()
    private val searchField = SearchTextField()
    private val modelsTableModel = ModelsTableModel()
    private val modelsTable = JBTable(modelsTableModel)
    private val selectedCountLabel = JBLabel("Selected: 0 models")
    private var isLoadingModels = false
    private val modelsPanel = createModelsPanel()

    // Step 3: Completion
    private val completionPanel = createCompletionPanel()

    init {
        title = "OpenRouter Setup Wizard"
        setOKButtonText("Next")
        setCancelButtonText("Skip")

        // Add all panels to card layout
        cardPanel.add(welcomePanel, "welcome")
        cardPanel.add(provisioningKeyPanel, "provisioning")
        cardPanel.add(modelsPanel, "models")
        cardPanel.add(completionPanel, "completion")

        // Setup listeners
        setupProvisioningKeyListener()
        setupSearchListener()

        // Setup selected count label
        selectedCountLabel.foreground = UIUtil.getLabelInfoForeground()

        init()
        updateButtons()
    }

    override fun createCenterPanel(): JComponent {
        cardPanel.preferredSize = Dimension(DIALOG_WIDTH, DIALOG_HEIGHT)
        return cardPanel
    }

    private fun createWelcomePanel(): JPanel {
        return panel {
            row {
                label("<html><h2>Welcome to OpenRouter!</h2></html>")
            }.bottomGap(BottomGap.MEDIUM)

            row {
                text(
                    "OpenRouter gives you access to 400+ AI models through a single API. " +
                        "This wizard will help you get started in just a few steps."
                )
            }.bottomGap(BottomGap.MEDIUM)

            separator()

            row {
                label("<html><b>What you'll need:</b></html>")
            }.topGap(TopGap.MEDIUM).bottomGap(BottomGap.SMALL)

            indent {
                row {
                    icon(AllIcons.General.User)
                    text("An OpenRouter account (free to create)")
                }.bottomGap(BottomGap.SMALL)

                row {
                    icon(AllIcons.Ide.Notification.PluginUpdate)
                    text("A Provisioning Key from OpenRouter")
                }.bottomGap(BottomGap.SMALL)

                row {
                    icon(AllIcons.General.Settings)
                    text("A few minutes to configure")
                }
            }
        }
    }

    private fun createProvisioningKeyPanel(): JPanel {
        return panel {
            row {
                label("<html><h3>Step 1: Add Your Provisioning Key</h3></html>")
            }.bottomGap(BottomGap.MEDIUM)

            row {
                text(
                    "Your Provisioning Key allows the plugin to manage API keys and access quota information."
                )
            }.bottomGap(BottomGap.SMALL)

            row {
                text("Get your key from:")
                browserLink(
                    "OpenRouter Provisioning Keys",
                    "https://openrouter.ai/settings/provisioning-keys"
                )
            }.bottomGap(BottomGap.MEDIUM)

            separator()

            row {
                label("Provisioning Key:")
            }.topGap(TopGap.MEDIUM).bottomGap(BottomGap.SMALL)

            row {
                cell(provisioningKeyField)
                    .resizableColumn()
                    .align(AlignX.FILL)
                    .comment("Paste your provisioning key here")
            }.bottomGap(BottomGap.SMALL)

            row {
                cell(validationIcon)
                cell(validationStatusLabel)
            }
        }
    }

    private fun createModelsPanel(): JPanel {
        val panel = JPanel(BorderLayout())
        panel.border = JBUI.Borders.empty(10)

        val contentPanel = panel {
            row {
                label("<html><h3>Step 2: Select Your Favorite Models</h3></html>")
            }.bottomGap(BottomGap.MEDIUM)

            row {
                text("Choose models you want to use. You can change this later in Settings.")
            }.bottomGap(BottomGap.MEDIUM)

            separator()

            row {
                label("Search:")
                cell(searchField)
                    .resizableColumn()
                    .align(AlignX.FILL)
            }.topGap(TopGap.MEDIUM).bottomGap(BottomGap.MEDIUM)

            row {
                val scrollPane = JBScrollPane(modelsTable)
                scrollPane.preferredSize = Dimension(MODELS_TABLE_WIDTH, MODELS_TABLE_HEIGHT)
                cell(scrollPane)
                    .resizableColumn()
                    .align(AlignX.FILL)
            }.resizableRow()

            row {
                cell(selectedCountLabel)
            }.topGap(TopGap.SMALL)
        }

        panel.add(contentPanel, BorderLayout.CENTER)

        // Setup table
        setupModelsTable()

        return panel
    }

    private fun createCompletionPanel(): JPanel {
        return panel {
            row {
                label("<html><h2>Setup Complete!</h2></html>")
            }.bottomGap(BottomGap.MEDIUM)

            row {
                icon(AllIcons.General.InspectionsOK)
                text("You're ready to go!")
            }.bottomGap(BottomGap.MEDIUM)

            separator()

            row {
                text(
                    "Copy the proxy server URL below and paste it into your favorite AI Assistant. " +
                        "For example, in JetBrains AI Assistant, paste this URL in the custom server settings."
                )
            }.topGap(TopGap.MEDIUM).bottomGap(BottomGap.MEDIUM)

            row {
                label("Proxy Server URL:")
            }.bottomGap(BottomGap.SMALL)

            row {
                val proxyUrl = OpenRouterProxyServer.buildProxyUrl(8080)
                val urlLabel = JBLabel()
                urlLabel.text = proxyUrl
                urlLabel.font = Font(Font.MONOSPACED, Font.PLAIN, URL_LABEL_FONT_SIZE)
                urlLabel.foreground = JBColor.BLUE
                cell(urlLabel)
                    .resizableColumn()

                button("Copy") {
                    copyProxyUrlToClipboard(proxyUrl)
                }
            }.bottomGap(BottomGap.MEDIUM)

            separator()

            row {
                label("<html><b>Need help with configuration?</b></html>")
            }.topGap(TopGap.MEDIUM).bottomGap(BottomGap.SMALL)

            row {
                browserLink(
                    "View AI Assistant Setup Guide",
                    "https://github.com/DimazzzZ/openrouter-intellij-plugin/blob/main/docs/AI_ASSISTANT_SETUP.md"
                )
            }
        }
    }

    override fun doOKAction() {
        when (currentStep) {
            0 -> {
                // Welcome -> Provisioning Key
                currentStep = 1
                cardLayout.show(cardPanel, "provisioning")
                updateButtons()
            }
            1 -> {
                // Provisioning Key -> Models
                // Key is already validated and saved
                if (!isKeyValid) {
                    return // Should not happen as button is disabled
                }

                // Load models for next step
                loadModels()

                currentStep = 2
                cardLayout.show(cardPanel, "models")
                updateButtons()
            }
            2 -> {
                // Models -> Completion
                // Save selected models
                saveFavoriteModels()

                currentStep = 3
                cardLayout.show(cardPanel, "completion")
                updateButtons()
            }
            3 -> {
                // Completion -> Close
                settingsService.setHasCompletedSetup(true)
                super.doOKAction()
            }
        }
    }

    override fun doCancelAction() {
        when (currentStep) {
            0 -> {
                // User skipped the wizard
                settingsService.setHasCompletedSetup(false)
                super.doCancelAction()
            }
            1 -> {
                // Back to welcome
                currentStep = 0
                cardLayout.show(cardPanel, "welcome")
                updateButtons()
            }
            2 -> {
                // Back to provisioning key
                currentStep = 1
                cardLayout.show(cardPanel, "provisioning")
                updateButtons()
            }
            3 -> {
                // Close button - same as finish
                settingsService.setHasCompletedSetup(true)
                super.doCancelAction()
            }
        }
    }

    private fun updateButtons() {
        when (currentStep) {
            0 -> {
                setOKButtonText("Next")
                setCancelButtonText("Skip")
                okAction.isEnabled = true
            }
            1 -> {
                setOKButtonText("Next")
                setCancelButtonText("Back")
                okAction.isEnabled = isKeyValid && !isValidating
            }
            2 -> {
                setOKButtonText("Next")
                setCancelButtonText("Back")
                okAction.isEnabled = !isLoadingModels
            }
            3 -> {
                setOKButtonText("Finish")
                setCancelButtonText("Close")
                okAction.isEnabled = true
            }
        }
    }

    // ========== Provisioning Key Validation ==========

    private fun setupProvisioningKeyListener() {
        provisioningKeyField.document.addDocumentListener(object : DocumentListener {
            override fun insertUpdate(e: DocumentEvent?) = onKeyChanged()
            override fun removeUpdate(e: DocumentEvent?) = onKeyChanged()
            override fun changedUpdate(e: DocumentEvent?) = onKeyChanged()
        })
    }

    private fun onKeyChanged() {
        val key = String(provisioningKeyField.password)
        if (key.isBlank()) {
            clearValidationStatus()
            isKeyValid = false
            updateButtons()
            return
        }

        // Debounce validation
        SwingUtilities.invokeLater {
            validateProvisioningKey(key)
        }
    }

    private fun validateProvisioningKey(key: String) {
        if (isValidating) return

        isValidating = true
        showValidationInProgress()
        updateButtons()

        // Validate by trying to fetch API keys list with the RAW key
        // (OpenRouter API expects the unencrypted key)
        coroutineScope.launch {
            try {
                val result = withTimeout(KEY_VALIDATION_TIMEOUT_MS) {
                    openRouterService.getApiKeysList(key)
                }
                ApplicationManager.getApplication().invokeLater({
                    isValidating = false
                    when (result) {
                        is ApiResult.Success -> {
                            // Valid key - encrypt and save it
                            val encrypted = EncryptionUtil.encrypt(key)
                            settingsService.setProvisioningKey(encrypted)
                            isKeyValid = true
                            showValidationSuccess()
                        }
                        is ApiResult.Error -> {
                            isKeyValid = false
                            showValidationError("Invalid provisioning key: ${result.message}")
                        }
                    }
                    updateButtons()
                }, ModalityState.any())
            } catch (error: Throwable) {
                ApplicationManager.getApplication().invokeLater({
                    isValidating = false
                    isKeyValid = false
                    showValidationError("Validation failed: ${error.message}")
                    updateButtons()
                }, ModalityState.any())
            }
        }
    }

    private fun clearValidationStatus() {
        validationIcon.icon = null
        validationStatusLabel.text = ""
    }

    private fun showValidationInProgress() {
        validationIcon.icon = AllIcons.Process.Step_1
        validationStatusLabel.text = "Validating..."
        validationStatusLabel.foreground = UIUtil.getLabelInfoForeground()
    }

    private fun showValidationSuccess() {
        validationIcon.icon = AllIcons.General.InspectionsOK
        validationStatusLabel.text = "Valid provisioning key"
        validationStatusLabel.foreground = JBColor.GREEN
    }

    private fun showValidationError(message: String) {
        validationIcon.icon = AllIcons.General.Error
        validationStatusLabel.text = message
        validationStatusLabel.foreground = JBColor.RED
    }

    // ========== Models Loading and Filtering ==========

    private fun loadModels() {
        if (isLoadingModels || allModels.isNotEmpty()) {
            return // Already loaded or loading
        }

        isLoadingModels = true
        updateButtons()

        coroutineScope.launch {
            try {
                val models = withTimeout(MODEL_LOADING_TIMEOUT_MS) {
                    favoriteModelsService.getAvailableModels(forceRefresh = false)
                }
                ApplicationManager.getApplication().invokeLater({
                    isLoadingModels = false
                    if (models != null) {
                        allModels = models
                        filterModels()

                        // Pre-select some popular models
                        preselectPopularModels()
                        updateSelectedCount()
                    } else {
                        PluginLogger.Settings.warn("Failed to load models in setup wizard")
                    }
                    updateButtons()
                }, ModalityState.any())
            } catch (error: Throwable) {
                ApplicationManager.getApplication().invokeLater({
                    isLoadingModels = false
                    PluginLogger.Settings.error("Error loading models in setup wizard", error)
                    updateButtons()
                }, ModalityState.any())
            }
        }
    }

    private fun setupSearchListener() {
        searchField.addDocumentListener(object : DocumentListener {
            override fun insertUpdate(e: DocumentEvent?) = filterModels()
            override fun removeUpdate(e: DocumentEvent?) = filterModels()
            override fun changedUpdate(e: DocumentEvent?) = filterModels()
        })
    }

    private fun filterModels() {
        val searchText = searchField.text.lowercase()

        filteredModels = allModels.filter { model ->
            searchText.isBlank() ||
                model.id.lowercase().contains(searchText) ||
                model.name.lowercase().contains(searchText) ||
                model.description?.lowercase()?.contains(searchText) == true
        }

        modelsTableModel.fireTableDataChanged()
    }

    private fun updateSelectedCount() {
        selectedCountLabel.text = "Selected: ${selectedModels.size} models"
    }

    private fun copyProxyUrlToClipboard(url: String) {
        try {
            val clipboard = java.awt.Toolkit.getDefaultToolkit().systemClipboard
            val stringSelection = java.awt.datatransfer.StringSelection(url)
            clipboard.setContents(stringSelection, null)
            PluginLogger.Settings.info("Proxy URL copied to clipboard: $url")
        } catch (e: Exception) {
            PluginLogger.Settings.error("Failed to copy proxy URL to clipboard", e)
        }
    }

    private fun setupModelsTable() {
        modelsTable.setShowGrid(false)
        modelsTable.intercellSpacing = Dimension(0, 0)
        modelsTable.rowHeight = TABLE_ROW_HEIGHT
        modelsTable.tableHeader.reorderingAllowed = false
        modelsTable.autoCreateRowSorter = true // Enable sorting

        // Column widths
        val columnModel = modelsTable.columnModel
        columnModel.getColumn(0).preferredWidth = CHECKBOX_COLUMN_WIDTH // Checkbox
        columnModel.getColumn(0).maxWidth = CHECKBOX_COLUMN_WIDTH
        columnModel.getColumn(1).preferredWidth = NAME_COLUMN_WIDTH // Model name (wider, no provider column)

        // Disable sorting on checkbox column
        val sorter = modelsTable.rowSorter as? javax.swing.table.TableRowSorter<*>
        sorter?.setSortable(0, false)
    }

    private fun preselectPopularModels() {
        val popularModels = listOf(
            "openai/gpt-4o",
            "anthropic/claude-3.5-sonnet",
            "google/gemini-pro-1.5",
            "meta-llama/llama-3.1-70b-instruct"
        )

        popularModels.forEach { modelId ->
            if (allModels.any { it.id == modelId }) {
                selectedModels.add(modelId)
            }
        }

        modelsTableModel.fireTableDataChanged()
        updateSelectedCount()
    }

    private fun saveFavoriteModels() {
        if (selectedModels.isNotEmpty()) {
            settingsService.setFavoriteModels(selectedModels.toList())
        }
    }

    // ========== Table Model ==========

    private inner class ModelsTableModel : AbstractTableModel() {
        override fun getRowCount(): Int = filteredModels.size

        override fun getColumnCount(): Int = 2 // Checkbox + Model name only

        override fun getColumnName(column: Int): String = when (column) {
            0 -> ""
            1 -> "Model"
            else -> ""
        }

        override fun getColumnClass(columnIndex: Int): Class<*> = when (columnIndex) {
            0 -> java.lang.Boolean::class.java
            else -> String::class.java
        }

        override fun isCellEditable(rowIndex: Int, columnIndex: Int): Boolean {
            return columnIndex == 0 // Only checkbox is editable
        }

        override fun getValueAt(rowIndex: Int, columnIndex: Int): Any? {
            if (rowIndex >= filteredModels.size) return null

            val model = filteredModels[rowIndex]
            return when (columnIndex) {
                0 -> selectedModels.contains(model.id)
                1 -> model.name
                else -> null
            }
        }

        override fun setValueAt(aValue: Any?, rowIndex: Int, columnIndex: Int) {
            if (columnIndex == 0 && rowIndex < filteredModels.size) {
                val model = filteredModels[rowIndex]
                val isSelected = aValue as? Boolean ?: false

                if (isSelected) {
                    selectedModels.add(model.id)
                } else {
                    selectedModels.remove(model.id)
                }

                fireTableCellUpdated(rowIndex, columnIndex)
                updateSelectedCount()
            }
        }
    }

    companion object {
        // Dialog dimensions
        private const val DIALOG_WIDTH = 700
        private const val DIALOG_HEIGHT = 500
        private const val MODELS_TABLE_WIDTH = 650
        private const val MODELS_TABLE_HEIGHT = 280

        // UI constants
        private const val TABLE_ROW_HEIGHT = 28
        private const val CHECKBOX_COLUMN_WIDTH = 40
        private const val NAME_COLUMN_WIDTH = 400
        private const val URL_LABEL_FONT_SIZE = 12

        // Timeouts (milliseconds)
        private const val KEY_VALIDATION_TIMEOUT_MS = 10000L
        private const val MODEL_LOADING_TIMEOUT_MS = 30000L

        /**
         * Show the setup wizard dialog
         */
        fun show(project: Project?): Boolean {
            val dialog = SetupWizardDialog(project)
            return dialog.showAndGet()
        }
    }
}
