package org.zhavoronkov.openrouter.toolwindow

import com.google.gson.Gson
import com.google.gson.JsonPrimitive
import com.google.gson.JsonSyntaxException
import com.google.gson.reflect.TypeToken
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.ui.ColorUtil
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.intellij.util.ui.JBUI
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.zhavoronkov.openrouter.models.ApiResult
import org.zhavoronkov.openrouter.models.ChatCompletionRequest
import org.zhavoronkov.openrouter.models.ChatMessage
import org.zhavoronkov.openrouter.models.ReasoningConfig
import org.zhavoronkov.openrouter.proxy.routing.RouterCatalog
import org.zhavoronkov.openrouter.proxy.routing.RouterRequestBuilder
import org.zhavoronkov.openrouter.services.OpenRouterService
import org.zhavoronkov.openrouter.services.OpenRouterSettingsService
import org.zhavoronkov.openrouter.ui.ModelVariantChipRenderer
import org.zhavoronkov.openrouter.utils.MarkdownRenderer
import org.zhavoronkov.openrouter.utils.ModelProviderUtils
import org.zhavoronkov.openrouter.utils.PluginLogger
import java.awt.BorderLayout
import java.awt.CardLayout
import java.awt.Component
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.event.ItemEvent
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.UUID
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.DefaultComboBoxModel
import javax.swing.DefaultListCellRenderer
import javax.swing.DefaultListModel
import javax.swing.JButton
import javax.swing.JEditorPane
import javax.swing.JList
import javax.swing.JMenuItem
import javax.swing.JOptionPane
import javax.swing.JPanel
import javax.swing.JPopupMenu
import javax.swing.JScrollBar
import javax.swing.SwingUtilities
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener

/**
 * Chat panel for interacting with OpenRouter models with multiple chat support
 */
