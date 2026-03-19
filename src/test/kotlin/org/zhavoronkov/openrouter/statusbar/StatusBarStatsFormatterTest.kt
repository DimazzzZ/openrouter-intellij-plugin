package org.zhavoronkov.openrouter.statusbar

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.zhavoronkov.openrouter.models.ActivityData
import org.zhavoronkov.openrouter.services.OpenRouterGenerationTrackingService
import java.time.LocalDate

@DisplayName("StatusBarStatsFormatter Tests")
class StatusBarStatsFormatterTest {

    @Test
    fun `formatStatusTextFromCredits should show percentage when hidden costs`() {
        val text = StatusBarStatsFormatter.formatStatusTextFromCredits(5.0, 10.0, showCosts = false)

        assertTrue(text.contains("50.0%"))
    }

    @Test
    fun `formatStatusTextFromCredits should show costs when enabled`() {
        val text = StatusBarStatsFormatter.formatStatusTextFromCredits(5.0, 10.0, showCosts = true)

        assertTrue(text.contains("$5.000/$10.00"))
    }

    @Test
    fun `calculateActivityRows should include today usage`() {
        val today = LocalDate.now().toString()
        val activities = listOf(
            ActivityData(
                date = today,
                model = "model",
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

        val rows = StatusBarStatsFormatter.calculateActivityRows(activities)

        assertTrue(rows.contains("Today:"))
    }

    @Test
    fun `formatStatusTooltipFromCredits should include activity rows`() {
        val today = LocalDate.now().toString()
        val activities = listOf(
            ActivityData(
                date = today,
                model = "model",
                modelPermaslug = null,
                endpointId = null,
                providerName = null,
                usage = 2.0,
                byokUsageInference = null,
                requests = 1,
                promptTokens = null,
                completionTokens = null,
                reasoningTokens = null
            )
        )

        val tooltip = StatusBarStatsFormatter.formatStatusTooltipFromCredits(
            "Ready",
            2.0,
            10.0,
            activities
        )

        assertTrue(tooltip.contains("Activity"))
        assertTrue(tooltip.contains("Ready"))
    }

    @Nested
    @DisplayName("Local Tracking Service Integration")
    inner class LocalTrackingServiceIntegration {

        @Test
        fun `calculateActivityRows should use local tracking data for Today when service is provided`() {
            val mockTrackingService = mock(OpenRouterGenerationTrackingService::class.java)
            `when`(mockTrackingService.getTodayCost()).thenReturn(1.234)

            // API returns 0 for today (simulating delayed data)
            val activities = emptyList<ActivityData>()

            val rows = StatusBarStatsFormatter.calculateActivityRows(activities, mockTrackingService)

            // Should show the local tracking value, not the API value
            assertTrue(rows.contains("\$1.234"))
        }

        @Test
        fun `calculateActivityRows should fall back to API data when tracking service is null`() {
            val today = LocalDate.now().toString()
            val activities = listOf(
                ActivityData(
                    date = today,
                    model = "model",
                    modelPermaslug = null,
                    endpointId = null,
                    providerName = null,
                    usage = 0.567,
                    byokUsageInference = null,
                    requests = 1,
                    promptTokens = null,
                    completionTokens = null,
                    reasoningTokens = null
                )
            )

            val rows = StatusBarStatsFormatter.calculateActivityRows(activities, null)

            // Should show the API value
            assertTrue(rows.contains("\$0.567"))
        }

        @Test
        fun `calculateActivityRows should use API data for Yesterday even when tracking service is provided`() {
            val mockTrackingService = mock(OpenRouterGenerationTrackingService::class.java)
            `when`(mockTrackingService.getTodayCost()).thenReturn(0.0)

            val yesterday = LocalDate.now().minusDays(1).toString()
            val activities = listOf(
                ActivityData(
                    date = yesterday,
                    model = "model",
                    modelPermaslug = null,
                    endpointId = null,
                    providerName = null,
                    usage = 0.789,
                    byokUsageInference = null,
                    requests = 5,
                    promptTokens = null,
                    completionTokens = null,
                    reasoningTokens = null
                )
            )

            val rows = StatusBarStatsFormatter.calculateActivityRows(activities, mockTrackingService)

            // Yesterday should still use API data
            assertTrue(rows.contains("\$0.789"))
        }

        @Test
        fun `formatStatusTooltipFromCredits should pass tracking service to activity rows`() {
            val mockTrackingService = mock(OpenRouterGenerationTrackingService::class.java)
            `when`(mockTrackingService.getTodayCost()).thenReturn(2.500)

            val tooltip = StatusBarStatsFormatter.formatStatusTooltipFromCredits(
                "Ready",
                5.0,
                10.0,
                emptyList(),
                mockTrackingService
            )

            // Should show the local tracking value for Today
            assertTrue(tooltip.contains("\$2.500"))
        }

        @Test
        fun `calculateActivityRows should still show 7 Days from API data`() {
            val mockTrackingService = mock(OpenRouterGenerationTrackingService::class.java)
            `when`(mockTrackingService.getTodayCost()).thenReturn(0.100)

            // Create activities for the past week
            val activities = (0..6).map { daysAgo ->
                val date = LocalDate.now().minusDays(daysAgo.toLong()).toString()
                ActivityData(
                    date = date,
                    model = "model",
                    modelPermaslug = null,
                    endpointId = null,
                    providerName = null,
                    usage = 1.0, // $1 per day = $7 total
                    byokUsageInference = null,
                    requests = 1,
                    promptTokens = null,
                    completionTokens = null,
                    reasoningTokens = null
                )
            }

            val rows = StatusBarStatsFormatter.calculateActivityRows(activities, mockTrackingService)

            // 7 Days should show sum from API (7 days * $1 = $7)
            assertTrue(rows.contains("\$7.000"))
        }
    }
}
