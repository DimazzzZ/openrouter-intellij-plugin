package org.zhavoronkov.openrouter.toolwindow.requests

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.zhavoronkov.openrouter.models.FixPage

class FixPageSettingsTest {

    @Test
    @DisplayName("every page a refusal names opens a settings page of its own")
    fun onePagePerFix() {
        val configurables = FixPage.entries.map(FixPageSettings::configurable)

        assertEquals(FixPage.entries.size, configurables.distinct().size)
    }

    /** A page not registered would open nothing; the Settings dialog finds a page by its registration. */
    @Test
    @DisplayName("every page it opens is registered in plugin.xml")
    fun registered() {
        val pluginXml = javaClass.classLoader.getResource("META-INF/plugin.xml")!!.readText()

        FixPage.entries.map(FixPageSettings::configurable).forEach {
            assertTrue(pluginXml.contains("instance=\"${it.name}\""), it.name)
        }
    }

    @Test
    @DisplayName("the action names the page")
    fun actionText() {
        assertEquals("Open Presets", FixPageSettings.actionText(FixPage.PRESETS))
        assertEquals("Open OpenRouter Settings", FixPageSettings.actionText(FixPage.DATA_REGION))
    }
}
