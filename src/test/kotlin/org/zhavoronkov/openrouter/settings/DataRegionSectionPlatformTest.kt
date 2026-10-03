package org.zhavoronkov.openrouter.settings

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.zhavoronkov.openrouter.models.DataRegion

/**
 * [DataRegionSection] is a platform test rather than a plain one only because its control is the
 * IDE's own `ComboBox`; the behaviour being pinned is ordinary logic.
 *
 * The case worth protecting is [testAWithdrawnSelectionIsKeptRatherThanSilentlyReset]. A region
 * can stop being allowed without the user doing anything - a plan downgrade, a new guardrail
 * policy, a replaced key - and quietly snapping the control back to Global would move that user's
 * traffic out of the region they deliberately chose. For anyone who chose it for data residency
 * that is the exact outcome they were guarding against, so the selection stays and the failure
 * shows up loudly as failing requests instead.
 */
class DataRegionSectionPlatformTest : BasePlatformTestCase() {

    private fun items(section: DataRegionSection): List<DataRegion?> =
        (0 until section.comboBox.itemCount).map { section.comboBox.getItemAt(it) }

    fun testProgrammaticUpdatesAreNotMistakenForTheUserChoosing() {
        val section = DataRegionSection()
        var notified = 0
        section.onRegionChosen = { notified++ }

        // Exactly what happens when the settings page opens: the stored region is restored, then
        // the account's availability arrives. Neither is a choice, and treating them as one cost
        // two network calls per load before this was guarded.
        section.setRegion(DataRegion.EUROPE)
        section.setAvailableRegions(listOf(DataRegion.GLOBAL, DataRegion.EUROPE))

        assertEquals(0, notified)
    }

    fun testOnlyTheGlobalRegionLeavesTheControlDisabledWithTheReason() {
        val section = DataRegionSection()

        section.setAvailableRegions(listOf(DataRegion.GLOBAL))

        assertFalse("nothing to choose between, so the control should be disabled", section.comboBox.isEnabled)
        assertEquals(DataRegionSection.UNAVAILABLE_TEXT, section.comment.text)
        assertEquals(DataRegion.GLOBAL, section.getRegion())
    }

    fun testMoreThanOneRegionEnablesTheControlAndExplainsTheTradeOff() {
        val section = DataRegionSection()

        section.setAvailableRegions(listOf(DataRegion.GLOBAL, DataRegion.EUROPE))

        assertTrue(section.comboBox.isEnabled)
        assertEquals(DataRegionSection.AVAILABLE_TEXT, section.comment.text)
        assertEquals(listOf(DataRegion.GLOBAL, DataRegion.EUROPE), items(section))
    }

    fun testBeforeTheAccountIsCheckedTheControlSaysSo() {
        val section = DataRegionSection()

        assertEquals(DataRegionSection.CHECKING_TEXT, section.comment.text)
    }

    fun testAWithdrawnSelectionIsReportedRatherThanLeftToFailSilently() {
        val section = DataRegionSection()
        section.setAvailableRegions(listOf(DataRegion.GLOBAL, DataRegion.EUROPE))
        section.setRegion(DataRegion.EUROPE)

        section.setAvailableRegions(listOf(DataRegion.GLOBAL))

        assertTrue(
            "the warning must name the region that went away",
            section.comment.text.contains(DataRegion.EUROPE.displayName)
        )
        assertTrue(
            "and the control must stay usable so the warning can be acted on",
            section.comboBox.isEnabled
        )
    }

    fun testAWithdrawnSelectionIsKeptRatherThanSilentlyReset() {
        val section = DataRegionSection()
        section.setAvailableRegions(listOf(DataRegion.GLOBAL, DataRegion.EUROPE))
        section.setRegion(DataRegion.EUROPE)

        // The account loses the entitlement while the settings page is open.
        section.setAvailableRegions(listOf(DataRegion.GLOBAL))

        assertEquals(
            "a region the user chose must not be swapped out from under them",
            DataRegion.EUROPE,
            section.getRegion()
        )
        assertTrue("and it must still be visible in the list", DataRegion.EUROPE in items(section))
    }

    fun testSettingARegionTheListDoesNotHaveYetAddsItRatherThanBeingIgnored() {
        val section = DataRegionSection()

        // Settings are read back before the account has been checked, so the stored region can
        // arrive while the list still holds nothing but Global.
        section.setRegion(DataRegion.US)

        assertEquals(DataRegion.US, section.getRegion())
        assertTrue(DataRegion.US in items(section))
    }

    fun testTheSameRegionIsNotAddedTwice() {
        val section = DataRegionSection()
        section.setAvailableRegions(listOf(DataRegion.GLOBAL, DataRegion.EUROPE))

        section.setRegion(DataRegion.EUROPE)
        section.setRegion(DataRegion.EUROPE)

        assertEquals(listOf(DataRegion.GLOBAL, DataRegion.EUROPE), items(section))
    }

    fun testTheUserChoosingARegionIsReported() {
        val section = DataRegionSection()
        section.setAvailableRegions(listOf(DataRegion.GLOBAL, DataRegion.EUROPE))
        val chosen = mutableListOf<DataRegion>()
        section.onRegionChosen = { chosen += it }

        section.comboBox.selectedItem = DataRegion.EUROPE

        assertEquals(listOf(DataRegion.EUROPE), chosen)
    }

    fun testNothingSelectedReadsAsGlobal() {
        val section = DataRegionSection()

        section.comboBox.selectedItem = null

        assertEquals(DataRegion.GLOBAL, section.getRegion())
    }

    fun testARegionMissingFavoritesSaysHowManyAndGlobalOrNoneMissingSaysNothingMore() {
        val section = DataRegionSection()
        section.setAvailableRegions(listOf(DataRegion.GLOBAL, DataRegion.EUROPE))
        val base = section.comment.text

        section.setFavoritesImpact(DataRegion.EUROPE, unavailableCount = 2, favoriteCount = 5)
        assertTrue(section.comment.text, section.comment.text.startsWith(base))
        assertTrue(section.comment.text, section.comment.text.contains("2 of your 5 favorite models"))

        section.setFavoritesImpact(DataRegion.EUROPE, unavailableCount = 0, favoriteCount = 5)
        assertEquals(base, section.comment.text)
        section.setFavoritesImpact(DataRegion.GLOBAL, unavailableCount = 2, favoriteCount = 5)
        assertEquals(base, section.comment.text)
    }
}
