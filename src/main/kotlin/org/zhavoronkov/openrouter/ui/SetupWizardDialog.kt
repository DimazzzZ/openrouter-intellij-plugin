package org.zhavoronkov.openrouter.ui

import com.intellij.icons.AllIcons
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.JBColor
import com.intellij.ui.SearchTextField
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPasswordField
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.dsl.builder.*
import com.intellij.ui.table.JBTable
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import org.zhavoronkov.openrouter.models.OpenRouterModelInfo
import org.zhavoronkov.openrouter.services.FavoriteModelsService
import org.zhavoronkov.openrouter.services.OpenRouterProxyService
import org.zhavoronkov.openrouter.services.OpenRouterService
import org.zhavoronkov.openrouter.services.OpenRouterSettingsService
import org.zhavoronkov.openrouter.utils.EncryptionUtil
import org.zhavoronkov.openrouter.utils.ModelProviderUtils
import org.zhavoronkov.openrouter.utils.PluginLogger
import java.awt.BorderLayout
import java.awt.CardLayout
import java.awt.Dimension
import java.awt.Font
import java.util.concurrent.TimeUnit
import javax.swing.*
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener
import javax.swing.table.AbstractTableModel
import javax.swing.table.DefaultTableCellRenderer

/**
 * Multi-step setup wizard for first-time users with validation and embedded model selection
 */
class SetupWizardDialog(private val project: Project?) : DialogWrapper(project) {

    private val settingsService = OpenRouterSettingsService.getInstance()
    private val proxyService = OpenRouterProxyService.getInstance()
    private val openRouterService = OpenRouterService.getInstance()
    private val favoriteModelsService = FavoriteModelsService.getInstance()

    private val cardLayout = CardLayout()
    private val cardPanel = JPanel(cardLayout)

    private var currentStep = 0
    private val totalSteps = 4

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
    private val providerComboBox = JComboBox<String>()
    private val modelsTableModel = ModelsTableModel()
    private val modelsTable = JBTable(modelsTableModel)
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

