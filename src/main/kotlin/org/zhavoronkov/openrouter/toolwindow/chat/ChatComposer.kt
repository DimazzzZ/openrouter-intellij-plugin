package org.zhavoronkov.openrouter.toolwindow.chat

import com.intellij.icons.AllIcons
import com.intellij.openapi.ui.ComboBox
import com.intellij.ui.InplaceButton
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.intellij.util.ui.JBUI
import org.zhavoronkov.openrouter.toolwindow.composer.ComposerLayout
import org.zhavoronkov.openrouter.toolwindow.composer.ComposerLayoutPolicy
import org.zhavoronkov.openrouter.toolwindow.composer.MiddleEllipsisComboRenderer
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.event.ActionListener
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import javax.swing.Icon
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener

private const val GAP = 4
private const val INPUT_MIN_ROWS = 1
private const val INPUT_MAX_ROWS = 6

/**
 * Gap between the two counter labels ("~0 tokens" and "Total: N"). Deliberately
 * wider than [GAP] (used for borders/padding elsewhere): at [GAP]'s 4px the two
 * labels read as one run-on string ("~0 tokens Total: 1814") rather than two
 * distinct values.
 */
private const val COUNTER_GAP = 8

/**
 * The bottom composer: input area, model combo, gear, token counters and Send.
 *
 * The control row (model / settings / counters / send) is laid out by
 * [ComposerLayout], which degrades the row as the tool window narrows instead
 * of clipping it. This class only wires the parts together; every decision
 * about what collapses first lives in the pure, unit-tested policy that
 * [ComposerLayout] consults.
 */
class ChatComposer {

    private val inputArea = JBTextArea(INPUT_MIN_ROWS, 0)
    private val inputScrollPane: JBScrollPane
    private val sendButton = JButton("Send")
    private val statusLabel = JBLabel("")
    private val inputTokensLabel = JBLabel("~0 tokens")

    private val settingsButton = BadgedInplaceButton(AllIcons.General.GearPlain, "Send parameters") {
        onSettingsClick()
    }.apply {
        isFocusable = false
    }

    private val countersPanel = JPanel(FlowLayout(FlowLayout.RIGHT, JBUI.scale(COUNTER_GAP), 0)).apply {
        isOpaque = false
        add(inputTokensLabel)
        add(statusLabel)
    }

    private lateinit var controlRow: JPanel

    // Tracks the last width syncInputHeight() was computed for, so the resize
    // listener below reacts only to real width changes (the tool window being
    // dragged narrower/wider) and not to the height-only changes syncInputHeight()
    // itself makes to inputScrollPane's preferredSize, which would otherwise
    // feed back into another resync on every call.
    private var lastInputWidth = -1

    var onSend: () -> Unit = {}
    var onTextChanged: (String) -> Unit = {}
    var onSettingsClick: () -> Unit = {}

    var text: String
        get() = inputArea.text
        set(value) { inputArea.text = value }

    val component: JComponent

    init {
        inputArea.lineWrap = true
        inputArea.wrapStyleWord = true
        inputArea.border = JBUI.Borders.empty(GAP)
        inputArea.document.addDocumentListener(object : DocumentListener {
            override fun insertUpdate(e: DocumentEvent) {
                onTextChanged(inputArea.text)
                syncInputHeight()
            }
            override fun removeUpdate(e: DocumentEvent) {
                onTextChanged(inputArea.text)
                syncInputHeight()
            }
            override fun changedUpdate(e: DocumentEvent) {
                onTextChanged(inputArea.text)
                syncInputHeight()
            }
        })

        // Re-wrapping a paragraph at a new width changes how many visual rows it
        // needs, so the input must resync on width changes too, not only on text
        // changes (see the field doc on lastInputWidth for why this is guarded
        // to width-only).
        inputArea.addComponentListener(object : ComponentAdapter() {
            override fun componentResized(e: ComponentEvent) {
                if (inputArea.width != lastInputWidth) {
                    lastInputWidth = inputArea.width
                    syncInputHeight()
                }
            }
        })

        sendButton.addActionListener { onSend() }
        inputTokensLabel.foreground = JBUI.CurrentTheme.Label.disabledForeground()

        inputScrollPane = JBScrollPane(inputArea)
        inputScrollPane.verticalScrollBarPolicy = JBScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED

        component = JPanel(BorderLayout()).apply {
            border = JBUI.Borders.emptyTop(GAP)
            add(inputScrollPane, BorderLayout.CENTER)
        }

        syncInputHeight()
    }

