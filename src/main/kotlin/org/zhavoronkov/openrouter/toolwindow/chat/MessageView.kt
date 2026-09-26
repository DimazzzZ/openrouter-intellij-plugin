package org.zhavoronkov.openrouter.toolwindow.chat

import com.intellij.icons.AllIcons
import com.intellij.ui.ColorUtil
import com.intellij.ui.InplaceButton
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import org.zhavoronkov.openrouter.utils.MarkdownRenderer
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Component
import java.awt.Dimension
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.BoxLayout
import javax.swing.JComponent
import javax.swing.JEditorPane
import javax.swing.JPanel
import javax.swing.border.Border

private const val USER_TINT_FRACTION = 0.06

/**
 * Fallback for the "Chat.userMessageBackground" theme key, which does not
 * exist in any stock IntelliJ theme. A flat [UIUtil.getPanelBackground] (the
 * previous fallback) paints the exact same colour as the conversation panel
 * behind it, so the "tinted block" a user message is supposed to sit in was
 * never visible - only the small indent distinguished it.
 *
 * Mixing the panel background toward the label foreground by a small,
 * quiet fraction keeps the block reading as a subtle surface rather than a
 * highlight, while staying measurably different from the panel in both light
 * and dark themes (both colours move toward black in light themes and toward
 * white in dark themes, so the mix always nudges away from the panel colour).
 */
internal fun userMessageBackgroundFallback(): Color =
    ColorUtil.mix(UIUtil.getPanelBackground(), UIUtil.getLabelForeground(), USER_TINT_FRACTION)

/**
 * One message in the conversation.
 *
 * Colours come from the theme: the previous implementation hard-coded
 * #6B9BD2 / #9B9BD2 / gray, which is wrong in the light theme and in every
 * custom theme.
 *
 * A user message's tinted block hugs its own text instead of spanning the
 * full row (the polish-pass fix for the block reading as a stray text field):
 * it sits at the [BorderLayout.WEST] of a full-width, invisible row, which is
 * what lets it size to its content while [MessagesPanel] still stretches the
 * row itself to the viewport's width. Its own padding reuses [MESSAGE_GAP_V]
 * / [MESSAGE_GAP_H] and the outer [component] drops its horizontal/top
 * padding for a user message by exactly that amount, so the two paddings
 * cancel out and user/assistant text starts on the same line, and the same
 * vertical gap separates every pair of messages regardless of who spoke.
 */
class MessageView(text: String, isUser: Boolean, footnote: String?) {

    val component: JComponent

    /** The full-width row holding [TintedBlock] (user) or the flat body (assistant). */
    private lateinit var contentRow: JComponent

    init {
        val body = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            isOpaque = false
            alignmentX = Component.LEFT_ALIGNMENT
        }

        MessageSegmenter.split(text).forEach { segment ->
            body.add(
                when (segment) {
                    is MessageSegment.Prose -> proseComponent(segment.markdown, isUser)
                    is MessageSegment.Code -> CodeSegmentView(segment).component
                }
            )
        }

        val container = if (isUser) huggingRow(body) else flatBlock(body)
        contentRow = container

