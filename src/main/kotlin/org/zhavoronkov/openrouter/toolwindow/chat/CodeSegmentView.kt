package org.zhavoronkov.openrouter.toolwindow.chat

import com.intellij.icons.AllIcons
import com.intellij.ui.InplaceButton
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Dimension
import java.awt.Font
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.awt.event.MouseWheelEvent
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.SwingUtilities

/**
 * A fenced code block: monospace, never wrapped, scrolled horizontally on its
 * own so the prose around it keeps wrapping normally (spec D9). Wrapping code
 * at 280px would destroy its indentation, which is why the segment gets a
 * scrollbar of its own instead of the message getting one.
 */
class CodeSegmentView(segment: MessageSegment.Code) {

    val component: JComponent

    init {
        val area = JBTextArea(segment.code).apply {
            isEditable = false
            lineWrap = false
            // getFontWithFallback never throws when the family is missing: on
            // macOS/headless it builds a plain java.awt.Font(name, style, size)
            // directly, and elsewhere it goes through StyleContext.getFont(...).
            // Both silently substitute a platform default font for an unknown
            // family (standard java.awt.Font behavior) rather than failing, so
            // the worst case if "JetBrains Mono" isn't installed is a
            // non-monospace fallback, not a crash.
            font = UIUtil.getFontWithFallback(EditorFontName, Font.PLAIN, UIUtil.getLabelFont().size)
            background = UIUtil.getTextFieldBackground()
            border = JBUI.Borders.empty(PAD)
        }

        val scrollPane = HorizontallyScrollingPane(area) { area.preferredSize }.apply {
            border = JBUI.Borders.customLine(JBUI.CurrentTheme.CustomFrameDecorations.separatorForeground())
            alignmentX = Component.LEFT_ALIGNMENT
        }
        forwardVerticalWheelToAncestor(scrollPane)

        val header = JPanel(BorderLayout()).apply {
            isOpaque = false
            segment.language?.let {
                add(JBLabel(it).apply { foreground = UIUtil.getContextHelpForeground() }, BorderLayout.WEST)
            }
            add(copyButton(segment.code), BorderLayout.EAST)
        }

        component = JPanel(BorderLayout()).apply {
            isOpaque = false
            alignmentX = Component.LEFT_ALIGNMENT
            border = JBUI.Borders.empty(GAP, 0)
            add(header, BorderLayout.NORTH)
            add(scrollPane, BorderLayout.CENTER)
        }
    }

    /**
     * The same borderless affordance the message-level copy button uses.
     *
     * This was a [JButton] carrying `JButton.buttonType = toolBarButton`, which is exactly the
     * approach the message button's own KDoc records as insufficient: it does not guarantee the
     * absence of a border or content-area fill in every LaF, and here it did not - the code block
     * carried a large bordered button while the message beside it had a bare icon. [InplaceButton]
     * is the platform's own borderless hover-icon affordance and is what the rest of this package
     * already uses.
     */
    private fun copyButton(code: String) = InplaceButton("Copy code", AllIcons.Actions.Copy) {
        StringSelection(code).let { Toolkit.getDefaultToolkit().systemClipboard.setContents(it, it) }
    }.withHandCursor().apply {
        isFocusable = false
    }

    private companion object {
        const val EditorFontName = "JetBrains Mono"
        const val PAD = 6
        const val GAP = 4
    }
}

