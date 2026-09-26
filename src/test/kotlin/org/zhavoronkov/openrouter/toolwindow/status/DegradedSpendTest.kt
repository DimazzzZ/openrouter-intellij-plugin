package org.zhavoronkov.openrouter.toolwindow.status

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("DegradedSpend")
class DegradedSpendTest {

    @Test
    @DisplayName("no snapshots produces an empty series")
    fun `no snapshots produces an empty series`() {
        assertTrue(DegradedSpend.spendSeries(emptyList()).isEmpty())
    }

    @Test
    @DisplayName("a single snapshot has nothing to difference against, so the series is empty")
    fun `a single snapshot produces an empty series`() {
        assertTrue(DegradedSpend.spendSeries(listOf(1L to 5.0)).isEmpty())
    }

    @Test
    @DisplayName("two ascending snapshots produce their one positive difference")
    fun `two ascending snapshots produce one difference`() {
        val series = DegradedSpend.spendSeries(listOf(1L to 10.0, 2L to 12.5))

        assertEquals(listOf(2.5), series)
    }

    @Test
    @DisplayName("an unsorted list is sorted by timestamp before differencing, not left in input order")
    fun `an unsorted list is sorted before differencing`() {
        // Fed out of order: t=3 (20.0), t=1 (10.0), t=2 (14.0). Chronological order is
        // 10.0 -> 14.0 -> 20.0, i.e. differences [4.0, 6.0]. Differencing the INPUT order instead
        // would produce [20.0 - 10.0, 14.0 - 20.0] = [10.0, -6.0] (clamped to [10.0, 0.0]) - a
        // different and wrong answer, so this pins that sorting actually happens.
        val series = DegradedSpend.spendSeries(
            listOf(3L to 20.0, 1L to 10.0, 2L to 14.0)
        )

        assertEquals(listOf(4.0, 6.0), series)
    }

    @Test
    @DisplayName("a credit top-up lowers totalUsed, which clamps to zero rather than negative spend")
    fun `a top-up clamps to zero instead of going negative`() {
        // totalUsed drops from 50.0 to 5.0 - a top-up, not $45 of negative spend.
        val series = DegradedSpend.spendSeries(listOf(1L to 50.0, 2L to 5.0))

        assertEquals(listOf(0.0), series)
    }

    @Test
    @DisplayName("positive control: a genuine rise is never clamped away")
    fun `a genuine rise is preserved`() {
        val series = DegradedSpend.spendSeries(listOf(1L to 0.0, 2L to 3.0, 3L to 3.0, 4L to 9.0))

        assertEquals(listOf(3.0, 0.0, 6.0), series)
    }

    @Test
    @DisplayName("a top-up mid-series clamps only the interval it falls in, not the whole series")
    fun `a top-up only clamps its own interval`() {
        val series = DegradedSpend.spendSeries(
            listOf(1L to 10.0, 2L to 15.0, 3L to 2.0, 4L to 6.0)
        )

        assertEquals(listOf(5.0, 0.0, 4.0), series)
    }
}
