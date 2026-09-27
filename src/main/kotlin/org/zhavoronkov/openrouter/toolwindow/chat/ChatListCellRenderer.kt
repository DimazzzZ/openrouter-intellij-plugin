package org.zhavoronkov.openrouter.toolwindow.chat

import com.intellij.icons.AllIcons
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import org.zhavoronkov.openrouter.toolwindow.ChatPanel
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Rectangle
import java.text.DateFormat
import java.util.Date
import javax.swing.JList
import javax.swing.JPanel
import javax.swing.ListCellRenderer

/**
 * One chat row: title on the left (ellipsised), date on the right — or, on
 * the hovered row, a delete `✕` in the same East slot the date normally
 * occupies, so the row's width budget never changes.
 *
 * The previous implementation laid the row out with
 * `<div style='width:100%'><span style='float:right'>` inside a JLabel. Swing's
 * HTML engine barely supports `float` and `width:100%` does not track list
 * width, so the date drifted and clipped. BorderLayout does exactly this job.
 */
internal class ChatListCellRenderer(
    private val dateFormat: DateFormat
) : ListCellRenderer<ChatPanel.ChatSession> {

    /** Set by [ChatListView] as the mouse moves; -1 means no row is hovered. */
    var hoveredIndex: Int = -1

    private val titleLabel = JBLabel()
    private val dateLabel = JBLabel()

    // Tooltip is NOT set here: JList never delegates to a renderer component's
    // own tooltip (this is a stateless, reused component, not a live one on
    // screen). ChatListView.getToolTipText(MouseEvent) provides the real
    // per-row tooltip for the ✕, reusing closeIconRect() below for the hit-test.
    private val closeLabel = JBLabel(AllIcons.Actions.Close)
    private var eastComponent: JBLabel = dateLabel
    private val panel = JPanel(BorderLayout(JBUI.scale(GAP), 0)).apply {
        border = JBUI.Borders.empty(CELL_BORDER_V, CELL_BORDER_H)
        add(titleLabel, BorderLayout.CENTER)
        add(eastComponent, BorderLayout.EAST)
    }

    override fun getListCellRendererComponent(
        list: JList<out ChatPanel.ChatSession>,
        value: ChatPanel.ChatSession?,
        index: Int,
        isSelected: Boolean,
        cellHasFocus: Boolean
    ): Component {
        titleLabel.text = value?.title.orEmpty()
        titleLabel.toolTipText = value?.title
        dateLabel.text = value?.let { dateFormat.format(Date(it.createdAt)) }.orEmpty()

        val background = if (isSelected) list.selectionBackground else list.background
        val foreground = if (isSelected) list.selectionForeground else list.foreground

        panel.background = background
        panel.isOpaque = true
        titleLabel.foreground = foreground
        dateLabel.foreground = if (isSelected) foreground else UIUtil.getContextHelpForeground()

        val wantEast = if (index == hoveredIndex) closeLabel else dateLabel
        if (eastComponent !== wantEast) {
            panel.remove(eastComponent)
            panel.add(wantEast, BorderLayout.EAST)
            eastComponent = wantEast
        }

        return panel
    }

    /**
     * The delete icon's rectangle in list coordinates for a row whose cell
     * bounds are [cellBounds], derived from the row's own layout (the
     * panel's real border insets and the icon's own size) rather than a
     * hard-coded offset. Only meaningful for the currently-hovered row.
     */
    fun closeIconRect(cellBounds: Rectangle): Rectangle {
        val insets = panel.insets
        val icon = closeLabel.icon
        val iconWidth = icon?.iconWidth ?: 0
        val iconHeight = icon?.iconHeight ?: 0
        val x = cellBounds.x + cellBounds.width - insets.right - iconWidth
        val y = cellBounds.y + (cellBounds.height - iconHeight) / 2
        return Rectangle(x, y, iconWidth, iconHeight)
    }

    private companion object {
        const val CELL_BORDER_V = 4
        const val CELL_BORDER_H = 8
        const val GAP = 8
    }
}
