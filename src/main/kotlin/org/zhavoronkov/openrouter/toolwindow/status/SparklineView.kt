package org.zhavoronkov.openrouter.toolwindow.status

import com.intellij.ui.JBColor
import com.intellij.util.ui.JBUI
import java.awt.BasicStroke
import java.awt.Dimension
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import javax.swing.JComponent

/**
 * Draws the polyline [SparklineGeometry.plot] returns for the current [values].
 *
 * Owns no arithmetic of its own - no axis scaling, no baseline decision, no
 * off-by-one handling. All of that lives in [SparklineGeometry], which is
 * pure and unit-tested in the fast task; this view asks it for points sized
 * to its own current pixel box and paints exactly the polyline those points
 * describe. No axes, no gridlines, no labels - just the line, anti-aliased,
 * in a theme colour.
 */
class SparklineView {

    /** The series to draw, oldest first. Reassigning repaints the view. */
    var values: List<Double> = emptyList()
        set(value) {
            field = value
            component.repaint()
        }

    val component: JComponent = SparklineCanvas()

    private inner class SparklineCanvas : JComponent() {
        init {
            isOpaque = false
            preferredSize = Dimension(JBUI.scale(PREFERRED_WIDTH), JBUI.scale(PREFERRED_HEIGHT))
        }

        /**
         * [SparklineGeometry.plot] already returns an empty list for an empty
         * series or a non-positive box - including on the very first paint,
         * before layout has given this component a real size - so the only
         * job here is to draw nothing in that case, and otherwise trace the
         * polyline through whatever points it hands back without touching
         * their coordinates.
         */
        override fun paintComponent(g: Graphics) {
            super.paintComponent(g)
            val points = SparklineGeometry.plot(values, width, height)
            if (points.isEmpty()) return

            val g2 = g.create() as Graphics2D
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
                g2.color = JBColor.namedColor(LINE_COLOR_KEY, JBUI.CurrentTheme.Focus.focusColor())
                g2.stroke = BasicStroke(JBUI.scale(LINE_STROKE_WIDTH).toFloat())
                g2.drawPolyline(
                    IntArray(points.size) { points[it].x },
                    IntArray(points.size) { points[it].y },
                    points.size
                )
            } finally {
                g2.dispose()
            }
        }
    }

    private companion object {
        const val PREFERRED_WIDTH = 200
        const val PREFERRED_HEIGHT = 32
        const val LINE_STROKE_WIDTH = 2
        const val LINE_COLOR_KEY = "OpenRouter.Status.sparklineLine"
    }
}
