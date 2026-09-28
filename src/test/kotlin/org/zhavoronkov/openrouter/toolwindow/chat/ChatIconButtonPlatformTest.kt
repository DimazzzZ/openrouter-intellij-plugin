package org.zhavoronkov.openrouter.toolwindow.chat

import com.intellij.icons.AllIcons
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.util.ui.JBUI
import java.awt.Color
import java.awt.Cursor
import java.awt.event.MouseEvent
import java.awt.image.BufferedImage

/**
 * The affordance the chat's borderless icon buttons owe the conversation toolbar beside them:
 * pointer, a hover background, and a distinct pressed background.
 *
 * These assert on painted pixels rather than on the flags behind them, because the bug being
 * locked down was precisely that every flag was already set correctly and nothing was drawn -
 * [com.intellij.ui.InplaceButton.paintHover] is an empty method unless a subclass wires it up, so
 * a state-based assertion would have been green throughout.
 */
class ChatIconButtonPlatformTest : BasePlatformTestCase() {

    private fun button() = ChatIconButton("Copy", AllIcons.Actions.Copy) { }.apply {
        size = preferredSize
        doLayout()
    }

    private fun ChatIconButton.mouse(id: Int) {
        dispatchEvent(
            MouseEvent(this, id, System.currentTimeMillis(), 0, width / 2, height / 2, 1, false, MouseEvent.BUTTON1)
        )
    }

    /** Paints onto an opaque white sheet so a translucent background still shows as a difference. */
    private fun render(button: ChatIconButton): BufferedImage {
        val image = BufferedImage(button.width, button.height, BufferedImage.TYPE_INT_RGB)
        val g = image.createGraphics()
        try {
            g.color = Color.WHITE
            g.fillRect(0, 0, image.width, image.height)
            button.paint(g)
        } finally {
            g.dispose()
        }
        return image
    }

    private fun differingPixels(a: BufferedImage, b: BufferedImage): Int {
        var differing = 0
        for (y in 0 until a.height) {
            for (x in 0 until a.width) {
                if (a.getRGB(x, y) != b.getRGB(x, y)) differing++
            }
        }
        return differing
    }

    fun testPointerCursorSoTheIconLooksClickableBeforeItIsHovered() {
        assertEquals(Cursor.HAND_CURSOR, button().cursor.type)
    }

    fun testTheButtonNeverTakesFocusFromTheComposer() {
        assertFalse(button().isFocusable)
    }

    fun testHoveringPaintsABackgroundBehindTheIcon() {
        val button = button()
        val idle = render(button)

        button.mouse(MouseEvent.MOUSE_ENTERED)
        val hovered = render(button)

        assertTrue(
            "hovering must paint a background - it painted $idle-identical pixels instead",
            differingPixels(idle, hovered) > 0
        )
    }

    fun testPressingPaintsADifferentBackgroundFromHovering() {
        val button = button()
        button.mouse(MouseEvent.MOUSE_ENTERED)
        val hovered = render(button)

        button.mouse(MouseEvent.MOUSE_PRESSED)
        val pressed = render(button)

        assertTrue(
            "pressing must be distinguishable from hovering",
            differingPixels(hovered, pressed) > 0
        )
    }

    fun testReleasingReturnsToThePlainHoverBackground() {
        val button = button()
        button.mouse(MouseEvent.MOUSE_ENTERED)
        val hovered = render(button)

        button.mouse(MouseEvent.MOUSE_PRESSED)
        button.mouse(MouseEvent.MOUSE_RELEASED)

        assertEquals("releasing must drop the pressed background", 0, differingPixels(hovered, render(button)))
    }

    /**
     * The colours are the toolbar's own, not ones chosen here: matching it in the default theme
     * while drifting in every other one is the failure this rules out.
     */
    fun testTheBackgroundsAreThePlatformActionButtonColours() {
        val button = button()
        button.mouse(MouseEvent.MOUSE_ENTERED)
        val hovered = render(button)
        button.mouse(MouseEvent.MOUSE_PRESSED)
        val pressed = render(button)

        val corner = { image: BufferedImage -> Color(image.getRGB(image.width / 2, 1)) }
        assertEquals(
            "hover must use JBUI.CurrentTheme.ActionButton.hoverBorder()",
            blendOnWhite(JBUI.CurrentTheme.ActionButton.hoverBorder()),
            corner(hovered)
        )
        assertEquals(
            "pressed must use JBUI.CurrentTheme.ActionButton.pressedBackground()",
            blendOnWhite(JBUI.CurrentTheme.ActionButton.pressedBackground()),
            corner(pressed)
        )
    }

    /** What an alpha-carrying theme colour becomes once [render] has composited it over white. */
    private fun blendOnWhite(color: Color): Color {
        val alpha = color.alpha / 255.0
        val over = { channel: Int -> Math.round(channel * alpha + 255 * (1 - alpha)).toInt() }
        return Color(over(color.red), over(color.green), over(color.blue))
    }
}
