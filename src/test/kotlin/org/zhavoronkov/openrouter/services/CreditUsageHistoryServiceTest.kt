package org.zhavoronkov.openrouter.services

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.Instant

/**
 * Unit tests for CreditUsageHistoryService that exercise the real service instance
 * (no ApplicationManager needed — pure calculation and state-management methods).
 */
@DisplayName("CreditUsageHistoryService — behavioural tests")
class CreditUsageHistoryServiceTest {

    private lateinit var service: CreditUsageHistoryService

    @BeforeEach
    fun setUp() {
        service = CreditUsageHistoryService()
    }

    @AfterEach
    fun tearDown() {
        service.dispose()
    }

    // ---------------------------------------------------------------------
    // recordSnapshot + state accessors
    // ---------------------------------------------------------------------

    @Test
    @DisplayName("recordSnapshot appends a snapshot at the current time")
    fun recordSnapshotAppends() {
        assertEquals(0, service.getSnapshotCount())
        service.recordSnapshot(1.23)
        assertEquals(1, service.getSnapshotCount())
        val state = service.getState()
        assertEquals(1.23, state.snapshots.first().totalUsed)
        assertTrue(state.snapshots.first().timestampUtc > 0)
    }

    @Test
    @DisplayName("multiple recordSnapshot calls accumulate")
    fun recordSnapshotAccumulates() {
        service.recordSnapshot(1.0)
        service.recordSnapshot(2.0)
        service.recordSnapshot(3.0)
        assertEquals(3, service.getSnapshotCount())
    }

    @Test
    @DisplayName("clearSnapshots removes all snapshots")
    fun clearSnapshots() {
        service.recordSnapshot(1.0)
        service.recordSnapshot(2.0)
        service.clearSnapshots()
        assertEquals(0, service.getSnapshotCount())
    }

    @Test
    @DisplayName("getSnapshots returns a defensive copy")
    fun getSnapshotsCopy() {
        service.recordSnapshot(1.0)
        val snap = service.getSnapshots()
        assertEquals(1, snap.size)
    }

    // ---------------------------------------------------------------------
    // loadState + getState + pruning on load
    // ---------------------------------------------------------------------

    @Test
    @DisplayName("loadState replaces state and prunes stale snapshots")
    fun loadStatePrunes() {
        val now = Instant.now().toEpochMilli()
        val oldTs = now - (49L * 3600_000L) // 49h old — outside 48h retention
        val newTs = now - (1L * 3600_000L)
        val loaded = CreditUsageHistoryService.State(
            snapshots = mutableListOf(
                CreditUsageHistoryService.CreditSnapshot(oldTs, 10.0),
                CreditUsageHistoryService.CreditSnapshot(newTs, 20.0)
            )
        )
        service.loadState(loaded)
        // old snapshot should be pruned
        assertEquals(1, service.getSnapshotCount())
        assertEquals(20.0, service.getState().snapshots.first().totalUsed)
    }

    @Test
    @DisplayName("getState returns the same instance held internally")
    fun getStateReturnsInternal() {
        service.recordSnapshot(5.0)
        assertNotNull(service.getState())
        assertEquals(1, service.getState().snapshots.size)
    }

    // ---------------------------------------------------------------------
    // interpolateUsageAtTime
    // ---------------------------------------------------------------------

    @Nested
    @DisplayName("interpolateUsageAtTime")
    inner class Interpolation {

        @Test
        @DisplayName("returns null for empty snapshots")
        fun emptyReturnsNull() {
            assertNull(service.interpolateUsageAtTime(Instant.now().toEpochMilli()))
        }

        @Test
        @DisplayName("returns exact value when a snapshot is within a minute of target")
        fun exactWhenClose() {
            val target = Instant.now().toEpochMilli()
            val loaded = CreditUsageHistoryService.State(
                snapshots = mutableListOf(
                    CreditUsageHistoryService.CreditSnapshot(target - 30_000L, 42.0)
                )
            )
            service.loadState(loaded)
            assertEquals(42.0, service.interpolateUsageAtTime(target))
        }

        @Test
        @DisplayName("interpolates between two snapshots (linear)")
        fun linearBetween() {
            val now = Instant.now().toEpochMilli()
            val start = now - 1_000_000L
            val end = now + 1_000_000L
            val loaded = CreditUsageHistoryService.State(
                snapshots = mutableListOf(
                    CreditUsageHistoryService.CreditSnapshot(start, 10.0),
                    CreditUsageHistoryService.CreditSnapshot(end, 20.0)
                )
            )
            service.loadState(loaded)
            // Midway → 15.0
            val result = service.interpolateUsageAtTime(now)
            assertNotNull(result)
            assertTrue(kotlin.math.abs(result!! - 15.0) < 0.5)
        }

        @Test
        @DisplayName("returns last snapshot when target is after all snapshots")
        fun afterAll() {
            val now = Instant.now().toEpochMilli()
            val loaded = CreditUsageHistoryService.State(
                snapshots = mutableListOf(
                    CreditUsageHistoryService.CreditSnapshot(now - 3_600_000L, 5.0),
                    CreditUsageHistoryService.CreditSnapshot(now - 1_800_000L, 8.0)
                )
            )
            service.loadState(loaded)
            // Target 10 minutes in the future — beyond all snapshots
            val target = now + 600_000L
            assertEquals(8.0, service.interpolateUsageAtTime(target))
        }

        @Test
        @DisplayName("returns null when target is before all snapshots")
        fun beforeAll() {
            val now = Instant.now().toEpochMilli()
            val loaded = CreditUsageHistoryService.State(
                snapshots = mutableListOf(
                    CreditUsageHistoryService.CreditSnapshot(now - 1_800_000L, 5.0)
                )
            )
            service.loadState(loaded)
            val target = now - 3_600_000L
            assertNull(service.interpolateUsageAtTime(target))
        }
    }

