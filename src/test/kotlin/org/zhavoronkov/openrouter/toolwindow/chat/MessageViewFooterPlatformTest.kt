package org.zhavoronkov.openrouter.toolwindow.chat

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.ui.InplaceButton
import com.intellij.ui.components.JBLabel
import java.awt.Color
import java.awt.Component
import java.awt.Container
import java.awt.Rectangle
import java.awt.image.BufferedImage
import javax.swing.JComponent
import javax.swing.SwingUtilities

private const val WIDE = 500
private const val NARROW = 220

/**
 * The footer under a reply says how it was produced: the answering model, the provider that served
 * it and what it cost on one line, and a warning above that line only when generation did not stop
 * normally.
 *
 * Asserted on painted pixels as well as on geometry, because a label can be present, sized and
 * carrying the right text while nothing of it reaches the screen - squeezed to zero width by its
 * neighbours, or clipped by the bubble - and every structural assertion stays green.
 */
class MessageViewFooterPlatformTest : BasePlatformTestCase() {

    private val summary = ReplySummary(
        requestedModel = "anthropic/claude-sonnet-4.5",
        provider = "Google Vertex",
        cost = 0.00042
    )

    private class Rendered(val root: JComponent, val image: BufferedImage)

    private fun render(summary: ReplySummary, width: Int = WIDE): Rendered {
        val view = MessageView("A short reply.", isUser = false, footnote = summary.facts, warning = summary.warning)
        val panel = MessagesPanel()
        panel.add(view.component)
        repeat(2) {
            panel.setSize(width, panel.preferredSize.height)
            layoutTreeRecursively(panel)
        }
        val root = view.component
        val image = BufferedImage(root.width, root.height.coerceAtLeast(1), BufferedImage.TYPE_INT_RGB)
        val graphics = image.createGraphics()
        try {
            graphics.color = Color.WHITE
            graphics.fillRect(0, 0, image.width, image.height)
            root.paint(graphics)
        } finally {
            graphics.dispose()
        }
        return Rendered(root, image)
    }

    private fun <T : Component> descendants(root: Component, type: Class<T>): List<T> {
        val found = mutableListOf<T>()
        fun walk(c: Component) {
            if (type.isInstance(c)) found += type.cast(c)
            if (c is Container) c.components.forEach(::walk)
        }
        walk(root)
        return found
    }

    private fun Rendered.label(text: String): JBLabel? =
        descendants(root, JBLabel::class.java).firstOrNull { it.text == text }

    private fun Rendered.copyButton(): InplaceButton = descendants(root, InplaceButton::class.java).single()

    private fun Rendered.boundsOf(c: Component): Rectangle {
        val origin = SwingUtilities.convertPoint(c.parent, c.location, root)
        return Rectangle(origin.x, origin.y, c.width, c.height)
    }

    /** Every non-background pixel inside [area], clipped to the image. */
    private fun Rendered.ink(area: Rectangle): List<Color> {
        val clipped = area.intersection(Rectangle(0, 0, image.width, image.height))
        val colors = mutableListOf<Color>()
        for (y in clipped.y until clipped.y + clipped.height) {
            for (x in clipped.x until clipped.x + clipped.width) {
                val rgb = image.getRGB(x, y)
                if (rgb != Color.WHITE.rgb) colors += Color(rgb)
            }
        }
        return colors
    }

    /** How far a colour is from grey: zero for any shade of grey, large for a saturated hue. */
    private fun Color.chroma(): Int = maxOf(red, green, blue) - minOf(red, green, blue)

    private fun onOneLine(a: Rectangle, b: Rectangle) = a.y < b.y + b.height && b.y < a.y + a.height

    fun testProviderAndCostArePaintedOnTheCopyButtonsLine() {
        val rendered = render(summary)
        val facts = rendered.label(summary.facts)
        assertNotNull("the footer must carry the reply's facts, found none reading '${summary.facts}'", facts)
        assertTrue("the facts must name the provider", facts!!.text.contains("Google Vertex"))
        assertTrue("the facts must say what the reply cost", facts.text.contains("$0.00042"))

        val factsBounds = rendered.boundsOf(facts)
        val button = rendered.boundsOf(rendered.copyButton())
        assertTrue(
            "the facts ($factsBounds) and the copy button ($button) must share one line",
            onOneLine(factsBounds, button)
        )
        assertTrue(
            "the facts (ending at x=${factsBounds.x + factsBounds.width}) must sit left of the copy button " +
                "(starting at x=${button.x})",
            factsBounds.x + factsBounds.width <= button.x
        )
        assertTrue(
            "the facts must actually be painted, not merely laid out",
            rendered.ink(factsBounds).isNotEmpty()
        )
    }

