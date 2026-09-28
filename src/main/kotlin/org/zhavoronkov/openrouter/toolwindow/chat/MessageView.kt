package org.zhavoronkov.openrouter.toolwindow.chat

import com.intellij.icons.AllIcons
import com.intellij.ui.ColorUtil
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
import java.awt.Insets
import java.awt.RenderingHints
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.Box
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
 * Both speakers get a [MessageBubble]: an outlined, rounded card around the
 * text. Only one of them being enclosed (the earlier arrangement, where a
 * user message sat in a tinted block and a reply was left bare) makes the
 * reply read as the page rather than as something someone said, so the eye
 * has to work out who is speaking from the tint alone. Enclosing both turns
 * that into a shape difference, and the user's fill then only has to say
 * which of the two speakers it is.
 *
 * A user message's bubble takes a fixed [USER_BUBBLE_FRACTION] of the row and
 * a reply's takes all of it, so which of the two is speaking can be read off
 * the shape alone, before the tint or the text. The fraction is held by
 * [MessageGapBorder], as a slice of the row reserved on the right-hand side.
 *
 * Padding is now symmetrical: each bubble supplies its own, and [component]
 * adds the same gap around both, so user and assistant text starts on the
 * same line and the same vertical gap separates every pair of messages
 * regardless of who spoke - all without the cancellation arithmetic the
 * half-enclosed arrangement needed.
 *
 * Each bubble ends in a [MessageFooter]: the model and cost, and the copy
 * button, on one right-aligned line at the bubble's foot.
 */
class MessageView(text: String, isUser: Boolean, footnote: String?) {

    val component: JComponent

    init {
        val body = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            isOpaque = false
            alignmentX = Component.LEFT_ALIGNMENT
        }

        MessageSegmenter.split(text).forEach { segment ->
            body.add(
                when (segment) {
                    is MessageSegment.Prose -> proseComponent(segment.markdown)
                    is MessageSegment.Code -> CodeSegmentView(segment).component
                }
            )
        }

        val copyButton = copyButton(text)
        val bubble = MessageBubble(body, filled = isUser, footer = MessageFooter(footnote, copyButton))

