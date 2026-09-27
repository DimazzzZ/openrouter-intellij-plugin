package org.zhavoronkov.openrouter.toolwindow.chat

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.ui.InplaceButton
import java.awt.BorderLayout
import java.awt.Container
import javax.swing.JButton
import javax.swing.JComponent

/**
 * Regression test for the visual-pass defect where the per-message copy
 * button was a large bordered [JButton] that shifted every message below it
 * into place on hover.
 */
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

    private fun southStripOf(view: MessageView): JComponent {
        val layout = view.component.layout as BorderLayout
        return layout.getLayoutComponent(view.component, BorderLayout.SOUTH) as JComponent
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

    fun testSouthStripHeightIsUnaffectedByTheCopyButtonsVisibility() {
        val view = MessageView("hello", isUser = false, footnote = "a footnote")
        val strip = southStripOf(view)
        val copyButton = findDescendant(strip, InplaceButton::class.java)!!

        assertFalse("the copy affordance starts hidden until hover", copyButton.isVisible)
        val heightWhileHidden = strip.preferredSize.height

        copyButton.isVisible = true
        val heightWhileVisible = strip.preferredSize.height

        assertTrue("the strip must reserve real height even for a footnote-only row", heightWhileHidden > 0)
        assertEquals(
            "the strip's height must not change when the copy button appears on hover",
            heightWhileHidden,
            heightWhileVisible
        )
    }

    fun testSouthStripHeightIsReservedEvenWithNoFootnote() {
        val view = MessageView("hello", isUser = false, footnote = null)
        val strip = southStripOf(view)

        assertTrue(
            "the strip must still reserve the copy button's height when there is no footnote",
            strip.preferredSize.height > 0
        )
    }
}
