package org.zhavoronkov.openrouter.ui

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.ui.table.JBTable
import java.awt.Color
import java.awt.image.BufferedImage

private const val CELL_WIDTH = 600
private const val CELL_HEIGHT = 24

/**
 * The favorites table's model cell paints its chips itself, so whether a chip is there at all is a
 * question about pixels: a label list can be right while nothing of it is drawn. Each chip is
 * found by its own fill colour.
 */
class VariantChipTableCellRendererPlatformTest : BasePlatformTestCase() {

    private fun paintCell(id: String, problem: String? = null): BufferedImage {
        val table = JBTable()
        val cell = VariantChipTableCellRenderer(problemOf = { problem })
            .getTableCellRendererComponent(table, id, false, false, 0, 0)
        cell.setSize(CELL_WIDTH, CELL_HEIGHT)
        val image = BufferedImage(CELL_WIDTH, CELL_HEIGHT, BufferedImage.TYPE_INT_RGB)
        val graphics = image.createGraphics()
        try {
            cell.paint(graphics)
        } finally {
            graphics.dispose()
        }
        return image
    }

    private fun BufferedImage.pixelsOf(color: Color): Int {
        var count = 0
        for (y in 0 until height) for (x in 0 until width) if (getRGB(x, y) == color.rgb) count++
        return count
    }

    private fun chip(id: String, label: String) =
        ModelVariantChipRenderer.chipsFor(id).single { it.label == label }

    fun testALatestSlugPaintsALatestChip() {
        val id = "~openai/gpt-astra-latest"

        val latest = chip(id, "Latest")

        assertTrue(
            "the Latest chip's fill must be painted in the cell",
            paintCell(id).pixelsOf(latest.background) > MIN_CHIP_PIXELS
        )
    }

    fun testALatestSlugWithAVariantPaintsBothChips() {
        val id = "~google/gemini-flash-latest:free"
        val image = paintCell(id)

        assertTrue("the Free chip must be painted", image.pixelsOf(chip(id, "Free").background) > MIN_CHIP_PIXELS)
        assertTrue(
            "the Latest chip must be painted too",
            image.pixelsOf(chip(id, "Latest").background) > MIN_CHIP_PIXELS
        )
    }

    fun testAnOrdinarySlugPaintsNoLatestChip() {
        val latestFill = chip("~openai/gpt-astra-latest", "Latest").background

        assertEquals(0, paintCell("openai/gpt-4o:free").pixelsOf(latestFill))
    }

    fun testAPairPaintsItsPresetChipBesideItsVariantChip() {
        val id = "x-ai/grok-4-fast:free@preset/research"
        val image = paintCell(id)

        val presetFill = chip(id, "research").background
        val freeFill = chip(id, "Free").background
        assertTrue("the preset chip must be painted", image.pixelsOf(presetFill) > MIN_CHIP_PIXELS)
        assertTrue("the model's Free chip must be painted too", image.pixelsOf(freeFill) > MIN_CHIP_PIXELS)
    }

    /** The reason itself is the row's tooltip; the chip is what makes the row stand out. */
    fun testAPairThatCannotBeSentPaintsAProblemChipAndGivesTheReason() {
        val id = "openai/gpt-4o@preset/gone"
        val problemFill = ModelVariantChipRenderer.problemChip().background

        val marked = paintCell(id, "No preset named 'gone' is saved on OpenRouter")
        assertTrue("the problem chip must be painted", marked.pixelsOf(problemFill) > MIN_CHIP_PIXELS)
        assertEquals("a pair that can be sent has none", 0, paintCell(id).pixelsOf(problemFill))
        val cell = VariantChipTableCellRenderer(problemOf = { "No preset named 'gone' is saved on OpenRouter" })
            .getTableCellRendererComponent(JBTable(), id, false, false, 0, 0) as javax.swing.JComponent
        assertEquals("No preset named 'gone' is saved on OpenRouter", cell.toolTipText)
    }

    fun testAPlainModelPaintsNoPresetChip() {
        val presetFill = chip("openai/gpt-4o@preset/research", "research").background

        assertEquals(0, paintCell("openai/gpt-4o").pixelsOf(presetFill))
    }

    private companion object {
        /** Well clear of a stray antialiased pixel that happens to match; a chip fills hundreds. */
        const val MIN_CHIP_PIXELS = 50
    }
}
