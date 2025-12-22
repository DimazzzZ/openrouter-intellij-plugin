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
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.BottomGap
import com.intellij.ui.dsl.builder.TopGap
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.table.JBTable
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext
import com.intellij.util.Alarm
import org.zhavoronkov.openrouter.models.ApiResult
import org.zhavoronkov.openrouter.models.AuthScope
import org.zhavoronkov.openrouter.models.OpenRouterModelInfo
import org.zhavoronkov.openrouter.proxy.OpenRouterProxyServer
import org.zhavoronkov.openrouter.services.FavoriteModelsService
import org.zhavoronkov.openrouter.services.OpenRouterService
import org.zhavoronkov.openrouter.services.OpenRouterSettingsService
import org.zhavoronkov.openrouter.utils.EncryptionUtil
import org.zhavoronkov.openrouter.utils.PluginLogger
import java.awt.BorderLayout
import java.awt.CardLayout
import java.awt.Dimension
import java.awt.Font
import java.io.IOException
import javax.swing.ButtonGroup
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.SwingUtilities
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener
import javax.swing.table.AbstractTableModel
import javax.swing.JRadioButton

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
    private var validationJob: Job? = null
    private val validationAlarm = Alarm(Alarm.ThreadToUse.SWING_THREAD, disposable)

    private var currentStep = 0

    // Initialize scopePredicates BEFORE creating panels
    private val scopePredicates = mutableListOf<ScopePredicate>()

    private inner class ScopePredicate(val scope: AuthScope) : com.intellij.ui.layout.ComponentPredicate() {
        private val listeners = mutableListOf<(Boolean) -> Unit>()

        override fun invoke(): Boolean = authScope == scope

        override fun addListener(listener: (Boolean) -> Unit) {
            listeners.add(listener)
            listener(invoke()) // Initial update
        }

        fun update() {
            val newValue = invoke()
            listeners.forEach { it(newValue) }
        }
    }

    // Step 0: Welcome
    private val welcomePanel = createWelcomePanel()

    // Step 1: Authentication Setup
    private val provisioningKeyField = JBPasswordField()
    private val apiKeyField = JBPasswordField()
    private var authScope = settingsService.apiKeyManager.authScope
    private val validationStatusLabel = JBLabel(" ")
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

        // Setup models search listener
        searchField.addDocumentListener(object : DocumentListener {
            override fun insertUpdate(e: DocumentEvent?) = applyModelFilter()
            override fun removeUpdate(e: DocumentEvent?) = applyModelFilter()
            override fun changedUpdate(e: DocumentEvent?) = applyModelFilter()
        })
        
        // Setup key listeners with extreme logging
        val keyListener = object : com.intellij.ui.DocumentAdapter() {
            override fun textChanged(e: DocumentEvent) {
                PluginLogger.Service.warn("[OpenRouter] Document textChanged event fired")
                onKeyChanged()
            }
        }
        
        PluginLogger.Service.warn("[OpenRouter] Attaching listeners to fields")
        apiKeyField.document.addDocumentListener(keyListener)
        provisioningKeyField.document.addDocumentListener(keyListener)

        // Setup selected count label
        selectedCountLabel.foreground = UIUtil.getLabelInfoForeground()

        init()
        updateButtons()
        
        PluginLogger.Service.warn("[OpenRouter] SetupWizardDialog initialized")
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
                    text("An API Key or Provisioning Key from OpenRouter")
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
                label("<html><h3>Step 1: Authentication Setup</h3></html>")
            }.bottomGap(BottomGap.MEDIUM)

            // Authentication group - using manual radio buttons for better visibility control
            val regularRadioButton = JRadioButton("Regular API Key (No monitoring)")
            val extendedRadioButton = JRadioButton("Extended (Provisioning Key)")
            val authButtonGroup = ButtonGroup()
            authButtonGroup.add(regularRadioButton)
            authButtonGroup.add(extendedRadioButton)
            
            // Set initial selection
            if (authScope == AuthScope.REGULAR) {
                regularRadioButton.isSelected = true
            } else {
                extendedRadioButton.isSelected = true
            }
            
            group("Authentication Scope") {
                buttonsGroup {
                    row {
                        cell<JRadioButton>(regularRadioButton)
                            .comment("Minimal permissions. Quota tracking and usage monitoring will be disabled.")
                    }
                    row {
                        cell<JRadioButton>(extendedRadioButton)
                            .comment("Full functionality. Allows the plugin to manage API keys and monitor usage.")
                    }
                }
            }

            // Store references to the groups for visibility control
            var regularGroupRow: com.intellij.ui.dsl.builder.Row? = null
            var extendedGroupRow: com.intellij.ui.dsl.builder.Row? = null

            separator()

            // Regular API Key section
            regularGroupRow = group("Regular API Key") {
                row {
                    text("Get your key from:")
                    browserLink(
                        "OpenRouter API Keys",
                        "https://openrouter.ai/settings/keys"
                    )
                }.bottomGap(BottomGap.MEDIUM)

                row {
                    label("API Key:")
                }.bottomGap(BottomGap.SMALL)

                row {
                    cell(apiKeyField)
                        .resizableColumn()
                        .align(AlignX.FILL)
                        .comment("Paste your API key here")
                }.bottomGap(BottomGap.SMALL)
            }
            regularGroupRow.visible(authScope == AuthScope.REGULAR)

            // Provisioning Key section
            extendedGroupRow = group("Extended (Provisioning Key)") {
                row {
                    text("Get your key from:")
                    browserLink(
                        "OpenRouter Provisioning Keys",
                        "https://openrouter.ai/settings/provisioning-keys"
                    )
                }.bottomGap(BottomGap.MEDIUM)

                row {
                    label("Provisioning Key:")
                }.bottomGap(BottomGap.SMALL)

                row {
                    cell(provisioningKeyField)
                        .resizableColumn()
                        .align(AlignX.FILL)
                        .comment("Paste your provisioning key here")
                }.bottomGap(BottomGap.SMALL)
            }
            extendedGroupRow.visible(authScope == AuthScope.EXTENDED)
            
            // Add action listeners to update visibility
            regularRadioButton.addActionListener {
                if (regularRadioButton.isSelected) {
                    authScope = AuthScope.REGULAR
                    regularGroupRow?.visible(true)
                    extendedGroupRow?.visible(false)
                    onKeyChanged()
                }
            }
            extendedRadioButton.addActionListener {
                if (extendedRadioButton.isSelected) {
                    authScope = AuthScope.EXTENDED
                    regularGroupRow?.visible(false)
                    extendedGroupRow?.visible(true)
                    onKeyChanged()
                }
            }

            row {
                cell(validationIcon)
                cell(validationStatusLabel)
            }
        }
    }

    private fun radioButtonSelected(scope: AuthScope): com.intellij.ui.layout.ComponentPredicate {
        val predicate = ScopePredicate(scope)
        scopePredicates.add(predicate)
        return predicate
    }

    private fun notifyScopeChanged() {
        scopePredicates.forEach { it.update() }
    }

    private fun createModelsPanel(): JPanel {
        val panel = JPanel(BorderLayout())
        panel.border = JBUI.Borders.empty(API_KEY_TRUNCATE_LENGTH)

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
                val proxyUrl = OpenRouterProxyServer.buildProxyUrl(DEFAULT_PROXY_PORT)
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
            STEP_WELCOME -> {
                // Welcome -> Provisioning Key
                currentStep = STEP_PROVISIONING
                cardLayout.show(cardPanel, "provisioning")
                updateButtons()
            }
            STEP_PROVISIONING -> {
                // Provisioning Key -> Models
                // Key is already validated and saved
                // Save settings before moving to models
                settingsService.apiKeyManager.authScope = authScope
                if (authScope == org.zhavoronkov.openrouter.models.AuthScope.REGULAR) {
                    val key = String(apiKeyField.password)
                    settingsService.apiKeyManager.setApiKey(key)
                } else {
                    val key = String(provisioningKeyField.password)
                    settingsService.apiKeyManager.setProvisioningKey(key)
                }
                
                currentStep = STEP_MODELS
                cardLayout.show(cardPanel, "models")
                loadModels()
                updateButtons()
            }
            STEP_MODELS -> {
                // Models -> Completion
                // Save selected models
                saveFavoriteModels()

                currentStep = STEP_COMPLETION
                cardLayout.show(cardPanel, "completion")
                updateButtons()
            }
            STEP_COMPLETION -> {
                // Completion -> Close
                settingsService.setupStateManager.setHasCompletedSetup(true)
                super.doOKAction()
            }
        }
    }

    override fun doCancelAction() {
        when (currentStep) {
            STEP_WELCOME -> {
                // User skipped the wizard
                settingsService.setupStateManager.setHasCompletedSetup(false)
                super.doCancelAction()
            }
            STEP_PROVISIONING -> {
                // Back to welcome
                currentStep = STEP_WELCOME
                cardLayout.show(cardPanel, "welcome")
                updateButtons()
            }
            STEP_MODELS -> {
                // Back to provisioning key
                currentStep = STEP_PROVISIONING
                cardLayout.show(cardPanel, "provisioning")
                updateButtons()
            }
            STEP_COMPLETION -> {
                // Close button - same as finish
                settingsService.setupStateManager.setHasCompletedSetup(true)
                super.doCancelAction()
            }
        }
    }

    private fun updateButtons() {
        when (currentStep) {
            STEP_WELCOME -> {
                setOKButtonText("Next")
                setCancelButtonText("Skip")
                okAction.isEnabled = true
            }
            STEP_PROVISIONING -> {
                setOKButtonText("Next")
                setCancelButtonText("Back")
                okAction.isEnabled = isKeyValid && !isValidating
            }
            STEP_MODELS -> {
                setOKButtonText("Next")
                setCancelButtonText("Back")
                okAction.isEnabled = !isLoadingModels
            }
            STEP_COMPLETION -> {
                setOKButtonText("Finish")
                setCancelButtonText("Close")
                okAction.isEnabled = true
            }
        }
    }

    // ========== Provisioning Key Validation ==========

    private fun onKeyChanged() {
        val key = if (authScope == AuthScope.REGULAR) {
            String(apiKeyField.password).trim()
        } else {
            String(provisioningKeyField.password).trim()
        }

        PluginLogger.Service.warn("[OpenRouter] onKeyChanged: scope=$authScope, keyLength=${key.length}")

        // Cancel any pending validation
        validationAlarm.cancelAllRequests()
        validationJob?.cancel()
        
        if (key.isBlank()) {
            PluginLogger.Service.warn("[OpenRouter] Key is blank, clearing status")
            isValidating = false
            clearValidationStatus()
            isKeyValid = false
            updateButtons()
            return
        }

        // Start new validation with debounce using Alarm
        // Only trigger if key is long enough to be a real key (min 10 chars)
        if (key.length >= 10) {
            validationAlarm.addRequest({
                PluginLogger.Service.warn("[OpenRouter] Alarm triggered, starting validateKey")
                validateKey(key)
            }, 500)
        } else {
            PluginLogger.Service.warn("[OpenRouter] Key too short for validation (${key.length} chars)")
            isValidating = false
            isKeyValid = false
            clearValidationStatus()
            updateButtons()
        }
    }

    private fun validateKey(key: String) {
        PluginLogger.Service.warn("[OpenRouter] validateKey starting for $authScope")
        
        isValidating = true
        showValidationInProgress()
        updateButtons()

        val currentScope = authScope

        // Use IO dispatcher for the launch to ensure it doesn't wait for EDT
        validationJob = coroutineScope.launch(Dispatchers.IO) {
            PluginLogger.Service.warn("[OpenRouter] Coroutine started on ${Thread.currentThread().name}")
            try {
                PluginLogger.Service.warn("[OpenRouter] Calling API for validation...")
                val result = if (currentScope == AuthScope.REGULAR) {
                    openRouterService.testApiKey(key)
                } else {
                    openRouterService.getApiKeysList(key)
                }
                
                PluginLogger.Service.warn("[OpenRouter] API call finished: $result")

                ApplicationManager.getApplication().invokeLater({
                    PluginLogger.Service.warn("[OpenRouter] invokeLater running to update UI")
                    isValidating = false
                    // Only update if the scope hasn't changed while we were validating
                    if (authScope == currentScope) {
                        when (result) {
                            is ApiResult.Success -> {
                                isKeyValid = true
                                showValidationSuccess()
                                PluginLogger.Service.warn("[OpenRouter] Validation SUCCESS")
                            }
                            is ApiResult.Error -> {
                                isKeyValid = false
                                val friendlyError = when {
                                    result.message.contains("No cookie auth", ignoreCase = true) -> "Invalid API key"
                                    result.message.contains("Missing Authentication", ignoreCase = true) -> "Invalid key format"
                                    else -> result.message
                                }
                                showValidationError(friendlyError)
                                PluginLogger.Service.warn("[OpenRouter] Validation FAILED: $friendlyError (Raw: ${result.message})")
                            }
                        }
                    } else {
                        PluginLogger.Service.warn("[OpenRouter] Scope changed during validation, ignoring result")
                    }
                    updateButtons()
                }, ModalityState.any())
            } catch (e: CancellationException) {
                PluginLogger.Service.warn("[OpenRouter] validateKey coroutine cancelled")
            } catch (e: Exception) {
                PluginLogger.Service.warn("[OpenRouter] Validation EXCEPTION", e)
                ApplicationManager.getApplication().invokeLater({
                    isValidating = false
                    isKeyValid = false
                    showValidationError("Validation failed: ${e.message ?: "Unknown error"}")
                    updateButtons()
                }, ModalityState.any())
            }
        }
    }

    private fun clearValidationStatus() {
        validationIcon.icon = null
        validationStatusLabel.text = " "
        validationIcon.isVisible = false
        validationStatusLabel.isVisible = true
    }

    private fun showValidationInProgress() {
        validationIcon.icon = AllIcons.Process.Step_1
        validationStatusLabel.text = "Validating..."
        validationStatusLabel.foreground = UIUtil.getLabelInfoForeground()
        validationIcon.isVisible = true
        validationStatusLabel.isVisible = true
        
        // Force UI update
        validationStatusLabel.revalidate()
        validationStatusLabel.repaint()
        validationIcon.revalidate()
        validationIcon.repaint()
    }

    private fun showValidationSuccess() {
        validationIcon.icon = AllIcons.General.InspectionsOK
        val type = if (authScope == AuthScope.REGULAR) "API key" else "provisioning key"
        validationStatusLabel.text = "Valid $type"
        validationStatusLabel.foreground = JBColor.GREEN
        validationIcon.isVisible = true
        validationStatusLabel.isVisible = true
        
        // Force UI update
        validationStatusLabel.revalidate()
        validationStatusLabel.repaint()
        validationIcon.revalidate()
        validationIcon.repaint()
    }

    private fun showValidationError(message: String) {
        // Map technical OpenRouter errors to user-friendly ones
        val friendlyMessage = when {
            message.contains("Missing Authentication header", ignoreCase = true) || 
            message.contains("Invalid key format", ignoreCase = true) -> 
                "Invalid key format or type"
            message.contains("Invalid provisioningkey", ignoreCase = true) -> 
                "Invalid provisioning key"
            message.contains("No cookie auth", ignoreCase = true) ||
            message.contains("Authentication failed", ignoreCase = true) ->
                "Invalid API key"
            else -> message
        }

        validationIcon.icon = AllIcons.General.Error
        validationStatusLabel.text = if (friendlyMessage.startsWith("Invalid")) friendlyMessage else "Invalid key: $friendlyMessage"
        validationStatusLabel.foreground = JBColor.RED
        validationIcon.isVisible = true
        validationStatusLabel.isVisible = true
        
        // Force UI update
        validationStatusLabel.revalidate()
        validationStatusLabel.repaint()
        validationIcon.revalidate()
        validationIcon.repaint()
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
                        applyModelFilter()

                        // Pre-select some popular models
                        preselectPopularModels()
                        updateSelectedCount()
                    } else {
                        PluginLogger.Settings.warn("Failed to load models in setup wizard")
                    }
                    updateButtons()
                }, ModalityState.any())
            } catch (error: TimeoutCancellationException) {
                ApplicationManager.getApplication().invokeLater({
                    isLoadingModels = false
                    PluginLogger.Settings.error("Model loading timeout in setup wizard", error)
                    updateButtons()
                }, ModalityState.any())
            } catch (error: IOException) {
                ApplicationManager.getApplication().invokeLater({
                    isLoadingModels = false
                    PluginLogger.Settings.error("Model loading network error in setup wizard", error)
                    updateButtons()
                }, ModalityState.any())
            } catch (error: IllegalStateException) {
                ApplicationManager.getApplication().invokeLater({
                    isLoadingModels = false
                    PluginLogger.Settings.error("Model loading invalid state in setup wizard", error)
                    updateButtons()
                }, ModalityState.any())
            } catch (expectedError: Exception) {
                ApplicationManager.getApplication().invokeLater({
                    isLoadingModels = false
                    PluginLogger.Settings.error("Error loading models in setup wizard", expectedError)
                    updateButtons()
                }, ModalityState.any())
            }
        }
    }

    private fun applyModelFilter() {
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
        } catch (e: java.awt.HeadlessException) {
            PluginLogger.Settings.warn("Clipboard not available in headless environment", e)
        } catch (e: IllegalStateException) {
            PluginLogger.Settings.error("Clipboard access denied", e)
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
            settingsService.favoriteModelsManager.setFavoriteModels(selectedModels.toList())
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

        // Default proxy port
        private const val DEFAULT_PROXY_PORT = 8080

        // String truncation length
        private const val API_KEY_TRUNCATE_LENGTH = 10

        // Wizard step identifiers
        private const val STEP_WELCOME = 0
        private const val STEP_PROVISIONING = 1
        private const val STEP_MODELS = 2
        private const val STEP_COMPLETION = 3

        /**
         * Show the setup wizard dialog
         */
        fun show(project: Project?): Boolean {
            val dialog = SetupWizardDialog(project)
            return dialog.showAndGet()
        }
    }
}