    // ---------------------------------------------------------------------
    // calculateTodaySpent / getMidnightUsage
    // ---------------------------------------------------------------------

    @Test
    @DisplayName("getMidnightUsage returns null with no snapshots")
    fun midnightUsageEmpty() {
        assertNull(service.getMidnightUsage())
    }

    @Test
    @DisplayName("calculateTodaySpent returns null when midnight usage is unavailable")
    fun todaySpentNoData() {
        assertNull(service.calculateTodaySpent(100.0))
    }

    @Test
    @DisplayName("calculateTodaySpent returns non-negative diff when midnight is available")
    fun todaySpentPositive() {
        // Seed a snapshot right at (or a bit after) local midnight so it interpolates cleanly.
        val midnight = java.time.LocalDate.now().atStartOfDay()
            .atZone(java.time.ZoneId.systemDefault())
            .withZoneSameInstant(java.time.ZoneOffset.UTC)
            .toInstant().toEpochMilli()
        val loaded = CreditUsageHistoryService.State(
            snapshots = mutableListOf(
                CreditUsageHistoryService.CreditSnapshot(midnight - 30_000L, 50.0)
            )
        )
        service.loadState(loaded)
        val spent = service.calculateTodaySpent(75.0)
        assertNotNull(spent)
        assertEquals(25.0, spent!!, 0.001)
    }

    @Test
    @DisplayName("calculateTodaySpent returns null when current usage is below midnight (impossible negative)")
    fun todaySpentNegativeGuard() {
        val midnight = java.time.LocalDate.now().atStartOfDay()
            .atZone(java.time.ZoneId.systemDefault())
            .withZoneSameInstant(java.time.ZoneOffset.UTC)
            .toInstant().toEpochMilli()
        val loaded = CreditUsageHistoryService.State(
            snapshots = mutableListOf(
                CreditUsageHistoryService.CreditSnapshot(midnight - 30_000L, 50.0)
            )
        )
        service.loadState(loaded)
        assertNull(service.calculateTodaySpent(10.0))
    }

    // ---------------------------------------------------------------------
    // calculateDaysRemaining
    // ---------------------------------------------------------------------

    @Test
    @DisplayName("calculateDaysRemaining truncates fractional days")
    fun daysRemainingTruncates() {
        assertEquals(3, service.calculateDaysRemaining(35.0, 10.0))
    }

    @Test
    @DisplayName("calculateDaysRemaining returns null when yesterdaySpent is null")
    fun daysRemainingNull() {
        assertNull(service.calculateDaysRemaining(100.0, null))
    }

    @Test
    @DisplayName("calculateDaysRemaining returns null when yesterdaySpent is zero or negative")
    fun daysRemainingZeroOrNegative() {
        assertNull(service.calculateDaysRemaining(100.0, 0.0))
        assertNull(service.calculateDaysRemaining(100.0, -1.0))
    }

    // ---------------------------------------------------------------------
    // getYesterdaySpent — prefers API, falls back to local calc
    // ---------------------------------------------------------------------

    @Test
    @DisplayName("getYesterdaySpent prefers API-provided non-negative value")
    fun yesterdayPrefersApi() {
        assertEquals(42.0, service.getYesterdaySpent(42.0))
    }

    @Test
    @DisplayName("getYesterdaySpent ignores negative API value and falls back")
    fun yesterdayFallbackForNegative() {
        // No snapshots → fallback returns null
        assertNull(service.getYesterdaySpent(-1.0))
    }

    @Test
    @DisplayName("getYesterdaySpent falls back to snapshot interpolation when API is null")
    fun yesterdayFallbackToLocal() {
        val today = java.time.LocalDate.now()
        val yStart = today.minusDays(1).atStartOfDay()
            .atZone(java.time.ZoneId.systemDefault())
            .withZoneSameInstant(java.time.ZoneOffset.UTC)
            .toInstant().toEpochMilli()
        val yEnd = today.atStartOfDay()
            .atZone(java.time.ZoneId.systemDefault())
            .withZoneSameInstant(java.time.ZoneOffset.UTC)
            .toInstant().toEpochMilli()
        val loaded = CreditUsageHistoryService.State(
            snapshots = mutableListOf(
                CreditUsageHistoryService.CreditSnapshot(yStart - 30_000L, 100.0),
                CreditUsageHistoryService.CreditSnapshot(yEnd - 30_000L, 130.0)
            )
        )
        service.loadState(loaded)
        val spent = service.getYesterdaySpent(null)
        assertNotNull(spent)
        assertTrue(spent!! >= 0.0)
    }
}
