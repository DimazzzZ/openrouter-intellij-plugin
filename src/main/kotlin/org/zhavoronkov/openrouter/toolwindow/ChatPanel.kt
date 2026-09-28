package org.zhavoronkov.openrouter.toolwindow

import com.google.gson.Gson
import com.google.gson.JsonPrimitive
import com.google.gson.JsonSyntaxException
import com.google.gson.reflect.TypeToken
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.ui.components.JBCheckBox
import com.intellij.util.ui.JBUI
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.zhavoronkov.openrouter.models.ApiResult
import org.zhavoronkov.openrouter.models.ChatCompletionRequest
import org.zhavoronkov.openrouter.models.ChatMessage
import org.zhavoronkov.openrouter.models.OutputSchema
import org.zhavoronkov.openrouter.proxy.routing.RouterCatalog
import org.zhavoronkov.openrouter.proxy.routing.RouterRequestBuilder
import org.zhavoronkov.openrouter.services.OpenRouterService
import org.zhavoronkov.openrouter.services.OpenRouterSettingsService
import org.zhavoronkov.openrouter.toolwindow.chat.ChatComposer
import org.zhavoronkov.openrouter.toolwindow.chat.ChatConversationView
import org.zhavoronkov.openrouter.toolwindow.chat.ChatExchange
import org.zhavoronkov.openrouter.toolwindow.chat.ChatFileWriter
import org.zhavoronkov.openrouter.toolwindow.chat.ChatListView
import org.zhavoronkov.openrouter.toolwindow.chat.ChatParamsPopup
import org.zhavoronkov.openrouter.toolwindow.chat.ChatRequestOptions
import org.zhavoronkov.openrouter.toolwindow.chat.ChatToolbar
import org.zhavoronkov.openrouter.toolwindow.chat.OutputModeContext
import org.zhavoronkov.openrouter.toolwindow.chat.OutputModeGate
import org.zhavoronkov.openrouter.toolwindow.chat.ReplySummary
import org.zhavoronkov.openrouter.ui.ModelVariantChipRenderer
import org.zhavoronkov.openrouter.utils.ModelProviderUtils
import org.zhavoronkov.openrouter.utils.PluginLogger
import java.awt.BorderLayout
import java.awt.CardLayout
import java.awt.event.ItemEvent
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.UUID
import javax.swing.DefaultComboBoxModel
import javax.swing.JOptionPane
import javax.swing.JPanel
import javax.swing.SwingUtilities

/**
 * Chat panel for interacting with OpenRouter models with multiple chat support
 */
