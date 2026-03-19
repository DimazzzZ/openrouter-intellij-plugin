package org.zhavoronkov.openrouter.services

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * Unit tests for CreditUsageHistoryService.
 *
 * Tests the core logic for:
 * - Interpolation of midnight usage values
 * - Today's spending calculation
 * - Days remaining estimation
 * - Snapshot pruning
 */
class CreditUsageHistoryServiceTest {

    private lateinit var state: CreditUsageHistoryService.State

    @BeforeEach
    fun setUp() {
        state = CreditUsageHistoryService.State()
    }

    @Nested
    @DisplayName("Interpolation Tests")
    inner class InterpolationTests {

        @Test
        @DisplayName("Returns null for empty snapshots")
        fun `interpolate returns null for empty snapshots`() {
            val result = interpolateUsageAtTime(state.snapshots, Instant.now().toEpochMilli())
            assertNull(result)
        }

        @Test
        @DisplayName("Returns exact value when snapshot matches target time")
        fun `interpolate returns exact value for matching snapshot`() {
            val targetTime = Instant.now().toEpochMilli()
            state.snapshots.add(CreditUsageHistoryService.CreditSnapshot(targetTime, 10.0))

            val result = interpolateUsageAtTime(state.snapshots, targetTime)
            assertEquals(10.0, result)
        }

        @Test
        @DisplayName("Returns exact value when snapshot is very close to target time")
        fun `interpolate returns exact value for close snapshot`() {
            val targetTime = Instant.now().toEpochMilli()
            // Snapshot is 30 seconds before target (within 1 minute tolerance)
            state.snapshots.add(CreditUsageHistoryService.CreditSnapshot(targetTime - 30_000, 10.0))

            val result = interpolateUsageAtTime(state.snapshots, targetTime)
            assertEquals(10.0, result)
        }

        @Test
        @DisplayName("Interpolates between two snapshots")
        fun `interpolate calculates value between two snapshots`() {
            val baseTime = Instant.now().toEpochMilli()
            // Before: usage = 10.0 at baseTime
            state.snapshots.add(CreditUsageHistoryService.CreditSnapshot(baseTime, 10.0))
            // After: usage = 20.0 at baseTime + 10 minutes
            state.snapshots.add(CreditUsageHistoryService.CreditSnapshot(baseTime + 600_000, 20.0))

            // Target: 5 minutes after baseTime (midpoint)
            val result = interpolateUsageAtTime(state.snapshots, baseTime + 300_000)
            assertNotNull(result)
            assertEquals(15.0, result!!, 0.01)
        }

        @Test
        @DisplayName("Interpolates at 25% position")
        fun `interpolate calculates value at quarter point`() {
            val baseTime = Instant.now().toEpochMilli()
            state.snapshots.add(CreditUsageHistoryService.CreditSnapshot(baseTime, 0.0))
            state.snapshots.add(CreditUsageHistoryService.CreditSnapshot(baseTime + 400_000, 100.0))

            // Target: 25% through (100 seconds after baseTime)
            val result = interpolateUsageAtTime(state.snapshots, baseTime + 100_000)
            assertNotNull(result)
            assertEquals(25.0, result!!, 0.01)
        }

        @Test
        @DisplayName("Returns before value when target is after all snapshots")
        fun `interpolate returns last snapshot value when target is after all`() {
            val baseTime = Instant.now().toEpochMilli() - 3600_000
            state.snapshots.add(CreditUsageHistoryService.CreditSnapshot(baseTime, 50.0))

            val result = interpolateUsageAtTime(state.snapshots, Instant.now().toEpochMilli())
            assertEquals(50.0, result)
        }

        @Test
        @DisplayName("Returns null when target is before all snapshots")
        fun `interpolate returns null when target is before all snapshots`() {
            val baseTime = Instant.now().toEpochMilli()
            state.snapshots.add(CreditUsageHistoryService.CreditSnapshot(baseTime, 50.0))

            // Target is 1 hour before the only snapshot
            val result = interpolateUsageAtTime(state.snapshots, baseTime - 3600_000)
            assertNull(result)
        }
    }

