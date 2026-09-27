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
}
