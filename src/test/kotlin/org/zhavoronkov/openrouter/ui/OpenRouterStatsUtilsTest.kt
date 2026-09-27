package org.zhavoronkov.openrouter.ui

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.zhavoronkov.openrouter.models.ActivityData
import java.time.LocalDate

@DisplayName("OpenRouterStatsUtils Tests")
class OpenRouterStatsUtilsTest {

    @Test
    fun `formatLargeNumber should add commas`() {
        val formatted = OpenRouterStatsUtils.formatLargeNumber(1234567)
        assertEquals("1,234,567", formatted)
    }

    @Test
    fun `buildModelsHtmlList should show more count`() {
        val models = listOf("a", "b", "c", "d", "e", "f")
        val html = OpenRouterStatsUtils.buildModelsHtmlList(models)
        assertTrue(html.contains("+1 more"))
    }

    @Test
    fun `filterActivitiesByTime should include today`() {
        val today = LocalDate.now()
        val activities = listOf(
            ActivityData(
                date = today.toString(),
                model = "a",
                modelPermaslug = null,
                endpointId = null,
                providerName = null,
                usage = 1.0,
                byokUsageInference = null,
                requests = 1,
                promptTokens = null,
                completionTokens = null,
                reasoningTokens = null
            )
        )

        val result = OpenRouterStatsUtils.filterActivitiesByTime(
            activities,
            today,
            today.minusDays(1),
            today.minusDays(7),
            isLast24h = true
        )

        assertEquals(1, result.size)
    }

    @Test
    fun `parseActivityDate should parse valid date`() {
        val date = OpenRouterStatsUtils.parseActivityDate("2025-01-01 00:00:00")
        assertNotNull(date)
        assertEquals(LocalDate.of(2025, 1, 1), date)
    }
}

/**
 * The absent-and-malformed side of [OpenRouterStatsUtils]: every field of an activity row is
 * nullable because the endpoint is outside our control, and a row it cannot read must be dropped
 * or counted as zero rather than emptying the whole view.
 */
@DisplayName("OpenRouterStatsUtils Absent-Data Tests")
class OpenRouterStatsUtilsAbsentDataTest {

    private fun row(date: String?, model: String? = "openai/gpt-4o-mini", usage: Double? = 1.0, requests: Int? = 1) =
        ActivityData(
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

    private val today: LocalDate = LocalDate.now()

    @Test
    @DisplayName("absent counts and amounts read as zero rather than dropping the row")
    fun absentNumbersCountAsZero() {
        val (requests, usage) = OpenRouterStatsUtils.calculateActivityStats(
            listOf(
                row(today.toString(), requests = null, usage = null),
                row(today.toString(), requests = 2, usage = 0.5)
            )
        )

        assertEquals(2L, requests)
        assertEquals(0.5, usage, 1e-9)
    }

    @Test
    @DisplayName("a row with no date is dropped from the last-24h view")
    fun rowWithoutDateIsDroppedFromLast24h() {
        val kept = OpenRouterStatsUtils.filterActivitiesByTime(
            listOf(row(today.toString()), row(null)),
            today,
            today.minusDays(1),
            today.minusDays(7),
            isLast24h = true
        )

        assertEquals(1, kept.size)
    }

    @Test
    @DisplayName("a row whose date will not parse is dropped from the last-24h view")
    fun rowWithUnparseableDateIsDroppedFromLast24h() {
        val kept = OpenRouterStatsUtils.filterActivitiesByTime(
            listOf(row(today.toString()), row("not-a-date")),
            today,
            today.minusDays(1),
            today.minusDays(7),
            isLast24h = true
        )

        assertEquals(1, kept.size)
    }

    @Test
    @DisplayName("a row with no date is dropped from the week view")
    fun rowWithoutDateIsDroppedFromWeek() {
        val kept = OpenRouterStatsUtils.filterActivitiesByTime(
            listOf(row(today.toString()), row(null)),
            today,
            today.minusDays(1),
            today.minusDays(7),
            isLast24h = false
        )

        assertEquals(1, kept.size)
    }

    @Test
    @DisplayName("a row whose date will not parse is dropped from the week view")
    fun rowWithUnparseableDateIsDroppedFromWeek() {
        val kept = OpenRouterStatsUtils.filterActivitiesByTime(
            listOf(row(today.toString()), row("garbage")),
            today,
            today.minusDays(1),
            today.minusDays(7),
            isLast24h = false
        )

        assertEquals(1, kept.size)
    }

    @Test
    @DisplayName("recent model names skip rows missing a model or a date")
    fun recentModelNamesSkipIncompleteRows() {
        val names = OpenRouterStatsUtils.extractRecentModelNames(
            listOf(
                row(today.toString(), model = "openai/gpt-4o-mini"),
                row(today.minusDays(2).toString(), model = null),
                row(null, model = "anthropic/claude-sonnet-4.5")
            )
        )

        assertEquals(listOf("openai/gpt-4o-mini"), names)
    }

    @Test
    @DisplayName("an unparseable date reads as absent rather than throwing")
    fun parseActivityDateRejectsGarbage() {
        assertNull(OpenRouterStatsUtils.parseActivityDate("not-a-date"))
        assertNotNull(OpenRouterStatsUtils.parseActivityDate(today.toString()))
        assertNotNull(OpenRouterStatsUtils.parseActivityDate(today.toString() + "T10:00:00Z"))
    }
}
