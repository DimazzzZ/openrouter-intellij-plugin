package org.zhavoronkov.openrouter.services

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.zhavoronkov.openrouter.models.GenerationTrackingInfo
import org.zhavoronkov.openrouter.models.OpenRouterSettings
import java.time.LocalDate
import java.time.ZoneId

@DisplayName("OpenRouter Generation Tracking Service Tests")
class OpenRouterGenerationTrackingServiceTest {

    private lateinit var service: OpenRouterGenerationTrackingService
    private lateinit var mockSettingsService: OpenRouterSettingsService

    @BeforeEach
    fun setUp() {
        service = OpenRouterGenerationTrackingService()
        mockSettingsService = mock(OpenRouterSettingsService::class.java)

        service.setSettingsServiceForTests(mockSettingsService)

        // Ensure default state doesn't rely on platform persistence
        service.loadState(OpenRouterGenerationTrackingService.State())
    }

    @Nested
    @DisplayName("Tracking Behavior")
    inner class TrackingBehavior {

        @Test
        fun `should not track generations when disabled`() {
            val state = OpenRouterSettings(trackGenerations = false)
            `when`(mockSettingsService.getState()).thenReturn(state)

            service.trackGeneration(createGeneration("gen-1"))

            assertEquals(0, service.getGenerationCount())
        }

        @Test
        fun `should track generations when enabled`() {
            val state = OpenRouterSettings(trackGenerations = true)
            `when`(mockSettingsService.getState()).thenReturn(state)

            service.trackGeneration(createGeneration("gen-1"))

            assertEquals(1, service.getGenerationCount())
            assertEquals("gen-1", service.getRecentGenerations(1).first().generationId)
        }

        @Test
        fun `should enforce max tracked generations`() {
            val state = OpenRouterSettings(trackGenerations = true, maxTrackedGenerations = 2)
            `when`(mockSettingsService.getState()).thenReturn(state)

            service.trackGeneration(createGeneration("gen-1"))
            service.trackGeneration(createGeneration("gen-2"))
            service.trackGeneration(createGeneration("gen-3"))

            val generations = service.getRecentGenerations(10)
            assertEquals(2, generations.size)
            assertEquals("gen-3", generations[0].generationId)
            assertEquals("gen-2", generations[1].generationId)
        }
    }

    @Nested
    @DisplayName("Aggregation Helpers")
    inner class AggregationHelpers {

        @Test
        fun `should sum total tokens and cost`() {
            val state = OpenRouterSettings(trackGenerations = true)
            `when`(mockSettingsService.getState()).thenReturn(state)

            service.trackGeneration(createGeneration("gen-1", totalTokens = 100, totalCost = 0.25))
            service.trackGeneration(createGeneration("gen-2", totalTokens = 200, totalCost = 0.50))

            assertEquals(300, service.getTotalRecentTokens())
            assertEquals(0.75, service.getTotalRecentCost(), 0.0001)
        }

        @Test
        fun `should ignore null token and cost values`() {
            val state = OpenRouterSettings(trackGenerations = true)
            `when`(mockSettingsService.getState()).thenReturn(state)

            service.trackGeneration(createGeneration("gen-1", totalTokens = null, totalCost = null))
            service.trackGeneration(createGeneration("gen-2", totalTokens = 50, totalCost = 0.10))

            assertEquals(50, service.getTotalRecentTokens())
            assertEquals(0.10, service.getTotalRecentCost(), 0.0001)
        }
    }