    /**
     * Recomputes the input area's preferred height so it auto-grows from one
     * row to [INPUT_MAX_ROWS] rows and then scrolls.
     *
     * Deliberately does NOT use [JBTextArea.getLineCount]: that method counts
     * only '\n' characters in the document, not the extra visual rows a
     * word-wrapped paragraph produces once [inputArea] has a real width.
     * (Verified directly: a single 200-word line with no newline reports
     * lineCount == 1 while its actual wrapped height is dozens of rows.)
     * [inputArea]'s own preferredSize already reflects the real wrap at its
     * current width, so the wanted row count is derived from that instead.
     */
    private fun syncInputHeight() {
        val lineHeight = inputArea.getFontMetrics(inputArea.font).height
        val textInsets = inputArea.insets
        val contentHeight = (inputArea.preferredSize.height - textInsets.top - textInsets.bottom)
            .coerceAtLeast(lineHeight)
        val wantedRows = ((contentHeight + lineHeight - 1) / lineHeight)
            .coerceIn(INPUT_MIN_ROWS, INPUT_MAX_ROWS)
        val insets = inputScrollPane.insets
        val height = lineHeight * wantedRows + textInsets.top + textInsets.bottom +
            insets.top + insets.bottom
        if (inputScrollPane.preferredSize.height != height) {
            inputScrollPane.preferredSize = Dimension(0, height)
            component.revalidate()
        }
    }

    /**
     * Wires the model combo into the composer's control row.
     *
     * Neither a preferred nor a minimum size is set here. A hard preferred size
     * is exactly what stopped the old combo from shrinking (spec cause #2), and
     * a minimum size would be pure decoration: `com.intellij.openapi.ui.ComboBox`
     * returns its PREFERRED size from `getMinimumSize()` regardless of what was
     * set (verified in a platform test: after `minimumSize = 80x30`,
     * `isMinimumSizeSet` is true while `minimumSize` still reports 165x24).
     * The combo's collapse floor is therefore enforced where it can be enforced:
     * by [ComposerLayout], from the single
     * [ComposerLayoutPolicy.MODEL_MIN_WIDTH] definition.
     */
    fun attachModelCombo(combo: ComboBox<String>) {
        combo.renderer = MiddleEllipsisComboRenderer(combo, combo.renderer)

        controlRow = JPanel(null).apply { isOpaque = false }
        controlRow.layout = ComposerLayout(
            model = combo,
            settings = settingsButton,
            counters = countersPanel,
            send = sendButton,
            sendIcon = AllIcons.Actions.Execute,
            sendText = "Send"
        )
        controlRow.add(combo)
        controlRow.add(settingsButton)
        controlRow.add(countersPanel)
        controlRow.add(sendButton)
        component.add(controlRow, BorderLayout.SOUTH)
        component.revalidate()
    }

    /** Exposed so Task 10 can hook up the parameters popup on the gear button. */
    fun settingsComponent(): JComponent = settingsButton

    /** Dot badge on the gear when any send parameter differs from Default (spec D7). */
    fun setSettingsBadge(on: Boolean) {
        settingsButton.badged = on
    }

    fun setSettingsTooltip(text: String) {
        settingsButton.toolTipText = text
    }

    fun setStatus(text: String) { statusLabel.text = text }

    fun setInputTokens(text: String) { inputTokensLabel.text = text }

    fun setBusy(busy: Boolean) {
        sendButton.isEnabled = !busy
        inputArea.isEnabled = !busy
    }

    fun requestFocusInInput() { inputArea.requestFocusInWindow() }

    /** Exposed so ChatPanel can keep installing its Enter / Cmd+Enter key handling. */
    fun inputComponent(): JBTextArea = inputArea

    /**
     * A borderless, hover-highlight icon button (the gear) that can paint a
     * small dot badge in its top-right corner.
     *
     * Opening send parameters is a secondary action next to the model
     * selector, so it must not carry the same visual weight as a bordered
     * button (item 4 of the polish pass): [InplaceButton] is the platform's
     * own borderless hover-icon affordance, used elsewhere in this package
     * for the per-message copy button, and paints no border or content-area
     * fill in any LaF - a plain [JButton] with `JButton.buttonType =
     * toolBarButton` (the previous approach) could not guarantee that.
     *
     * The badge is painted rather than shipped as a second icon asset, so it
     * follows the theme (via [JBUI.CurrentTheme]) and needs no asset. It is
     * the entire compensation for moving the send parameters out of sight
     * into a popup (Task 10 / spec D7): it must be correct whenever any
     * parameter differs from Default.
     */
    private class BadgedInplaceButton(
        icon: Icon,
        tooltip: String,
        onClick: () -> Unit
    ) : InplaceButton(tooltip, icon, ActionListener { onClick() }) {

        var badged: Boolean = false
            set(value) {
                field = value
                repaint()
            }

        override fun paintComponent(g: Graphics) {
            super.paintComponent(g)
            if (!badged) return
            val g2 = g.create() as Graphics2D
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
                g2.color = JBUI.CurrentTheme.Focus.focusColor()
                val d = JBUI.scale(BADGE_DIAMETER)
                val inset = JBUI.scale(BADGE_INSET)
                g2.fillOval(width - d - inset, inset, d, d)
            } finally {
                g2.dispose()
            }
        }

        private companion object {
            const val BADGE_DIAMETER = 5
            const val BADGE_INSET = 2
        }
    }
}
