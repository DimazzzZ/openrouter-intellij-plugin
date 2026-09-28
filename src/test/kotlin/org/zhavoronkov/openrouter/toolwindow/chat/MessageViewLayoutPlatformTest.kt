package org.zhavoronkov.openrouter.toolwindow.chat

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import java.awt.BorderLayout
import java.awt.Container
import java.awt.Point
import javax.swing.JEditorPane
import javax.swing.SwingUtilities

private const val LAYOUT_WIDTH = 400
private const val USER_BUBBLE_FRACTION = 0.8
private const val LONG_REPLY_WORD_COUNT = 200

/**
 * The polish-pass fixes to the message layout (items 1, 2 and 6): the user
 * tinted block hugs its content instead of spanning the row, user and
 * assistant text start at the same left edge, and the vertical gap between
 * two messages is uniform no matter who spoke.
 *
 * Two [javax.swing.JPanel.validate] passes mirror
 * [ChatConversationView.remeasure]: a [WrappingEditorPane]/`HuggingEditorPane`
 * only learns its real width on the first pass, so its height (and anything
 * measured from it) is only correct from the second pass on.
 */
class MessageViewLayoutPlatformTest : BasePlatformTestCase() {

    /**
     * A stand-in for [ChatConversationView.remeasure] usable without a real,
     * peered window: nothing under test ever gets a native peer in this
     * headless platform-test process, so [java.awt.Component.isValid] is
     * always false and [java.awt.Container.validate] is a no-op here. Laying
     * out the tree by hand, one [Container.doLayout] pass per level, does the
     * same job `validate()` does in production. Two full passes are done for
     * the same reason `remeasure()` does two: a [WrappingEditorPane] (and the
     * hugging pane user messages use) only learns its real width on the
     * first pass, so anything measured from its height needs a second one.
     */
    private fun layoutInPanel(vararg views: MessageView, width: Int = LAYOUT_WIDTH): MessagesPanel {
        val panel = MessagesPanel()
        views.forEach { panel.add(it.component) }
        // A message's own `component` has no maximum height, so pairing a
        // wildly oversized height (e.g. Short.MAX_VALUE) with BoxLayout would
        // stretch it to fill the leftover space, corrupting the very gap this
        // class measures. MessagesPanel is never stretched vertically in
        // production either - `getScrollableTracksViewportHeight() == false`
        // means the real scroll pane always gives it exactly its own
        // preferred height - so re-deriving that height, like production
        // does, is what keeps this realistic.
        panel.setSize(width, panel.preferredSize.height)
        layoutTreeRecursively(panel)
        panel.setSize(width, panel.preferredSize.height)
        layoutTreeRecursively(panel)
        return panel
    }

    private fun centerOf(container: Container): Container =
        (container.layout as BorderLayout).getLayoutComponent(container, BorderLayout.CENTER) as Container

    private fun <T> findDescendant(root: java.awt.Component, type: Class<T>): T? {
        if (type.isInstance(root)) {
            @Suppress("UNCHECKED_CAST")
            return root as T
        }
        if (root is Container) {
            for (child in root.components) {
                findDescendant(child, type)?.let { return it }
            }
        }
        return null
    }

    // --- A user message's bubble is a fixed fraction of the row ------------

    /**
     * Both lengths, because a width that depends on the text is the exact thing being ruled out:
     * a bubble that merely fits inside 80% satisfies any upper bound the long case can state, and
     * one that fills the row satisfies any lower bound the short case can state. Only asserting
     * that the two come out the same width says the content has no say in it.
     */
    fun testAUserBubbleTakesTheSameFractionOfTheRowWhateverItSays() {
        val short = MessageView("pareto", isUser = true, footnote = null)
        val long = MessageView("pareto ".repeat(60).trim(), isUser = true, footnote = null)
        layoutInPanel(short)
        layoutInPanel(long)

        val shortWidth = centerOf(short.component).width
        val longWidth = centerOf(long.component).width
        val expected = (LAYOUT_WIDTH * USER_BUBBLE_FRACTION).toInt()

        assertEquals(
            "a one-word and a sixty-word user message must come out the same width",
            longWidth,
            shortWidth
        )
        assertTrue(
            "a user bubble must take ${'$'}USER_BUBBLE_FRACTION of the ${'$'}LAYOUT_WIDTH row (about " +
                "${'$'}{expected}px), was ${'$'}{shortWidth}px",
            kotlin.math.abs(shortWidth - expected) <= JBUI_TOLERANCE + MESSAGE_GAP_ALLOWANCE
        )
    }