    @Nested
    @DisplayName("State Updates")
    inner class StateUpdates {

        @Test
        fun `should update generation stats in place`() {
            val state = OpenRouterSettings(trackGenerations = true)
            `when`(mockSettingsService.getState()).thenReturn(state)

            service.trackGeneration(createGeneration("gen-1", totalTokens = 10, totalCost = 0.01))

            service.updateGenerationStats(
                generationId = "gen-1",
                promptTokens = 5,
                completionTokens = 6,
                totalTokens = 11,
                totalCost = 0.02
            )

            val updated = service.getRecentGenerations(1).first()
            assertEquals(5, updated.promptTokens)
            assertEquals(6, updated.completionTokens)
            assertEquals(11, updated.totalTokens)
            assertEquals(0.02, updated.totalCost)
        }

        @Test
        fun `should not update when generation id is missing`() {
            val state = OpenRouterSettings(trackGenerations = true)
            `when`(mockSettingsService.getState()).thenReturn(state)

            service.trackGeneration(createGeneration("gen-1", totalTokens = 10, totalCost = 0.01))

            service.updateGenerationStats(
                generationId = "missing",
                promptTokens = 5,
                completionTokens = 6,
                totalTokens = 11,
                totalCost = 0.02
            )

            val unchanged = service.getRecentGenerations(1).first()
            assertEquals(10, unchanged.totalTokens)
            assertEquals(0.01, unchanged.totalCost)
        }

        @Test
        fun `clearGenerations should remove all records`() {
            val state = OpenRouterSettings(trackGenerations = true)
            `when`(mockSettingsService.getState()).thenReturn(state)

            service.trackGeneration(createGeneration("gen-1"))
            service.trackGeneration(createGeneration("gen-2"))

            service.clearGenerations()

            assertTrue(service.getRecentGenerations(10).isEmpty())
        }
    }

