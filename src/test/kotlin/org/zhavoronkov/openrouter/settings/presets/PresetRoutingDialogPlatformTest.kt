package org.zhavoronkov.openrouter.settings.presets

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.ui.dsl.builder.panel
import com.intellij.util.ui.JBUI
import org.zhavoronkov.openrouter.models.ProviderRoutingPreferences
import org.zhavoronkov.openrouter.settings.ProviderRoutingForm

/** The routing dialog must fit a laptop screen; the whole form is taller than one, so it scrolls. */
class PresetRoutingDialogPlatformTest : BasePlatformTestCase() {

    fun testTheRoutingDialogFitsASmallScreen() {
        val form = ProviderRoutingForm().apply { show(ProviderRoutingPreferences(order = listOf("a", "b", "c"))) }

        val content = PresetDialog.RoutingDialog.content(form)

        val limit = JBUI.scale(SMALL_SCREEN)
        val bare = panel { ProviderRoutingForm().addTo(this) }
        assertTrue(
            "the form alone, ${bare.minimumSize.height} px, is what needs the scrolling",
            bare.minimumSize.height > limit
        )
        assertTrue(
            "minimum height ${content.minimumSize.height} fits a 768 px screen",
            content.minimumSize.height < limit
        )
        assertTrue(
            "preferred height ${content.preferredSize.height} fits a 768 px screen",
            content.preferredSize.height < limit
        )
    }

    private companion object {
        const val SMALL_SCREEN = 768
    }
}
