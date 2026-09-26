package org.zhavoronkov.openrouter.toolwindow.status

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("BreakdownRowTooltip")
class BreakdownRowTooltipTest {

    @Test
    @DisplayName("a single request is singular, not '1 requests'")
    fun `singular wording for exactly one request`() {
        val tooltip = BreakdownRowTooltip.forRow("anthropic/claude-sonnet-4.5", 1L)

        assertEquals(
            "anthropic/claude-sonnet-4.5 — 1 request",
            tooltip,
            "wrong wording would render the plural noun for a count of exactly one"
        )
    }

    @Test
    @DisplayName("more than one request is plural")
    fun `plural wording for more than one request`() {
        val tooltip = BreakdownRowTooltip.forRow("anthropic/claude-sonnet-4.5", 31L)

        assertEquals(
            "anthropic/claude-sonnet-4.5 — 31 requests",
            tooltip,
            "wrong wording would render '31 requests' as '31 request' (missing the 's')"
        )
    }

    @Test
    @DisplayName("zero requests is plural, not treated the same as one")
    fun `plural wording for zero requests`() {
        val tooltip = BreakdownRowTooltip.forRow("openai/gpt-4o-mini", 0L)

        assertEquals(
            "openai/gpt-4o-mini — 0 requests",
            tooltip,
            "a naive '== 1L' check inverted, or a '<= 1' check, would wrongly singularise zero"
        )
    }

    @Test
    @DisplayName("the full model id is recoverable verbatim from the front of the tooltip")
    fun `full model id is recoverable`() {
        val longId = "anthropic/claude-3.7-sonnet-20250219-extended-thinking"

        val tooltip = BreakdownRowTooltip.forRow(longId, 500L)

        assertTrue(
            tooltip.startsWith(longId),
            "the middle-ellipsised label's own tooltip promise (the full id, verbatim) must " +
                "survive being extended with the request count: got '$tooltip'"
        )
    }

    @Test
    @DisplayName("the count is never rendered as a bare number beside the model id")
    fun `count is never a bare number`() {
        val tooltip = BreakdownRowTooltip.forRow("m", 31L)

        assertTrue(
            tooltip.contains("31 requests"),
            "a bare '31' with no unit word is ambiguous next to a model id: got '$tooltip'"
        )
    }
}
