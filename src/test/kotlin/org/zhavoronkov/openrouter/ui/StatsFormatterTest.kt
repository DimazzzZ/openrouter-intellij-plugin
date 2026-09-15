package org.zhavoronkov.openrouter.ui

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertIterableEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.zhavoronkov.openrouter.models.ActivityData
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@DisplayName("StatsFormatter Tests")
class StatsFormatterTest {

    private fun activity(
        date: String? = "2025-01-01",
        model: String? = "openai/gpt-4",
        usage: Double? = 0.0,
        requests: Int? = 0
    ) = ActivityData(
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
    fun `formatCurrency should format with decimals`() {
        val value = StatsFormatter.formatCurrency(1.23456)
        assertEquals("1.235", value)
    }

    @Test
    fun `formatCurrency should honor an explicit decimals argument`() {
        assertEquals("1.2346", StatsFormatter.formatCurrency(1.23456, decimals = 4))
        assertEquals("2", StatsFormatter.formatCurrency(1.5, decimals = 0))
    }

    @Test
    fun `formatActivityText should combine requests and usage`() {
        // usage rendered with 4 decimals via ACTIVITY_DECIMALS default
        assertEquals("12 requests, $0.5000", StatsFormatter.formatActivityText(12, 0.5))
    }

    @Test
    fun `calculateActivityStats should sum values`() {
        val activities = listOf(
            ActivityData(
                date = "2025-01-01",
                model = "a",
                modelPermaslug = null,
                endpointId = null,
                providerName = null,
                usage = 1.0,
                byokUsageInference = null,
                requests = 2,
                promptTokens = null,
                completionTokens = null,
                reasoningTokens = null
            ),
            ActivityData(
                date = "2025-01-02",
                model = "b",
                modelPermaslug = null,
                endpointId = null,
                providerName = null,
                usage = 2.5,
                byokUsageInference = null,
                requests = 3,
                promptTokens = null,
                completionTokens = null,
                reasoningTokens = null
            )
        )

        val (requests, usage) = StatsFormatter.calculateActivityStats(activities)

        assertEquals(5, requests)
        assertEquals(3.5, usage)
    }

    @Test
    fun `calculateActivityStats should treat null requests and usage as zero`() {
        val (requests, usage) = StatsFormatter.calculateActivityStats(
            listOf(activity(usage = null, requests = null), activity(usage = 4.0, requests = 6))
        )
        assertEquals(6L, requests)
        assertEquals(4.0, usage)
    }

    @Test
    fun `calculateActivityStats should return zeros for an empty list`() {
        val (requests, usage) = StatsFormatter.calculateActivityStats(emptyList())
        assertEquals(0L, requests)
        assertEquals(0.0, usage)
    }

    @Test
    fun `buildModelsHtmlList should show empty message`() {
        val html = StatsFormatter.buildModelsHtmlList(emptyList())

        assertTrue(html.contains("No models used"))
    }

    @Test
    fun `buildModelsHtmlList should render one li per model`() {
        val html = StatsFormatter.buildModelsHtmlList(listOf("a/b", "c/d"))
        assertTrue(html.startsWith("<html><ul"))
        assertTrue(html.contains("<li style='margin: 2px 0;'>a/b</li>"))
        assertTrue(html.contains("<li style='margin: 2px 0;'>c/d</li>"))
        assertTrue(html.endsWith("</ul></html>"))
    }

    @Nested
    @DisplayName("filterActivitiesByTime")
    inner class FilterActivitiesByTime {

        @Test
        fun `keeps activities on or after the cutoff date`() {
            val today = LocalDate.now().format(DateTimeFormatter.ISO_DATE)
            val recent = activity(date = today)
            val old = activity(date = "2000-01-01")

            val kept = StatsFormatter.filterActivitiesByTime(listOf(recent, old), hoursAgo = 24)

            assertEquals(listOf(recent), kept)
        }

        @Test
        fun `drops activities with a null date`() {
            val kept = StatsFormatter.filterActivitiesByTime(listOf(activity(date = null)), hoursAgo = 24)
            assertTrue(kept.isEmpty())
        }

        @Test
        fun `drops activities whose date fails to parse`() {
            val kept = StatsFormatter.filterActivitiesByTime(listOf(activity(date = "not-a-date")), hoursAgo = 24)
            assertTrue(kept.isEmpty())
        }

        @Test
        fun `parses timestamps with a time component`() {
            val today = LocalDate.now().format(DateTimeFormatter.ISO_DATE)
            val withTime = activity(date = "$today 13:45:00")

            val kept = StatsFormatter.filterActivitiesByTime(listOf(withTime), hoursAgo = 24)

            assertEquals(listOf(withTime), kept)
        }

        @Test
        fun `excludes activities before the cutoff`() {
            val fiveDaysAgo = LocalDate.now().minusDays(5).format(DateTimeFormatter.ISO_DATE)
            val kept = StatsFormatter.filterActivitiesByTime(listOf(activity(date = fiveDaysAgo)), hoursAgo = 24)
            assertFalse(kept.any { it.date == fiveDaysAgo })
        }
    }

    @Nested
    @DisplayName("extractRecentModelNames")
    inner class ExtractRecentModelNames {

        @Test
        fun `returns models ordered by most recent activity date`() {
            val activities = listOf(
                activity(model = "old/model", date = "2025-01-01"),
                activity(model = "new/model", date = "2025-06-01"),
                activity(model = "old/model", date = "2025-02-01")
            )

            val names = StatsFormatter.extractRecentModelNames(activities)

            assertIterableEquals(listOf("new/model", "old/model"), names)
        }

        @Test
        fun `skips entries missing a model or date`() {
            val activities = listOf(
                activity(model = null, date = "2025-06-01"),
                activity(model = "kept/model", date = null),
                activity(model = "kept/model", date = "2025-03-01")
            )

            val names = StatsFormatter.extractRecentModelNames(activities)

            assertIterableEquals(listOf("kept/model"), names)
        }

        @Test
        fun `returns an empty list when there are no usable activities`() {
            assertTrue(StatsFormatter.extractRecentModelNames(emptyList()).isEmpty())
        }
    }
}