@Suppress("TooManyFunctions", "LargeClass")
class ChatPanel(
    private val project: Project,
    private val settingsService: OpenRouterSettingsService,
    private val openRouterService: OpenRouterService
) {

    companion object {
        private const val PANEL_BORDER = 4
        private const val INPUT_ROWS = 2
        private const val INPUT_COLUMNS = 30
        private const val MAX_TOKENS = 4096
        private const val TEMPERATURE = 0.7
        private const val ACTIVE_CHAT_KEY = "openrouter.chat.activeSession"
        private const val CHATS_FILENAME = "openrouter-chats.json"
        private const val SETTINGS_FILENAME = "openrouter-chat-settings.json"
        private const val CHARS_PER_TOKEN = 4.0
        private const val CARD_LIST = "list"
        private const val CARD_CHAT = "chat"
        private const val MIN_TEXT_AREA_WIDTH = 100
        private const val HEADER_FONT_SIZE_INCREASE = 2f
        private const val FLOW_LAYOUT_GAP = 4
        private const val COMBO_BOX_WIDTH = 180
        private const val SETTINGS_COMBO_BOX_WIDTH = 90
        private const val TITLE_MAX_LENGTH = 50
        private const val MESSAGE_BORDER_V = 1
        private const val MESSAGE_BORDER_H = 2
        private const val CELL_BORDER_V = 4
        private const val CELL_BORDER_H = 8

        // Sentinel prefix for separator items inserted into the model dropdown
        // to render labeled group headers (Routers / Your Presets / Favorites).
        // Renderers detect this and draw a disabled caption line.
        private const val SEPARATOR_PREFIX = "__SEP__"
    }

    private val mainPanel: JPanel
    private val cardLayout: CardLayout
    private val contentPanel: JPanel

    // Chat list view
    private val chatListModel = DefaultListModel<ChatSession>()
    private val chatList: JBList<ChatSession>

    // Chat view
    private lateinit var messagesPanel: JPanel
    private lateinit var messagesScrollPane: JBScrollPane
    private lateinit var reasoningVerbosityPanel: JPanel
    private val inputArea: JBTextArea
    private val sendButton: JButton
    private val modelComboBox: ComboBox<String>
    private val reasoningComboBox: ComboBox<String>
    private val verbosityComboBox: ComboBox<String>
    private val routerParamComboBox: ComboBox<String>
    private lateinit var routerParamLabel: JBLabel
    private lateinit var routerParamHelpLabel: JBLabel

    // Remembers which router-param the combo box currently reflects, so
    // non-selection-driven refresh paths (favorites reload, async init
    // callback) do NOT rebuild the model and silently wipe the user's
    // choice. Rebuild only when the effective param actually changes.
    private var shownRouterParamKey: String? = null
    private val statusLabel: JBLabel
    private val inputTokensLabel: JBLabel

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

    init {
        // Initialize components
        statusLabel = JBLabel("")
        inputTokensLabel = JBLabel("~0 tokens")
        inputArea = JBTextArea(INPUT_ROWS, INPUT_COLUMNS)
        sendButton = JButton("Send")
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
        chatList = JBList(chatListModel)

        // Create main panel with CardLayout
        cardLayout = CardLayout()
        contentPanel = JPanel(cardLayout)

        mainPanel = JPanel(BorderLayout())
        mainPanel.border = JBUI.Borders.empty(PANEL_BORDER)

        // Create header
        val headerPanel = createHeaderPanel()
        mainPanel.add(headerPanel, BorderLayout.NORTH)

        // Create chat list view
        val listView = createChatListView()
        contentPanel.add(listView, CARD_LIST)

        // Create chat view
        val chatView = createChatView()
        contentPanel.add(chatView, CARD_CHAT)

        mainPanel.add(contentPanel, BorderLayout.CENTER)

        // Setup input area
        setupInputArea()

        // Setup chat list
        setupChatList()

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

    private fun createHeaderPanel(): JPanel {
        val panel = JPanel(BorderLayout())
        panel.border = JBUI.Borders.emptyBottom(FLOW_LAYOUT_GAP)

        // Left: title
        val titleLabel = JBLabel("AI Chat")
        titleLabel.font = titleLabel.font.deriveFont(titleLabel.font.size + HEADER_FONT_SIZE_INCREASE)
        panel.add(titleLabel, BorderLayout.WEST)

        // Right: buttons
        val buttonsPanel = JPanel(FlowLayout(FlowLayout.RIGHT, FLOW_LAYOUT_GAP, 0))

        val newChatButton = JButton("+ New Chat")
        newChatButton.addActionListener { createNewChat() }
        buttonsPanel.add(newChatButton)

        panel.add(buttonsPanel, BorderLayout.EAST)

        return panel
    }

    private fun createChatListView(): JPanel {
        val panel = JPanel(BorderLayout())

        chatList.cellRenderer = ChatListCellRenderer()
        val listScrollPane = JBScrollPane(chatList)
        listScrollPane.border = JBUI.Borders.empty()
        panel.add(listScrollPane, BorderLayout.CENTER)

        return panel
    }

    private fun createChatView(): JPanel {
        val panel = JPanel(BorderLayout())

        // Top panel with two rows
        val topPanel = JPanel()
        topPanel.layout = BoxLayout(topPanel, BoxLayout.Y_AXIS)
        topPanel.border = JBUI.Borders.emptyBottom(FLOW_LAYOUT_GAP)

        // Row 1: Back button (left) + Model selector (right)
        val row1 = JPanel(BorderLayout())
        val backButton = JButton("← Back")
        backButton.addActionListener { showChatList() }
        row1.add(backButton, BorderLayout.WEST)

        val modelPanel = JPanel(FlowLayout(FlowLayout.RIGHT, FLOW_LAYOUT_GAP, 0))
        modelPanel.add(JBLabel("Model:"))
        modelComboBox.preferredSize = Dimension(COMBO_BOX_WIDTH, modelComboBox.preferredSize.height)
        modelPanel.add(modelComboBox)
        row1.add(modelPanel, BorderLayout.EAST)
        topPanel.add(row1)

        // Row 2: Reasoning + Verbosity (right-aligned, initially hidden)
        val reasoningOptions = arrayOf("Default", "None", "Minimal", "Low", "Medium", "High", "XHigh")
        reasoningComboBox.model = DefaultComboBoxModel(reasoningOptions)
        reasoningComboBox.preferredSize = Dimension(SETTINGS_COMBO_BOX_WIDTH, reasoningComboBox.preferredSize.height)
        reasoningComboBox.toolTipText = "Reasoning effort (for supported models)"

        val verbosityOptions = arrayOf("Default", "Low", "Medium", "High", "XHigh", "Max")
        verbosityComboBox.model = DefaultComboBoxModel(verbosityOptions)
        verbosityComboBox.preferredSize = Dimension(SETTINGS_COMBO_BOX_WIDTH, verbosityComboBox.preferredSize.height)
        verbosityComboBox.toolTipText = "Response verbosity (for supported models)"

        reasoningVerbosityPanel = JPanel(FlowLayout(FlowLayout.RIGHT, FLOW_LAYOUT_GAP, 0))
        reasoningVerbosityPanel.add(JBLabel("Reasoning:"))
        reasoningVerbosityPanel.add(reasoningComboBox)
        reasoningVerbosityPanel.add(JBLabel("Verbosity:"))
        reasoningVerbosityPanel.add(verbosityComboBox)
        routerParamLabel = JBLabel("Router:")
        routerParamComboBox.preferredSize =
            Dimension(SETTINGS_COMBO_BOX_WIDTH, routerParamComboBox.preferredSize.height)
        routerParamComboBox.toolTipText = "Router parameter (for openrouter/* routers)"
        reasoningVerbosityPanel.add(routerParamLabel)
        reasoningVerbosityPanel.add(routerParamComboBox)
        // Inline help text surfaces the accepted values that used to hide in the
        // combo tooltip nobody hovers (e.g. "Suggested: general-fast (or type
        // your own)"). Rendered as gray sub-label beside the control.
        routerParamHelpLabel = JBLabel().apply {
            foreground = com.intellij.util.ui.UIUtil.getContextHelpForeground()
            isVisible = false
        }
        reasoningVerbosityPanel.add(routerParamHelpLabel)
        reasoningVerbosityPanel.isVisible = false
        topPanel.add(reasoningVerbosityPanel)

        topPanel.add(Box.createVerticalStrut(0)) // spacer
        panel.add(topPanel, BorderLayout.NORTH)

        // Messages area
        messagesPanel = JPanel()
        messagesPanel.layout = BoxLayout(messagesPanel, BoxLayout.Y_AXIS)
        messagesPanel.background = JBUI.CurrentTheme.ToolWindow.background()

        messagesScrollPane = JBScrollPane(messagesPanel)
        messagesScrollPane.verticalScrollBarPolicy = JBScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED
        messagesScrollPane.horizontalScrollBarPolicy = JBScrollPane.HORIZONTAL_SCROLLBAR_NEVER
        messagesScrollPane.border = JBUI.Borders.empty()
        panel.add(messagesScrollPane, BorderLayout.CENTER)

        // Bottom panel with input
        val bottomPanel = createBottomPanel()
        panel.add(bottomPanel, BorderLayout.SOUTH)

        return panel
    }

    private fun createBottomPanel(): JPanel {
        val panel = JPanel(BorderLayout())
        panel.border = JBUI.Borders.emptyTop(FLOW_LAYOUT_GAP)

        // Input area
        inputArea.lineWrap = true
        inputArea.wrapStyleWord = true
        inputArea.border = JBUI.Borders.empty(FLOW_LAYOUT_GAP)

        val inputScrollPane = JBScrollPane(inputArea)
        inputScrollPane.verticalScrollBarPolicy = JBScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED
        panel.add(inputScrollPane, BorderLayout.CENTER)

        // Bottom row with token info and send button
        val bottomRow = JPanel(BorderLayout())

        // Left side: input token estimation
        inputTokensLabel.foreground = JBUI.CurrentTheme.Label.disabledForeground()
        bottomRow.add(inputTokensLabel, BorderLayout.WEST)

        // Right side: total tokens and send button
        val rightPanel = JPanel(FlowLayout(FlowLayout.RIGHT, FLOW_LAYOUT_GAP, 0))
        rightPanel.add(statusLabel)
        sendButton.addActionListener { sendMessage() }
        rightPanel.add(sendButton)
        bottomRow.add(rightPanel, BorderLayout.EAST)

        panel.add(bottomRow, BorderLayout.SOUTH)

        return panel
    }

    private fun setupChatList() {
        // Double-click to open chat
        chatList.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                if (e.clickCount == 2) {
                    val selected = chatList.selectedValue
                    if (selected != null) {
                        openChat(selected.id)
                    }
                }
            }

            override fun mousePressed(e: MouseEvent) = showPopupIfNeeded(e)
            override fun mouseReleased(e: MouseEvent) = showPopupIfNeeded(e)

            private fun showPopupIfNeeded(e: MouseEvent) {
                if (e.isPopupTrigger) {
                    val index = chatList.locationToIndex(e.point)
                    if (index >= 0) {
                        chatList.selectedIndex = index
                        showChatContextMenu(e.x, e.y)
                    }
                }
            }
        })
    }

    private fun showChatContextMenu(x: Int, y: Int) {
        val popup = JPopupMenu()

        val openItem = JMenuItem("Open")
        openItem.addActionListener {
            val selected = chatList.selectedValue
            if (selected != null) {
                openChat(selected.id)
            }
        }
        popup.add(openItem)

        val deleteItem = JMenuItem("Delete")
        deleteItem.addActionListener {
            val selected = chatList.selectedValue
            if (selected != null) {
                confirmAndDeleteChat(selected.id, selected.title)
            }
        }
        popup.add(deleteItem)

        popup.show(chatList, x, y)
    }

    private fun setupInputArea() {
        inputArea.addKeyListener(object : KeyAdapter() {
            override fun keyPressed(e: KeyEvent) {
                when {
                    e.keyCode == KeyEvent.VK_ENTER && (e.isControlDown || e.isMetaDown) -> {
                        e.consume()
                        val caretPos = inputArea.caretPosition
                        inputArea.insert("\n", caretPos)
                    }
                    e.keyCode == KeyEvent.VK_ENTER && !e.isShiftDown && !e.isControlDown && !e.isMetaDown -> {
                        e.consume()
                        sendMessage()
                    }
                }
            }
        })

        inputArea.document.addDocumentListener(object : DocumentListener {
            override fun insertUpdate(e: DocumentEvent) = updateInputTokenEstimate()
            override fun removeUpdate(e: DocumentEvent) = updateInputTokenEstimate()
            override fun changedUpdate(e: DocumentEvent) = updateInputTokenEstimate()
        })
    }

    private fun showChatList() {
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

        messagesPanel.removeAll()

        if (chat != null) {
            displayChatMessages(chat)
            updateTokenDisplay(chat.totalTokens)
        } else {
            showWelcomeMessage()
        }

        messagesPanel.revalidate()
        messagesPanel.repaint()
        scrollToBottom()

        saveActiveChat()
        cardLayout.show(contentPanel, CARD_CHAT)
        inputArea.requestFocusInWindow()
    }

    private fun displayChatMessages(chat: ChatSession) {
        if (chat.messages.isEmpty()) {
            showWelcomeMessage()
            return
        }
        for (msg in chat.messages) {
            when (msg.role) {
                "user" -> addCompactMessage(msg.content, isUser = true)
                "assistant" -> addCompactMessage(msg.content, isUser = false)
                "system" -> addSystemMessage(msg.content)
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
        chatListModel.clear()
        for (chat in chatSessions) {
            chatListModel.addElement(chat)
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
            val json = gson.toJson(chatSessions)
            val file = getChatsFile()
            file.writeText(json)
        } catch (e: IOException) {
            PluginLogger.warn("Failed to save chat sessions: ${e.message}")
        }
    }

    private fun saveActiveChat() {
        activeChatId?.let {
            PropertiesComponent.getInstance(project).setValue(ACTIVE_CHAT_KEY, it)
        }
    }

    private fun updateInputTokenEstimate() {
        val text = inputArea.text
        val estimatedTokens = if (text.isEmpty()) {
            0
        } else {
            (text.length / CHARS_PER_TOKEN).toInt().coerceAtLeast(1)
        }
        inputTokensLabel.text = "~$estimatedTokens tokens"
    }

    private fun updateTokenDisplay(tokens: Int = 0) {
        statusLabel.text = if (tokens > 0) "Total: $tokens" else ""
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
            getSettingsFile().writeText(json)
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

        reasoningVerbosityPanel.isVisible = true

        reasoningComboBox.isEnabled = supportsReasoning
        reasoningComboBox.toolTipText = if (supportsReasoning) {
            "Reasoning effort"
        } else {
            "$selectedModel does not support reasoning"
        }

        verbosityComboBox.isEnabled = supportsVerbosity
        verbosityComboBox.toolTipText = if (supportsVerbosity) {
            "Response verbosity"
        } else {
            "$selectedModel does not support verbosity"
        }

        if (!supportsReasoning) reasoningComboBox.selectedIndex = 0
        if (!supportsVerbosity) verbosityComboBox.selectedIndex = 0

        updateRouterParamState(selectedModel)
    }

    /**
     * Show and populate the router-param control when [selectedModel] is a
     * router that takes a parameter; hide it otherwise. All router knowledge
     * comes from RouterCatalog — no per-router branching here.
     */
    private fun updateRouterParamState(selectedModel: String) {
        val update = RouterRequestBuilder.paramControlUpdate(shownRouterParamKey, selectedModel)
        routerParamLabel.isVisible = update.visible
        routerParamComboBox.isVisible = update.visible
        routerParamHelpLabel.isVisible = update.visible
        shownRouterParamKey = update.paramKey
        if (!update.visible || !update.rebuild) return
        // The effective param changed, so (re)build the control. This resets
        // the chosen value; the pure helper guarantees we only reach here on a
        // real change, never on a stray refresh (see paramControlUpdate).
        val param = RouterCatalog.find(selectedModel)?.param ?: return
        // For free-type params, mark the label editable so the caret-editable
        // field isn't mistaken for a closed dropdown.
        val editableHint = if (param.freeText) " (editable)" else ""
        routerParamLabel.text = param.label + editableHint + ":"
        routerParamComboBox.toolTipText = param.description
        // Surface the same description inline so it's actually seen.
        routerParamHelpLabel.text = param.description
        val model = DefaultComboBoxModel(param.suggestions.toTypedArray())
        routerParamComboBox.model = model
        // Editable enums and float ranges accept free text; closed enums do not.
        routerParamComboBox.isEditable = param.freeText
        // Seed the control with the user's persisted default for this router
        // (Settings → OpenRouter → Router Defaults), falling back to blank when
        // none is saved. Blank means "no selection → OpenRouter default". For a
        // closed enum the blank first row is also how the user clears a choice.
        val savedDefault = settingsService.routerDefaultsManager.get(selectedModel)
        routerParamComboBox.selectedItem = savedDefault ?: ""
    }

    private fun showWelcomeMessage() {
        addSystemMessage("Welcome! Press Enter to send, Cmd+Enter for new line.")
    }

    @Suppress("ReturnCount")
    private fun sendMessage() {
        if (isLoading) return

        val userMessage = inputArea.text.trim()
        if (userMessage.isEmpty()) return

        val selectedModel = modelComboBox.selectedItem as? String
        if (selectedModel.isNullOrEmpty() || selectedModel.startsWith(SEPARATOR_PREFIX)) {
            showError("Please select a model")
            return
        }

        if (!settingsService.isConfigured()) {
            showError("OpenRouter is not configured. Please set your API key in settings.")
            return
        }

        inputArea.text = ""
        inputArea.requestFocusInWindow()

        addUserMessage(userMessage)

        val currentChat = chatSessions.find { it.id == activeChatId }
        currentChat?.messages?.add(ChatMessageData("user", userMessage))

        // Update chat title if first message
        if (currentChat != null && currentChat.messages.size == 1) {
            currentChat.title = generateChatTitle(userMessage)
            updateChatList()
        }

        setLoading(true)

        coroutineScope.launch {
            sendChatRequest(selectedModel, currentChat)
        }
    }

    private fun generateChatTitle(message: String): String {
        return if (message.length > TITLE_MAX_LENGTH) {
            message.take(TITLE_MAX_LENGTH) + "..."
        } else {
            message
        }
    }

    private suspend fun sendChatRequest(model: String, currentChat: ChatSession?) {
        try {
            val messages = currentChat?.messages?.map { msg ->
                ChatMessage(role = msg.role, content = JsonPrimitive(msg.content))
            } ?: emptyList()

            val reasoningConfig = when (reasoningComboBox.selectedItem as? String) {
                null, "Default" -> null
                "None" -> ReasoningConfig(effort = "none")
                "Minimal" -> ReasoningConfig(effort = "minimal")
                "Low" -> ReasoningConfig(effort = "low")
                "Medium" -> ReasoningConfig(effort = "medium")
                "High" -> ReasoningConfig(effort = "high")
                "XHigh" -> ReasoningConfig(effort = "xhigh")
                else -> null
            }

            val verbosityValue = when (val v = verbosityComboBox.selectedItem as? String) {
                null, "Default" -> null
                else -> v.lowercase()
            }

            val routerValue = routerParamComboBox.selectedItem as? String
            val plugins = RouterRequestBuilder.buildPlugins(model, routerValue)

            val request = ChatCompletionRequest(
                model = model,
                messages = messages,
                maxTokens = MAX_TOKENS,
                temperature = TEMPERATURE,
                stream = false,
                reasoning = reasoningConfig,
                verbosity = verbosityValue,
                plugins = plugins
            )

            val result = openRouterService.createChatCompletion(request)

            SwingUtilities.invokeLater {
                handleChatResponse(result, currentChat, model)
            }
        } catch (e: IOException) {
            SwingUtilities.invokeLater {
                showError("Network error: ${e.message}")
                setLoading(false)
            }
        }
    }

    private fun handleChatResponse(
        result: ApiResult<org.zhavoronkov.openrouter.models.ChatCompletionResponse>,
        currentChat: ChatSession?,
        requestedModel: String
    ) {
        setLoading(false)
        inputArea.requestFocusInWindow()

        when (result) {
            is ApiResult.Success -> handleSuccessResponse(result.data, currentChat, requestedModel)
            is ApiResult.Error -> showError("Error: ${result.message}")
        }
    }

    private fun handleSuccessResponse(
        response: org.zhavoronkov.openrouter.models.ChatCompletionResponse,
        currentChat: ChatSession?,
        requestedModel: String
    ) {
        val assistantMessage = response.choices?.firstOrNull()?.message?.content
        if (assistantMessage == null) {
            showError("No response from model")
            return
        }

        val messageText = extractMessageText(assistantMessage)
        // For routers, echo which underlying model OpenRouter resolved to as a
        // small footnote attached under the reply (not a separate system line).
        val routedFootnote = RouterRequestBuilder.resolvedModelLabel(requestedModel, response.model)
        addAssistantMessage(messageText, footnote = routedFootnote)
        currentChat?.messages?.add(ChatMessageData("assistant", messageText))

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

    private fun addUserMessage(message: String) = addCompactMessage(message, isUser = true)
    private fun addAssistantMessage(message: String, footnote: String? = null) =
        addCompactMessage(message, isUser = false, footnote = footnote)

    private fun addSystemMessage(message: String) {
        val label = JBLabel("<html><i style='color: gray; font-size: 9px;'>$message</i></html>")
        label.border = JBUI.Borders.empty(MESSAGE_BORDER_V, MESSAGE_BORDER_H)
        label.alignmentX = JBLabel.LEFT_ALIGNMENT
        messagesPanel.add(label)
        scrollToBottom()
    }

    private fun addCompactMessage(message: String, isUser: Boolean, footnote: String? = null) {
        val rolePrefix = if (isUser) "You:" else "Assistant:"
        val roleColor = if (isUser) "#6B9BD2" else "#9B9BD2"

        // Use a JPanel with role label and selectable text area
        val messagePanel = JPanel(BorderLayout())
        messagePanel.border = JBUI.Borders.empty(MESSAGE_BORDER_V, MESSAGE_BORDER_H)
        messagePanel.alignmentX = JPanel.LEFT_ALIGNMENT
        messagePanel.background = JBUI.CurrentTheme.ToolWindow.background()

        // Role label (non-selectable prefix)
        val roleLabel = JBLabel(rolePrefix)
        roleLabel.foreground = ColorUtil.fromHex(roleColor)
        roleLabel.border = JBUI.Borders.emptyRight(FLOW_LAYOUT_GAP)
        roleLabel.verticalAlignment = JBLabel.TOP

        // Use same UI font for both message types to avoid font mismatch
        val uiFont = inputArea.font ?: JBLabel().font
        val uiForeground = JBUI.CurrentTheme.Label.foreground()
        val uiBackground = JBUI.CurrentTheme.ToolWindow.background()

        if (!isUser) {
            // Assistant: render Markdown to HTML using JEditorPane
            // Use role prefix inside HTML to ensure inline rendering with colored label
            val htmlContent = MarkdownRenderer.wrapInHtmlDocumentWithRolePrefix(
                bodyHtml = MarkdownRenderer.renderToHtml(message),
                rolePrefix = rolePrefix,
                roleColorHex = roleColor,
                fontFamily = uiFont.family,
                fontSizePx = uiFont.size,
                contentColorHex = ColorUtil.toHex(uiForeground)
            )
            val textPane = JEditorPane("text/html", htmlContent)
            textPane.isEditable = false
            textPane.border = null
            textPane.margin = JBUI.emptyInsets()
            textPane.background = uiBackground
            textPane.font = uiFont
            textPane.foreground = uiForeground
            // Force JEditorPane to honor display properties for HTML content
            textPane.putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, true)
            textPane.putClientProperty(JEditorPane.W3C_LENGTH_UNITS, true)
            messagePanel.add(textPane, BorderLayout.CENTER)
        } else {
            // User: plain text in JBTextArea
            val textArea = JBTextArea(message)
            textArea.isEditable = false
            textArea.lineWrap = true
            textArea.wrapStyleWord = true
            textArea.border = null
            textArea.background = uiBackground
            textArea.foreground = uiForeground
            textArea.font = uiFont
            textArea.caret = javax.swing.text.DefaultCaret()
            textArea.putClientProperty("caretWidth", 2)
            // Set minimum height to at least fit one line
            textArea.minimumSize = Dimension(MIN_TEXT_AREA_WIDTH, textArea.preferredSize.height)
            messagePanel.add(roleLabel, BorderLayout.WEST)
            messagePanel.add(textArea, BorderLayout.CENTER)
        }

        messagesPanel.add(messagePanel)
        // Attach the router "Routed to X" footnote directly under the reply, so
        // it reads as metadata on the message rather than a standalone system line.
        if (!footnote.isNullOrBlank()) {
            val footnoteLabel = JBLabel(
                "<html><i style='color: gray; font-size: 9px;'>$footnote</i></html>"
            )
            footnoteLabel.border = JBUI.Borders.empty(0, MESSAGE_BORDER_H)
            footnoteLabel.alignmentX = JBLabel.LEFT_ALIGNMENT
            messagePanel.add(footnoteLabel, BorderLayout.SOUTH)
        }
        messagesPanel.revalidate()
        messagesPanel.repaint()
        scrollToBottom()
    }

    private fun scrollToBottom() {
        SwingUtilities.invokeLater {
            val scrollBar: JScrollBar = messagesScrollPane.verticalScrollBar
            scrollBar.value = scrollBar.maximum
        }
    }

    private fun setLoading(loading: Boolean) {
        isLoading = loading
        sendButton.isEnabled = !loading
        inputArea.isEnabled = !loading
        modelComboBox.isEnabled = !loading

        if (loading) {
            statusLabel.text = "Thinking..."
            val label = JBLabel("<html><i style='color: gray;'>...</i></html>")
            label.name = "loadingLabel"
            label.border = JBUI.Borders.empty(MESSAGE_BORDER_V, MESSAGE_BORDER_H)
            label.alignmentX = JBLabel.LEFT_ALIGNMENT
            messagesPanel.add(label)
            messagesPanel.revalidate()
            scrollToBottom()
        }
    }

    private fun showError(message: String) {
        // Remove loading indicator
        messagesPanel.components.filterIsInstance<JBLabel>()
            .find { it.name == "loadingLabel" }
            ?.let { messagesPanel.remove(it) }

        val label = JBLabel("<html><span style='color: #FF6B6B;'>⚠ $message</span></html>")
        label.border = JBUI.Borders.empty(MESSAGE_BORDER_V, MESSAGE_BORDER_H)
        label.alignmentX = JBLabel.LEFT_ALIGNMENT
        messagesPanel.add(label)
        messagesPanel.revalidate()
        scrollToBottom()
    }

    fun getPanel(): JPanel = mainPanel

    fun dispose() {
        saveChats()
        coroutineScope.cancel()
    }

    data class ChatMessageData(val role: String, val content: String)

    data class ChatSession(
        val id: String,
        var title: String,
        val messages: MutableList<ChatMessageData>,
        var totalTokens: Int,
        val createdAt: Long
    )

    /**
     * Chat list cell renderer with title and date
     */
    private inner class ChatListCellRenderer : DefaultListCellRenderer() {
        override fun getListCellRendererComponent(
            list: JList<*>?,
            value: Any?,
            index: Int,
            isSelected: Boolean,
            cellHasFocus: Boolean
        ): Component {
            super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus)

            val chat = value as? ChatSession
            if (chat != null) {
                val date = dateFormat.format(Date(chat.createdAt))
                text = "<html><div style='width: 100%;'>" +
                    "<span>${chat.title}</span>" +
                    "<span style='color: gray; float: right;'>$date</span>" +
                    "</div></html>"
                toolTipText = chat.title
            }

            border = JBUI.Borders.empty(CELL_BORDER_V, CELL_BORDER_H)

            return this
        }
    }
}