        init()
        updateButtons()
    }

    override fun createCenterPanel(): JComponent {
        cardPanel.preferredSize = Dimension(700, 500)
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
            }.topGap(TopGap.MEDIUM).bottomGap(BottomGap.SMALL)

            row {
                label("Provider:")
                cell(providerComboBox)
                    .comment("Filter by AI provider")
            }.bottomGap(BottomGap.MEDIUM)

            row {
                val scrollPane = JBScrollPane(modelsTable)
                scrollPane.preferredSize = Dimension(650, 250)
                cell(scrollPane)
                    .resizableColumn()
                    .align(AlignX.FILL)
            }.resizableRow()

            row {
                text("Selected: 0 models")
                    .apply {
                        component.foreground = UIUtil.getLabelInfoForeground()
                    }
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
                text("You're all set! Here's what to do next:")
            }.bottomGap(BottomGap.MEDIUM)

            separator()

            indent {
                row {
                    label("1.")
                    text("Start the proxy server in Settings → Tools → OpenRouter")
                }.topGap(TopGap.MEDIUM).bottomGap(BottomGap.SMALL)

                row {
                    label("2.")
                    text("Configure JetBrains AI Assistant to use the proxy")
                }.bottomGap(BottomGap.SMALL)

                row {
                    label("3.")
                    text("Start using 400+ AI models!")
                }.bottomGap(BottomGap.MEDIUM)
            }

            separator()

            row {
                text("The proxy server provides an OpenAI-compatible API at:")
            }.topGap(TopGap.MEDIUM).bottomGap(BottomGap.SMALL)

            row {
                val urlLabel = JBLabel("http://localhost:8080")
                urlLabel.font = Font(Font.MONOSPACED, Font.PLAIN, 12)
                urlLabel.foreground = JBColor.BLUE
                cell(urlLabel)
            }.bottomGap(BottomGap.MEDIUM)

            separator()

            row {
                label("<html><b>Need help?</b></html>")
            }.topGap(TopGap.MEDIUM).bottomGap(BottomGap.SMALL)

            row {
                browserLink(
                    "View Documentation",
                    "https://github.com/DimazzzZ/openrouter-intellij-plugin"
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
        // User skipped the wizard
        settingsService.setHasCompletedSetup(false)
        super.doCancelAction()
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
        openRouterService.getApiKeysList(key)
            .orTimeout(10, TimeUnit.SECONDS)
            .thenAccept { response ->
                ApplicationManager.getApplication().invokeLater({
                    isValidating = false
                    if (response != null) {
                        // Valid key - encrypt and save it
                        val encrypted = EncryptionUtil.encrypt(key)
                        settingsService.setProvisioningKey(encrypted)
                        isKeyValid = true
                        showValidationSuccess()
                    } else {
                        isKeyValid = false
                        showValidationError("Invalid provisioning key")
                    }
                    updateButtons()
                }, ModalityState.any())
            }
            .exceptionally { error ->
                ApplicationManager.getApplication().invokeLater({
                    isValidating = false
                    isKeyValid = false
                    showValidationError("Validation failed: ${error.message}")
                    updateButtons()
                }, ModalityState.any())
                null
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

        favoriteModelsService.getAvailableModels(forceRefresh = false)
            .orTimeout(30, TimeUnit.SECONDS)
            .thenAccept { models ->
                ApplicationManager.getApplication().invokeLater({
                    isLoadingModels = false
                    if (models != null) {
                        allModels = models
                        setupProviderFilter()
                        filterModels()

                        // Pre-select some popular models
                        preselectPopularModels()
                    } else {
                        PluginLogger.Settings.warn("Failed to load models in setup wizard")
                    }
                    updateButtons()
                }, ModalityState.any())
            }
            .exceptionally { error ->
                ApplicationManager.getApplication().invokeLater({
                    isLoadingModels = false
                    PluginLogger.Settings.error("Error loading models in setup wizard", error)
                    updateButtons()
                }, ModalityState.any())
                null
            }
    }

    private fun setupProviderFilter() {
        val providers = mutableSetOf("All Providers")
        allModels.forEach { model ->
            providers.add(ModelProviderUtils.extractProvider(model.id))
        }

        providerComboBox.removeAllItems()
        providers.sorted().forEach { providerComboBox.addItem(it) }

        providerComboBox.addActionListener {
            filterModels()
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
        val selectedProvider = providerComboBox.selectedItem as? String ?: "All Providers"

        filteredModels = allModels.filter { model ->
            val matchesSearch = searchText.isBlank() ||
                    model.id.lowercase().contains(searchText) ||
                    model.name.lowercase().contains(searchText) ||
                    model.description?.lowercase()?.contains(searchText) == true

            val matchesProvider = selectedProvider == "All Providers" ||
                    ModelProviderUtils.extractProvider(model.id) == selectedProvider

            matchesSearch && matchesProvider
        }

        modelsTableModel.fireTableDataChanged()
    }

    private fun setupModelsTable() {
        modelsTable.setShowGrid(false)
        modelsTable.intercellSpacing = Dimension(0, 0)
        modelsTable.rowHeight = 28
        modelsTable.tableHeader.reorderingAllowed = false

        // Column widths
        val columnModel = modelsTable.columnModel
        columnModel.getColumn(0).preferredWidth = 40  // Checkbox
        columnModel.getColumn(0).maxWidth = 40
        columnModel.getColumn(1).preferredWidth = 250 // Model name
        columnModel.getColumn(2).preferredWidth = 100 // Provider

        // Center align checkbox column
        val centerRenderer = DefaultTableCellRenderer()
        centerRenderer.horizontalAlignment = SwingConstants.CENTER
        columnModel.getColumn(0).cellRenderer = centerRenderer
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
    }

    private fun saveFavoriteModels() {
        if (selectedModels.isNotEmpty()) {
            settingsService.setFavoriteModels(selectedModels.toList())
        }
    }

    // ========== Table Model ==========

    private inner class ModelsTableModel : AbstractTableModel() {
        override fun getRowCount(): Int = filteredModels.size

        override fun getColumnCount(): Int = 3

        override fun getColumnName(column: Int): String = when (column) {
            0 -> ""
            1 -> "Model"
            2 -> "Provider"
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
                2 -> ModelProviderUtils.extractProvider(model.id)
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
            }
        }
    }

    companion object {
        /**
         * Show the setup wizard dialog
         */
        fun show(project: Project?): Boolean {
            val dialog = SetupWizardDialog(project)
            return dialog.showAndGet()
        }
    }
}