    fun testAssistantMessageStaysFullWidthRegardlessOfLength() {
        val view = MessageView("A short reply.", isUser = false, footnote = null)
        layoutInPanel(view)

        val flat = centerOf(view.component)

        assertTrue(
            "assistant messages must stay full width, was ${flat.width}px of $LAYOUT_WIDTH",
            flat.width > LAYOUT_WIDTH * 3 / 4
        )
    }

    // --- Item 2: text starts at the same left edge --------------------------

    fun testUserAndAssistantTextStartAtTheSameLeftEdge() {
        val userView = MessageView("pareto", isUser = true, footnote = null)
        val assistantView = MessageView("pareto", isUser = false, footnote = null)
        layoutInPanel(userView)
        layoutInPanel(assistantView)

        val userTextX = leftEdgeOfText(userView)
        val assistantTextX = leftEdgeOfText(assistantView)

        assertEquals(
            "user and assistant text must start on the same vertical line",
            assistantTextX,
            userTextX
        )
    }

    private fun leftEdgeOfText(view: MessageView): Int = leftEdgeAndTopOfText(view).x

    /**
     * Spacing pass (see the visual-pass audit,
     * "Spacing pass"): [testUserAndAssistantTextStartAtTheSameLeftEdge] above
     * only compares the prose pane's own component bounds, which is a
     * necessary but not sufficient check - a component can start at the
     * right x while what it actually PAINTS (the real glyph pixels, inside
     * whatever the HTML view's own box model does) starts somewhere else.
     * This measures the real thing the reported bug is about: the x of the
     * first non-background pixel actually painted, for a user message and
     * an assistant message laid out in the same container at the same
     * width. Pinned equal so this cannot regress a third time without a
     * test catching it.
     */
    fun testUserAndAssistantFirstPaintedTextPixelLineUp() {
        val userView = MessageView("pareto", isUser = true, footnote = null)
        val assistantView = MessageView("pareto", isUser = false, footnote = null)
        layoutInPanel(userView)
        layoutInPanel(assistantView)

        val userPixelX = firstPaintedTextPixelX(userView)
        val assistantPixelX = firstPaintedTextPixelX(assistantView)

        assertEquals(
            "the first painted text pixel must line up for user and assistant messages",
            assistantPixelX,
            userPixelX
        )
    }

    /**
     * Renders just the prose pane against a solid white backdrop and returns
     * the absolute x (relative to [view]'s own component) of the first pixel
     * anywhere in the pane that is not that backdrop colour - i.e. the first
     * column a real glyph is actually painted in, not merely where the pane's
     * own bounds start.
     */
    private fun firstPaintedTextPixelX(view: MessageView): Int {
        val pane = findDescendant(view.component, JEditorPane::class.java) ?: error("no prose pane found")
        val paneOrigin = SwingUtilities.convertPoint(pane, Point(0, 0), view.component)

        val width = pane.width.coerceAtLeast(1)
        val height = pane.height.coerceAtLeast(1)
        val image = java.awt.image.BufferedImage(width, height, java.awt.image.BufferedImage.TYPE_INT_ARGB)
        val graphics = image.createGraphics()
        graphics.color = java.awt.Color.WHITE
        graphics.fillRect(0, 0, width, height)
        pane.paint(graphics)
        graphics.dispose()

        for (x in 0 until width) {
            for (y in 0 until height) {
                val argb = image.getRGB(x, y)
                val alpha = (argb ushr 24) and 0xFF
                val rgb = argb and 0xFFFFFF
                if (alpha != 0 && rgb != 0xFFFFFF) {
                    return paneOrigin.x + x
                }
            }
        }
        error("no painted pixel found in the prose pane - message text is missing")
    }

    // --- Item 6: uniform vertical rhythm -------------------------------------