    @Nested
    @DisplayName("Daily Cost Calculations")
    inner class DailyCostCalculations {

        private fun getStartOfDayMillis(daysAgo: Int): Long {
            val zone = ZoneId.systemDefault()
            val date = LocalDate.now(zone).minusDays(daysAgo.toLong())
            return date.atStartOfDay(zone).toInstant().toEpochMilli()
        }

        private fun createGenerationWithTimestamp(
            id: String,
            timestamp: Long,
            totalCost: Double? = 0.5
        ): GenerationTrackingInfo {
            return GenerationTrackingInfo(
                generationId = id,
                model = "openai/gpt-4",
                timestamp = timestamp,
                promptTokens = null,
                completionTokens = null,
                totalTokens = 100,
                totalCost = totalCost
            )
        }

        @Test
        fun `getTodayCost should return sum of today's generations`() {
            val state = OpenRouterSettings(trackGenerations = true)
            `when`(mockSettingsService.getState()).thenReturn(state)

            // Create generations for today
            val todayMidMorning = getStartOfDayMillis(0) + 10 * 60 * 60 * 1000 // 10 AM today
            val todayAfternoon = getStartOfDayMillis(0) + 15 * 60 * 60 * 1000 // 3 PM today

            service.trackGeneration(createGenerationWithTimestamp("today-1", todayMidMorning, 0.25))
            service.trackGeneration(createGenerationWithTimestamp("today-2", todayAfternoon, 0.35))

            assertEquals(0.60, service.getTodayCost(), 0.0001)
        }

        @Test
        fun `getYesterdayCost should return sum of yesterday's generations`() {
            val state = OpenRouterSettings(trackGenerations = true)
            `when`(mockSettingsService.getState()).thenReturn(state)

            // Create generations for yesterday
            val yesterdayMorning = getStartOfDayMillis(1) + 9 * 60 * 60 * 1000 // 9 AM yesterday
            val yesterdayEvening = getStartOfDayMillis(1) + 20 * 60 * 60 * 1000 // 8 PM yesterday

            service.trackGeneration(createGenerationWithTimestamp("yesterday-1", yesterdayMorning, 0.15))
            service.trackGeneration(createGenerationWithTimestamp("yesterday-2", yesterdayEvening, 0.20))

            assertEquals(0.35, service.getYesterdayCost(), 0.0001)
        }

        @Test
        fun `getTodayCost should exclude yesterday's generations`() {
            val state = OpenRouterSettings(trackGenerations = true)
            `when`(mockSettingsService.getState()).thenReturn(state)

            // Create generations for today and yesterday
            val todayTimestamp = getStartOfDayMillis(0) + 12 * 60 * 60 * 1000 // noon today
            val yesterdayTimestamp = getStartOfDayMillis(1) + 12 * 60 * 60 * 1000 // noon yesterday

            service.trackGeneration(createGenerationWithTimestamp("today", todayTimestamp, 0.50))
            service.trackGeneration(createGenerationWithTimestamp("yesterday", yesterdayTimestamp, 1.00))

            assertEquals(0.50, service.getTodayCost(), 0.0001)
        }

        @Test
        fun `getYesterdayCost should exclude today's and older generations`() {
            val state = OpenRouterSettings(trackGenerations = true)
            `when`(mockSettingsService.getState()).thenReturn(state)

            // Create generations for today, yesterday, and 2 days ago
            val todayTimestamp = getStartOfDayMillis(0) + 12 * 60 * 60 * 1000
            val yesterdayTimestamp = getStartOfDayMillis(1) + 12 * 60 * 60 * 1000
            val twoDaysAgoTimestamp = getStartOfDayMillis(2) + 12 * 60 * 60 * 1000

            service.trackGeneration(createGenerationWithTimestamp("today", todayTimestamp, 0.50))
            service.trackGeneration(createGenerationWithTimestamp("yesterday", yesterdayTimestamp, 0.30))
            service.trackGeneration(createGenerationWithTimestamp("two-days-ago", twoDaysAgoTimestamp, 0.80))

            assertEquals(0.30, service.getYesterdayCost(), 0.0001)
        }

        @Test
        fun `getCostForDay should work for arbitrary days ago`() {
            val state = OpenRouterSettings(trackGenerations = true)
            `when`(mockSettingsService.getState()).thenReturn(state)

            // Create generations for 3 days ago
            val threeDaysAgo = getStartOfDayMillis(3) + 12 * 60 * 60 * 1000

            service.trackGeneration(createGenerationWithTimestamp("three-days", threeDaysAgo, 0.75))

            assertEquals(0.75, service.getCostForDay(3), 0.0001)
        }

        @Test
        fun `getTodayCost should return zero when no generations today`() {
            val state = OpenRouterSettings(trackGenerations = true)
            `when`(mockSettingsService.getState()).thenReturn(state)

            // Only add yesterday's generation
            val yesterdayTimestamp = getStartOfDayMillis(1) + 12 * 60 * 60 * 1000
            service.trackGeneration(createGenerationWithTimestamp("yesterday", yesterdayTimestamp, 0.50))

            assertEquals(0.0, service.getTodayCost(), 0.0001)
        }

        @Test
        fun `getTodayCost should ignore null cost values`() {
            val state = OpenRouterSettings(trackGenerations = true)
            `when`(mockSettingsService.getState()).thenReturn(state)

            val todayTimestamp = getStartOfDayMillis(0) + 12 * 60 * 60 * 1000

            service.trackGeneration(createGenerationWithTimestamp("today-1", todayTimestamp, 0.25))
            service.trackGeneration(createGenerationWithTimestamp("today-2", todayTimestamp, null))
            service.trackGeneration(createGenerationWithTimestamp("today-3", todayTimestamp, 0.15))

            assertEquals(0.40, service.getTodayCost(), 0.0001)
        }

        @Test
        fun `getTodayCost should return zero when generations list is empty`() {
            val state = OpenRouterSettings(trackGenerations = true)
            `when`(mockSettingsService.getState()).thenReturn(state)

            assertEquals(0.0, service.getTodayCost(), 0.0001)
        }
    }

    private fun createGeneration(
        id: String,
        totalTokens: Int? = 100,
        totalCost: Double? = 0.5
    ): GenerationTrackingInfo {
        return GenerationTrackingInfo(
            generationId = id,
            model = "openai/gpt-4",
            timestamp = System.currentTimeMillis(),
            promptTokens = null,
            completionTokens = null,
            totalTokens = totalTokens,
            totalCost = totalCost
        )
    }
}
