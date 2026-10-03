package org.zhavoronkov.openrouter.toolwindow.requests

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.zhavoronkov.openrouter.toolwindow.requests.RequestsColumn.COST
import org.zhavoronkov.openrouter.toolwindow.requests.RequestsColumn.MODEL
import org.zhavoronkov.openrouter.toolwindow.requests.RequestsColumn.SENDER
import org.zhavoronkov.openrouter.toolwindow.requests.RequestsColumn.TIME
import org.zhavoronkov.openrouter.toolwindow.requests.RequestsColumn.WARNING

class RequestsColumnPolicyTest {

    private val widths = mapOf(TIME to 70, SENDER to 90, COST to 60, WARNING to 24)

    private fun visible(width: Int) = RequestsColumnPolicy.visible(width, modelMinWidth = 120, widths = widths)

    @Test
    @DisplayName("a wide table shows every column in table order")
    fun wide() {
        assertEquals(listOf(TIME, SENDER, MODEL, COST, WARNING), visible(364))
    }

    @Test
    @DisplayName("columns give way time first, then sender, then cost")
    fun narrowing() {
        assertEquals(listOf(SENDER, MODEL, COST, WARNING), visible(363))
        assertEquals(listOf(MODEL, COST, WARNING), visible(293))
        assertEquals(listOf(MODEL, WARNING), visible(203))
    }

    @Test
    @DisplayName("the requested id and the warning mark stay however narrow")
    fun neverDropped() {
        assertEquals(listOf(MODEL, WARNING), visible(10))
    }

    @Test
    @DisplayName("a column whose width is not known takes no room")
    fun unknownWidth() {
        val partial = mapOf(TIME to 70, COST to 60)

        assertEquals(
            listOf(TIME, SENDER, MODEL, COST, WARNING),
            RequestsColumnPolicy.visible(250, modelMinWidth = 120, widths = partial)
        )
        assertEquals(
            listOf(SENDER, MODEL, COST, WARNING),
            RequestsColumnPolicy.visible(249, modelMinWidth = 120, widths = partial)
        )
    }

    @Test
    @DisplayName("a dragged column takes its new place among the shown ones")
    fun reorderedShown() {
        val order = listOf(TIME, SENDER, MODEL, COST, WARNING)

        assertEquals(
            listOf(COST, TIME, SENDER, MODEL, WARNING),
            RequestsColumnPolicy.reordered(order, listOf(COST, TIME, SENDER, MODEL, WARNING))
        )
    }

    @Test
    @DisplayName("a column dropped for width keeps its slot while the shown ones move")
    fun reorderedAroundHidden() {
        val order = listOf(TIME, SENDER, MODEL, COST, WARNING)

        assertEquals(
            listOf(TIME, SENDER, WARNING, MODEL, COST),
            RequestsColumnPolicy.reordered(order, listOf(WARNING, MODEL, COST))
        )
    }
}