        component = JPanel(BorderLayout()).apply {
            isOpaque = false
            alignmentX = Component.LEFT_ALIGNMENT
            border = MessageGapBorder(if (isUser) 1.0 - USER_BUBBLE_FRACTION else 0.0)
            add(bubble, BorderLayout.CENTER)
            addMouseListener(object : MouseAdapter() {
                override fun mouseEntered(e: MouseEvent) {
                    copyButton.isPainted = true
                }
                override fun mouseExited(e: MouseEvent) {
                    if (!contains(e.point)) copyButton.isPainted = false
                }
            })
        }
    }

    /**
     * The copy button, which lives at the foot of the message's own bubble.
     *
     * It is what pays for losing cross-message selection when each message became its own
     * component (spec D5), and it shows only on hover so it costs no attention.
     *
     * It briefly floated over the message's top-right corner, which was the fix for an earlier
     * arrangement where it sat below its own message and directly above the NEXT one, so hovering
     * what looked like the next message's corner copied the previous one. The bubble settles that
     * on its own - the outline says where one message ends - so the button can go back to the
     * bottom-right, beside the model and cost it belongs with.
     */
    private fun copyButton(text: String): ChatIconButton =
        ChatIconButton("Copy message", AllIcons.Actions.Copy) {
            StringSelection(text).let {
                Toolkit.getDefaultToolkit().systemClipboard.setContents(it, it)
            }
        }.apply {
            isPainted = false
        }

    private fun proseComponent(markdown: String): JComponent {
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

    /**
     * The line at the foot of a bubble: the model and what it cost on the left of the copy button,
     * both pushed to the right-hand end.
     *
     * The copy button is hidden by not being painted rather than by [JComponent.setVisible],
     * because an invisible child is one Swing's layouts skip entirely: the row would lose the
     * button's width and height every time the pointer left, so the footnote would slide sideways
     * and every message below would shift up. Unpainted, the button keeps its slot and hovering
     * moves nothing.
     */
    private class MessageFooter(footnote: String?, copyButton: JComponent) : JPanel(BorderLayout()) {
        init {
            isOpaque = false
            border = JBUI.Borders.emptyTop(FOOTER_GAP_V)
            add(
                JPanel().apply {
                    layout = BoxLayout(this, BoxLayout.X_AXIS)
                    isOpaque = false
                    footnote?.takeIf { it.isNotBlank() }?.let {
                        add(footnoteLabel(it))
                        add(Box.createRigidArea(Dimension(JBUI.scale(FOOTER_GAP_H), 0)))
                    }
                    add(copyButton)
                },
                BorderLayout.EAST
            )
        }

        private companion object {
            const val FOOTER_GAP_V = 4
            const val FOOTER_GAP_H = 6

            fun footnoteLabel(footnote: String) = JBLabel(footnote).apply {
                foreground = UIUtil.getContextHelpForeground()
                font = JBUI.Fonts.smallFont()
            }
        }
    }

    /**
     * A rounded card around one message's text: outlined for both speakers, and additionally
     * tinted for the user so the two are told apart by more than position.
     *
     * The outline is [JBColor.border] - the colour the IDE already outlines its own components
     * with - rather than a colour invented here, so the bubble stays quiet in every theme instead
     * of matching the default one and drifting everywhere else.
     */
    private class MessageBubble(
        body: JComponent,
        private val filled: Boolean,
        footer: JComponent
    ) : JPanel(BorderLayout()) {
        init {
            isOpaque = false
            border = JBUI.Borders.empty(BUBBLE_PAD_V, BUBBLE_PAD_H)
            add(body, BorderLayout.CENTER)
            add(footer, BorderLayout.SOUTH)
        }

        override fun paintComponent(g: Graphics) {
            val g2 = g.create() as Graphics2D
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
                val arc = JBUI.scale(ARC)
                if (filled) {
                    g2.color = JBColor.namedColor("Chat.userMessageBackground", userMessageBackgroundFallback())
                    g2.fillRoundRect(0, 0, width, height, arc, arc)
                }
                g2.color = JBColor.namedColor("Chat.messageBorder", JBColor.border())
                // Inset by the stroke's own width: drawRoundRect draws ON the coordinates it is
                // given, so a right/bottom edge at width/height falls outside the clip and the
                // outline comes out open on two sides.
                g2.drawRoundRect(0, 0, width - 1, height - 1, arc, arc)
            } finally {
                g2.dispose()
            }
            super.paintComponent(g)
        }

        private companion object {
            const val ARC = 10
            const val BUBBLE_PAD_V = 6
            const val BUBBLE_PAD_H = 10
        }
    }

    /**
     * The gap around a bubble - the space between one message and the next, and between a message
     * and the conversation's edge - plus, for a user message, the slice of the row its bubble is
     * not allowed to take.
     *
     * That slice has to be a border inset rather than a narrower component, because the bubble
     * sits in a [BorderLayout.CENTER] slot, which is stretched to whatever is left over after the
     * insets. It cannot be a fixed number of pixels either: the conversation is a tool window and
     * is resized constantly, and the fraction has to survive that. A border is the one place
     * [java.awt.Container.doLayout] asks the question late enough to answer it from the row's real
     * width.
     */
    private class MessageGapBorder(private val reservedRightFraction: Double) : Border {

        override fun getBorderInsets(c: Component): Insets {
            val vertical = JBUI.scale(MESSAGE_GAP_V)
            val horizontal = JBUI.scale(MESSAGE_GAP_H)
            val reserved = (c.width * reservedRightFraction).toInt()
            return Insets(vertical, horizontal, vertical, horizontal + reserved)
        }

        override fun isBorderOpaque(): Boolean = false

        override fun paintBorder(c: Component, g: Graphics, x: Int, y: Int, width: Int, height: Int) = Unit
    }

    private companion object {
        const val MESSAGE_GAP_V = 4
        const val MESSAGE_GAP_H = 6

        /** How much of the row a user message's bubble takes; a reply takes all of it. */
        const val USER_BUBBLE_FRACTION = 0.9
    }
}
