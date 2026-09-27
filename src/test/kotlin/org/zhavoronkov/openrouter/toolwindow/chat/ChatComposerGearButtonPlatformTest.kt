package org.zhavoronkov.openrouter.toolwindow.chat

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.ui.InplaceButton
import javax.swing.JButton

/**
 * Regression test for item 4 of the chat UI polish pass: the gear must be a
 * borderless icon button in the JetBrains manner (hover highlight only, no
 * border, no filled content area) - not a bordered [JButton] carrying the
 * same visual weight as the model selector beside it - while still keeping
 * its badge dot and tooltip.
 */
class ChatComposerGearButtonPlatformTest : BasePlatformTestCase() {

    fun testGearIsABorderlessInplaceButtonNotABorderedJButton() {
        val composer = ChatComposer()

        val gear = composer.settingsComponent()

        assertTrue(
            "the gear must be the platform's borderless InplaceButton",
            gear is InplaceButton
        )
        assertFalse(
            "the gear must not be a plain bordered JButton",
            gear is JButton
        )
    }

    fun testGearKeepsItsTooltip() {
        val composer = ChatComposer()

        composer.setSettingsTooltip("Reasoning: high")

        assertEquals("Reasoning: high", composer.settingsComponent().toolTipText)
    }

    fun testGearBadgeToggleDoesNotThrow() {
        val composer = ChatComposer()

        composer.setSettingsBadge(true)
        composer.setSettingsBadge(false)
        // No assertion beyond "does not throw": the badge is painted, not a
        // separate readable property, and repainting off-screen is harmless.
    }
}
