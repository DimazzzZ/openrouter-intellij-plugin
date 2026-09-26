package org.zhavoronkov.openrouter.toolwindow.chat

import com.intellij.ide.ui.laf.darcula.DarculaLaf
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.ui.ColorUtil
import com.intellij.util.ui.UIUtil
import javax.swing.UIManager
import javax.swing.plaf.metal.MetalLookAndFeel

/**
 * Regression test for the visual-pass defect where user and assistant
 * messages were indistinguishable: "Chat.userMessageBackground" is not a
 * stock IntelliJ theme key, so [userMessageBackgroundFallback] is what
 * actually paints in every real install. It must differ measurably from the
 * panel background it sits on top of - the old fallback (a bare
 * `UIUtil.getPanelBackground()`) never did, since it painted the exact same
 * colour as the panel behind it.
 *
 * The platform's `LafManager` theme registry (installed themes / default
 * light-dark pair) is empty in this headless test sandbox - it boots on plain
 * `MetalLookAndFeel`, and [com.intellij.ide.ui.laf.IntelliJLaf] cannot be
 * installed standalone here (it only overrides [DarculaLaf.getName], so a raw
 * instantiation renders identically to Darcula without the platform's theme
 * JSON layered on top by `LafManagerImpl`). [DarculaLaf] itself, however,
 * installs correctly and is a genuine, distinct dark palette. Metal's own
 * light palette stands in for a light theme: both are real, live
 * `UIManager`-backed palettes, exercised through the exact getters
 * [MessageView]'s paint code uses.
 */
class MessageViewTintFallbackPlatformTest : BasePlatformTestCase() {

    fun testFallbackDiffersFromThePanelBackgroundInALightAndADarkPalette() {
        val original = UIManager.getLookAndFeel()
        try {
            assertFallbackDiffersFromPanel(MetalLookAndFeel(), "light")
            assertFallbackDiffersFromPanel(DarculaLaf(), "dark")
        } finally {
            UIManager.setLookAndFeel(original)
        }
    }

    private fun assertFallbackDiffersFromPanel(laf: javax.swing.LookAndFeel, themeLabel: String) {
        UIManager.setLookAndFeel(laf)

        val panel = UIUtil.getPanelBackground()
        val fallback = userMessageBackgroundFallback()
        val panelHex = ColorUtil.toHex(panel)
        val fallbackHex = ColorUtil.toHex(fallback)
        println("[MessageView tint] $themeLabel palette: panel=$panelHex fallback=$fallbackHex")

        assertTrue(
            "$themeLabel palette: the tint must be measurably different from the panel background," +
                " got panel=$panelHex fallback=$fallbackHex",
            panel.rgb != fallback.rgb
        )
    }
}
