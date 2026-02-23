package org.zhavoronkov.openrouter.toolwindow

import com.google.gson.Gson
import com.google.gson.JsonPrimitive
import com.google.gson.JsonSyntaxException
import com.google.gson.reflect.TypeToken
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.project.Project
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
import org.zhavoronkov.openrouter.services.OpenRouterService
import org.zhavoronkov.openrouter.services.OpenRouterSettingsService
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
import javax.swing.BoxLayout
import javax.swing.DefaultComboBoxModel
import javax.swing.DefaultListCellRenderer
import javax.swing.DefaultListModel
import javax.swing.JButton
import javax.swing.JComboBox
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
        private const val HEADER_FONT_SIZE_INCREASE = 2f
        private const val FLOW_LAYOUT_GAP = 4
        private const val COMBO_BOX_WIDTH = 180
        private const val TITLE_MAX_LENGTH = 50
        private const val MESSAGE_BORDER_V = 1
        private const val MESSAGE_BORDER_H = 2
        private const val CELL_BORDER_V = 4
        private const val CELL_BORDER_H = 8
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
    private val inputArea: JBTextArea
    private val sendButton: JButton
    private val modelComboBox: JComboBox<String>
    private val statusLabel: JBLabel
    private val inputTokensLabel: JBLabel

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
        modelComboBox = JComboBox()
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

        // Save model selection when changed
        modelComboBox.addItemListener { e ->
            if (e.stateChange == ItemEvent.SELECTED) {
                saveSelectedModel()
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

        // Top panel with back button and model selector
        val topPanel = JPanel(BorderLayout())
        topPanel.border = JBUI.Borders.emptyBottom(FLOW_LAYOUT_GAP)

        // Left: Back button only
        val backButton = JButton("← Back")
        backButton.addActionListener { showChatList() }
        topPanel.add(backButton, BorderLayout.WEST)

        // Right: Model selector
        val modelPanel = JPanel(FlowLayout(FlowLayout.RIGHT, FLOW_LAYOUT_GAP, 0))
        modelPanel.add(JBLabel("Model:"))
        modelComboBox.preferredSize = Dimension(COMBO_BOX_WIDTH, modelComboBox.preferredSize.height)
        modelPanel.add(modelComboBox)
        topPanel.add(modelPanel, BorderLayout.EAST)

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

    private fun loadFavoriteModels() {
        val favorites = settingsService.favoriteModelsManager.getFavoriteModels()
        val model = DefaultComboBoxModel<String>()

        if (favorites.isEmpty()) {
            model.addElement("openai/gpt-4o")
            model.addElement("anthropic/claude-3.5-sonnet")
        } else {
            favorites.forEach { model.addElement(it) }
        }

        modelComboBox.model = model
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

    private fun showWelcomeMessage() {
        addSystemMessage("Welcome! Press Enter to send, Cmd+Enter for new line.")
    }

    @Suppress("ReturnCount")
    private fun sendMessage() {
        if (isLoading) return

        val userMessage = inputArea.text.trim()
        if (userMessage.isEmpty()) return

        val selectedModel = modelComboBox.selectedItem as? String
        if (selectedModel.isNullOrEmpty()) {
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

            val request = ChatCompletionRequest(
                model = model,
                messages = messages,
                maxTokens = MAX_TOKENS,
                temperature = TEMPERATURE,
                stream = false
            )

            val result = openRouterService.createChatCompletion(request)

            SwingUtilities.invokeLater {
                handleChatResponse(result, currentChat)
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
        currentChat: ChatSession?
    ) {
        setLoading(false)
        inputArea.requestFocusInWindow()

        when (result) {
            is ApiResult.Success -> handleSuccessResponse(result.data, currentChat)
            is ApiResult.Error -> showError("Error: ${result.message}")
        }
    }

    private fun handleSuccessResponse(
        response: org.zhavoronkov.openrouter.models.ChatCompletionResponse,
        currentChat: ChatSession?
    ) {
        val assistantMessage = response.choices?.firstOrNull()?.message?.content
        if (assistantMessage == null) {
            showError("No response from model")
            return
        }

        val messageText = extractMessageText(assistantMessage)
        addAssistantMessage(messageText)
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
    private fun addAssistantMessage(message: String) = addCompactMessage(message, isUser = false)

    private fun addSystemMessage(message: String) {
        val label = JBLabel("<html><i style='color: gray; font-size: 9px;'>$message</i></html>")
        label.border = JBUI.Borders.empty(MESSAGE_BORDER_V, MESSAGE_BORDER_H)
        label.alignmentX = JBLabel.LEFT_ALIGNMENT
        messagesPanel.add(label)
        scrollToBottom()
    }

    private fun addCompactMessage(message: String, isUser: Boolean) {
        val rolePrefix = if (isUser) "You" else "Assistant"
        val roleColor = if (isUser) "#6B9BD2" else "#9B9B9B"

        val escapedMessage = message
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\n", "<br>")

        val htmlContent = "<html><b style='color: $roleColor;'>$rolePrefix:</b> $escapedMessage</html>"
        val contentLabel = JBLabel(htmlContent)
        contentLabel.border = JBUI.Borders.empty(MESSAGE_BORDER_V, MESSAGE_BORDER_H)
        contentLabel.verticalAlignment = JBLabel.TOP
        contentLabel.alignmentX = JBLabel.LEFT_ALIGNMENT

        messagesPanel.add(contentLabel)
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
