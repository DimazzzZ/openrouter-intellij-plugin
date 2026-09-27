package org.zhavoronkov.openrouter.toolwindow.status

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * [BreakdownColumnPolicy]'s resize contract, covered without a running IDE.
 *
 * Reference widths (scaled px) and the totals they produce, all against
 * `modelMinWidth = 140`, `requestsWidth = 30`, `spendWidth = 60`, `gap = 12`:
 *   required = 140 + 30 + 60 + 12*2 = 254
 */
@DisplayName("BreakdownColumnPolicy")
class BreakdownColumnPolicyTest {

    private val modelMinWidth = 140
    private val requestsWidth = 30
    private val spendWidth = 60
    private val gap = 12
    private val requiredWidth = 254

    @Test
    @DisplayName("a wide row shows the requests column")
    fun `a wide row shows the requests column`() {
        val shown = BreakdownColumnPolicy.showsRequestsColumn(
            availableWidth = 600,
            modelMinWidth = modelMinWidth,
            requestsWidth = requestsWidth,
            spendWidth = spendWidth,
            gap = gap
        )

        assertTrue(shown)
    }

    @Test
    @DisplayName("the 280px floor hides the requests column when it would starve the model")
    fun `the 280px floor hides the requests column when it would starve the model`() {
        // Realistic widths for a 3-digit count ("500") next to a $-prefixed, 4-decimal spend
        // figure ("$12.3456") at this tab's font - required = 140 + 70 + 90 + 12*2 = 324, which
        // does not fit in the tab's own 280px floor (324 > 280).
        val shown = BreakdownColumnPolicy.showsRequestsColumn(
            availableWidth = 280,
            modelMinWidth = modelMinWidth,
            requestsWidth = 70,
            spendWidth = 90,
            gap = gap
        )

        assertFalse(shown)
    }

    @Test
    @DisplayName("exactly enough room shows the column - the boundary is inclusive")
    fun `exactly enough room shows the column - the boundary is inclusive`() {
        val shown = BreakdownColumnPolicy.showsRequestsColumn(
            availableWidth = requiredWidth,
            modelMinWidth = modelMinWidth,
            requestsWidth = requestsWidth,
            spendWidth = spendWidth,
            gap = gap
        )

        assertTrue(shown)
    }

    @Test
    @DisplayName("one pixel short of the requirement hides the column")
    fun `one pixel short of the requirement hides the column`() {
        val shown = BreakdownColumnPolicy.showsRequestsColumn(
            availableWidth = requiredWidth - 1,
            modelMinWidth = modelMinWidth,
            requestsWidth = requestsWidth,
            spendWidth = spendWidth,
            gap = gap
        )

        assertFalse(shown)
    }

    @Test
    @DisplayName("a wider requests column alone can flip a fitting row to not fitting")
    fun `a wider requests column alone can flip a fitting row to not fitting`() {
        // Same availableWidth that exactly fits requestsWidth = 30 (requiredWidth, above) no
        // longer fits once requestsWidth alone grows by a single pixel - proves requestsWidth is
        // actually load-bearing in the arithmetic, not merely accepted and ignored.
        val shown = BreakdownColumnPolicy.showsRequestsColumn(
            availableWidth = requiredWidth,
            modelMinWidth = modelMinWidth,
            requestsWidth = requestsWidth + 1,
            spendWidth = spendWidth,
            gap = gap
        )

        assertFalse(shown)
    }

    @Test
    @DisplayName("a wider spend column alone can flip a fitting row to not fitting")
    fun `a wider spend column alone can flip a fitting row to not fitting`() {
        // Same shape as the requests-column test above, but for spendWidth - proves the
        // never-truncating spend column's own width is still counted against the model's share,
        // not silently dropped from the sum.
        val shown = BreakdownColumnPolicy.showsRequestsColumn(
            availableWidth = requiredWidth,
            modelMinWidth = modelMinWidth,
            requestsWidth = requestsWidth,
            spendWidth = spendWidth + 1,
            gap = gap
        )

        assertFalse(shown)
    }

    @Test
    @DisplayName("raising the model floor alone can flip a fitting row to not fitting")
    fun `raising the model floor alone can flip a fitting row to not fitting`() {
        val shown = BreakdownColumnPolicy.showsRequestsColumn(
            availableWidth = requiredWidth,
            modelMinWidth = modelMinWidth + 1,
            requestsWidth = requestsWidth,
            spendWidth = spendWidth,
            gap = gap
        )

        assertFalse(shown)
    }

    @Test
    @DisplayName("a narrower gap alone can flip a not-fitting row to fitting")
    fun `a narrower gap alone can flip a not-fitting row to fitting`() {
        // requiredWidth - 1 does not fit at gap = 12 (see above); shrinking the gap by a single
        // pixel (spent twice - GAP_COUNT) frees up exactly the 2px needed, proving gap is
        // multiplied rather than added once.
        val shown = BreakdownColumnPolicy.showsRequestsColumn(
            availableWidth = requiredWidth - 1,
            modelMinWidth = modelMinWidth,
            requestsWidth = requestsWidth,
            spendWidth = spendWidth,
            gap = gap - 1
        )

        assertTrue(shown)
    }

    @Test
    @DisplayName("zero available width never shows the column")
    fun `zero available width never shows the column`() {
        val shown = BreakdownColumnPolicy.showsRequestsColumn(
            availableWidth = 0,
            modelMinWidth = modelMinWidth,
            requestsWidth = requestsWidth,
            spendWidth = spendWidth,
            gap = gap
        )

        assertFalse(shown)
    }
}