// ChatPanel is the coordinator: session state, persistence and the send path.
// The views it used to contain now live in toolwindow/chat and toolwindow/composer.
// Still over detekt's LargeClass/TooManyFunctions thresholds (883 lines, 40 functions).
// Getting under them means extracting saveChats/loadChats and the send path, which is
// a separate piece of work with its own risk - see the redesign plan.
@Suppress("TooManyFunctions", "LargeClass")
class ChatPanel(
    private val project: Project,
    private val settingsService: OpenRouterSettingsService,
    private val openRouterService: OpenRouterService
) {

    companion object {
        private const val PANEL_BORDER = 4
        private const val ACTIVE_CHAT_KEY = "openrouter.chat.activeSession"
        private const val CHATS_FILENAME = "openrouter-chats.json"
        private const val SETTINGS_FILENAME = "openrouter-chat-settings.json"
        private const val CHARS_PER_TOKEN = 4.0
        private const val CARD_LIST = "list"
        private const val CARD_CHAT = "chat"
        private const val TITLE_MAX_LENGTH = 50

        // Sentinel prefix for separator items inserted into the model dropdown
        // to render labeled group headers (Routers / Your Presets / Favorites).
        // Renderers detect this and draw a disabled caption line.
        private const val SEPARATOR_PREFIX = "__SEP__"

        // The gear popup's own comment for a disabled reasoning/verbosity
        // control (ChatParamsPopup.setReasoningSupport/setVerbositySupport).
        // The model name is left out on purpose: it is already visible right
        // next to the gear in the composer's model combo, and repeating it
        // was a major height driver in the popup's two-column layout (see
        // ChatParamsPopup.FORM_WIDTH's doc). The combo's own tooltip
        // (below) keeps the model-specific wording for anyone who hovers.
        private const val PARAM_NOT_SUPPORTED_REASON = "Not supported by this model"
    }

    private val mainPanel: JPanel
    private val cardLayout: CardLayout
    private val contentPanel: JPanel

    // Chat view
    private val modelComboBox: ComboBox<String>
    private val reasoningComboBox: ComboBox<String>
    private val verbosityComboBox: ComboBox<String>
    private val routerParamComboBox: ComboBox<String>

    // Deliberately never reset after a send: a follow-up question in the same investigation should
    // not need the box ticked again, the same way Reasoning and Verbosity stay as they were left.
    private val webSearchCheckBox: JBCheckBox

    // The send parameters (reasoning, verbosity, router param, web search), in
    // a popup off the composer's gear button. Owns no state of its own beyond
    // what these controls already hold.
    private lateinit var paramsPopup: ChatParamsPopup

    // Keeps the Output mode control and Send in step with what the selected Model can serve.
    private lateinit var outputModeGate: OutputModeGate

    // What the selected Model declares in the catalogue, or null when it is not known; read by the
    // Output Mode gating every time the selection or the Model changes.
    private var selectedModelParameters: List<String>? = null

    // Remembers which router-param the combo box currently reflects, so
    // non-selection-driven refresh paths (favorites reload, async init
    // callback) do NOT rebuild the model and silently wipe the user's
    // choice. Rebuild only when the effective param actually changes.
    private var shownRouterParamKey: String? = null

    // Category for each item currently in the model dropdown, keyed by the exact
    // string value. Drives the labeled section headers (Routers / Your Presets /
    // Favorites) rendered via SimpleListCellRenderer.getSeparatorAbove, so users
    // can see that the "lot of default entries" are routers, not presets.
    private enum class ModelGroup(val caption: String) {
        ROUTER("Routers"),
        PRESET("Your Presets"),
        FAVORITE("Favorites")
    }
    private val modelGroups = mutableMapOf<String, ModelGroup>()

    private fun separatorKey(group: ModelGroup) = SEPARATOR_PREFIX + group.caption

    // Chat sessions
    private val chatSessions = mutableListOf<ChatSession>()
    private var activeChatId: String? = null

    private val coroutineScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var isLoading = false
    private val gson = Gson()
    private val dateFormat = SimpleDateFormat("dd.MM.yyyy, HH:mm")

    private val chatListView = ChatListView(dateFormat).apply {
        onOpen = { openChat(it.id) }
        onDelete = { confirmAndDeleteChat(it.id, it.title) }
        onRename = { session, title -> renameChat(session, title) }
    }

    private val composer = ChatComposer().apply {
        onSend = { sendMessage() }
        onTextChanged = { updateInputTokenEstimate(it) }
    }

    private val conversationView = ChatConversationView()

    private val toolbar = ChatToolbar("OpenRouterChatToolbar").apply {
        onBack = { showChatList() }
        onNewChat = { createNewChat() }
        onCopyConversation = { copyConversationToClipboard() }
    }

    init {
        // Initialize components
        modelComboBox = ComboBox<String>()
        modelComboBox.renderer = object : com.intellij.ui.SimpleListCellRenderer<String>() {
            override fun customize(
                list: javax.swing.JList<out String>,
                value: String?,
                index: Int,
                selected: Boolean,
                hasFocus: Boolean
            ) {
                if (value != null) {
                    // Separator items are disabled, rendered as a gray caption line.
                    if (value.startsWith(SEPARATOR_PREFIX)) {
                        text = value.removePrefix(SEPARATOR_PREFIX)
                        isEnabled = false
                        return
                    }
                    text = ModelVariantChipRenderer.renderRow(value)
                    toolTipText = ModelVariantChipRenderer.tooltipFor(value)
                }
            }
        }
        reasoningComboBox = ComboBox<String>()
        verbosityComboBox = ComboBox<String>()
        routerParamComboBox = ComboBox<String>()
        routerParamComboBox.isEditable = true
        webSearchCheckBox = JBCheckBox()

        paramsPopup = ChatParamsPopup(reasoningComboBox, verbosityComboBox, routerParamComboBox, webSearchCheckBox)
        composer.onSettingsClick = { paramsPopup.show(composer.settingsComponent()) }
        // Any selection change on a send parameter can flip whether it is
        // "non-default", so the gear badge/tooltip has to be recomputed from
        // each combo's own change event, not only from the model-capability
        // refresh path (updateReasoningVerbosityState already covers that one
        // via refreshParamsBadge() at its end).
        reasoningComboBox.addActionListener { refreshParamsBadge() }
        verbosityComboBox.addActionListener { refreshParamsBadge() }
        routerParamComboBox.addActionListener { refreshParamsBadge() }
        webSearchCheckBox.addActionListener { refreshParamsBadge() }
        outputModeGate = OutputModeGate(paramsPopup, composer)
        outputModeGate.onSelectionChanged = { refreshParamsBadge() }

        // Create main panel with CardLayout
        cardLayout = CardLayout()
        contentPanel = JPanel(cardLayout)

        mainPanel = JPanel(BorderLayout())
        mainPanel.border = JBUI.Borders.empty(PANEL_BORDER)

        // Toolbar strip above the conversation
        mainPanel.add(toolbar.component, BorderLayout.NORTH)

        // Create chat list view
        contentPanel.add(chatListView.component, CARD_LIST)

        // Create chat view
        val chatView = createChatView()
        contentPanel.add(chatView, CARD_CHAT)

        mainPanel.add(contentPanel, BorderLayout.CENTER)

        // Setup input area
        setupInputArea()

        // Load models and restore selection
        loadFavoriteModels()
        restoreSelectedModel()

        // Save model selection when changed and update reasoning/verbosity visibility
        modelComboBox.addItemListener { e ->
            if (e.stateChange == ItemEvent.SELECTED) {
                // Separator rows are visual-only; if the user lands on one
                // (keyboard nav / click), jump to the next real item so the
                // rest of the panel never sees a separator key.
                val selected = modelComboBox.selectedItem as? String
                if (selected != null && selected.startsWith(SEPARATOR_PREFIX)) {
                    val next = firstRealModelAfter(modelComboBox.selectedIndex)
                    if (next != null) {
                        modelComboBox.selectedIndex = next
                    }
                    return@addItemListener
                }
                saveSelectedModel()
                updateReasoningVerbosityState()
            }
        }

        // Set initial reasoning/verbosity state after model is restored
        coroutineScope.launch {
            try {
                org.zhavoronkov.openrouter.services.FavoriteModelsService.getInstance()
                    .getAvailableModels(forceRefresh = false)
            } catch (_: Exception) { }
            SwingUtilities.invokeLater {
                updateReasoningVerbosityState()
            }
        }

        // Load saved chats
        loadChats()
    }

    private fun createChatView(): JPanel {
        val panel = JPanel(BorderLayout())

        // The send-parameter controls are placed by ChatParamsPopup, opened from the composer's
        // gear button, not by this panel. ChatPanel still owns them: their model and enabled state
        // are set here, and the popup reads their selection into each request. The choices come
        // from ChatExchange rather than from lists here, because it is what turns them into a
        // request: a label this panel offers but that module cannot translate would be dropped
        // from the request silently instead of failing to compile.
        reasoningComboBox.model = DefaultComboBoxModel(ChatExchange.REASONING_CHOICES.toTypedArray())
        reasoningComboBox.toolTipText = "Reasoning effort (for supported models)"

        verbosityComboBox.model = DefaultComboBoxModel(ChatExchange.VERBOSITY_CHOICES.toTypedArray())
        verbosityComboBox.toolTipText = "Response verbosity (for supported models)"

        routerParamComboBox.toolTipText = "Router parameter (for openrouter/* routers)"

        // Messages area
        panel.add(conversationView.component, BorderLayout.CENTER)

        // Bottom panel with input. The model combo moves into the composer's
        // own control row here; its model, renderer content and ItemListener
        // stay in ChatPanel, only the placement changes.
        composer.attachModelCombo(modelComboBox)
        panel.add(composer.component, BorderLayout.SOUTH)

        return panel
    }

    private fun setupInputArea() {
        composer.inputComponent().addKeyListener(object : KeyAdapter() {
            override fun keyPressed(e: KeyEvent) {
                when {
                    e.keyCode == KeyEvent.VK_ENTER && (e.isControlDown || e.isMetaDown) -> {
                        e.consume()
                        val caretPos = composer.inputComponent().caretPosition
                        composer.inputComponent().insert("\n", caretPos)
                    }
                    e.keyCode == KeyEvent.VK_ENTER && !e.isShiftDown && !e.isControlDown && !e.isMetaDown -> {
                        e.consume()
                        sendMessage()
                    }
                }
            }
        })
    }

    private fun showChatList() {
        toolbar.setInConversation(false)
        toolbar.setTitle("Chats")
        cardLayout.show(contentPanel, CARD_LIST)
        updateChatList()
    }

    private fun createNewChat() {
        val newChat = ChatSession(
            id = UUID.randomUUID().toString(),
            title = "New Chat",
            messages = mutableListOf(),
            totalTokens = 0,
            createdAt = System.currentTimeMillis()
        )
        chatSessions.add(0, newChat)
        saveChats()
        openChat(newChat.id)
    }

    private fun openChat(chatId: String) {
        activeChatId = chatId
        val chat = chatSessions.find { it.id == chatId }

        toolbar.setInConversation(true)
        toolbar.setTitle(chat?.title ?: "Chat")

        conversationView.clear()

        if (chat != null) {
            displayChatMessages(chat)
            updateTokenDisplay(chat.totalTokens)
        } else {
            showWelcomeMessage()
        }

        conversationView.refreshAfterDisplay()

        saveActiveChat()
        cardLayout.show(contentPanel, CARD_CHAT)
        composer.requestFocusInInput()
    }

    private fun displayChatMessages(chat: ChatSession) {
        if (chat.messages.isEmpty()) {
            showWelcomeMessage()
            return
        }
        for (msg in chat.messages) {
            when (msg.role) {
                "user" -> conversationView.addMessage(msg.content, isUser = true)
                "assistant" -> showAssistantMessage(msg)
                "system" -> conversationView.addSystemMessage(msg.content)
            }
        }
    }

    private fun confirmAndDeleteChat(chatId: String, title: String) {
        val result = JOptionPane.showConfirmDialog(
            mainPanel,
            "Delete chat \"$title\"?",
            "Confirm Delete",
            JOptionPane.YES_NO_OPTION,
            JOptionPane.WARNING_MESSAGE
        )

        if (result == JOptionPane.YES_OPTION) {
            deleteChat(chatId)
        }
    }

    private fun deleteChat(chatId: String) {
        chatSessions.removeIf { it.id == chatId }
        updateChatList()
        saveChats()
    }

    private fun updateChatList() {
        chatListView.setSessions(chatSessions)
    }

    private fun renameChat(session: ChatSession, title: String) {
        session.title = title
        saveChats()
        updateChatList()
        // The chat-list card (where rename is reachable) and the conversation
        // card (where the toolbar shows a per-chat title) are mutually
        // exclusive under the CardLayout in `contentPanel` — renaming the
        // active chat cannot happen while its title is on screen. This guard
        // is defensive only, in case that invariant ever changes.
        if (session.id == activeChatId) {
            toolbar.setTitle(title)
        }
    }

    private fun getChatsFile(): File {
        val configPath = PathManager.getConfigPath()
        val pluginDir = File(configPath, "openrouter")
        if (!pluginDir.exists()) {
            pluginDir.mkdirs()
        }
        return File(pluginDir, CHATS_FILENAME)
    }

    private fun loadChats() {
        try {
            val file = getChatsFile()
            if (file.exists()) {
                val json = file.readText()
                val type = object : TypeToken<List<ChatSession>>() {}.type
                val loaded: List<ChatSession> = gson.fromJson(json, type)
                chatSessions.clear()
                chatSessions.addAll(loaded)
            }
        } catch (e: JsonSyntaxException) {
            PluginLogger.warn("Failed to parse chat sessions: ${e.message}")
        } catch (e: IOException) {
            PluginLogger.warn("Failed to load chat sessions: ${e.message}")
        }

        updateChatList()

        // Restore active chat or show list
        val savedActiveId = PropertiesComponent.getInstance(project).getValue(ACTIVE_CHAT_KEY)
        if (savedActiveId != null && chatSessions.any { it.id == savedActiveId }) {
            openChat(savedActiveId)
        } else {
            showChatList()
        }
    }

    private fun saveChats() {
        try {
            // Serialised here, written on a background thread: this runs on the EDT after every
            // finished reply and on every chat create/delete, and the file grows with the
            // conversation. See ChatFileWriter.
            val json = gson.toJson(chatSessions)
            ChatFileWriter.write(getChatsFile(), json)
        } catch (e: IOException) {
            PluginLogger.warn("Failed to save chat sessions: ${e.message}")
        }
    }

    private fun saveActiveChat() {
        activeChatId?.let {
            PropertiesComponent.getInstance(project).setValue(ACTIVE_CHAT_KEY, it)
        }
    }

    private fun updateInputTokenEstimate(text: String) {
        val estimatedTokens = if (text.isEmpty()) {
            0
        } else {
            (text.length / CHARS_PER_TOKEN).toInt().coerceAtLeast(1)
        }
        composer.setInputTokens("~$estimatedTokens tokens")
    }

    private fun updateTokenDisplay(tokens: Int = 0) {
        composer.setStatus(if (tokens > 0) "Total: $tokens" else "")
    }

    fun refreshModels() {
        loadFavoriteModels()
        coroutineScope.launch {
            try {
                org.zhavoronkov.openrouter.services.FavoriteModelsService.getInstance()
                    .getAvailableModels(forceRefresh = false)
            } catch (_: Exception) { }
            SwingUtilities.invokeLater {
                updateReasoningVerbosityState()
            }
        }
    }

    private fun loadFavoriteModels() {
        val favorites = settingsService.favoriteModelsManager.getFavoriteModels()
        val customPresets = settingsService.presetsManager.getCustomPresets()
        val model = DefaultComboBoxModel<String>()
        modelGroups.clear()

        var lastGroup: ModelGroup? = null
        fun addSeparatorIfNewGroup(group: ModelGroup) {
            if (lastGroup != group) {
                model.addElement(separatorKey(group))
                lastGroup = group
            }
        }

        // Add first-class routers first, sourced from the single catalog so every
        // router slug (auto, fusion, fusion-flash, pareto-code, free) is selectable
        // and its param panel becomes reachable. See RouterCatalog.
        addSeparatorIfNewGroup(ModelGroup.ROUTER)
        RouterCatalog.slugs.forEach { slug ->
            model.addElement(slug)
            modelGroups[slug] = ModelGroup.ROUTER
        }

        // Add custom presets (with @preset/ prefix), skipping any that collide
        // with a catalog router slug.
        customPresets.forEach { presetSlug ->
            val presetModelId = settingsService.presetsManager.getPresetModelId(presetSlug)
            if (!RouterCatalog.isRouter(presetModelId)) {
                addSeparatorIfNewGroup(ModelGroup.PRESET)
                model.addElement(presetModelId)
                modelGroups[presetModelId] = ModelGroup.PRESET
            }
        }

        // Add separator if we have presets and favorites
        val hasPresets = model.size > 0
        val hasFavorites = favorites.isNotEmpty()

        // Add favorite models
        if (hasFavorites) {
            favorites.forEach {
                if (!RouterCatalog.isRouter(it) && !modelGroups.containsKey(it)) {
                    addSeparatorIfNewGroup(ModelGroup.FAVORITE)
                    model.addElement(it)
                    modelGroups[it] = ModelGroup.FAVORITE
                }
            }
        } else if (!hasPresets) {
            // Fallback if no favorites and no presets configured
            listOf("openai/gpt-4o", "anthropic/claude-3.5-sonnet").forEach {
                addSeparatorIfNewGroup(ModelGroup.FAVORITE)
                model.addElement(it)
                modelGroups[it] = ModelGroup.FAVORITE
            }
        }

        modelComboBox.model = model
        // Never leave a separator row selected as the default (index 0 is the
        // "Routers" header). Land on the first real model instead.
        val current = modelComboBox.selectedItem as? String
        if (current == null || current.startsWith(SEPARATOR_PREFIX)) {
            firstRealModelAfter(-1)?.let { modelComboBox.selectedIndex = it }
        }
    }

    /**
     * Index of the first non-separator item strictly after [index], or null if
     * none. Used to skip the visual group-header rows in the model dropdown.
     */
    private fun firstRealModelAfter(index: Int): Int? {
        for (i in (index + 1) until modelComboBox.itemCount) {
            val v = modelComboBox.getItemAt(i)
            if (v != null && !v.startsWith(SEPARATOR_PREFIX)) return i
        }
        return null
    }

    private fun getSettingsFile(): File {
        val configPath = PathManager.getConfigPath()
        val pluginDir = File(configPath, "openrouter")
        if (!pluginDir.exists()) {
            pluginDir.mkdirs()
        }
        return File(pluginDir, SETTINGS_FILENAME)
    }

    private fun saveSelectedModel() {
        val selected = modelComboBox.selectedItem as? String ?: return
        if (selected.startsWith(SEPARATOR_PREFIX)) return
        try {
            val settings = mutableMapOf<String, String>()
            settings["selectedModel"] = selected
            val json = gson.toJson(settings)
            ChatFileWriter.write(getSettingsFile(), json)
        } catch (e: IOException) {
            PluginLogger.warn("Failed to save selected model: ${e.message}")
        }
    }

    private fun restoreSelectedModel() {
        try {
            val file = getSettingsFile()
            if (!file.exists()) {
                return
            }
            val json = file.readText()
            val type = object : TypeToken<Map<String, String>>() {}.type
            val settings: Map<String, String> = gson.fromJson(json, type)
            val saved = settings["selectedModel"] ?: return
            selectModelInComboBox(saved)
        } catch (e: JsonSyntaxException) {
            PluginLogger.warn("Failed to parse settings: ${e.message}")
        } catch (e: IOException) {
            PluginLogger.warn("Failed to load settings: ${e.message}")
        }
    }

    private fun selectModelInComboBox(modelName: String) {
        for (i in 0 until modelComboBox.itemCount) {
            if (modelComboBox.getItemAt(i) == modelName) {
                modelComboBox.selectedIndex = i
                break
            }
        }
    }

    private fun updateReasoningVerbosityState() {
        val selectedModel = modelComboBox.selectedItem as? String ?: return
        if (selectedModel.startsWith(SEPARATOR_PREFIX)) return

        val favoriteModelsService = org.zhavoronkov.openrouter.services.FavoriteModelsService.getInstance()
        val modelInfo = favoriteModelsService.getModelById(selectedModel)

        val supportsReasoning = modelInfo?.let {
            ModelProviderUtils.hasCapability(it, ModelProviderUtils.Capability.REASONING)
        } ?: false

        val supportsVerbosity = modelInfo?.let {
            ModelProviderUtils.hasCapability(it, ModelProviderUtils.Capability.VERBOSITY)
        } ?: false

        paramsPopup.setReasoningSupport(supportsReasoning, PARAM_NOT_SUPPORTED_REASON)
        paramsPopup.setVerbositySupport(supportsVerbosity, PARAM_NOT_SUPPORTED_REASON)
        selectedModelParameters = modelInfo?.supportedParameters
        outputModeGate.update(OutputModeContext(selectedModel, selectedModelParameters, savedSchemas()))

        reasoningComboBox.toolTipText = if (supportsReasoning) {
            "Reasoning effort"
        } else {
            "$selectedModel does not support reasoning"
        }

        verbosityComboBox.toolTipText = if (supportsVerbosity) {
            "Response verbosity"
        } else {
            "$selectedModel does not support verbosity"
        }

        // Reset rather than kept and blocked, unlike an Output Mode. Reasoning and verbosity are
        // hints about how to answer; a Model without them still answers the same question, and
        // the disabled control says why it reads Default. An Output Mode is a promise about the
        // reply's shape, which the user would otherwise believe was kept.
        if (!supportsReasoning) reasoningComboBox.selectedIndex = 0
        if (!supportsVerbosity) verbosityComboBox.selectedIndex = 0

        // updateRouterParamState refreshes the gear badge itself (it always
        // runs, so it's the single place that has to cover both this method's
        // reasoning/verbosity changes and its own router-param change).
        updateRouterParamState(selectedModel)
    }

    private fun savedSchemas(): List<OutputSchema> = settingsService.outputSchemasManager.all()

    /** Recomputes the gear badge/tooltip from the current combo selections (spec D7). */
    private fun refreshParamsBadge() {
        composer.setSettingsBadge(paramsPopup.hasNonDefaultSelection())
        composer.setSettingsTooltip(paramsPopup.activeSummary())
    }

    /**
     * Show and populate the router-param control when [selectedModel] is a
     * router that takes a parameter; hide it otherwise. All router knowledge
     * comes from RouterCatalog — no per-router branching here.
     */
    private fun updateRouterParamState(selectedModel: String) {
        val update = RouterRequestBuilder.paramControlUpdate(shownRouterParamKey, selectedModel)
        shownRouterParamKey = update.paramKey

        // Visible only when selectedModel is a router with a param (see
        // paramControlUpdate); null otherwise, including the not-visible case.
        val param = if (update.visible) RouterCatalog.find(selectedModel)?.param else null
        // For free-type params, flag the label editable so the caret-editable
        // field isn't mistaken for a closed dropdown.
        val editableHint = if (param?.freeText == true) " (editable)" else ""
        paramsPopup.setRouterParam(
            label = param?.label?.plus(editableHint),
            description = param?.description,
            visible = update.visible
        )
        refreshParamsBadge()

        if (!update.visible || !update.rebuild) return
        // The effective param changed, so (re)build the control. This resets
        // the chosen value; the pure helper guarantees we only reach here on a
        // real change, never on a stray refresh (see paramControlUpdate).
        val effectiveParam = param ?: return
        routerParamComboBox.toolTipText = effectiveParam.description
        val model = DefaultComboBoxModel(effectiveParam.suggestions.toTypedArray())
        routerParamComboBox.model = model
        // Editable enums and float ranges accept free text; closed enums do not.
        routerParamComboBox.isEditable = effectiveParam.freeText
        // Seed the control with the user's persisted default for this router
        // (Settings → OpenRouter → Router Defaults), falling back to blank when
        // none is saved. Blank means "no selection → OpenRouter default". For a
        // closed enum the blank first row is also how the user clears a choice.
        val savedDefault = settingsService.routerDefaultsManager.get(selectedModel)
        routerParamComboBox.selectedItem = savedDefault ?: ""
    }

    private fun showWelcomeMessage() {
        conversationView.addSystemMessage("Welcome! Press Enter to send, Cmd+Enter for new line.")
    }

    @Suppress("ReturnCount")
    private fun sendMessage() {
        // Not composer.canSend: Enter reaches here with Send disabled, and a blocked send must say
        // why below rather than do nothing.
        if (isLoading) return

        val userMessage = composer.text.trim()
        if (userMessage.isEmpty()) return

        val selectedModel = modelComboBox.selectedItem as? String
        if (selectedModel.isNullOrEmpty() || selectedModel.startsWith(SEPARATOR_PREFIX)) {
            conversationView.showError("Please select a model")
            return
        }

        if (!settingsService.isConfigured()) {
            conversationView.showError("OpenRouter is not configured. Please set your API key in settings.")
            return
        }

        // Read on the EDT, at the moment of sending, and checked against the same rule that blocks
        // Send: what goes out is exactly what the controls showed when the user sent it. The saved
        // schemas are re-read first, so a schema deleted since the popup was last brought up to
        // date blocks this send - and says why - rather than being sent or silently ignored.
        val context = OutputModeContext(selectedModel, selectedModelParameters, savedSchemas())
        outputModeGate.update(context)
        outputModeGate.blockedReason()?.let { reason ->
            conversationView.showError(reason)
            return
        }
        val options = paramsPopup.requestOptions()

        composer.text = ""
        composer.requestFocusInInput()

        addUserMessage(userMessage)

        val currentChat = chatSessions.find { it.id == activeChatId }
        currentChat?.messages?.add(ChatMessageData("user", userMessage))

        // Update chat title if first message
        if (currentChat != null && currentChat.messages.size == 1) {
            currentChat.title = generateChatTitle(userMessage)
            toolbar.setTitle(currentChat.title)
            updateChatList()
        }

        setLoading(true)

        coroutineScope.launch {
            sendChatRequest(selectedModel, currentChat, options, context.schemas)
        }
    }

    private fun generateChatTitle(message: String): String {
        return if (message.length > TITLE_MAX_LENGTH) {
            message.take(TITLE_MAX_LENGTH) + "..."
        } else {
            message
        }
    }

    private suspend fun sendChatRequest(
        model: String,
        currentChat: ChatSession?,
        options: ChatRequestOptions,
        schemas: List<OutputSchema>
    ) {
        try {
            val messages = currentChat?.messages?.map { msg ->
                ChatMessage(role = msg.role, content = JsonPrimitive(msg.content))
            } ?: emptyList()

            val request = ChatExchange.buildRequest(
                model = model,
                messages = messages,
                options = options,
                webSearch = settingsService.webSearchManager.current(),
                schemas = schemas
            )

            val result = openRouterService.createChatCompletion(request)

            SwingUtilities.invokeLater {
                handleChatResponse(result, currentChat, request)
            }
        } catch (e: IOException) {
            SwingUtilities.invokeLater {
                conversationView.showError("Network error: ${e.message}")
                setLoading(false)
            }
        } catch (e: IllegalArgumentException) {
            // ChatExchange refuses to build a request whose Output Mode it cannot express, which
            // sendMessage's check rules out for the snapshot it passes here; reaching this means a
            // caller skipped that check. Said plainly, and the chat is left usable rather than
            // stuck waiting for a reply that is never requested.
            PluginLogger.warn("Could not build the chat request: ${e.message}")
            SwingUtilities.invokeLater {
                conversationView.showError("Could not send: ${e.message}")
                setLoading(false)
                outputModeGate.update(outputModeGate.context)
            }
        }
    }

    private fun handleChatResponse(
        result: ApiResult<org.zhavoronkov.openrouter.models.ChatCompletionResponse>,
        currentChat: ChatSession?,
        request: ChatCompletionRequest
    ) {
        setLoading(false)
        composer.requestFocusInInput()

        when (result) {
            is ApiResult.Success -> handleSuccessResponse(result.data, currentChat, request)
            is ApiResult.Error -> conversationView.showError("Error: ${result.message}")
        }
    }

    private fun handleSuccessResponse(
        response: org.zhavoronkov.openrouter.models.ChatCompletionResponse,
        currentChat: ChatSession?,
        request: ChatCompletionRequest
    ) {
        val assistantMessage = response.choices?.firstOrNull()?.message?.content
        if (assistantMessage == null) {
            conversationView.showError("No response from model")
            return
        }

        val messageText = extractMessageText(assistantMessage)
        // How the reply was produced, echoed under it as a small footnote rather than as a
        // separate system line. Shown from the saved message itself, so a live reply and a
        // reopened one go through the same rendering and cannot disagree.
        val reply = ChatMessageData.reply(messageText, ChatExchange.summarizeReply(request, response))
        showAssistantMessage(reply)
        currentChat?.messages?.add(reply)

        val usage = response.usage
        if (usage != null) {
            val tokens = usage.totalTokens ?: 0
            currentChat?.let { it.totalTokens += tokens }
            updateTokenDisplay(currentChat?.totalTokens ?: 0)
        }

        saveChats()
    }

    private fun extractMessageText(content: com.google.gson.JsonElement): String {
        return when {
            content.isJsonPrimitive -> content.asString
            content.isJsonArray -> {
                content.asJsonArray.mapNotNull { element ->
                    when {
                        element.isJsonObject -> {
                            val obj = element.asJsonObject
                            if (obj.has("text")) obj.get("text").asString else null
                        }
                        element.isJsonPrimitive -> element.asString
                        else -> null
                    }
                }.joinToString("")
            }
            else -> content.toString()
        }
    }

    private fun addUserMessage(message: String) = conversationView.addMessage(message, isUser = true)
    private fun showAssistantMessage(message: ChatMessageData) =
        conversationView.addMessage(
            message.content,
            isUser = false,
            footnote = message.footerFacts,
            warning = message.footerWarning
        )

    private fun setLoading(loading: Boolean) {
        isLoading = loading
        composer.setBusy(loading)
        modelComboBox.isEnabled = !loading

        if (loading) {
            composer.setStatus("Thinking...")
            conversationView.showLoading()
        } else {
            // Clearing the status is not optional: only the success path with
            // usage data overwrites it (via updateTokenDisplay, which runs after
            // this). Every error path - ApiResult.Error, the IOException catch,
            // "No response from model" - and a success with no usage block would
            // otherwise leave "Thinking..." sitting next to the model name for
            // the rest of the session.
            composer.setStatus("")
            conversationView.hideLoading()
        }
    }

    private fun copyConversationToClipboard() {
        val chat = chatSessions.find { it.id == activeChatId } ?: return
        val text = chat.messages.joinToString("\n\n") { "${it.role}: ${it.content}" }
        java.awt.datatransfer.StringSelection(text).let {
            java.awt.Toolkit.getDefaultToolkit().systemClipboard.setContents(it, it)
        }
    }

    fun getPanel(): JPanel = mainPanel

    fun dispose() {
        saveChats()
        coroutineScope.cancel()
    }

    /**
     * One saved message. A reply also carries its [ReplySummary] - which model answered, which
     * provider served it, what it cost and why it stopped - as structured fields, so that
     * reopening a chat renders the footer afresh, warning included, and the footer's wording can
     * change without stranding old messages. Stored with the message rather than derived on
     * display, because by the time a chat is reopened the only model the panel still knows about
     * is the one selected now, which is the wrong answer for every message that predates the last
     * time the picker changed.
     *
     * [footnote] holds an already-rendered footer line. It is read only when [summary] is absent,
     * and a message with a summary leaves it null, which Gson does not write.
     *
     * Every field past [content] is nullable and defaults to null so a chat saved without it still
     * loads: Gson leaves an absent field null.
     */
    data class ChatMessageData(
        val role: String,
        val content: String,
        val footnote: String? = null,
        val summary: ReplySummary? = null
    ) {
        /** The footer's line of facts: rendered from [summary], or else the stored [footnote]. */
        val footerFacts: String?
            get() = summary?.facts ?: footnote

        val footerWarning: String?
            get() = summary?.warning

        companion object {
            fun reply(content: String, summary: ReplySummary) =
                ChatMessageData(role = "assistant", content = content, summary = summary)
        }
    }

    data class ChatSession(
        val id: String,
        var title: String,
        val messages: MutableList<ChatMessageData>,
        var totalTokens: Int,
        val createdAt: Long
    )
}
