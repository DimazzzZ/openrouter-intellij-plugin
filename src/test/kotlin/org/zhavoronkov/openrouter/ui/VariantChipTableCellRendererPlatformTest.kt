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

    private fun paintCell(id: String): BufferedImage {
        val table = JBTable()
        val cell = VariantChipTableCellRenderer()
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

    private companion object {
        /** Well clear of a stray antialiased pixel that happens to match; a chip fills hundreds. */
        const val MIN_CHIP_PIXELS = 50
    }
}
