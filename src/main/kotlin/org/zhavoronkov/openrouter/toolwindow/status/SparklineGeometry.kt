package org.zhavoronkov.openrouter.toolwindow.status

/**
 * Turns a series of values into polyline coordinates inside a box.
 *
 * Pure on purpose - no Swing, no AWT, no IntelliJ - so the arithmetic that
 * decides where the trend line actually goes is covered by ordinary unit tests
 * in the fast headless task, and [SparklineView] is left with nothing to do but
 * draw the points it is handed. The same split as the chat's ComposerLayout
 * over ComposerLayoutPolicy.
 *
 * Coordinates are in the box's own space: x grows right from 0, y grows DOWN
 * from 0, which is Swing's convention, so the largest value has the smallest y.
 */
object SparklineGeometry {

    data class Point(val x: Int, val y: Int)

    /**
     * @param values the series, oldest first
     * @param width box width in pixels, already scaled by the caller
     * @param height box height in pixels, already scaled by the caller
     * @return one point per value, or an empty list when there is nothing to draw
     */
    fun plot(values: List<Double>, width: Int, height: Int): List<Point> {
        if (values.isEmpty() || width <= 0 || height <= 0) return emptyList()

        val maxY = height - 1
        val min = values.min()
        val max = values.max()
        val span = max - min

        return values.mapIndexed { index, value ->
            Point(x = xFor(index, values.size, width), y = yFor(value, min, span, maxY))
        }
    }

    /**
     * A single point sits at the left edge; otherwise the series spans the full
     * width so the last sample touches the right edge.
     */
    private fun xFor(index: Int, count: Int, width: Int): Int =
        if (count == 1) 0 else index * (width - 1) / (count - 1)

    /**
     * A flat series - including an all-zero one, which is what a quiet day looks
     * like - has no span to scale against. Dropping it to the baseline reads as
     * "nothing happened"; stretching it across the box would read as activity.
     */
    private fun yFor(value: Double, min: Double, span: Double, maxY: Int): Int {
        if (span <= 0.0) return maxY
        val normalised = (value - min) / span
        return maxY - (normalised * maxY).toInt()
    }
}