    fun testANormalStopPaintsNoWarning() {
        val rendered = render(summary.copy(finishReason = "stop"))

        assertEquals(
            "a normal stop must add nothing to the footer but the facts",
            listOf(summary.facts),
            descendants(rendered.root, JBLabel::class.java).map { it.text }
        )
        val wholeMessage = Rectangle(0, 0, rendered.image.width, rendered.image.height)
        assertTrue(
            "nothing in a normally-stopped reply may be painted in a warning colour",
            rendered.ink(wholeMessage).none { it.chroma() > MAX_GREY_CHROMA }
        )
    }

    fun testATruncatedReplyWarnsAboveTheFactsInADistinctColour() {
        val truncated = summary.copy(finishReason = "length")
        val rendered = render(truncated)

        val warning = rendered.label("Cut off at the token limit")
        assertNotNull("a reply cut off at the token limit must say so", warning)
        val warningBounds = rendered.boundsOf(warning!!)
        val factsBounds = rendered.boundsOf(rendered.label(truncated.facts)!!)

        assertTrue(
            "the warning ($warningBounds) must sit above the facts ($factsBounds), not beside or below them",
            warningBounds.y + warningBounds.height <= factsBounds.y
        )
        assertTrue(
            "the warning must be painted in a colour, not in the facts' grey",
            rendered.ink(warningBounds).count { it.chroma() > MAX_GREY_CHROMA } > MIN_COLOURED_PIXELS
        )
        assertTrue(
            "the facts must stay grey beside a warning, so the two are told apart",
            rendered.ink(factsBounds).none { it.chroma() > MAX_GREY_CHROMA }
        )
    }

    fun testAContentFilterStopNamesItsOwnReason() {
        val rendered = render(summary.copy(finishReason = "content_filter"))

        assertNotNull(
            "a reply stopped by a content filter must say that, not that it was truncated",
            rendered.label("Stopped by a content filter")
        )
        assertNull(rendered.label("Cut off at the token limit"))
    }

    /**
     * A side panel is the chat's normal home, and at that width the facts no longer fit. They must
     * give way - cut short on their own line - rather than wrap onto a second one or push the copy
     * button out of the bubble.
     */
    fun testTheFooterStaysOnOneLineAtANarrowWidth() {
        val longSummary = ReplySummary(
            requestedModel = "openrouter/auto",
            respondingModel = "anthropic/claude-sonnet-4.5-20250929",
            provider = "Amazon Bedrock",
            cost = 0.0123,
            finishReason = "length"
        )
        val rendered = render(longSummary, NARROW)
        val facts = rendered.label(longSummary.facts)!!
        val factsBounds = rendered.boundsOf(facts)
        val button = rendered.boundsOf(rendered.copyButton())

        assertTrue(
            "the facts must give way at a narrow width rather than keep their full " +
                "${facts.preferredSize.width}px; they were given ${factsBounds.width}px",
            facts.preferredSize.width > factsBounds.width
        )
        assertTrue(
            "the facts ($factsBounds) must be a single line, as tall as the copy button ($button) at most",
            factsBounds.height <= maxOf(button.height, facts.getFontMetrics(facts.font).height + 2)
        )
        assertTrue("the facts and the copy button must share one line", onOneLine(factsBounds, button))
        assertTrue(
            "the copy button ($button) must stay inside the message (width ${rendered.root.width})",
            button.x >= 0 && button.x + button.width <= rendered.root.width
        )
        descendants(rendered.root, JBLabel::class.java).forEach { label ->
            val bounds = rendered.boundsOf(label)
            assertTrue(
                "'${label.text}' ($bounds) must stay inside the message (width ${rendered.root.width})",
                bounds.x >= 0 && bounds.x + bounds.width <= rendered.root.width
            )
        }
        assertTrue("the squeezed facts must still be painted", rendered.ink(factsBounds).isNotEmpty())
    }

    private companion object {
        /** Well clear of the tint antialiasing puts on grey text, well short of a warning hue. */
        const val MAX_GREY_CHROMA = 60

        /** Enough coloured pixels to be a glyph or an icon rather than a stray fringe. */
        const val MIN_COLOURED_PIXELS = 20
    }
}