        component = JPanel(BorderLayout()).apply {
            isOpaque = false
            alignmentX = Component.LEFT_ALIGNMENT
            border = messageBorder(isUser)
            add(container, BorderLayout.CENTER)
            val strip = southStrip(text, footnote)
            add(strip, BorderLayout.SOUTH)
            addMouseListener(object : MouseAdapter() {
                override fun mouseEntered(e: MouseEvent) = setActionsVisible(strip, true)
                override fun mouseExited(e: MouseEvent) {
                    if (!contains(e.point)) setActionsVisible(strip, false)
                }
            })
        }
    }

    /**
     * The outer padding around [container] and the south strip.
     *
     * A user message removes the top/left padding here because [TintedBlock]
     * supplies the exact same amount itself - see the class doc. The bottom
     * (below the strip) and right stay uniform for both speakers: they are
     * not part of the tinted card, so there is nothing to cancel against.
     */
    private fun messageBorder(isUser: Boolean): Border = if (isUser) {
        JBUI.Borders.empty(0, 0, MESSAGE_GAP_V, MESSAGE_GAP_H)
    } else {
        JBUI.Borders.empty(MESSAGE_GAP_V, MESSAGE_GAP_H)
    }

    /**
     * Wraps [TintedBlock] in a full-width, invisible row so it can hug its
     * own content while still satisfying [MessagesPanel]'s width-tracking
     * `Scrollable` contract.
     *
     * [BorderLayout.WEST] is what makes this work: unlike `CENTER` (which
     * [flatBlock] uses and which BorderLayout always stretches to fill), a
     * `WEST` child keeps its own preferred width and is left-aligned within
     * whatever width the row is given - exactly "hug content, stay left,
     * let the container still fill the row" that item 1 asks for.
     */
    private fun huggingRow(body: JComponent): JComponent = JPanel(BorderLayout()).apply {
        isOpaque = false
        add(TintedBlock(body), BorderLayout.WEST)
    }

    /**
     * Footnote on the left, actions on the right. The copy button is what pays
     * for losing cross-message selection when each message became its own
     * component (spec D5); it appears on hover so it costs no attention.
     *
     * [InplaceButton] is the platform's borderless hover-icon affordance (used
     * for things like inline "close tab" actions) - a plain [javax.swing.JButton]
     * with `JButton.buttonType = toolBarButton` is not enough to drop its
     * border/content-area fill in every LaF, and painted as a big rounded
     * rectangle here.
     *
     * The strip's own preferred height is pinned to the button's height
     * regardless of the button's visibility, so hovering never changes the
     * strip's height and shifts every message below it - `isVisible` on a
     * component inside a [BorderLayout] slot makes that slot's contribution to
     * the parent's preferred size vanish, which is exactly what produced the
     * jump.
     */
    private fun southStrip(text: String, footnote: String?): JComponent {
        val copyButton = InplaceButton("Copy message", AllIcons.Actions.Copy) {
            StringSelection(text).let {
                Toolkit.getDefaultToolkit().systemClipboard.setContents(it, it)
            }
        }.apply {
            isFocusable = false
            isVisible = false
        }
        val reservedHeight = copyButton.preferredSize.height
        return object : JPanel(BorderLayout()) {
            override fun getPreferredSize(): Dimension {
                val natural = super.getPreferredSize()
                return Dimension(natural.width, maxOf(natural.height, reservedHeight))
            }
        }.apply {
            isOpaque = false
            footnote?.takeIf { it.isNotBlank() }?.let { add(footnoteLabel(it), BorderLayout.CENTER) }
            add(copyButton, BorderLayout.EAST)
        }
    }

    private fun setActionsVisible(strip: JComponent, visible: Boolean) {
        strip.components.filterIsInstance<InplaceButton>().forEach { it.isVisible = visible }
    }

    private fun proseComponent(markdown: String, isUser: Boolean): JComponent {
        val font = UIUtil.getLabelFont()
        val html = MarkdownRenderer.wrapInHtmlDocument(
            bodyHtml = MarkdownRenderer.renderToHtml(markdown),
            fontFamily = font.family,
            fontSizePx = font.size,
            colorHex = ColorUtil.toHex(JBUI.CurrentTheme.Label.foreground())
        )
        // Two kinds of content force this pane wider than the viewport and will
        // not shrink: HTMLEditorKit sizes a <table> from its content, and it
        // does not wrap <pre>. A <pre> reaches a prose segment when a fenced
        // block is nested inside a list or blockquote - MessageSegmenter
        // deliberately leaves those in place rather than tearing the fence out
        // of its container, and isolates the container into its own prose
        // segment precisely so this scroll pane's blast radius stays small.
        return when {
            html.contains("<table") || html.contains("<pre") -> horizontallyScrollableProse(html)
            isUser -> HuggingEditorPane(html).apply {
                isOpaque = false
                alignmentX = Component.LEFT_ALIGNMENT
            }
            else -> WrappingEditorPane("text/html", html).apply {
                isOpaque = false
                alignmentX = Component.LEFT_ALIGNMENT
            }
        }
    }

    /**
     * Unlike [WrappingEditorPane], this must NOT self-clamp to a fixed width:
     * HTMLEditorKit renders a `<pre>`/`<table>` at its own natural,
     * unbreakable width no matter what width the component is told it has, so
     * clamping the pane's reported width (as [WrappingEditorPane] deliberately
     * does, for the in-flow wrapping case) only clips the wide content instead
     * of letting it scroll - confirmed with a headless probe (task 13 report):
     * inside a [com.intellij.ui.components.JBScrollPane], a [WrappingEditorPane]'s
     * self-referential width got stuck at its 200px fallback forever, since
     * nothing ever grows it back. A plain, unclamped [JEditorPane] reports its
     * true natural width instead, which is what [HorizontallyScrollingPane]
     * needs to size the scrollbar correctly.
     *
     * Width-then-height audit (2026-09-18): `pane.preferredSize` being read
     * on every layout pass (via [HorizontallyScrollingPane]'s lazy
     * `naturalSize` lambda) rather than snapshotted once is safe here
     * precisely because this pane is unclamped - see
     * [HorizontallyScrollingPane]'s KDoc for the full reasoning.
     */
    private fun horizontallyScrollableProse(html: String): JComponent {
        val pane = JEditorPane("text/html", html).apply {
            isEditable = false
            isOpaque = false
            border = null
            margin = JBUI.emptyInsets()
            putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, true)
            putClientProperty(JEditorPane.W3C_LENGTH_UNITS, true)
        }
        val scrollPane = HorizontallyScrollingPane(pane) { pane.preferredSize }.apply {
            isOpaque = false
            viewport.isOpaque = false
            border = JBUI.Borders.empty()
            alignmentX = Component.LEFT_ALIGNMENT
        }
        forwardVerticalWheelToAncestor(scrollPane)
        return scrollPane
    }

    private fun flatBlock(body: JComponent): JComponent = JPanel(BorderLayout()).apply {
        isOpaque = false
        add(body, BorderLayout.CENTER)
    }

    private fun footnoteLabel(footnote: String) = JBLabel(footnote).apply {
        foreground = UIUtil.getContextHelpForeground()
        font = JBUI.Fonts.smallFont()
        horizontalAlignment = JBLabel.RIGHT
    }

    /**
     * A [JEditorPane] for a user message's prose: reports the width its text
     * actually needs, capped at [HUG_MAX_FRACTION] of [contentRow]'s width, so
     * a short message hugs its text (item 1) instead of stretching across the
     * whole row. Longer text wraps at the cap, same as [WrappingEditorPane].
     *
     * Measuring "the width the text actually needs" relies on the same Swing
     * behaviour [WrappingEditorPane] works around: a fresh HTML view, given no
     * width constraint, reports the width of its longest unbroken line as its
     * preferred width. Forcing a huge width first (rather than reading the
     * pane's untouched preferred size) keeps this correct even after a later
     * layout pass has already constrained the pane to a narrower size.
     *
     * [contentRow] is read - not passed in at construction - because this pane
     * is built before [huggingRow] exists (the row wraps the block, which
     * wraps the body, which contains this pane); by the time Swing actually
     * lays this out, `contentRow` has long since been assigned.
     */
    private inner class HuggingEditorPane(html: String) : JEditorPane("text/html", html) {

        init {
            isEditable = false
            border = null
            margin = JBUI.emptyInsets()
            putClientProperty(HONOR_DISPLAY_PROPERTIES, true)
            putClientProperty(W3C_LENGTH_UNITS, true)
        }

        override fun getPreferredSize(): Dimension {
            setSize(Short.MAX_VALUE.toInt(), Short.MAX_VALUE.toInt())
            val natural = super.getPreferredSize().width
            val targetWidth = natural.coerceAtMost(maxHugWidth())
            setSize(targetWidth, Short.MAX_VALUE.toInt())
            return Dimension(targetWidth, super.getPreferredSize().height)
        }

        override fun getMaximumSize(): Dimension = preferredSize

        private fun maxHugWidth(): Int {
            val rowWidth = if (contentRow.width > 0) contentRow.width else JBUI.scale(HUG_FALLBACK_WIDTH)
            return (rowWidth * HUG_MAX_FRACTION).toInt()
        }
    }

    /** Rounded tinted background for user messages. */
    private class TintedBlock(body: JComponent) : JPanel(BorderLayout()) {
        init {
            isOpaque = false
            border = JBUI.Borders.empty(MESSAGE_GAP_V, MESSAGE_GAP_H)
            add(body, BorderLayout.CENTER)
        }

        override fun paintComponent(g: Graphics) {
            val g2 = g.create() as Graphics2D
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
                g2.color = JBColor.namedColor("Chat.userMessageBackground", userMessageBackgroundFallback())
                g2.fillRoundRect(0, 0, width, height, JBUI.scale(ARC), JBUI.scale(ARC))
            } finally {
                g2.dispose()
            }
            super.paintComponent(g)
        }

        private companion object {
            const val ARC = 8
        }
    }

    private companion object {
        const val MESSAGE_GAP_V = 4
        const val MESSAGE_GAP_H = 6

        /** Cap on a user block's width, as a fraction of the row's width (item 1). */
        const val HUG_MAX_FRACTION = 0.8

        /** Same fallback [WrappingEditorPane] uses for a not-yet-measured row. */
        const val HUG_FALLBACK_WIDTH = 200
    }
}