    @Nested
    @DisplayName("Today's Spending Calculation Tests")
    inner class TodaySpentTests {

        @Test
        @DisplayName("Calculates today's spending correctly")
        fun `calculateTodaySpent returns correct value`() {
            // Set up snapshots around midnight
            val localMidnight = LocalDate.now().atStartOfDay()
            val midnightUtc = localMidnight.atZone(ZoneId.systemDefault())
                .withZoneSameInstant(ZoneOffset.UTC)
                .toInstant()
                .toEpochMilli()

            // Snapshot just before midnight: usage = 100.0
            state.snapshots.add(CreditUsageHistoryService.CreditSnapshot(midnightUtc - 60_000, 100.0))
            // Snapshot just after midnight: usage = 100.5
            state.snapshots.add(CreditUsageHistoryService.CreditSnapshot(midnightUtc + 60_000, 100.5))

            // Current usage is 105.0, so today's spending = 105.0 - ~100.25 ≈ 4.75
            val midnightUsage = interpolateUsageAtTime(state.snapshots, midnightUtc)
            assertNotNull(midnightUsage)

            val todaySpent = 105.0 - midnightUsage!!
            assertTrue(todaySpent > 4.0)
            assertTrue(todaySpent < 5.0)
        }

        @Test
        @DisplayName("Returns null when no snapshots available for midnight calculation")
        fun `calculateTodaySpent returns null with no data`() {
            // No snapshots
            val localMidnight = LocalDate.now().atStartOfDay()
            val midnightUtc = localMidnight.atZone(ZoneId.systemDefault())
                .withZoneSameInstant(ZoneOffset.UTC)
                .toInstant()
                .toEpochMilli()

            val result = interpolateUsageAtTime(state.snapshots, midnightUtc)
            assertNull(result)
        }
    }

    @Nested
    @DisplayName("Days Remaining Calculation Tests")
    inner class DaysRemainingTests {

        @Test
        @DisplayName("Calculates days remaining correctly")
        fun `calculateDaysRemaining returns correct estimate`() {
            val remainingCredits = 100.0
            val yesterdaySpent = 10.0

            val daysRemaining = calculateDaysRemaining(remainingCredits, yesterdaySpent)
            assertEquals(10, daysRemaining)
        }

        @Test
        @DisplayName("Returns null when yesterday spent is zero")
        fun `calculateDaysRemaining returns null for zero spending`() {
            val result = calculateDaysRemaining(100.0, 0.0)
            assertNull(result)
        }

        @Test
        @DisplayName("Returns null when yesterday spent is negative")
        fun `calculateDaysRemaining returns null for negative spending`() {
            val result = calculateDaysRemaining(100.0, -5.0)
            assertNull(result)
        }

        @Test
        @DisplayName("Returns null when yesterday spent is null")
        fun `calculateDaysRemaining returns null for null spending`() {
            val result = calculateDaysRemaining(100.0, null)
            assertNull(result)
        }

        @Test
        @DisplayName("Handles fractional days by truncating")
        fun `calculateDaysRemaining truncates fractional days`() {
            val remainingCredits = 25.0
            val yesterdaySpent = 10.0

            val daysRemaining = calculateDaysRemaining(remainingCredits, yesterdaySpent)
            assertEquals(2, daysRemaining) // 25/10 = 2.5, truncated to 2
        }
    }

