package org.zhavoronkov.openrouter.toolwindow.chat

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.ui.InplaceButton
import com.intellij.ui.components.JBScrollPane
import java.awt.BorderLayout
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
    /**
     * The copy button must belong to its own message, visibly.
     *
     * It used to sit in the south strip, at the bottom-right - which put it directly above the
     * NEXT message's first line and below its own, so hovering what looked like the next message's
     * corner copied the previous one. It now floats over its own message's top-right.
     */
    fun testCopyAffordanceSitsAtTheTopOfItsOwnMessage() {
        val view = MessageView(
            "a message with enough text to occupy several lines ".repeat(LINE_REPEATS),
            isUser = false,
            footnote = "Routed to somewhere"
        )
        val root = view.component
        val copyButton = laidOutCopyButton(root)
        val inRoot = boundsIn(root, copyButton)
        val topRegion = root.insets.top + inRoot.height

        assertTrue(
            "the copy button (y=${inRoot.y}) must sit at the top of the message, not at its foot " +
                "(message height=${root.height})",
            inRoot.y <= topRegion
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
     * The button starts hidden until hover, and a BorderLayout skips invisible children - so
     * without making it visible first every geometry assertion would be comparing zeroes and would
     * pass whatever the code did. The size check is here so that can never quietly happen again.
     */
    private fun laidOutCopyButton(root: JComponent): InplaceButton {
        val button = findDescendant(root, InplaceButton::class.java)!!
        button.isVisible = true
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
     * Hovering must not move anything.
     *
     * The strip used to reserve the button's height for exactly this reason - a component
     * appearing inside a BorderLayout slot grows that slot and shoves every message below it down
     * the moment the pointer arrives. The button no longer occupies a slot at all, so the property
     * now holds by construction; this keeps it that way.
     */
    fun testShowingTheCopyButtonDoesNotChangeTheMessageHeight() {
        val view = MessageView("hello", isUser = false, footnote = "a footnote")
        val copyButton = findDescendant(view.component, InplaceButton::class.java)!!

        assertFalse("the copy affordance starts hidden until hover", copyButton.isVisible)
        val heightWhileHidden = view.component.preferredSize.height

        copyButton.isVisible = true
        val heightWhileVisible = view.component.preferredSize.height

        assertTrue("the message must have a real height", heightWhileHidden > 0)
        assertEquals(
            "the message's height must not change when the copy button appears on hover",
            heightWhileHidden,
            heightWhileVisible
        )
    }

    /**
     * A message with no footnote carries no footnote row: the row's height used to be reserved for
     * the copy button, and the button is no longer in it.
     */
    fun testAMessageWithoutAFootnoteHasNoFootnoteRow() {
        val view = MessageView("hello", isUser = false, footnote = null)

        val layout = view.component.layout as BorderLayout
        assertNull(
            "nothing should be reserved at the foot of a message that has no footnote",
            layout.getLayoutComponent(view.component, BorderLayout.SOUTH)
        )
    }
}
