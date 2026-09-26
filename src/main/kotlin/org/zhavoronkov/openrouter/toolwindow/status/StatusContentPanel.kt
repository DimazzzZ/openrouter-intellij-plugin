package org.zhavoronkov.openrouter.toolwindow.status

import com.intellij.util.ui.JBUI
import java.awt.Dimension
import java.awt.GridBagLayout
import java.awt.Rectangle
import javax.swing.JPanel
import javax.swing.Scrollable
import javax.swing.SwingConstants

/**
 * [StatusTabPanel.createContentPanel]'s own view, wrapped in a [com.intellij.ui.components.JBScrollPane]
 * by [StatusTabPanel.createStatusPanel] - the fix for defect D (the breakdown's spend column
 * pushed off-screen by an unbounded-width model name).
 *
 * A plain `JPanel` is not [Scrollable], so [javax.swing.JViewport] falls back to laying it out at
 * its own PREFERRED width whenever that exceeds the viewport - that is the mechanism a horizontal
 * scrollbar relies on, and exactly what was happening here: the longest model id in the breakdown
 * pushed this panel's preferred width past the tool window's real width, so every column to its
 * right (the requests count, and the spend figure the block exists to answer) was laid out beyond
 * the visible viewport and simply never seen - not clipped WITHIN a visible row, pushed entirely
 * out of it. [BreakdownColumnPolicy] and [ModelNameLabel][BreakdownBlock]'s own middle-ellipsis
 * measure `rowsPanel`'s CURRENT width to decide what fits, but that width was never bounded either:
 * a `GridBagLayout` child asked to `fill = HORIZONTAL` still only ever receives ITS PARENT's real
 * width, so if the parent (ultimately this panel) is itself sized to an arbitrary preferred width,
 * every descendant down the chain inherits that same unbounded number, and no ellipsis or
 * column-hiding decision downstream of it can ever see the tool window's REAL width to react to.
 *
 * This is the identical bug class already fixed on the chat side of this repo - see
 * [org.zhavoronkov.openrouter.toolwindow.chat.MessagesPanel]'s own KDoc: a view that does not
 * implement [Scrollable] tells its containing viewport nothing about the width its own layout
 * depends on. `getScrollableTracksViewportWidth() = true` is the fix in both places: it tells the
 * viewport to hand this panel exactly the VIEWPORT's width, never its own (potentially huge)
 * preferred width, which is what makes `GridBagLayout` lay every row out at the tool window's real
 * width - the same real width [BreakdownColumnPolicy] and the model label's own middle-ellipsis
 * were always designed to react to, and could not, until now, ever actually receive.
 *
 * This is also why the earlier `weightx` fix (Task 17 - see [StatusTabPanel.createContentPanel]'s
 * own KDoc) did not finish the job on its own: `weightx` only distributes space WITHIN whatever
 * width this container is given, and the viewport was handing it a width larger than itself in the
 * first place. `getScrollableTracksViewportHeight() = false`, matching [MessagesPanel][org.zhavoronkov.openrouter.toolwindow.chat.MessagesPanel]:
 * this panel is taller than a narrow tool window far more often than it is wider, and the vertical
 * scrollbar [StatusTabPanel.createStatusPanel]'s [com.intellij.ui.components.JBScrollPane] already
 * relies on to show the whole page is the intended way to reach content below the fold - tracking
 * viewport height too would instead squash this panel's own preferred height down to the viewport's,
 * clipping everything past it with no way to scroll to it at all.
 */
class StatusContentPanel : JPanel(GridBagLayout()), Scrollable {

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
