package org.zhavoronkov.openrouter.toolwindow.chat

import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Container
import javax.swing.JComponent
import javax.swing.JScrollPane
import javax.swing.SwingUtilities

/**
 * A [BorderLayout] with one extra child that floats over the container's top-right corner instead
 * of taking a slot.
 *
 * Floating rather than occupying a row is what keeps the conversation's vertical rhythm: an
 * action attached to a message should not make every message taller, and the alternative - its own
 * strip above the text - adds a row to each one whether or not anyone ever hovers it.
 *
 * The overlay is refused a slot in [addLayoutComponent], so BorderLayout never sizes or positions
 * it and the container's preferred size ignores it entirely; [layoutContainer] then places it by
 * hand once everything else is in place.
 */
internal class TopRightOverlayLayout(private val overlay: JComponent) : BorderLayout() {

    override fun addLayoutComponent(comp: Component, constraints: Any?) {
        if (comp === overlay) return
        super.addLayoutComponent(comp, constraints)
    }

    override fun removeLayoutComponent(comp: Component) {
        if (comp === overlay) return
        super.removeLayoutComponent(comp)
    }

    override fun layoutContainer(target: Container) {
        super.layoutContainer(target)

        val insets = target.insets
        val size = overlay.preferredSize
        val x = target.width - insets.right - size.width - scrollbarClearance(target)
        overlay.setBounds(x, insets.top, size.width, size.height)
    }

    /**
     * How much room to leave at the right edge for the conversation's vertical scrollbar.
     *
     * The scrollbar is drawn over the right edge of the content, so a button flush against that
     * edge becomes unclickable the moment the conversation is long enough to scroll - which is
     * most of the time, and exactly when someone wants to copy an earlier message.
     *
     * Measured from the real scrollbar rather than assumed, because its width follows the look and
     * feel and the display's scaling. The constant is only reached before the message has been
     * added to the scroll pane.
     */
    private fun scrollbarClearance(target: Container): Int {
        val scrollPane = SwingUtilities.getAncestorOfClass(JScrollPane::class.java, target) as? JScrollPane
        return scrollPane?.verticalScrollBar?.preferredSize?.width ?: JBUI.scale(FALLBACK_SCROLLBAR_WIDTH)
    }

    private companion object {
        /** Matches what a JBScrollPane reports at 100% scaling. */
        const val FALLBACK_SCROLLBAR_WIDTH = 14
    }
}
