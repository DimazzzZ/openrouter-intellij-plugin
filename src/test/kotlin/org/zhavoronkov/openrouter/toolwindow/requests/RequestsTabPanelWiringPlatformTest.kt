package org.zhavoronkov.openrouter.toolwindow.requests

import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.zhavoronkov.openrouter.services.OpenRouterSettingsService

/** The Requests tab on its production collaborators: its two switches are kept in the plugin's settings. */
class RequestsTabPanelWiringPlatformTest : BasePlatformTestCase() {

    fun testTheTabsSwitchesAreKeptInTheSettings() {
        val preferences = OpenRouterSettingsService.getInstance().uiPreferencesManager
        val groupBefore = preferences.requestsGroupBursts
        val keepBefore = preferences.keepRequestBodies
        val panel = RequestsTabPanel(confirmClear = { false })
        Disposer.register(testRootDisposable, panel)
        try {
            PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

            panel.groupBursts.doClick()
            panel.keepBodies.doClick()

            assertEquals(!groupBefore, preferences.requestsGroupBursts)
            assertEquals(!keepBefore, preferences.keepRequestBodies)
        } finally {
            preferences.requestsGroupBursts = groupBefore
            preferences.keepRequestBodies = keepBefore
        }
    }
}
