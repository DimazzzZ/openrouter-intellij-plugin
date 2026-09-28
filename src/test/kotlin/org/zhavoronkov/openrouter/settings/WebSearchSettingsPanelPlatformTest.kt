package org.zhavoronkov.openrouter.settings

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.util.xmlb.XmlSerializer
import org.zhavoronkov.openrouter.models.OpenRouterSettings
import org.zhavoronkov.openrouter.models.WebSearchEngine
import org.zhavoronkov.openrouter.models.WebSearchSettings
import org.zhavoronkov.openrouter.services.settings.WebSearchSettingsManager
import java.awt.Container
import javax.swing.JLabel

/**
 * The Web Search settings page, driven the way a user drives it and read back through the
 * Settings layer. The page is built over its own [OpenRouterSettings] rather than the IDE-wide
 * service, so nothing here leaks into the settings other tests see.
 */
class WebSearchSettingsPanelPlatformTest : BasePlatformTestCase() {

    private lateinit var settings: OpenRouterSettings
    private lateinit var manager: WebSearchSettingsManager
    private lateinit var page: WebSearchSettingsPanel

    override fun setUp() {
        super.setUp()
        settings = OpenRouterSettings()
        manager = WebSearchSettingsManager(settings) {}
        page = WebSearchSettingsPanel(manager)
    }

    private fun labels(root: Container): List<String> {
        val found = mutableListOf<String>()
        fun walk(c: java.awt.Component) {
            if (c is JLabel) found += c.text.orEmpty()
            if (c is Container) c.components.forEach(::walk)
        }
        walk(root)
        return found
    }

    private fun modes(): List<String> = (0 until page.mode.itemCount).map { page.mode.getItemAt(it) }

    fun testThePageOffersEveryTuningChoice() {
        val texts = labels(page.createPanel())

        listOf("Engine:", "Results:", "Include domains:", "Exclude domains:", "Mode:").forEach {
            assertTrue("expected a '$it' row, got $texts", texts.contains(it))
        }
    }

    fun testAnUntouchedPageStoresNothingAndSendsTheBareEntry() {
        page.createPanel()

        assertFalse("opening the page must not count as a change", page.isModified())
        assertEquals(emptyMap<String, Any>(), page.snapshot().pluginParams())
    }

    fun testApplyStoresWhatThePageShows() {
        page.createPanel()
        page.engine.selectedItem = "exa"
        page.mode.selectedItem = "deep"
        page.maxResults.number = 8
        page.includeDomains.text = "docs.gradle.org, *.jetbrains.com"
        page.excludeDomains.text = "pinterest.com"

        assertTrue("an edited page must report a change", page.isModified())
        page.apply()

        assertEquals(
            WebSearchSettings(
                engine = WebSearchEngine.EXA,
                maxResults = 8,
                includeDomains = listOf("docs.gradle.org", "*.jetbrains.com"),
                excludeDomains = listOf("pinterest.com"),
                mode = "deep"
            ),
            manager.current()
        )
        assertFalse("once applied, the page matches what is stored", page.isModified())
    }

    fun testResetShowsWhatIsStoredAndDiscardsEdits() {
        manager.replace(WebSearchSettings(engine = WebSearchEngine.PARALLEL, maxResults = 3, mode = "advanced"))
        page.createPanel()

        assertEquals("parallel", page.engine.selectedItem)
        assertEquals("advanced", page.mode.selectedItem)
        assertEquals(3, page.maxResults.number)

        page.maxResults.number = 12
        page.reset()

        assertEquals(3, page.maxResults.number)
        assertFalse(page.isModified())
    }

    fun testModeIsOfferedOnlyForEnginesThatTakeOne() {
        page.createPanel()

        assertFalse("with OpenRouter picking the engine, no mode can be known to apply", page.mode.isEnabled)
        assertEquals(WebSearchSettingsPanel.NO_MODE_TEXT, page.modeComment.text)

        page.engine.selectedItem = "exa"
        assertTrue(page.mode.isEnabled)
        assertEquals(listOf(WebSearchSettingsPanel.UNSET) + WebSearchEngine.EXA.selectableModes, modes())
        assertEquals("Default is Exa's own, auto.", page.modeComment.text)

        page.engine.selectedItem = "firecrawl"
        assertFalse(page.mode.isEnabled)
    }

    /**
     * A mode the new engine does not take goes back to Default on the page itself, so what the
     * page shows is what will be sent. A mode both engines take is kept.
     */
    fun testChangingTheEngineKeepsOnlyAModeTheNewEngineTakes() {
        page.createPanel()
        page.engine.selectedItem = "exa"
        page.mode.selectedItem = "fast"

        page.engine.selectedItem = "parallel"
        assertEquals("a mode Parallel also takes must survive the switch", "fast", page.mode.selectedItem)

        page.mode.selectedItem = "turbo"
        page.engine.selectedItem = "exa"
        assertEquals(WebSearchSettingsPanel.UNSET, page.mode.selectedItem)
        assertNull(page.snapshot().mode)
    }

    /** What the IDE does on shutdown and startup: the settings object written to XML and read back. */
    fun testAppliedSettingsSurviveARestart() {
        page.createPanel()
        page.engine.selectedItem = "exa"
        page.mode.selectedItem = "deep"
        page.includeDomains.text = "docs.gradle.org"
        page.apply()

        val restarted = XmlSerializer.deserialize(XmlSerializer.serialize(settings), OpenRouterSettings::class.java)
        val reopened = WebSearchSettingsPanel(WebSearchSettingsManager(restarted) {})
        reopened.createPanel()

        assertEquals(page.snapshot(), reopened.snapshot())
        assertEquals("exa", reopened.engine.selectedItem)
        assertEquals("deep", reopened.mode.selectedItem)
    }

    /** The registered page itself, over the IDE's own settings: it opens clean and says what it is. */
    fun testTheRegisteredPageOpensWithoutAChange() {
        val configurable = WebSearchConfigurable()
        try {
            assertEquals("Web Search", configurable.displayName)
            assertNotNull(configurable.createComponent())
            assertFalse("a page just opened must not report a change", configurable.isModified)
        } finally {
            configurable.disposeUIResources()
        }
    }
}
