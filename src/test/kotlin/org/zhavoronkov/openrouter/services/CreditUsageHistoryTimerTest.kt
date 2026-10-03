package org.zhavoronkov.openrouter.services

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.kotlin.whenever
import org.zhavoronkov.openrouter.models.CreditsData

/**
 * The snapshot timer, on virtual time: a snapshot at start and one every five minutes, a refresh
 * asked for when the cache has no credits yet, and nothing at all once the timer is stopped.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@DisplayName("CreditUsageHistoryService snapshot timer")
class CreditUsageHistoryTimerTest {

    private val scope = TestScope()
    private val cache: OpenRouterStatsCache = mock(OpenRouterStatsCache::class.java)

    private fun service(cache: OpenRouterStatsCache? = this.cache) =
        CreditUsageHistoryService(scope = scope, statsCache = { cache })

    @Test
    fun `a snapshot is taken at start and then every five minutes`() {
        whenever(cache.getCachedCredits()).thenReturn(CreditsData(totalCredits = 10.0, totalUsage = 2.5))
        val service = service()

        service.startSnapshotTimer()
        scope.runCurrent()
        assertEquals(1, service.getSnapshotCount())

        scope.advanceTimeBy(FIVE_MINUTES)
        scope.runCurrent()
        assertEquals(2, service.getSnapshotCount())
        assertEquals(2.5, service.getState().snapshots.last().totalUsed)
        service.stopSnapshotTimer()
    }

    @Test
    fun `starting twice runs one timer`() {
        whenever(cache.getCachedCredits()).thenReturn(CreditsData(totalCredits = 10.0, totalUsage = 1.0))
        val service = service()

        service.startSnapshotTimer()
        service.startSnapshotTimer()
        scope.runCurrent()

        assertEquals(1, service.getSnapshotCount())
        service.stopSnapshotTimer()
    }

    @Test
    fun `a stopped timer takes no more snapshots, and can be started again`() {
        whenever(cache.getCachedCredits()).thenReturn(CreditsData(totalCredits = 10.0, totalUsage = 1.0))
        val service = service()
        service.startSnapshotTimer()
        scope.runCurrent()

        service.stopSnapshotTimer()
        scope.advanceTimeBy(FIVE_MINUTES * 3)
        scope.runCurrent()
        assertEquals(1, service.getSnapshotCount())

        service.startSnapshotTimer()
        scope.runCurrent()
        assertEquals(2, service.getSnapshotCount())
        service.stopSnapshotTimer()
    }

    @Test
    fun `with no credits cached yet it asks for a refresh and records what arrives`() {
        whenever(cache.getCachedCredits())
            .thenReturn(null)
            .thenReturn(CreditsData(totalCredits = 10.0, totalUsage = 4.0))
        val service = service()

        service.startSnapshotTimer()
        scope.runCurrent()
        scope.advanceTimeBy(REFRESH_WAIT)
        scope.runCurrent()

        verify(cache).refresh()
        assertEquals(4.0, service.getState().snapshots.single().totalUsed)
        service.stopSnapshotTimer()
    }

    @Test
    fun `a refresh that brings nothing records nothing`() {
        whenever(cache.getCachedCredits()).thenReturn(null)
        val service = service()

        service.startSnapshotTimer()
        scope.advanceTimeBy(REFRESH_WAIT)
        scope.runCurrent()

        assertEquals(0, service.getSnapshotCount())
        service.stopSnapshotTimer()
    }

    @Test
    fun `without the stats cache nothing is recorded`() {
        val service = service(cache = null)

        service.startSnapshotTimer()
        scope.runCurrent()

        assertEquals(0, service.getSnapshotCount())
        service.stopSnapshotTimer()
    }

    @Test
    fun `a cache that is not available yet is reported, not thrown`() {
        whenever(cache.getCachedCredits()).thenThrow(IllegalStateException("service not ready"))
        val service = service()

        service.startSnapshotTimer()
        scope.runCurrent()

        assertEquals(0, service.getSnapshotCount())
        service.stopSnapshotTimer()
    }

    @Test
    fun `a timer started after the service was disposed takes no snapshot`() {
        whenever(cache.getCachedCredits()).thenReturn(CreditsData(totalCredits = 10.0, totalUsage = 1.0))
        val service = service()
        service.startSnapshotTimer()
        scope.runCurrent()

        service.dispose()
        service.startSnapshotTimer()
        scope.advanceTimeBy(FIVE_MINUTES)
        scope.runCurrent()

        assertEquals(1, service.getSnapshotCount())
    }

    private companion object {
        const val FIVE_MINUTES = 5 * 60_000L
        const val REFRESH_WAIT = 2_001L
    }
}
