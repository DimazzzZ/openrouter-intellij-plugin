package org.zhavoronkov.openrouter.toolwindow.chat

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.ui.InplaceButton
import java.awt.Cursor
import java.awt.Dimension
import java.awt.Rectangle
import java.awt.image.BufferedImage
import javax.swing.JButton

/**
 * Regression test for item 4 of the chat UI polish pass: the gear must be a
 * borderless icon button in the JetBrains manner (hover highlight only, no
 * border, no filled content area) - not a bordered [JButton] carrying the
 * same visual weight as the model selector beside it - while still keeping
 * its badge dot and tooltip.
 */
class ChatComposerGearButtonPlatformTest : BasePlatformTestCase() {

    private companion object {
        /** The height ComposerLayout gives the gear: the row's, matching the model combo. */
        const val ROW_HEIGHT = 28
    }

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

    /**
     * The gear is a click target, so it should say so under the pointer.
     *
     * InplaceButton inherits the default arrow - it sets no cursor of its own - so a borderless
     * icon button reads as decoration until it is clicked on spec.
     */
    fun testGearShowsAHandCursor() {
        val composer = ChatComposer()

        val cursor = composer.settingsComponent().cursor

        assertEquals("the gear must show a hand cursor", Cursor.HAND_CURSOR, cursor.type)
    }

    fun testGearKeepsItsTooltip() {
        val composer = ChatComposer()

        composer.setSettingsTooltip("Reasoning: high")

        assertEquals("Reasoning: high", composer.settingsComponent().toolTipText)
    }

    /**
     * The badge must sit on the gear, not float above it.
     *
     * ComposerLayout gives the gear the whole ROW's height so it lines up with the model combo
     * beside it - `place(settings, x, insets.top, settings.preferredSize.width, height, ...)` -
     * while InplaceButton paints its 16px icon centred in those taller bounds. A badge positioned
     * from the BUTTON's top edge therefore ends up above the icon entirely: measured at y=2..6
     * against an icon starting at y=6, which is the detached dot in the bug report.
     *
     * The badge is painted rather than exposed as a property, so it is read the only way it can
     * be: render with it off and on, and take the bounding box of the pixels that changed.
     */
    fun testBadgeSitsOnTheIconEvenWhenTheRowMakesTheButtonTaller() {
        val composer = ChatComposer()
        val gear = composer.settingsComponent() as InplaceButton
        gear.size = Dimension(gear.preferredSize.width, ROW_HEIGHT)
        gear.doLayout()

        composer.setSettingsBadge(false)
        val plain = render(gear)
        composer.setSettingsBadge(true)
        val badged = render(gear)

        val badge = changedPixels(plain, badged)
        assertNotNull("the badge painted nothing, so this test proves nothing", badge)

        // Verified against the platform rather than assumed: InplaceButton paints a 16px icon at
        // y=6 in a 16x28 button, which is exact vertical centring.
        val icon = gear.icon
        val iconRect = Rectangle(
            (gear.width - icon.iconWidth) / 2,
            (gear.height - icon.iconHeight) / 2,
            icon.iconWidth,
            icon.iconHeight
        )
        assertTrue(
            "the badge $badge must sit within the icon $iconRect, not float in the button's padding",
            iconRect.contains(badge)
        )
    }

    private fun render(button: InplaceButton): BufferedImage {
        val image = BufferedImage(button.width, button.height, BufferedImage.TYPE_INT_ARGB)
        val g = image.createGraphics()
        button.paint(g)
        g.dispose()
        return image
    }

    /** Bounding box of every pixel that differs between two renders. */
    private fun changedPixels(a: BufferedImage, b: BufferedImage): Rectangle? {
        var minX = Int.MAX_VALUE
        var minY = Int.MAX_VALUE
        var maxX = -1
        var maxY = -1
        for (y in 0 until a.height) {
            for (x in 0 until a.width) {
                if (a.getRGB(x, y) != b.getRGB(x, y)) {
                    if (x < minX) minX = x
                    if (y < minY) minY = y
                    if (x > maxX) maxX = x
                    if (y > maxY) maxY = y
                }
            }
        }
        return if (maxX < 0) null else Rectangle(minX, minY, maxX - minX + 1, maxY - minY + 1)
    }

    fun testGearBadgeToggleDoesNotThrow() {
        val composer = ChatComposer()

        composer.setSettingsBadge(true)
        composer.setSettingsBadge(false)
        // No assertion beyond "does not throw": the badge is painted, not a
        // separate readable property, and repainting off-screen is harmless.
    }
}
