package org.zhavoronkov.openrouter.toolwindow.status

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.zhavoronkov.openrouter.models.ActivityData
import java.time.LocalDate

@DisplayName("ActivityAggregator")
class ActivityAggregatorTest {

    private val today = LocalDate.of(2026, 9, 19)

    private fun row(date: String?, model: String, usage: Double, requests: Int) = ActivityData(
        date = date,
        model = model,
        modelPermaslug = null,
        endpointId = null,
        providerName = null,
        usage = usage,
        byokUsageInference = null,
        requests = requests,
        promptTokens = null,
        completionTokens = null,
        reasoningTokens = null
    )

    @Test
    @DisplayName("rows are totalled per model")
    fun `rows are totalled per model`() {
        val result = ActivityAggregator.byModel(
            listOf(
                row("2026-09-19", "anthropic/claude-sonnet-4.5", 0.30, 3),
                row("2026-09-19", "anthropic/claude-sonnet-4.5", 0.21, 2),
                row("2026-09-19", "openai/gpt-4o-mini", 0.05, 9)
            ),
            ActivityAggregator.Period.DAY,
            today
        )

        assertEquals(2, result.size)
        assertEquals("anthropic/claude-sonnet-4.5", result[0].model)
        assertEquals(0.51, result[0].usage, 1e-9)
        assertEquals(5L, result[0].requests)
    }

    @Test
    @DisplayName("results are ordered by spend, descending")
    fun `results are ordered by spend descending`() {
        val result = ActivityAggregator.byModel(
            listOf(
                row("2026-09-19", "cheap", 0.01, 1),
                row("2026-09-19", "expensive", 5.00, 1),
                row("2026-09-19", "middling", 0.50, 1)
            ),
            ActivityAggregator.Period.DAY,
            today
        )

        assertEquals(listOf("expensive", "middling", "cheap"), result.map { it.model })
    }

    @Test
    @DisplayName("a row outside the period is excluded")
    fun `a row outside the period is excluded`() {
        val result = ActivityAggregator.byModel(
            listOf(
                row("2026-09-19", "inside", 1.0, 1),
                row("2026-09-01", "outside", 9.0, 1)
            ),
            ActivityAggregator.Period.DAY,
            today
        )

        assertEquals(listOf("inside"), result.map { it.model })
    }

    @Test
    @DisplayName("the period boundary is inclusive at both ends")
    fun `the period boundary is inclusive`() {
        val rows = listOf(
            row("2026-09-19", "today", 1.0, 1),
            row("2026-09-13", "sevenDaysAgo", 1.0, 1),
            row("2026-09-12", "eightDaysAgo", 1.0, 1)
        )

        val result = ActivityAggregator.byModel(rows, ActivityAggregator.Period.WEEK, today)

        assertEquals(setOf("today", "sevenDaysAgo"), result.map { it.model }.toSet())
    }

    @Test
    @DisplayName("a row with no model is dropped rather than grouped under an empty name")
    fun `a row with no model is dropped`() {
        val result = ActivityAggregator.byModel(
            listOf(row("2026-09-19", "real", 1.0, 1).copy(model = null)),
            ActivityAggregator.Period.DAY,
            today
        )

        assertTrue(result.isEmpty())
    }

    @Test
    @DisplayName("an unparseable date is dropped rather than throwing")
    fun `an unparseable date is dropped`() {
        val result = ActivityAggregator.byModel(
            listOf(
                row("not-a-date", "bad", 1.0, 1),
                row("2026-09-19", "good", 1.0, 1)
            ),
            ActivityAggregator.Period.DAY,
            today
        )

        assertEquals(listOf("good"), result.map { it.model })
    }

    @Test
    @DisplayName("null usage and null requests count as zero")
    fun `null usage and requests count as zero`() {
        val result = ActivityAggregator.byModel(
            listOf(row("2026-09-19", "m", 1.0, 1).copy(usage = null, requests = null)),
            ActivityAggregator.Period.DAY,
            today
        )

        assertEquals(0.0, result[0].usage, 1e-9)
        assertEquals(0L, result[0].requests)
    }

    @Test
    @DisplayName("an empty input yields an empty result")
    fun `an empty input yields an empty result`() {
        assertTrue(
            ActivityAggregator.byModel(emptyList(), ActivityAggregator.Period.MONTH, today).isEmpty()
        )
    }

    @Test
    @DisplayName("a row with a blank or absent date is dropped rather than thrown on")
    fun `a row without a usable date is dropped`() {
        val result = ActivityAggregator.byModel(
            listOf(
                row("2026-09-19", "openai/gpt-4o-mini", 0.10, 1),
                row(null, "openai/gpt-4o-mini", 99.0, 99),
                row("", "openai/gpt-4o-mini", 99.0, 99),
                row("   ", "openai/gpt-4o-mini", 99.0, 99)
            ),
            ActivityAggregator.Period.DAY,
            today
        )

        assertEquals(1, result.size)
        assertEquals(0.10, result[0].usage)
        assertEquals(1L, result[0].requests)
    }
}
