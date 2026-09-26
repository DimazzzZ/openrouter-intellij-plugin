package org.zhavoronkov.openrouter.toolwindow.chat

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionToolbar
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.project.DumbAware
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.JPanel

private const val TITLE_GAP = 6
private const val BOTTOM_GAP = 4

/**
 * The single strip of chrome above the conversation.
 *
 * Replaces three stacked rows (title + New Chat, Back + Model, params); the
 * model and the send parameters move to the composer in Tasks 9 and 10. This
 * strip does not clip like the old rows did because BorderLayout's WEST/EAST
 * groups hold only 1-2 icon actions each (~90px total), and the one flexible
 * element, the title, sits in CENTER, where BorderLayout gives it whatever
 * space remains and JBLabel ellipsises it if it doesn't fit. Keep the side
 * groups this small: a third action reintroduces the clipping, and the `»`
 * overflow chevron will not save it, since BorderLayout sizes each side
 * toolbar to its own preferred width regardless of how little room is left.
 */
class ChatToolbar(private val place: String) {

    var onBack: () -> Unit = {}
    var onNewChat: () -> Unit = {}
    var onCopyConversation: () -> Unit = {}

    private var inConversation = false

    private val titleLabel = JBLabel("Chats")

    private val backAction = action(AllIcons.Actions.Back, "Back to chats", { inConversation }) { onBack() }
    private val newChatAction = action(AllIcons.General.Add, "New chat", { true }) { onNewChat() }
    private val copyAction =
        action(AllIcons.Actions.Copy, "Copy conversation", { inConversation }) { onCopyConversation() }

    val component: JComponent

    init {
        val leftGroup = DefaultActionGroup(backAction)
        val rightGroup = DefaultActionGroup(copyAction, newChatAction)

        val leftBar = toolbar(leftGroup)
        val rightBar = toolbar(rightGroup)

        titleLabel.border = JBUI.Borders.empty(0, TITLE_GAP)

        component = JPanel(BorderLayout()).apply {
            border = JBUI.Borders.emptyBottom(BOTTOM_GAP)
            add(leftBar.component, BorderLayout.WEST)
            add(titleLabel, BorderLayout.CENTER)
            add(rightBar.component, BorderLayout.EAST)
        }
    }

    fun setTitle(title: String) {
        titleLabel.text = title
        titleLabel.toolTipText = title
    }

    fun setInConversation(inConversation: Boolean) {
        this.inConversation = inConversation
    }

    private fun toolbar(group: DefaultActionGroup): ActionToolbar =
        ActionManager.getInstance().createActionToolbar(place, group, true).also {
            it.targetComponent = titleLabel
        }

    private fun action(
        icon: Icon,
        text: String,
        visible: () -> Boolean,
        perform: () -> Unit
    ): AnAction = object : AnAction(text, text, icon), DumbAware {
        override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

        override fun update(e: AnActionEvent) {
            e.presentation.isVisible = visible()
        }

        override fun actionPerformed(e: AnActionEvent) = perform()
    }
}