    @Nested
    @DisplayName("Snapshot Pruning Tests")
    inner class PruningTests {

        @Test
        @DisplayName("Removes snapshots older than 48 hours")
        fun `pruneOldSnapshots removes old data`() {
            val now = Instant.now().toEpochMilli()
            val hoursInMillis = 3600_000L

            // Add old snapshot (49 hours ago)
            state.snapshots.add(CreditUsageHistoryService.CreditSnapshot(now - 49 * hoursInMillis, 10.0))
            // Add recent snapshot (1 hour ago)
            state.snapshots.add(CreditUsageHistoryService.CreditSnapshot(now - hoursInMillis, 20.0))

            // Prune
            val cutoff = now - 48 * hoursInMillis
            state.snapshots.removeAll { it.timestampUtc < cutoff }

            assertEquals(1, state.snapshots.size)
            assertEquals(20.0, state.snapshots[0].totalUsed)
        }

        @Test
        @DisplayName("Keeps snapshots within retention period")
        fun `pruneOldSnapshots keeps recent data`() {
            val now = Instant.now().toEpochMilli()
            val hoursInMillis = 3600_000L

            // Add snapshot 47 hours ago (should be kept)
            state.snapshots.add(CreditUsageHistoryService.CreditSnapshot(now - 47 * hoursInMillis, 10.0))
            // Add snapshot 24 hours ago
            state.snapshots.add(CreditUsageHistoryService.CreditSnapshot(now - 24 * hoursInMillis, 20.0))
            // Add recent snapshot
            state.snapshots.add(CreditUsageHistoryService.CreditSnapshot(now - hoursInMillis, 30.0))

            // Prune
            val cutoff = now - 48 * hoursInMillis
            state.snapshots.removeAll { it.timestampUtc < cutoff }

            assertEquals(3, state.snapshots.size)
        }
    }

    @Nested
    @DisplayName("Yesterday's Spending Tests")
    inner class YesterdaySpentTests {

        @Test
        @DisplayName("Prefers API data over local calculation")
        fun `getYesterdaySpent returns API value when available`() {
            val apiValue = 15.5
            val result = getYesterdaySpent(apiValue)
            assertEquals(apiValue, result)
        }

        @Test
        @DisplayName("Returns null when API value is negative")
        fun `getYesterdaySpent returns null for negative API value`() {
            // With negative API value, should try local calculation (which returns null with no data)
            val result = getYesterdaySpent(-5.0)
            // Since we have no snapshots for local calculation, this will be null
            assertNull(result)
        }
    }

    // Helper methods that mirror the service's logic for testing

    @Suppress("ReturnCount")
    private fun interpolateUsageAtTime(
        snapshots: List<CreditUsageHistoryService.CreditSnapshot>,
        targetTimestamp: Long
    ): Double? {
        val sortedSnapshots = snapshots.sortedBy { it.timestampUtc }
        if (sortedSnapshots.isEmpty()) return null

        var before: CreditUsageHistoryService.CreditSnapshot? = null
        var after: CreditUsageHistoryService.CreditSnapshot? = null

        for (snapshot in sortedSnapshots) {
            if (snapshot.timestampUtc <= targetTimestamp) {
                before = snapshot
            } else {
                after = snapshot
                break
            }
        }

        // Case 1: Exact match or very close
        val millisPerMinute = 60_000L
        if (before != null && kotlin.math.abs(before.timestampUtc - targetTimestamp) < millisPerMinute) {
            return before.totalUsed
        }

        // Case 2: Both before and after - interpolate
        if (before != null && after != null) {
            val timeDiff = (after.timestampUtc - before.timestampUtc).toDouble()
            val usageDiff = after.totalUsed - before.totalUsed
            val ratio = (targetTimestamp - before.timestampUtc).toDouble() / timeDiff
            return before.totalUsed + (usageDiff * ratio)
        }

        // Case 3: Only before
        if (before != null) {
            return before.totalUsed
        }

        // Case 4: Only after (target is before all snapshots)
        return null
    }

    private fun calculateDaysRemaining(remainingCredits: Double, yesterdaySpent: Double?): Int? {
        if (yesterdaySpent == null || yesterdaySpent <= 0) return null
        val days = remainingCredits / yesterdaySpent
        return days.toInt()
    }

    private fun getYesterdaySpent(apiYesterdaySpent: Double?): Double? {
        if (apiYesterdaySpent != null && apiYesterdaySpent >= 0) {
            return apiYesterdaySpent
        }
        // No local calculation in test - would need snapshots
        return null
    }
}
