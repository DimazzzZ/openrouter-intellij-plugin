package org.zhavoronkov.openrouter.toolwindow.chat

import com.intellij.util.ui.JBUI
import java.awt.Dimension
import java.awt.Rectangle
import javax.swing.BoxLayout
import javax.swing.JPanel
import javax.swing.Scrollable
import javax.swing.SwingConstants

/**
 * Vertical stack of messages that always matches the viewport's width.
 *
 * Without `getScrollableTracksViewportWidth() = true`, a BoxLayout column asks
 * each child for its preferred size, and a JEditorPane's preferred height
 * depends on the width it is given — so heights came out wrong and messages
 * were clipped. Tracking the viewport width is what makes wrapped text measure
 * correctly, and it is why the horizontal scrollbar can stay off here: wide
 * content gets its own scroll pane inside a segment (Task 13).
 */
class MessagesPanel : JPanel(), Scrollable {

    init {
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
        background = JBUI.CurrentTheme.ToolWindow.background()
    }

    override fun getPreferredScrollableViewportSize(): Dimension = preferredSize

    override fun getScrollableTracksViewportWidth(): Boolean = true

    override fun getScrollableTracksViewportHeight(): Boolean = false

    override fun getScrollableUnitIncrement(visibleRect: Rectangle, orientation: Int, direction: Int): Int =
        JBUI.scale(UNIT_INCREMENT)

    override fun getScrollableBlockIncrement(visibleRect: Rectangle, orientation: Int, direction: Int): Int =
        if (orientation == SwingConstants.VERTICAL) visibleRect.height else visibleRect.width

    private companion object {
        const val UNIT_INCREMENT = 16
    }
}
