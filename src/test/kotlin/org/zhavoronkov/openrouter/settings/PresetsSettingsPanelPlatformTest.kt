package org.zhavoronkov.openrouter.settings

import com.google.gson.JsonParser
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.zhavoronkov.openrouter.presets.PresetCopy
import org.zhavoronkov.openrouter.presets.PresetListing
import org.zhavoronkov.openrouter.presets.PresetVersion
import org.zhavoronkov.openrouter.settings.presets.PresetDraft
import org.zhavoronkov.openrouter.settings.presets.PresetSetting
import java.nio.file.Files

/** The Presets page over a real copy with its OpenRouter reads faked. */
class PresetsSettingsPanelPlatformTest : BasePlatformTestCase() {

    private var listed: List<PresetListing>? = listOf(PresetListing("research", "Research"))
    private val versions = mutableMapOf(
        "research" to PresetVersion(null, JsonParser.parseString("""{"tools":[{"type":"openrouter:web_search"}]}""").asJsonObject)
    )
    private val saved = mutableListOf<PresetDraft>()
    private val mirrored = mutableListOf<List<String>>()
    private var edited: (PresetDraft) -> PresetDraft? = { it }
    private var opened = 0
    private var dialogsOpened = 0
    private val unreadable = mutableListOf<String>()
    private lateinit var copy: PresetCopy

    private fun page(configured: Boolean = true): PresetsSettingsPanel {
        copy = PresetCopy(
            file = Files.createTempDirectory("presets").resolve("presets.json"),
            list = { listed },
            read = { versions[it] },
            scope = CoroutineScope(Dispatchers.Unconfined)
        )
        val page = PresetsSettingsPanel(
            copy = { copy },
            isConfigured = { configured },
            schemas = { emptyList() },
            save = { saved += it; versions[it.slug] = PresetVersion(it.systemPrompt, it.config()); listed = (listed.orEmpty() + PresetListing(it.slug, it.slug)).distinctBy { l -> l.slug }; null },
            edit = { _, draft, _, _, _ -> dialogsOpened++; edited(draft) },
            askSlug = { "web-json" },
            confirmDelete = { true },
            openSite = { opened++ },
            listSlugs = { mirrored += it },
            tellUnreadable = { unreadable += it },
            autoRefresh = false
        )
        Disposer.register(testRootDisposable, page)
        page.createPanel()
        return page
    }

    private fun readNow(page: PresetsSettingsPanel) {
        runBlocking { copy.refresh() }
        page.show()
    }

    private fun waitForSave() {
        PlatformTestUtil.waitWithEventsDispatching(
            "the save did not finish",
            { saved.isNotEmpty() && copy.snapshot()?.find(saved.last().slug) != null },
            5
        )
        PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
    }

    fun testAPageNeverReadSaysItIsReading() {
        val page = page()

        assertEquals(0, page.listModel.size())
        assertEquals("Reading your presets…", page.status.text)
    }

    fun testThePresetsAreListedFromTheCopyAndMirroredForTheProxy() {
        val page = page()

        readNow(page)

        assertEquals(listOf("research"), (0 until page.listModel.size()).map { page.listModel[it].slug })
        assertTrue(page.status.text.startsWith("1 preset, read"))
        assertEquals(listOf("research"), mirrored.last())
    }

    fun testWhenOpenRouterCannotBeReachedTheLastCopyIsShown() {
        val page = page()
        readNow(page)
        listed = null

        runBlocking { copy.refresh() }
        page.show(readFailed = true)

        assertEquals(1, page.listModel.size())
        assertTrue(page.status.text.startsWith("Could not reach OpenRouter"))
    }

    fun testNewSavesThePresetTheDialogDescribesAndReadsTheCopyAgain() {
        val page = page()
        readNow(page)
        edited = { draft -> draft.apply { add(PresetSetting.MAX_TOKENS) } }

        page.createPreset()
        waitForSave()

        assertEquals("web-json", saved.single().slug)
        assertEquals("""{"max_tokens":4096}""", saved.single().config().toString())
        assertEquals(listOf("research", "web-json"), (0 until page.listModel.size()).map { page.listModel[it].slug })
    }

    fun testAPresetWhoseVersionCouldNotBeReadIsNotOpenedSoSavingCannotWipeIt() {
        versions.remove("research")
        val page = page()
        readNow(page)
        page.list.selectedIndex = 0

        page.editSelected()
        PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

        assertEquals(0, dialogsOpened)
        assertTrue(saved.isEmpty())
        assertEquals(listOf("research"), unreadable)
    }

    fun testACancelledDialogSavesNothing() {
        val page = page()
        readNow(page)
        edited = { null }
        page.list.selectedIndex = 0

        page.editSelected()
        PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

        assertTrue(saved.isEmpty())
    }

    fun testDeleteOpensOpenRouterAfterSayingWhy() {
        val page = page()
        readNow(page)
        page.list.selectedIndex = 0

        page.deleteSelected()

        assertEquals(1, opened)
    }

    fun testWithoutAnApiKeyThePageSaysSo() {
        val page = page(configured = false)

        assertTrue(page.status.text.contains("API key"))
    }
}
