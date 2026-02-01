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