/**
 * A [JBScrollPane] that shows only a horizontal scrollbar and reports a
 * preferred size driven by the content's own natural height - never its
 * width, which BoxLayout ignores anyway as long as [getMaximumSize] stretches
 * to fill the column (the same idiom [WrappingEditorPane] already uses).
 *
 * The scrollbar's own thickness is added to the height only when the content
 * is actually wider than the space available, not unconditionally. Confirmed
 * empirically (task 13 report) that [JBScrollPane]'s own preferred-size
 * computation for `HORIZONTAL_SCROLLBAR_AS_NEEDED` never reserves this space
 * itself - not for the default overlay scrollbar style, and not even when the
 * scrollbar is forced into its opaque/persistent style - so reserving it
 * unconditionally would just be permanent wasted padding under every code
 * block or table that happens to already fit. The thickness itself is read
 * from the real [getHorizontalScrollBar]'s own preferred size rather than a
 * hard-coded constant, so it tracks whatever the current look-and-feel and
 * DPI scale actually need (measured at 14px, unscaled, in this plugin's test
 * environment - see the task 13 report for how that was confirmed).
 *
 * [naturalSize] must report the view's own unclamped preferred size. A plain
 * [javax.swing.JEditorPane] does this on its own; [WrappingEditorPane] does
 * not - it deliberately self-clamps to the width it was last given, which is
 * correct for in-flow wrapping prose but wrong here (see the divergence
 * recorded in the task 13 report for why [WrappingEditorPane] itself is not
 * reused for the horizontally-scrolling prose case).
 *
 * **Width-then-height audit (2026-09-18):** [naturalSize] looks like the same
 * "read a width-dependent height too early" shape that bit [WrappingEditorPane]
 * and `ChatParamsPopup.buildForm()` (see
 * the visual-pass audit), but it is not, at either
 * call site:
 * - [getPreferredSize] calls [naturalSize] lazily, every time Swing itself
 *   asks for the preferred size during layout - never snapshotted once into a
 *   stored `Dimension` before a constraining width exists, which is the part
 *   that was actually wrong in both shipped bugs.
 * - [CodeSegmentView]'s `area` is a [com.intellij.ui.components.JBTextArea]
 *   with `lineWrap = false`: it never reflows, so its preferred height is
 *   width-independent regardless of when it is read.
 * - [MessageView]'s `pane` (a raw [javax.swing.JEditorPane] rendering a
 *   `<pre>`/`<table>`) is deliberately left unclamped (see
 *   `MessageView.horizontallyScrollableProse`'s KDoc): the width it is
 *   finally drawn at is its own unconstrained natural width, since that is
 *   the whole point of scrolling it horizontally instead of wrapping it. The
 *   width [naturalSize] measures at and the width the pane is drawn at are
 *   therefore always the same value, so there is no "wrong width" for the
 *   height to be measured against.
 */
internal class HorizontallyScrollingPane(
    view: JComponent,
    private val naturalSize: () -> Dimension
) : JBScrollPane(view) {

    init {
        horizontalScrollBarPolicy = HORIZONTAL_SCROLLBAR_AS_NEEDED
        verticalScrollBarPolicy = VERTICAL_SCROLLBAR_NEVER
    }

    override fun getPreferredSize(): Dimension {
        val natural = naturalSize()
        val available = if (width > 0) width else natural.width
        val allowance = if (natural.width > available) horizontalScrollBar.preferredSize.height else 0
        return Dimension(0, natural.height + allowance)
    }

    override fun getMaximumSize(): Dimension = Dimension(Int.MAX_VALUE, preferredSize.height)
}

/**
 * A horizontal-only nested scroll pane swallows vertical mouse-wheel input
 * instead of letting it scroll the conversation around it: confirmed
 * empirically (task 13 report) that dispatching a plain wheel event at a pane
 * whose vertical scrollbar policy is `NEVER` leaves every ancestor's viewport
 * untouched and the event comes back consumed, rather than falling through.
 *
 * Forward plain (non-shift) wheel rotation to the nearest ancestor
 * [JScrollPane] - the conventional wheel gesture for a conversation - so
 * scrolling over a code block or a wide table keeps scrolling the
 * conversation. Shift+wheel (the conventional horizontal-scroll chord) is
 * left alone so this pane can still be scrolled horizontally by wheel.
 */
internal fun forwardVerticalWheelToAncestor(pane: JScrollPane) {
    pane.addMouseWheelListener { event ->
        if (!event.isShiftDown) {
            val ancestor = SwingUtilities.getAncestorOfClass(JScrollPane::class.java, pane) as? JScrollPane
            if (ancestor != null) {
                ancestor.dispatchEvent(
                    MouseWheelEvent(
                        ancestor,
                        event.id,
                        event.`when`,
                        event.modifiersEx,
                        1,
                        1,
                        event.clickCount,
                        false,
                        event.scrollType,
                        event.scrollAmount,
                        event.wheelRotation
                    )
                )
                event.consume()
            }
        }
    }
}
