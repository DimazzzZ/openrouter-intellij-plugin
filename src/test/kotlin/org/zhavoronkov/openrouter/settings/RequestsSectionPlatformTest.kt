package org.zhavoronkov.openrouter.settings

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.zhavoronkov.openrouter.models.OpenRouterSettings
import org.zhavoronkov.openrouter.services.settings.UIPreferencesManager

/** The Requests tab's settings on the OpenRouter page: each one alone counts as a change, and is stored. */
class RequestsSectionPlatformTest : BasePlatformTestCase() {

    private val prefs = UIPreferencesManager(OpenRouterSettings()) {}

    private fun shown(): RequestsSection = RequestsSection().also { it.reset(prefs) }

    fun testTheSectionOpensShowingWhatIsStored() {
        prefs.requestWarningBalloons = false
        prefs.requestLogLimit = 500
        prefs.keepRequestBodies = true

        val section = shown()

        assertFalse(section.warningBalloons.isSelected)
        assertEquals(500, section.limit.number)
        assertTrue(section.keepBodies.isSelected)
        assertFalse("what is stored is not a change", section.isModified(prefs))
    }

    fun testEachSettingAloneCountsAsAChange() {
        val balloons = shown().apply { warningBalloons.isSelected = !warningBalloons.isSelected }
        assertTrue("the warning balloons", balloons.isModified(prefs))

        val limit = shown().apply { limit.number = limit.number + 100 }
        assertTrue("the number of requests kept", limit.isModified(prefs))

        val bodies = shown().apply { keepBodies.isSelected = !keepBodies.isSelected }
        assertTrue("keeping each request's prompt and reply", bodies.isModified(prefs))
    }

    fun testApplyStoresWhatTheSectionShows() {
        val section = shown()
        section.warningBalloons.isSelected = !prefs.requestWarningBalloons
        section.limit.number = 2_000
        section.keepBodies.isSelected = !prefs.keepRequestBodies
        val balloons = section.warningBalloons.isSelected
        val bodies = section.keepBodies.isSelected

        section.apply(prefs)

        assertEquals(balloons, prefs.requestWarningBalloons)
        assertEquals(2_000, prefs.requestLogLimit)
        assertEquals(bodies, prefs.keepRequestBodies)
        assertFalse("applied is no longer a change", section.isModified(prefs))
    }
}
