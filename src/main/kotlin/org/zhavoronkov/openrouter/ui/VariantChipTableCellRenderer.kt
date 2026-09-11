package org.zhavoronkov.openrouter.ui

import com.intellij.ui.JBColor
import com.intellij.util.ui.JBUI
import org.zhavoronkov.openrouter.utils.ModelProviderUtils
import java.awt.Color
import java.awt.Component
import java.awt.Dimension
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.geom.RoundRectangle2D
import javax.swing.JLabel
import javax.swing.JTable
import javax.swing.table.TableCellRenderer

/**
 * Table cell renderer that paints a model id followed by a variant chip with
 * real rounded corners.
 *
 * Why not HTML? Swing's [javax.swing.text.html.CSS] only understands a CSS1
 * subset and throws an NPE on `border-radius`, which is why
 * [ModelVariantChipRenderer] emits square chips. Painting the chip ourselves
 * with [Graphics2D] and a [RoundRectangle2D] is the only way to get rounded
 * chips inside a Swing table cell. The palette and labels are sourced from
 * [ModelVariantChipRenderer] so both renderers stay visually consistent.
 */
class VariantChipTableCellRenderer(
    private val isAvailable: (String) -> Boolean = { true },
) : JLabel(), TableCellRenderer {

    private var modelId: String = ""
    private var chipLabel: String? = null
    private var chipBg: Color = JBColor.GRAY
    private var chipFg: Color = JBColor.WHITE

    init {
        isOpaque = true
        border = JBUI.Borders.empty(0, CELL_H_PADDING)
    }

    override fun getTableCellRendererComponent(
        table: JTable?,
        value: Any?,
        isSelected: Boolean,
        hasFocus: Boolean,
        row: Int,
        column: Int
    ): Component {
        val id = value as? String ?: ""
        modelId = ModelProviderUtils.stripVariant(id)
        chipLabel = ModelVariantChipRenderer.chipLabelFor(id)

        val parsed = ModelProviderUtils.parseModelId(id)
        when {
            parsed.variant != null -> {
                chipBg = ModelVariantChipRenderer.chipBackground(parsed.variant)
                chipFg = ModelVariantChipRenderer.chipForeground(parsed.variant)
            }
            parsed.unknownVariant != null -> {
                chipBg = ModelVariantChipRenderer.unknownChipBackground()
                chipFg = ModelVariantChipRenderer.unknownChipForeground()
            }
        }

        text = modelId
        val available = isAvailable(id)
        toolTipText = if (available) ModelVariantChipRenderer.tooltipFor(id) else UNAVAILABLE_TOOLTIP
        background = if (isSelected) {
            table?.selectionBackground ?: JBColor.BLUE
        } else {
            table?.background ?: JBColor.WHITE
        }
        foreground = when {
            isSelected -> table?.selectionForeground ?: JBColor.WHITE
            !available -> JBUI.CurrentTheme.Label.disabledForeground()
            else -> table?.foreground ?: JBColor.BLACK
        }
        return this
    }

    override fun getPreferredSize(): Dimension {
        val base = super.getPreferredSize()
        val label = chipLabel ?: return base
        return Dimension(base.width + CELL_GAP + chipWidthFor(label), base.height)
    }

    override fun paintComponent(g: Graphics) {
        super.paintComponent(g)
        val label = chipLabel ?: return
        val g2 = g.create() as Graphics2D
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            val fm = getFontMetrics(font)
            val textWidth = fm.stringWidth(modelId)
            val chipWidth = chipWidthFor(label)
            val chipHeight = fm.height + 2 * CHIP_V_PADDING
            val chipX = insets.left + textWidth + CELL_GAP
            val chipY = (height - chipHeight) / 2
            val arc = JBUI.scale(CHIP_ARC).toFloat()
            val shape = RoundRectangle2D.Float(
                chipX.toFloat(),
                chipY.toFloat(),
                chipWidth.toFloat(),
                chipHeight.toFloat(),
                arc,
                arc
            )
            g2.color = chipBg
            g2.fill(shape)
            g2.color = chipFg
            g2.drawString(
                label,
                chipX + JBUI.scale(CHIP_H_PADDING),
                chipY + CHIP_V_PADDING + fm.ascent
            )
        } finally {
            g2.dispose()
        }
    }

    private fun chipWidthFor(label: String): Int {
        val fm = getFontMetrics(font)
        return fm.stringWidth(label) + 2 * JBUI.scale(CHIP_H_PADDING)
    }

    companion object {
        private const val UNAVAILABLE_TOOLTIP = "Not in the current model catalog"
        private const val CHIP_ARC = 8
        private const val CHIP_H_PADDING = 6
        private const val CHIP_V_PADDING = 1
        private val CELL_GAP = JBUI.scale(6)
        private const val CELL_H_PADDING = 4
    }
}
