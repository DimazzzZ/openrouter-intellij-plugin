package org.zhavoronkov.openrouter.ui

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.zhavoronkov.openrouter.models.ActivityData

@DisplayName("StatsFormatter Tests")
class StatsFormatterTest {

    @Test
    fun `formatCurrency should format with decimals`() {
        val value = StatsFormatter.formatCurrency(1.23456)
        assertEquals("1.235", value)
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
    fun `buildModelsHtmlList should show empty message`() {
        val html = StatsFormatter.buildModelsHtmlList(emptyList())

        assertTrue(html.contains("No models used"))
    }
}
