package org.zhavoronkov.openrouter.toolwindow.chat

import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.Font
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import javax.swing.JComponent
import javax.swing.JScrollBar
import javax.swing.SwingUtilities

private const val MESSAGE_BORDER_V = 1
private const val MESSAGE_BORDER_H = 2
private const val LOADING_LABEL_NAME = "loadingLabel"

/**
 * The messages area: rendering/appending chat messages, the loading
 * indicator and clearing.
 *
 * Message rendering itself lives in [MessageView]; this view owns the
 * scroll pane, the loading indicator and system/error lines.
 */
class ChatConversationView {

    private val messagesPanel = MessagesPanel()

    private val scrollPane = JBScrollPane(messagesPanel).apply {
        verticalScrollBarPolicy = JBScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED
        horizontalScrollBarPolicy = JBScrollPane.HORIZONTAL_SCROLLBAR_NEVER
        border = JBUI.Borders.empty()
    }

    init {
        // Re-measure every message when the viewport is resized (e.g. the tool
        // window is dragged narrower/wider): a JEditorPane's preferred height
        // depends on its width, so a width change invalidates every cached
        // height. A ComponentListener reacts only to actual size changes;
        // JViewport's own ChangeListener also fires on every scroll-position
        // change, which would force a full re-layout on every scroll tick.
        scrollPane.viewport.addComponentListener(object : ComponentAdapter() {
            override fun componentResized(e: ComponentEvent) {
                remeasure()
            }
        })
    }

    val component: JComponent get() = scrollPane

    /** Shows a message at the bottom; the view it returns can have its footer updated later. */
    fun addMessage(text: String, isUser: Boolean, footnote: String? = null, warning: String? = null): MessageView {
        val view = MessageView(text, isUser, footnote, warning)
        messagesPanel.add(view.component)
        remeasure()
        scrollToBottom()
        return view
    }

    fun addSystemMessage(message: String) {
        val label = JBLabel(message).apply {
            foreground = UIUtil.getContextHelpForeground()
            font = JBUI.Fonts.smallFont().deriveFont(Font.ITALIC)
            border = JBUI.Borders.empty(MESSAGE_BORDER_V, MESSAGE_BORDER_H)
            alignmentX = JBLabel.LEFT_ALIGNMENT
        }
        messagesPanel.add(label)
        remeasure()
        scrollToBottom()
    }

    fun showError(message: String) {
        removeLoadingLabel()

        val label = JBLabel("⚠ $message").apply {
            foreground = JBColor.RED
            border = JBUI.Borders.empty(MESSAGE_BORDER_V, MESSAGE_BORDER_H)
            alignmentX = JBLabel.LEFT_ALIGNMENT
        }
        messagesPanel.add(label)
        remeasure()
        scrollToBottom()
    }

    fun showLoading() {
        val label = JBLabel("...").apply {
            name = LOADING_LABEL_NAME
            foreground = UIUtil.getContextHelpForeground()
            font = font.deriveFont(Font.ITALIC)
            border = JBUI.Borders.empty(MESSAGE_BORDER_V, MESSAGE_BORDER_H)
            alignmentX = JBLabel.LEFT_ALIGNMENT
        }
        messagesPanel.add(label)
        remeasure()
        scrollToBottom()
    }

    /**
     * Removes the loading indicator added by [showLoading]. The legacy
     * `setLoading(false)` branch in ChatPanel never actually removed it -
     * only [showError] did - so a stray "..." row survived above every
     * successful reply until the next `openChat()` cleared the whole panel.
     * Fixed here: this is what makes the success path actually remove it.
     * Removing it twice, or when it is absent, is a harmless no-op.
     */
    fun hideLoading() {
        removeLoadingLabel()
        messagesPanel.revalidate()
        messagesPanel.repaint()
    }

    private fun removeLoadingLabel() {
        messagesPanel.components.filterIsInstance<JBLabel>()
            .find { it.name == LOADING_LABEL_NAME }
            ?.let { messagesPanel.remove(it) }
    }

    fun clear() {
        messagesPanel.removeAll()
        messagesPanel.revalidate()
        messagesPanel.repaint()
    }

    /**
     * Not part of the Task 3 brief's interface list, added to preserve the
     * exact ChatPanel.openChat() sequence: removeAll (clear), replay
     * messages, then a final revalidate/repaint/scroll so a chat that ends
     * on a system message (e.g. the welcome message, which does not
     * revalidate itself) still renders correctly. See the Task 3 report.
     */
    fun refreshAfterDisplay() {
        remeasure()
        scrollToBottom()
    }

    /**
     * A BoxLayout pass queries every child's `getPreferredSize()` - and so a
     * [WrappingEditorPane]'s width - BEFORE it applies the new width via
     * `setBounds()` in that SAME pass. A brand-new pane has never been given
     * a width yet, so its first-ever measurement reads its fallback width,
     * not the real one, and that wrong height gets baked into this pass even
     * though the pane's WIDTH is corrected by the same pass's `setBounds()`
     * call. For a live resize the next tick self-corrects, but a new message
     * has no "next tick": left alone, it renders at the wrong height and
     * stays wrong until something else forces another pass - the same
     * clipped/over-tall symptom this task exists to remove, arriving a
     * different way.
     *
     * The fix is a second pass, once the real width from the first pass is
     * known. `revalidate()` alone only *schedules* another asynchronous
     * pass; verified against a headless BoxLayout probe, that is not
     * guaranteed to run before an already-queued `scrollToBottom()`
     * callback (both are queued as separate `invokeLater` turns, and
     * `revalidate()`'s own follow-up pass is scheduled asynchronously by
     * `RepaintManager`, so its actual execution can land after a plain
     * `scrollToBottom()` submitted right after it). Calling `validate()`
     * inside the deferred block forces that second pass to run
     * synchronously, so anything queued after this method returns -
     * `scrollToBottom()`, called right after in every path below - is
     * guaranteed to see the corrected height. This does not belong inside
     * [WrappingEditorPane.getPreferredSize] itself: triggering a layout pass
     * from inside a measurement call is how you get layout thrash. It runs
     * once per change (this call schedules exactly one deferred block; that
     * block's own `revalidate()` call schedules a follow-up `RepaintManager`
     * pass too, but by the time it fires the panel is already valid from
     * this method's synchronous `validate()`, so it finds nothing to do -
     * no ping-pong).
     */
    private fun remeasure() {
        messagesPanel.revalidate()
        messagesPanel.repaint()
        SwingUtilities.invokeLater {
            messagesPanel.revalidate()
            messagesPanel.validate()
            messagesPanel.repaint()
        }
    }

    private fun scrollToBottom() {
        SwingUtilities.invokeLater {
            val scrollBar: JScrollBar = scrollPane.verticalScrollBar
            scrollBar.value = scrollBar.maximum
        }
    }
}
