package org.zhavoronkov.openrouter.ui

import com.intellij.icons.AllIcons
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.Messages
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
import com.intellij.util.Alarm
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import org.zhavoronkov.openrouter.models.ApiResult
import org.zhavoronkov.openrouter.models.AuthScope
import org.zhavoronkov.openrouter.models.OpenRouterModelInfo
import org.zhavoronkov.openrouter.proxy.OpenRouterProxyServer
import org.zhavoronkov.openrouter.services.FavoriteModelsService
import org.zhavoronkov.openrouter.services.OpenRouterService
import org.zhavoronkov.openrouter.services.OpenRouterSettingsService
import org.zhavoronkov.openrouter.utils.PluginLogger
import java.awt.BorderLayout
import java.awt.CardLayout
import java.awt.Dimension
import java.awt.Font
import javax.swing.ButtonGroup
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JRadioButton
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
    private val validationAlarm = Alarm(Alarm.ThreadToUse.POOLED_THREAD, disposable)
    private var validationJob: Job? = null

    // UI Components
    private lateinit var regularRadioButton: JRadioButton
    private lateinit var extendedRadioButton: JRadioButton
    private val keyField = JBPasswordField()

    private var currentStep = 0

    // PKCE Handler - extracted to separate class
    private var pkceHandler: PkceAuthHandler? = null

    // Initialize uiPredicates BEFORE creating panels
    private val uiPredicates = mutableListOf<UpdatablePredicate>()

    private abstract inner class UpdatablePredicate : com.intellij.ui.layout.ComponentPredicate() {
        private val listeners = mutableListOf<(Boolean) -> Unit>()

        override fun addListener(listener: (Boolean) -> Unit) {
            listeners.add(listener)
            listener(invoke()) // Initial update
        }

        fun update() {
            val newValue = invoke()
            listeners.forEach { it(newValue) }
        }
    }

    private inner class ScopePredicate(val scope: AuthScope) : UpdatablePredicate() {
        override fun invoke(): Boolean = authScope == scope
    }

    // Step 0: Welcome
    private val welcomePanel = createWelcomePanel()

    // Step 1: Authentication Setup
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
    private val modelsLoadingLabel = JBLabel("Loading models...")
    private val modelsLoadingIcon = JBLabel(AllIcons.Process.Step_passive)
    private var isLoadingModels = false
    private val modelsPanel = createModelsPanel()

    // Step 3: Completion
    private var proxyAutoStart = settingsService.proxyManager.isProxyAutoStartEnabled()
    private var proxyPort = settingsService.proxyManager.getProxyPort().let { if (it == 0) DEFAULT_PROXY_PORT else it }
    private var startProxyNow = true
    private var isAdvancedProxySetup = settingsService.proxyManager.getProxyPort() != 0 &&
        settingsService.proxyManager.getProxyPort() != DEFAULT_PROXY_PORT ||
        !settingsService.proxyManager.isProxyAutoStartEnabled()
    private val proxyUrlLabel = JBLabel()
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

        // Enable sorting for models table
        modelsTable.rowSorter = javax.swing.table.TableRowSorter(modelsTableModel)

        // Setup models search listener
        searchField.addDocumentListener(object : DocumentListener {
            override fun insertUpdate(e: DocumentEvent?) = applyModelFilter()
            override fun removeUpdate(e: DocumentEvent?) = applyModelFilter()
            override fun changedUpdate(e: DocumentEvent?) = applyModelFilter()
        })

        // Setup key listeners
        val keyListener = object : com.intellij.ui.DocumentAdapter() {
            override fun textChanged(e: DocumentEvent) {
                onKeyChanged()
            }
        }

        keyField.document.addDocumentListener(keyListener)

        // Setup selected count label
        selectedCountLabel.foreground = UIUtil.getLabelInfoForeground()

        init()
        updateButtons()

        SetupWizardLogger.info("SetupWizardDialog initialized")
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
            regularRadioButton = JRadioButton("Regular API Key (No monitoring)")
            extendedRadioButton = JRadioButton("Extended (Provisioning Key)")
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

            separator()

            group("Authentication Details") {
                row {
                    text("Get your key from:")
                    browserLink(
                        "OpenRouter Provisioning Keys",
                        "https://openrouter.ai/settings/provisioning-keys"
                    ).visibleIf(radioButtonSelected(AuthScope.EXTENDED))
                }.bottomGap(BottomGap.MEDIUM).visibleIf(radioButtonSelected(AuthScope.EXTENDED))

                row {
                    button("Connect to OpenRouter") {
                        startPkceAuthFlow()
                    }.visibleIf(radioButtonSelected(AuthScope.REGULAR))
                        .comment("Opens browser to authorize and automatically retrieves your API key")
                }.bottomGap(BottomGap.MEDIUM)

                row {
                    label("API Key:").visibleIf(radioButtonSelected(AuthScope.REGULAR))
                    label("Provisioning Key:").visibleIf(radioButtonSelected(AuthScope.EXTENDED))
                }.bottomGap(BottomGap.SMALL)

                row {
                    cell(keyField)
                        .resizableColumn()
                        .align(AlignX.FILL)
                }.bottomGap(BottomGap.SMALL)

                row {
                    comment("Paste your API key here").visibleIf(radioButtonSelected(AuthScope.REGULAR))
                    comment("Paste your provisioning key here").visibleIf(radioButtonSelected(AuthScope.EXTENDED))
                }
            }

            // Add action listeners to update visibility
            regularRadioButton.addActionListener {
                if (regularRadioButton.isSelected && authScope != AuthScope.REGULAR) {
                    authScope = AuthScope.REGULAR
                    notifyScopeChanged()
                    // Trigger validation immediately on scope change
                    val key = String(keyField.password).trim()
                    if (key.length >= 10) {
                        validationAlarm.cancelAllRequests()
                        validateKey(key)
                    } else {
                        onKeyChanged()
                    }
                }
            }
            extendedRadioButton.addActionListener {
                if (extendedRadioButton.isSelected && authScope != AuthScope.EXTENDED) {
                    authScope = AuthScope.EXTENDED
                    notifyScopeChanged()
                    // Trigger validation immediately on scope change
                    val key = String(keyField.password).trim()
                    if (key.length >= 10) {
                        validationAlarm.cancelAllRequests()
                        validateKey(key)
                    } else {
                        onKeyChanged()
                    }
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
        uiPredicates.add(predicate)
        return predicate
    }

    private fun advancedProxySelected(): com.intellij.ui.layout.ComponentPredicate {
        val predicate = object : UpdatablePredicate() {
            override fun invoke() = isAdvancedProxySetup
        }
        uiPredicates.add(predicate)
        return predicate
    }

    private fun notifyScopeChanged() {
        uiPredicates.forEach { it.update() }
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
                cell(modelsLoadingIcon)
                cell(modelsLoadingLabel)
                cell(selectedCountLabel)
            }.topGap(TopGap.SMALL)
        }

        panel.add(contentPanel, BorderLayout.CENTER)

        // Setup table
        setupModelsTable()

        return panel
    }

    private fun createCompletionPanel(): JPanel {
        val standardRadioButton = JRadioButton("Standard Setup (Recommended)")
        val advancedRadioButton = JRadioButton("Advanced Setup")
        val group = ButtonGroup()
        group.add(standardRadioButton)
        group.add(advancedRadioButton)

        if (isAdvancedProxySetup) {
            advancedRadioButton.isSelected = true
        } else {
            standardRadioButton.isSelected = true
        }

        val updateProxyUrl = {
            val port = if (isAdvancedProxySetup) proxyPort else DEFAULT_PROXY_PORT
            proxyUrlLabel.text = OpenRouterProxyServer.buildProxyUrl(port)
        }

        standardRadioButton.addActionListener {
            isAdvancedProxySetup = false
            proxyAutoStart = true
            proxyPort = DEFAULT_PROXY_PORT
            updateProxyUrl()
            notifyScopeChanged() // Reuse this to trigger visibility updates
        }

        advancedRadioButton.addActionListener {
            isAdvancedProxySetup = true
            updateProxyUrl()
            notifyScopeChanged()
        }

        return panel {
            row {
                label("<html><h2>Setup Complete!</h2></html>")
            }.bottomGap(BottomGap.MEDIUM)

            row {
                icon(AllIcons.General.InspectionsOK)
                text("You're ready to go!")
            }.bottomGap(BottomGap.MEDIUM)

            separator()

            group("Proxy Server Setup") {
                buttonsGroup {
                    row {
                        cell(standardRadioButton)
                            .comment("Uses an available port (default 8080) and starts automatically with IDE.")
                    }
                    row {
                        cell(advancedRadioButton)
                            .comment("Configure custom port and autostart preferences.")
                    }
                }

                indent {
                    row("Port:") {
                        intTextField(1024..65535)
                            .applyToComponent {
                                text = proxyPort.toString()
                                document.addDocumentListener(object : DocumentListener {
                                    override fun insertUpdate(e: DocumentEvent?) = update()
                                    override fun removeUpdate(e: DocumentEvent?) = update()
                                    override fun changedUpdate(e: DocumentEvent?) = update()
                                    private fun update() {
                                        val newPort = text.toIntOrNull()
                                        if (newPort != null) {
                                            proxyPort = newPort
                                            updateProxyUrl()
                                        }
                                    }
                                })
                            }
                    }.visibleIf(advancedProxySelected())

                    row {
                        checkBox("Start automatically with IDE")
                            .applyToComponent {
                                isSelected = proxyAutoStart
                                addActionListener { proxyAutoStart = isSelected }
                            }
                    }.visibleIf(advancedProxySelected())
                }
            }

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
                updateProxyUrl()
                proxyUrlLabel.font = Font(Font.MONOSPACED, Font.PLAIN, URL_LABEL_FONT_SIZE)
                proxyUrlLabel.foreground = JBColor.BLUE
                cell(proxyUrlLabel)
                    .resizableColumn()

                button("Copy") {
                    copyProxyUrlToClipboard(proxyUrlLabel.text)
                }
            }.bottomGap(BottomGap.MEDIUM)

            row {
                checkBox("Start proxy server now")
                    .applyToComponent {
                        isSelected = startProxyNow
                        addActionListener { startProxyNow = isSelected }
                    }
            }

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
                val key = String(keyField.password).trim()
                if (authScope == org.zhavoronkov.openrouter.models.AuthScope.REGULAR) {
                    settingsService.apiKeyManager.setApiKey(key)
                } else {
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
                // Save proxy settings
                settingsService.proxyManager.setProxyPort(proxyPort)
                settingsService.proxyManager.setProxyAutoStart(proxyAutoStart)

                // Start proxy if requested
                if (startProxyNow) {
                    OpenRouterProxyServer.getInstance().start()
                }

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
                okAction.isEnabled = !isLoadingModels && allModels.isNotEmpty()
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
        val key = String(keyField.password).trim()

        SetupWizardLogger.logValidationEvent("Key changed", "scope=$authScope, length=${key.length}")

        // Cancel any pending validation
        validationAlarm.cancelAllRequests()
        validationJob?.cancel()

        if (key.isBlank()) {
            SetupWizardLogger.logValidationEvent("Key cleared")
            isValidating = false
            clearValidationStatus()
            isKeyValid = false
            updateButtons()
            return
        }

        // Start new validation with debounce using Alarm
        // Only trigger if key is long enough to be a real key (min 10 chars)
        if (key.length >= SetupWizardConfig.PKCE_KEY_MIN_LENGTH) {
            validationAlarm.addRequest({
                SetupWizardLogger.logValidationEvent("Debounce triggered, starting validation")
                validateKey(key)
            }, SetupWizardConfig.KEY_VALIDATION_DEBOUNCE_MS.toInt())
        } else {
            SetupWizardLogger.logValidationEvent("Key too short", "length=${key.length}")
            isValidating = false
            isKeyValid = false
            clearValidationStatus()
            updateButtons()
        }
    }

    private fun validateKey(key: String) {
        SetupWizardLogger.logValidationEvent("Starting validation", "scope=$authScope")

        isValidating = true
        showValidationInProgress()
        updateButtons()

        val currentScope = authScope

        // Use IO dispatcher for the launch to ensure it doesn't wait for EDT
        validationJob = coroutineScope.launch(Dispatchers.IO) {
            try {
                var detectedProvisioning = false
                val result = if (currentScope == AuthScope.REGULAR) {
                    val regularResult = openRouterService.testApiKey(key)
                    if (regularResult is ApiResult.Success) {
                        // Check if it's actually a provisioning key
                        val provisioningResult = openRouterService.getApiKeysList(key)
                        if (provisioningResult is ApiResult.Success) {
                            detectedProvisioning = true
                            SetupWizardLogger.logValidationEvent("Detected Provisioning Key in Regular mode")
                        }
                    }
                    regularResult
                } else {
                    openRouterService.getApiKeysList(key)
                }

                ApplicationManager.getApplication().invokeLater({
                    isValidating = false
                    // Only update if the scope hasn't changed while we were validating
                    if (authScope == currentScope) {
                        // Handle auto-switch for provisioning keys
                        if (detectedProvisioning && currentScope == AuthScope.REGULAR) {
                            authScope = AuthScope.EXTENDED
                            if (::extendedRadioButton.isInitialized) {
                                extendedRadioButton.isSelected = true
                            }
                            notifyScopeChanged()
                            Messages.showInfoMessage(
                                "The provided key is a Provisioning Key. Switched to Extended mode for full features.",
                                "Key Type Detected"
                            )
                        }

                        when (result) {
                            is ApiResult.Success -> {
                                isKeyValid = true
                                showValidationSuccess()
                                SetupWizardLogger.logValidationEvent("Validation successful")
                            }
                            is ApiResult.Error -> {
                                isKeyValid = false
                                val friendlyError = SetupWizardErrorHandler.handleValidationError(result)
                                showValidationError(friendlyError)
                                SetupWizardLogger.logValidationEvent("Validation failed", friendlyError)
                            }
                        }
                    } else {
                        SetupWizardLogger.logValidationEvent("Scope changed during validation, ignoring result")
                    }
                    updateButtons()
                }, ModalityState.any())
            } catch (e: CancellationException) {
                SetupWizardLogger.logValidationEvent("Validation cancelled")
            } catch (e: Exception) {
                SetupWizardLogger.error("Validation exception", e)
                ApplicationManager.getApplication().invokeLater({
                    isValidating = false
                    isKeyValid = false
                    showValidationError(SetupWizardErrorHandler.handleNetworkError(e, "key validation"))
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

    // ========== PKCE Authentication Flow ==========

    private fun startPkceAuthFlow() {
        SetupWizardLogger.logPkceEvent("Starting PKCE flow from UI")

        // Cancel any existing PKCE handler
        pkceHandler?.cancel()

        // Create new PKCE handler with callbacks
        pkceHandler = PkceAuthHandler(
            coroutineScope = coroutineScope,
            openRouterService = openRouterService,
            onSuccess = { apiKey ->
                // Handle successful authentication
                SetupWizardLogger.logPkceEvent(
                    "PKCE authentication successful - updating UI",
                    "keyLength=${apiKey.length}, authScope=$authScope"
                )
                keyField.text = apiKey
                SetupWizardLogger.logPkceEvent("Key field updated, starting validation")
                validateKey(apiKey)
            },
            onError = { errorMessage ->
                // Handle authentication error
                SetupWizardLogger.logPkceEvent("PKCE authentication failed", errorMessage)
                showValidationError(errorMessage)
            },
            onStatusUpdate = { status ->
                // Update UI with current status
                ApplicationManager.getApplication().invokeLater({
                    showValidationInProgress()
                    validationStatusLabel.text = status
                }, ModalityState.any())
            }
        )

        // Start the PKCE flow
        pkceHandler?.startAuthFlow()
    }

    // ========== Models Loading and Filtering ==========

    private fun loadModels() {
        if (isLoadingModels || allModels.isNotEmpty()) {
            return // Already loaded or loading
        }

        SetupWizardLogger.logModelLoadingEvent("Starting model load")
        isLoadingModels = true
        modelsLoadingIcon.icon = AllIcons.Process.Step_1
        modelsLoadingIcon.isVisible = true
        modelsLoadingLabel.text = "Loading models from OpenRouter..."
        modelsLoadingLabel.isVisible = true
        updateButtons()

        coroutineScope.launch(Dispatchers.IO) {
            try {
                val models = withTimeout(SetupWizardConfig.MODEL_LOADING_TIMEOUT_MS) {
                    favoriteModelsService.getAvailableModels(forceRefresh = true)
                }

                ApplicationManager.getApplication().invokeLater({
                    isLoadingModels = false
                    if (models != null && models.isNotEmpty()) {
                        SetupWizardLogger.logModelLoadingEvent("Successfully loaded ${models.size} models")
                        allModels = models

                        // Pre-select existing favorites
                        val existingFavorites = settingsService.favoriteModelsManager.getFavoriteModels()
                        selectedModels.clear()
                        selectedModels.addAll(existingFavorites)

                        applyModelFilter()

                        // If no favorites exist, pre-select popular ones
                        if (selectedModels.isEmpty()) {
                            preselectPopularModels()
                        }

                        updateSelectedCount()
                        modelsLoadingIcon.isVisible = false
                        modelsLoadingLabel.isVisible = false
                    } else {
                        SetupWizardLogger.logModelLoadingEvent("Failed to load models or list is empty")
                        modelsLoadingIcon.icon = AllIcons.General.Error
                        modelsLoadingLabel.text = "Failed to load models. Please check your connection."
                        modelsLoadingLabel.foreground = JBColor.RED
                    }
                    updateButtons()
                }, ModalityState.any())
            } catch (e: Exception) {
                SetupWizardLogger.error("Exception in loadModels", e)
                ApplicationManager.getApplication().invokeLater({
                    isLoadingModels = false
                    modelsLoadingIcon.icon = AllIcons.General.Error
                    modelsLoadingLabel.text = SetupWizardErrorHandler.handleModelLoadingError(e)
                    modelsLoadingLabel.foreground = JBColor.RED
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

    // ========== Cleanup ==========

    override fun dispose() {
        super.dispose()
        // Cancel all ongoing operations
        pkceHandler?.cancel()
        validationJob?.cancel()
        // Cancel the coroutine scope to prevent memory leaks
        coroutineScope.coroutineContext.cancel()
    }

    companion object {
        // Use SetupWizardConfig for all constants
        private val DIALOG_WIDTH = SetupWizardConfig.DIALOG_WIDTH
        private val DIALOG_HEIGHT = SetupWizardConfig.DIALOG_HEIGHT
        private val MODELS_TABLE_WIDTH = SetupWizardConfig.MODELS_TABLE_WIDTH
        private val MODELS_TABLE_HEIGHT = SetupWizardConfig.MODELS_TABLE_HEIGHT
        private val TABLE_ROW_HEIGHT = SetupWizardConfig.TABLE_ROW_HEIGHT
        private val CHECKBOX_COLUMN_WIDTH = SetupWizardConfig.CHECKBOX_COLUMN_WIDTH
        private val NAME_COLUMN_WIDTH = SetupWizardConfig.NAME_COLUMN_WIDTH
        private val URL_LABEL_FONT_SIZE = SetupWizardConfig.URL_LABEL_FONT_SIZE
        private val API_KEY_TRUNCATE_LENGTH = SetupWizardConfig.API_KEY_TRUNCATE_LENGTH
        private val DEFAULT_PROXY_PORT = SetupWizardConfig.DEFAULT_PROXY_PORT

        // Wizard step identifiers
        private val STEP_WELCOME = SetupWizardConfig.STEP_WELCOME
        private val STEP_PROVISIONING = SetupWizardConfig.STEP_PROVISIONING
        private val STEP_MODELS = SetupWizardConfig.STEP_MODELS
        private val STEP_COMPLETION = SetupWizardConfig.STEP_COMPLETION

        /**
         * Show the setup wizard dialog
         */
        fun show(project: Project?): Boolean {
            val dialog = SetupWizardDialog(project)
            return dialog.showAndGet()
        }
    }
}
