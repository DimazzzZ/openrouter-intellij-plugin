package org.zhavoronkov.openrouter.services

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.zhavoronkov.openrouter.utils.PluginLogger
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * Service for tracking historical credit usage to calculate "today's spending".
 *
 * Since the OpenRouter API doesn't provide today's activity data (only yesterday and earlier),
 * this service periodically snapshots the "total used" credits value and stores it locally.
 * By comparing the current usage with the interpolated midnight value, we can calculate
 * how much has been spent today.
 *
 * Data is stored in UTC and retained for 48 hours. When the IDE isn't running at midnight,
 * the service interpolates the midnight value from surrounding snapshots.
 */
@Service(Service.Level.APP)
@State(
    name = "CreditUsageHistory",
    storages = [Storage("openrouter-credit-history.xml")]
)
@Suppress("TooManyFunctions")
class CreditUsageHistoryService : PersistentStateComponent<CreditUsageHistoryService.State>, Disposable {

    companion object {
        private const val SNAPSHOT_INTERVAL_MINUTES = 5L
        private const val RETENTION_HOURS = 48L
        private const val MILLIS_PER_HOUR = 3600000L
        private const val MILLIS_PER_MINUTE = 60000L
        private const val REFRESH_DELAY_MS = 2000L

        fun getInstance(): CreditUsageHistoryService {
            return ApplicationManager.getApplication().getService(CreditUsageHistoryService::class.java)
        }
    }

    /**
     * A single credit usage snapshot.
     * Note: Default values in primary constructor serve as no-arg constructor for XML serialization.
     */
    data class CreditSnapshot(
        var timestampUtc: Long = 0L,
        var totalUsed: Double = 0.0
    )

    /**
     * Persistent state containing all snapshots.
     */
    data class State(
        var snapshots: MutableList<CreditSnapshot> = mutableListOf()
    )

    private var state = State()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    @Volatile
    private var isRunning = false

    override fun getState(): State = state

    override fun loadState(loadedState: State) {
        state = loadedState
        pruneOldSnapshots()
        PluginLogger.Service.info("CreditUsageHistoryService: Loaded ${state.snapshots.size} snapshots")
    }

    /**
     * Start the background snapshot timer.
     * Should be called after plugin initialization.
     */
    fun startSnapshotTimer() {
        if (isRunning) {
            PluginLogger.Service.debug("CreditUsageHistoryService: Timer already running")
            return
        }
        isRunning = true
        PluginLogger.Service.info(
            "CreditUsageHistoryService: Starting snapshot timer (${SNAPSHOT_INTERVAL_MINUTES}min interval)"
        )

        scope.launch {
            takeSnapshotIfNeeded()
            while (isActive) {
                delay(SNAPSHOT_INTERVAL_MINUTES * MILLIS_PER_MINUTE)
                takeSnapshotIfNeeded()
            }
        }
    }

    /**
     * Stop the background timer.
     */
    fun stopSnapshotTimer() {
        isRunning = false
        PluginLogger.Service.info("CreditUsageHistoryService: Stopped snapshot timer")
    }

    /**
     * Take a snapshot of current credit usage if the service is configured.
     */
    private suspend fun takeSnapshotIfNeeded() {
        try {
            val statsCache = getStatsCacheSafely() ?: return
            val credits = statsCache.getCachedCredits()

            if (credits == null) {
                statsCache.refresh()
                delay(REFRESH_DELAY_MS)
                val refreshedCredits = statsCache.getCachedCredits() ?: return
                recordSnapshot(refreshedCredits.totalUsage)
            } else {
                recordSnapshot(credits.totalUsage)
            }
        } catch (e: IllegalStateException) {
            PluginLogger.Service.warn("CreditUsageHistoryService: Service not available: ${e.message}")
        }
    }

    /**
     * Record a credit usage snapshot at the current time.
     */
    fun recordSnapshot(totalUsed: Double) {
        val now = Instant.now().toEpochMilli()
        val snapshot = CreditSnapshot(timestampUtc = now, totalUsed = totalUsed)

        synchronized(state) {
            state.snapshots.add(snapshot)
            pruneOldSnapshots()
        }

        PluginLogger.Service.debug(
            "CreditUsageHistoryService: Recorded snapshot - totalUsed=$totalUsed at ${formatTimestamp(now)}"
        )
    }

    /**
     * Remove snapshots older than retention period.
     */
    private fun pruneOldSnapshots() {
        val cutoff = Instant.now().toEpochMilli() - (RETENTION_HOURS * MILLIS_PER_HOUR)
        val beforeCount = state.snapshots.size
        state.snapshots.removeAll { it.timestampUtc < cutoff }
        val removedCount = beforeCount - state.snapshots.size
        if (removedCount > 0) {
            PluginLogger.Service.debug("CreditUsageHistoryService: Pruned $removedCount old snapshots")
        }
    }

    /**
     * Calculate today's spending by comparing current usage with midnight value.
     */
    fun calculateTodaySpent(currentTotalUsed: Double): Double? {
        val midnightUsage = getMidnightUsage() ?: return null
        val todaySpent = currentTotalUsed - midnightUsage
        return if (todaySpent >= 0) todaySpent else null
    }

