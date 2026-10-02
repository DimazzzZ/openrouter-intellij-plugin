package org.zhavoronkov.openrouter.statusbar

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.zhavoronkov.openrouter.models.ActivityData
import org.zhavoronkov.openrouter.models.CreditsData
import org.zhavoronkov.openrouter.services.CreditUsageHistoryService
import java.time.LocalDate
import java.time.ZoneId

/** The status bar's activity rows with the application's credit history to ask, as in a running IDE. */
class StatusBarStatsFormatterPlatformTest : BasePlatformTestCase() {

    private val history get() = CreditUsageHistoryService.getInstance()

    override fun tearDown() {
        try {
            history.clearSnapshots()
        } finally {
            super.tearDown()
        }
    }

    fun testTheHistoryAnswersTodaysSpendAndTheDaysTheBalanceLasts() {
        history.clearSnapshots()
        history.recordSnapshot(totalUsed = 2.0)
        val yesterday = LocalDate.now(ZoneId.of("UTC")).minusDays(1).toString()
        val activity = listOf(ActivityData(yesterday, "m", null, null, null, 1.0, null, 1, null, null, null))

        val html = StatusBarStatsFormatter.calculateActivityRowsWithHistory(
            activity,
            creditsData = CreditsData(totalCredits = 10.0, totalUsage = 2.5),
            remainingCredits = 7.5
        )

        assertTrue("the balance lasts 7.5 / 1.0 days: $html", html.contains("~7 days"))
    }

    fun testTodaysSpendIsWhatWasUsedSinceTheSnapshotBeforeMidnight() {
        history.clearSnapshots()
        val beforeMidnight = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).minusHours(1).toInstant()
        history.state.snapshots.add(
            CreditUsageHistoryService.CreditSnapshot(timestampUtc = beforeMidnight.toEpochMilli(), totalUsed = 2.0)
        )

        val html = StatusBarStatsFormatter.calculateActivityRowsWithHistory(
            emptyList(),
            creditsData = CreditsData(totalCredits = 10.0, totalUsage = 2.5),
            remainingCredits = 7.5
        )

        assertTrue("today is 2.5 - 2.0 used since midnight: $html", html.contains("\$0.500"))
    }

    fun testWithoutCreditsTheHistoryIsNotAsked() {
        val html = StatusBarStatsFormatter.calculateActivityRowsWithHistory(
            emptyList(),
            creditsData = null,
            remainingCredits = 0.0
        )

        assertTrue("no yesterday spend, no estimate: $html", html.contains("N/A"))
    }
}
