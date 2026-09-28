package org.zhavoronkov.openrouter.toolwindow.chat

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import java.awt.Color
import java.awt.Rectangle
import java.awt.image.BufferedImage

private const val LAYOUT_WIDTH = 400

/**
 * Every message is enclosed in an outlined, rounded bubble, and only the user's is filled.
 *
 * Asserted on painted pixels because that is the whole of what was asked for and the whole of what
 * can go wrong: the bubble is drawn in `paintComponent`, so no arrangement of components, borders
 * or colours proves anything about what actually reaches the screen. In particular the outline is
 * drawn a pixel inside the component's own bounds, and getting that wrong leaves it open along two
 * edges while every structural assertion stays green.
 */
class MessageBubblePlatformTest : BasePlatformTestCase() {

    private fun render(isUser: Boolean): BufferedImage {
        val view = MessageView("pareto", isUser = isUser, footnote = null)
        val panel = MessagesPanel()
        panel.add(view.component)
        repeat(2) {
            panel.setSize(LAYOUT_WIDTH, panel.preferredSize.height)
            layoutTreeRecursively(panel)
        }

        // Only the message itself, not the panel: MessagesPanel is opaque, so painting it first
        // leaves no backdrop for `isBlank` to recognise and the bubble's bounds come back as the
        // whole panel. The message's own component is transparent, which is exactly what lets the
        // white sheet stand in for the conversation behind it.
        val message = view.component
        val image = BufferedImage(message.width, message.height.coerceAtLeast(1), BufferedImage.TYPE_INT_RGB)
        val graphics = image.createGraphics()
        try {
            graphics.color = Color.WHITE
            graphics.fillRect(0, 0, image.width, image.height)
            message.paint(graphics)
        } finally {
            graphics.dispose()
        }
        return image
    }

    private fun BufferedImage.isBlank(x: Int, y: Int) = getRGB(x, y) == Color.WHITE.rgb

    /** The bounding box of everything painted: the bubble's outline is the outermost mark. */
    private fun BufferedImage.paintedBounds(): Rectangle {
        var minX = width
        var minY = height
        var maxX = -1
        var maxY = -1
        for (y in 0 until height) {
            for (x in 0 until width) {
                if (isBlank(x, y)) continue
                if (x < minX) minX = x
                if (y < minY) minY = y
                if (x > maxX) maxX = x
                if (y > maxY) maxY = y
            }
        }
        assertTrue("nothing was painted at all", maxX >= 0)
        return Rectangle(minX, minY, maxX - minX + 1, maxY - minY + 1)
    }

    /** How much of the bubble's own top edge is actually drawn, ignoring the rounded corners. */
    private fun horizontalRunAlongTopEdge(image: BufferedImage, bounds: Rectangle): Double {
        val from = bounds.x + bounds.width / 4
        val to = bounds.x + bounds.width * 3 / 4
        val drawn = (from until to).count { !image.isBlank(it, bounds.y) }
        return drawn.toDouble() / (to - from)
    }

    private fun assertOutlined(isUser: Boolean) {
        val image = render(isUser)
        val bounds = image.paintedBounds()
        val speaker = if (isUser) "user" else "assistant"

        assertEquals(
            "a $speaker message's bubble must be outlined along its whole top edge",
            1.0,
            horizontalRunAlongTopEdge(image, bounds),
            0.01
        )
        assertFalse(
            "a $speaker message's bubble must be outlined down its left edge too",
            image.isBlank(bounds.x, bounds.y + bounds.height / 2)
        )
        assertFalse(
            "a $speaker message's bubble must be closed on the right edge, not open",
            image.isBlank(bounds.x + bounds.width - 1, bounds.y + bounds.height / 2)
        )
        assertFalse(
            "a $speaker message's bubble must be closed along the bottom edge, not open",
            image.isBlank(bounds.x + bounds.width / 2, bounds.y + bounds.height - 1)
        )
    }

    fun testAUserMessageIsEnclosedInABubble() = assertOutlined(isUser = true)

    fun testAnAssistantMessageIsEnclosedInABubbleToo() = assertOutlined(isUser = false)

    /**
     * Sampled inside the bubble's own padding, just below the outline and above the first line of
     * text, which is the one strip that is fill and nothing else for both speakers.
     */
    private fun fillJustInsideTheTopEdge(isUser: Boolean): Int {
        val image = render(isUser)
        val bounds = image.paintedBounds()
        return image.getRGB(bounds.x + bounds.width / 2, bounds.y + INSIDE_EDGE_OFFSET)
    }

    fun testOnlyTheUserBubbleIsFilled() {
        assertFalse(
            "a reply's bubble must be outline-only - filling both makes the conversation two walls of colour",
            fillJustInsideTheTopEdge(isUser = false) != Color.WHITE.rgb
        )
        assertTrue(
            "the user's bubble must still carry its tint, so the two speakers differ by more than position",
            fillJustInsideTheTopEdge(isUser = true) != Color.WHITE.rgb
        )
    }

    private companion object {
        /** Clear of the outline and its antialiasing, and clear of the first line of text. */
        const val INSIDE_EDGE_OFFSET = 3
    }
}
