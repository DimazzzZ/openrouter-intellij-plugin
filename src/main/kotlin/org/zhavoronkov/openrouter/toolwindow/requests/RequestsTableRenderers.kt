package org.zhavoronkov.openrouter.toolwindow.requests

import com.intellij.util.ui.JBUI
import org.zhavoronkov.openrouter.toolwindow.chat.CHAT_WARNING_FOREGROUND
import org.zhavoronkov.openrouter.toolwindow.composer.MiddleEllipsis
import java.awt.Component
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import javax.swing.Icon
import javax.swing.JTable
import javax.swing.SwingConstants
import javax.swing.table.DefaultTableCellRenderer

/** The requested id, cut in the middle to the column's width so both author and model show. */
internal class MiddleEllipsisRenderer : DefaultTableCellRenderer() {
    override fun getTableCellRendererComponent(
        table: JTable,
        value: Any?,
        isSelected: Boolean,
        hasFocus: Boolean,
        row: Int,
        column: Int
    ): Component {
        super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column)
        val full = value?.toString().orEmpty()
        val room = table.columnModel.getColumn(column).width - insets.left - insets.right
        val metrics = getFontMetrics(font)
        text = MiddleEllipsis.fit(full, room, metrics::stringWidth)
        toolTipText = full.takeIf { text != it }
        return this
    }
}

/** The warning mark: an icon, with the reason as its tooltip, or nothing for a normal request. */
internal class WarningRenderer : DefaultTableCellRenderer() {
    override fun getTableCellRendererComponent(
        table: JTable,
        value: Any?,
        isSelected: Boolean,
        hasFocus: Boolean,
        row: Int,
        column: Int
    ): Component {
        super.getTableCellRendererComponent(table, "", isSelected, hasFocus, row, column)
        val reason = value?.toString().orEmpty()
        icon = WarningMark.takeIf { reason.isNotEmpty() }
        toolTipText = reason.ifEmpty { null }
        horizontalAlignment = SwingConstants.CENTER
        return this
    }
}

/**
 * A filled triangle in the chat's warning colour, so a request that went wrong reads as the same
 * kind of thing as the warning under a chat reply, and stands out in a column of plain text.
 */
internal object WarningMark : Icon {
    private const val SIZE = 10

    override fun getIconWidth() = JBUI.scale(SIZE)
    override fun getIconHeight() = JBUI.scale(SIZE)

    override fun paintIcon(c: Component?, g: Graphics, x: Int, y: Int) {
        val g2 = g.create() as Graphics2D
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            g2.color = CHAT_WARNING_FOREGROUND
            val size = iconWidth
            g2.fillPolygon(intArrayOf(x, x + size / 2, x + size), intArrayOf(y + size, y, y + size), 3)
        } finally {
            g2.dispose()
        }
    }
}
