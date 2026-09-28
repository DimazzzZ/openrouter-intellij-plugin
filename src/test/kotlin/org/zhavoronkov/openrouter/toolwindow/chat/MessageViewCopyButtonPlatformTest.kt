package org.zhavoronkov.openrouter.toolwindow.chat

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.ui.InplaceButton
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import java.awt.Container
import java.awt.Cursor
import java.awt.Dimension
import java.awt.Rectangle
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JPanel

/**
 * Regression test for the visual-pass defect where the per-message copy
 * button was a large bordered [JButton] that shifted every message below it
 * into place on hover.
 */
private const val WIDTH = 500
private const val LINE_REPEATS = 8

class MessageViewCopyButtonPlatformTest : BasePlatformTestCase() {

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

    /**
     * Same reasoning as the composer's gear: InplaceButton inherits the arrow, and a borderless
     * icon has nothing but its hover highlight to say it can be clicked.
     */
    private fun tallReply(footnote: String? = "Routed to somewhere") = MessageView(
        "a message with enough text to occupy several lines ".repeat(LINE_REPEATS),
        isUser = false,
        footnote = footnote
    )

    /**
     * The copy button sits at the foot of its own bubble, at the right-hand end.
     *
     * It spent a while floating over the message's top-right corner, which was the fix for an
     * earlier arrangement that put it below its own message and directly above the NEXT one, so
     * hovering what looked like the next message's corner copied the previous one. The bubble
     * settles that by itself, so this pins the button back at the bottom-right where it reads as
     * part of the message's own footer.
     */
    fun testCopyAffordanceSitsAtTheFootOfItsOwnBubble() {
        val root = tallReply().component
        val inRoot = boundsIn(root, laidOutCopyButton(root))

        assertTrue(
            "the copy button (y=${inRoot.y}) must sit at the foot of the message, not at its top " +
                "(message height=${root.height})",
            inRoot.y > root.height * 2 / 3
        )
        assertTrue(
            "the copy button must sit at the right-hand end: it starts at x=${inRoot.x} of ${root.width}",
            inRoot.x > root.width * 2 / 3
        )
    }

    /**
     * The model and what it cost share the button's line, immediately to its left - the whole
     * point of moving the button down here rather than leaving it in a corner of its own.
     */
    fun testTheFootnoteSharesTheCopyButtonsLine() {
        val root = tallReply().component
        val button = boundsIn(root, laidOutCopyButton(root))
        val footnote = boundsIn(root, findDescendant(root, JBLabel::class.java)!!)

        assertTrue(
            "the footnote (ends at x=${footnote.x + footnote.width}) must sit left of the copy " +
                "button (starts at x=${button.x})",
            footnote.x + footnote.width <= button.x
        )
        assertTrue(
            "the footnote (y=${footnote.y}..${footnote.y + footnote.height}) and the copy button " +
                "(y=${button.y}..${button.y + button.height}) must be on one line",
            footnote.y < button.y + button.height && button.y < footnote.y + footnote.height
        )
    }

    /**
     * A vertical scrollbar is drawn over the right edge of the conversation, so a button flush
     * against that edge cannot be clicked at all once the conversation is long enough to scroll.
     */
    fun testCopyAffordanceClearsTheVerticalScrollbar() {
        val view = MessageView("hello", isUser = false, footnote = null)
        val root = view.component
        val copyButton = laidOutCopyButton(root)
        val inRoot = boundsIn(root, copyButton)
        val scrollbarWidth = JBScrollPane(JPanel()).verticalScrollBar.preferredSize.width
        val gapToRightEdge = root.width - (inRoot.x + inRoot.width)

        assertTrue(
            "the copy button must clear the scrollbar: only ${gapToRightEdge}px to the right edge, " +
                "and the scrollbar is ${scrollbarWidth}px wide",
            gapToRightEdge >= scrollbarWidth
        )
    }

    /**
     * Lays the message out and returns the copy button with real bounds.
     *
     * The size check is here because a button that was never laid out reports zeroes, and every
     * geometry assertion below would then pass whatever the code did.
     */
    private fun laidOutCopyButton(root: JComponent): InplaceButton {
        val button = findDescendant(root, InplaceButton::class.java)!!
        root.size = Dimension(WIDTH, root.preferredSize.height)
        layoutTree(root)

        assertTrue("the button was never laid out, so this test would prove nothing", button.width > 0)
        return button
    }

    /**
     * The button's own x/y are relative to whatever holds it, so they say nothing about where it
     * is in the message. Converted, or an assertion compares a child-relative zero against the
     * message's own coordinates and passes no matter what.
     */
    private fun boundsIn(root: JComponent, c: java.awt.Component): Rectangle {
        val origin = javax.swing.SwingUtilities.convertPoint(c.parent, c.location, root)
        return Rectangle(origin.x, origin.y, c.width, c.height)
    }

    private fun layoutTree(c: java.awt.Container) {
        c.doLayout()
        c.components.forEach { if (it is java.awt.Container) layoutTree(it) }
    }

    fun testCopyAffordanceShowsAHandCursor() {
        val view = MessageView("hello", isUser = false, footnote = null)

        val copyButton = findDescendant(view.component, InplaceButton::class.java)!!

        assertEquals("the copy affordance must show a hand cursor", Cursor.HAND_CURSOR, copyButton.cursor.type)
    }

    fun testCopyAffordanceIsBorderlessNotABorderedJButton() {
        val view = MessageView("hello", isUser = false, footnote = "a footnote")

        assertNull(
            "the copy affordance must not be a plain bordered JButton",
            findDescendant(view.component, JButton::class.java)
        )
        assertNotNull(
            "the copy affordance must be the platform's borderless InplaceButton",
            findDescendant(view.component, InplaceButton::class.java)
        )
    }

    /**
     * Hovering must not move anything - not the message below it, and not the footnote beside it.
     *
     * This is the whole reason the button is hidden by not painting rather than by
     * [JComponent.setVisible]: Swing's layouts skip an invisible child, so the footer would give
     * up the button's width and height whenever the pointer left and take them back on every
     * hover, sliding the footnote sideways and shifting every message below.
     */
    fun testShowingTheCopyButtonMovesNothing() {
        val root = tallReply("a footnote").component
        val copyButton = laidOutCopyButton(root) as ChatIconButton
        val footnote = findDescendant(root, JBLabel::class.java)!!

        assertFalse("the copy affordance starts hidden until hover", copyButton.isPainted)
        val heightWhileHidden = root.preferredSize.height
        val footnoteWhileHidden = boundsIn(root, footnote)

        copyButton.isPainted = true
        root.size = Dimension(WIDTH, root.preferredSize.height)
        layoutTree(root)

        assertTrue("the message must have a real height", heightWhileHidden > 0)
        assertEquals(
            "the message's height must not change when the copy button appears on hover",
            heightWhileHidden,
            root.preferredSize.height
        )
        assertEquals(
            "the footnote must not move when the copy button appears on hover",
            footnoteWhileHidden,
            boundsIn(root, footnote)
        )
    }

    /** A message with no footnote shows no footnote text, but still offers the copy button. */
    fun testAMessageWithoutAFootnoteStillHasItsCopyButton() {
        val root = tallReply(footnote = null).component

        assertNull(
            "a message with no footnote must show no footnote text",
            findDescendant(root, JBLabel::class.java)
        )
        assertNotNull("the copy button must be there regardless", laidOutCopyButton(root))
    }
}