    /**
     * Get the estimated usage value at midnight (start of today in user's local timezone).
     */
    fun getMidnightUsage(): Double? {
        val localMidnight = LocalDate.now().atStartOfDay()
        val midnightUtc = localMidnight.atZone(ZoneId.systemDefault())
            .withZoneSameInstant(ZoneOffset.UTC)
            .toInstant()
            .toEpochMilli()

        return interpolateUsageAtTime(midnightUtc)
    }

    /**
     * Interpolate the usage value at a specific timestamp.
     */
    @Suppress("ReturnCount")
    fun interpolateUsageAtTime(targetTimestamp: Long): Double? {
        val sortedSnapshots = state.snapshots.sortedBy { it.timestampUtc }
        if (sortedSnapshots.isEmpty()) return null

        val (before, after) = findSurroundingSnapshots(sortedSnapshots, targetTimestamp)

        // Case 1: Exact match or very close to a snapshot
        if (before != null && kotlin.math.abs(before.timestampUtc - targetTimestamp) < MILLIS_PER_MINUTE) {
            return before.totalUsed
        }

        // Case 2: We have both before and after - interpolate
        if (before != null && after != null) {
            return interpolateBetweenSnapshots(before, after, targetTimestamp)
        }

        // Case 3: Only have "before" (target is after all snapshots)
        if (before != null) {
            return before.totalUsed
        }

        // Case 4: Only have "after" - can't interpolate backwards
        return null
    }

    private fun findSurroundingSnapshots(
        snapshots: List<CreditSnapshot>,
        targetTimestamp: Long
    ): Pair<CreditSnapshot?, CreditSnapshot?> {
        var before: CreditSnapshot? = null
        var after: CreditSnapshot? = null

        for (snapshot in snapshots) {
            if (snapshot.timestampUtc <= targetTimestamp) {
                before = snapshot
            } else {
                after = snapshot
                break
            }
        }
        return Pair(before, after)
    }

    private fun interpolateBetweenSnapshots(
        before: CreditSnapshot,
        after: CreditSnapshot,
        targetTimestamp: Long
    ): Double {
        val timeDiff = (after.timestampUtc - before.timestampUtc).toDouble()
        val usageDiff = after.totalUsed - before.totalUsed
        val ratio = (targetTimestamp - before.timestampUtc).toDouble() / timeDiff
        return before.totalUsed + (usageDiff * ratio)
    }

    /**
     * Get yesterday's spending. Prefers API data, falls back to local calculation.
     * Note: Reserved for future use when detailed yesterday statistics are needed.
     */
    @Suppress("unused")
    fun getYesterdaySpent(apiYesterdaySpent: Double?): Double? {
        if (apiYesterdaySpent != null && apiYesterdaySpent >= 0) {
            return apiYesterdaySpent
        }
        return calculateYesterdayFromSnapshots()
    }

    @Suppress("ReturnCount")
    private fun calculateYesterdayFromSnapshots(): Double? {
        val today = LocalDate.now()
        val yesterdayStart = today.minusDays(1).atStartOfDay()
        val yesterdayEnd = today.atStartOfDay()

        val startUtc = yesterdayStart.atZone(ZoneId.systemDefault())
            .withZoneSameInstant(ZoneOffset.UTC)
            .toInstant()
            .toEpochMilli()

        val endUtc = yesterdayEnd.atZone(ZoneId.systemDefault())
            .withZoneSameInstant(ZoneOffset.UTC)
            .toInstant()
            .toEpochMilli()

        val startUsage = interpolateUsageAtTime(startUtc) ?: return null
        val endUsage = interpolateUsageAtTime(endUtc) ?: return null

        val spent = endUsage - startUsage
        return if (spent >= 0) spent else null
    }

    /**
     * Calculate estimated days remaining based on yesterday's spending rate.
     */
    fun calculateDaysRemaining(remainingCredits: Double, yesterdaySpent: Double?): Int? {
        if (yesterdaySpent == null || yesterdaySpent <= 0) return null
        val days = remainingCredits / yesterdaySpent
        return days.toInt()
    }

    /** Get all snapshots for debugging/display purposes. */
    @Suppress("unused")
    fun getSnapshots(): List<CreditSnapshot> = state.snapshots.toList()

    /** Get snapshot count for diagnostics. */
    fun getSnapshotCount(): Int = state.snapshots.size

    /** Clear all snapshots (for testing or reset purposes). */
    @Suppress("unused")
    fun clearSnapshots() {
        state.snapshots.clear()
        PluginLogger.Service.info("CreditUsageHistoryService: Cleared all snapshots")
    }

    private fun getStatsCacheSafely(): OpenRouterStatsCache? {
        return try {
            OpenRouterStatsCache.getInstance()
        } catch (e: IllegalStateException) {
            PluginLogger.Service.warn("CreditUsageHistoryService: OpenRouterStatsCache not available: ${e.message}")
            null
        }
    }

    private fun formatTimestamp(timestamp: Long): String {
        return LocalDateTime.ofInstant(
            Instant.ofEpochMilli(timestamp),
            ZoneId.systemDefault()
        ).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
    }

    override fun dispose() {
        stopSnapshotTimer()
        scope.cancel()
        PluginLogger.Service.info("CreditUsageHistoryService disposed")
    }
}
