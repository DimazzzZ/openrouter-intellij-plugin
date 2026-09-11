package org.zhavoronkov.openrouter.ui

import com.intellij.util.ui.JBUI
import java.awt.Color
import java.awt.Dimension
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.geom.RoundRectangle2D
import javax.swing.JComponent

/**
 * A standalone rounded variant chip, painted the same way as the chips in
 * [VariantChipTableCellRenderer] so the Favorite Models legend stays visually
 * identical to the chips shown in the model tables.
 *
 * Unlike the table cell renderer (which paints a model id followed by a chip),
 * this component paints just the chip and sizes itself to fit the label.
 */
class VariantChipLabel(
    private val label: String,
    private val chipBg: Color,
    private val chipFg: Color,
) : JComponent() {

    init {
        isOpaque = false
        font = JBUI.Fonts.smallFont()
    }

    override fun getPreferredSize(): Dimension {
        val fm = getFontMetrics(font)
        val width = fm.stringWidth(label) + 2 * JBUI.scale(CHIP_H_PADDING)
        val height = fm.height + 2 * CHIP_V_PADDING
        return Dimension(width, height)
    }
    override fun getMinimumSize(): Dimension = preferredSize
    override fun getMaximumSize(): Dimension = preferredSize

    override fun paintComponent(g: Graphics) {
        super.paintComponent(g)
        val g2 = g.create() as Graphics2D
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            val fm = getFontMetrics(font)
            val arc = JBUI.scale(CHIP_ARC).toFloat()
            val shape = RoundRectangle2D.Float(
                0f,
                0f,
                width.toFloat(),
                height.toFloat(),
                arc,
                arc
            )
            g2.color = chipBg
            g2.fill(shape)
            g2.color = chipFg
            g2.drawString(label, JBUI.scale(CHIP_H_PADDING), CHIP_V_PADDING + fm.ascent)
        } finally {
            g2.dispose()
        }
    }

    companion object {
        private const val CHIP_ARC = 8
        private const val CHIP_H_PADDING = 6
        private const val CHIP_V_PADDING = 1
    }
}