    /**
     * The gap is measured from the *content* two messages actually show, not
     * from their raw bounding boxes: [javax.swing.BoxLayout] abuts consecutive
     * components with zero space between them (confirmed directly - the
     * boxes' own y-deltas are always 0), so the entire visible gap lives
     * inside each component's own border/padding. That is exactly the
     * mechanism items 2 and 6 rely on: measuring text-top-to-strip-bottom is
     * what a reader's eye actually tracks as "the space between messages".
     */
    fun testVerticalGapIsUniformRegardlessOfSpeakerOrder() {
        val gapAssistantAssistant = gapBetween(isUserFirst = false, isUserSecond = false)
        val gapAssistantUser = gapBetween(isUserFirst = false, isUserSecond = true)
        val gapUserAssistant = gapBetween(isUserFirst = true, isUserSecond = false)
        val gapUserUser = gapBetween(isUserFirst = true, isUserSecond = true)

        assertTrue("there must be a real gap between messages", gapAssistantAssistant > 0)
        assertEquals(gapAssistantAssistant, gapAssistantUser)
        assertEquals(gapAssistantAssistant, gapUserAssistant)
        assertEquals(gapAssistantAssistant, gapUserUser)
    }

    private fun gapBetween(isUserFirst: Boolean, isUserSecond: Boolean): Int {
        val first = MessageView("hi", isUserFirst, footnote = null)
        val second = MessageView("hi", isUserSecond, footnote = null)
        layoutInPanel(first, second)

        return textTopAbsoluteY(second) - contentBottomAbsoluteY(first)
    }

    // --- Width-then-height audit: no descendant clipped by the message's own
    // --- bottom edge, the shape both WrappingEditorPane and ChatParamsPopup
    // --- shipped bugs shared. See the visual-pass audit.

    fun testLongWrappedReplyIsNotClippedByBottomEdge() {
        val longReply = "pareto ".repeat(LONG_REPLY_WORD_COUNT).trim()
        val view = MessageView(longReply, isUser = false, footnote = null)

        layoutInPanel(view)

        assertNoDescendantClippedByBottomEdge(view.component)
    }

    fun testProseSegmentWithATableIsNotClippedByBottomEdge() {
        val markdownWithTable = """
            | Col A | Col B |
            | --- | --- |
            | 1 | 2 |
        """.trimIndent()
        val view = MessageView(markdownWithTable, isUser = false, footnote = null)

        layoutInPanel(view)

        assertNoDescendantClippedByBottomEdge(view.component)
    }

    fun testProseSegmentWithAPreIsNotClippedByBottomEdge() {
        // A fence nested inside a blockquote is isolated by MessageSegmenter
        // into its own Prose segment whose rendered HTML contains a `<pre>`
        // (see MessageSegmenter's KDoc), which is what routes it through
        // MessageView.horizontallyScrollableProse instead of WrappingEditorPane.
        val markdownWithPreInBlockquote = """
            > ```
            > line one
            > line two
            > line three
            > ```
        """.trimIndent()
        val view = MessageView(markdownWithPreInBlockquote, isUser = false, footnote = null)

        layoutInPanel(view)

        assertNoDescendantClippedByBottomEdge(view.component)
    }

    /**
     * The bottom of the message's content, below which only its own padding remains.
     *
     * Measured from the message rather than from its footnote strip: the strip only exists when
     * there is a footnote, now that the copy button floats over the message instead of sitting in
     * that strip. The property under test - a uniform gap between messages whoever spoke - is the
     * same either way.
     */
    private fun contentBottomAbsoluteY(view: MessageView): Int =
        view.component.y + view.component.height - view.component.insets.bottom

    private fun textTopAbsoluteY(view: MessageView): Int =
        view.component.y + leftEdgeAndTopOfText(view).y

    private fun leftEdgeAndTopOfText(view: MessageView): Point {
        val pane = findDescendant(view.component, JEditorPane::class.java)
            ?: error("no prose pane found")
        return SwingUtilities.convertPoint(pane, Point(0, 0), view.component)
    }

    private companion object {
        /** Rounding slack for the 80% cap computed from an already-scaled row width. */
        const val JBUI_TOLERANCE = 2

        /** The left and right gaps between the bubble and the row, which the fraction sits inside. */
        const val MESSAGE_GAP_ALLOWANCE = 16
    }
}
