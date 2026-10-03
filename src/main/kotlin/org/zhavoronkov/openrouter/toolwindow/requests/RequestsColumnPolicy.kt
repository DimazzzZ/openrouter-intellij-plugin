package org.zhavoronkov.openrouter.toolwindow.requests

/**
 * A column of the Requests table. [dropOrder] is when it gives way at a narrow width: the lowest
 * goes first, and a null one never does - the requested id is the row's point, and the warning
 * mark is what a user scans the table for. A [resizable] one keeps the width the user drags it
 * to; the requested id always takes what is left, and the warning mark is only as wide as its icon.
 */
enum class RequestsColumn(val title: String, val dropOrder: Int?) {
    TIME("Time", 0),
    SENDER("Sender", 1),
    MODEL("Requested", null),
    COST("Cost", 2),
    WARNING("", null);

    val resizable: Boolean get() = this != MODEL && this != WARNING
}

/**
 * Which Requests columns fit a width, so a narrow tool window drops columns rather than wrapping a
 * cell or scrolling sideways.
 *
 * Free of Swing, like [org.zhavoronkov.openrouter.toolwindow.status.BreakdownColumnPolicy]: the
 * table measures each column's widest cell and passes the widths in already scaled, and this does
 * the arithmetic. The requested id takes whatever is left, and is never squeezed below
 * [modelMinWidth] to keep a droppable column.
 */
object RequestsColumnPolicy {

    /** The requested id's floor, in unscaled pixels: a middle-truncated slug stays readable. */
    const val MODEL_MIN_WIDTH = 120

    /**
     * @param widths each fixed column's width, the gap between columns included; the requested
     *   id's entry is ignored, since it takes the rest.
     * @return the columns to show, in table order.
     */
    fun visible(availableWidth: Int, modelMinWidth: Int, widths: Map<RequestsColumn, Int>): List<RequestsColumn> {
        val shown = RequestsColumn.entries.toMutableList()
        fun fits() = modelMinWidth + shown.filter { it != RequestsColumn.MODEL }.sumOf { widths[it] ?: 0 } <=
            availableWidth
        val droppable = RequestsColumn.entries.filter { it.dropOrder != null }.sortedBy { it.dropOrder }
        for (column in droppable) {
            if (fits()) break
            shown.remove(column)
        }
        return shown
    }

    /**
     * [order] with the [shown] columns in the order the table now shows them, after the user
     * dragged one: each slot a shown column held takes the next shown column, and a column dropped
     * for width keeps its slot, so a wider tab brings it back where it was.
     *
     * @param order every column, in the order the user arranged them.
     * @param shown the columns on screen, left to right; each one is in [order].
     */
    fun reordered(order: List<RequestsColumn>, shown: List<RequestsColumn>): List<RequestsColumn> {
        val next = shown.iterator()
        return order.map { if (it in shown) next.next() else it }
    }
}
