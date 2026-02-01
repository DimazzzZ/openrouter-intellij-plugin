package org.zhavoronkov.openrouter.ui

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
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
