package org.zhavoronkov.openrouter.settings

import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.UIUtil
import org.zhavoronkov.openrouter.models.Preset
import org.zhavoronkov.openrouter.models.PresetDesignatedVersion
import org.zhavoronkov.openrouter.services.OpenRouterService
import org.zhavoronkov.openrouter.settings.presets.PresetsPageState
import org.zhavoronkov.openrouter.settings.presets.PresetsPageState.EmptyState
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JList
import javax.swing.JTextField

/**
 * Platform test for the Presets settings page. The panel is a thin Swing adapter over
 * [PresetsPageState]; this test builds it with `autoLoad = false` (so no network is touched)
 * and drives the view-model directly, asserting the Swing surface renders from it. The async
 * load/save paths are covered by OpenRouterServicePresetsTest against a mock server.
 */
class PresetsSettingsPanelPlatformTest : BasePlatformTestCase() {

    private lateinit var state: PresetsPageState
    private var panel: PresetsSettingsPanel? = null

    override fun setUp() {
        super.setUp()
        state = PresetsPageState()
    }

    override fun tearDown() {
        try {
            panel?.let { Disposer.dispose(it) }
        } finally {
            super.tearDown()
        }
    }

    private fun createPanel(configured: Boolean = true): JComponent {
        val created = PresetsSettingsPanel(
            serviceProvider = { OpenRouterService.getInstance() },
            isConfigured = { configured },
            state = state,
            autoLoad = false,
        )
        panel = created
        return created.createPanel()
    }

    private fun preset(
        slug: String,
        model: String? = null,
        systemPrompt: String? = null,
        extra: Map<String, Any?> = emptyMap()
    ): Preset {
        val config = LinkedHashMap<String, Any?>()
        if (model != null) config["model"] = model
        config.putAll(extra)
        val version = if (config.isNotEmpty() || systemPrompt != null) {
            PresetDesignatedVersion(version = 1, systemPrompt = systemPrompt, config = config.ifEmpty { null })
        } else {
            null
        }
        return Preset(id = slug, name = slug, slug = slug, designatedVersion = version)
    }

    private fun detailArea(root: JComponent): JBTextArea =
        UIUtil.findComponentsOfType(root, JBTextArea::class.java).first { it.name == "presetDetailArea" }

    private fun presetsList(root: JComponent): JList<*> =
        UIUtil.findComponentOfType(root, JList::class.java) ?: error("presets list not found")

    private fun emptyText(root: JComponent): String =
        (presetsList(root) as JBList<*>).emptyText.text

    /** The editor fields are the enabled/disabled JBTextFields that mirror the view-model editor. */
    private fun editorFields(root: JComponent): List<JTextField> =
        UIUtil.findComponentsOfType(root, JBTextField::class.java)

    fun testWarningBannerAndHiddenListWhenKeyMissing() {
        val root = createPanel(configured = false)

        val openSettings = UIUtil.findComponentsOfType(root, JButton::class.java).find { it.text == "Open Settings" }
        assertNotNull("Open Settings button should be shown without a key", openSettings)
        assertEquals(EmptyState.NOT_CONFIGURED, state.emptyState())
        assertFalse("Nothing is staged, so the page is unmodified", panel!!.isModified())
    }

    fun testListRendersFetchedPresetsAndStatus() {
        val root = createPanel()

        state.setPresets(listOf(preset("email", model = "openai/gpt-4o"), preset("blog")))

        val model = presetsList(root).model
        assertEquals(2, model.size)
        assertEquals("email", (model.getElementAt(0) as Preset).slug)
        assertEquals("2 presets", state.statusText())
    }

    fun testSelectingPresetShowsReadOnlyDetail() {
        val root = createPanel()
        state.setPresets(
            listOf(
                preset(
                    "email",
                    model = "openai/gpt-4o",
                    systemPrompt = "be terse",
                    extra = mapOf("temperature" to 0.3)
                )
            )
        )

        state.beginEdit("email")

        val text = detailArea(root).text
        assertTrue("detail should show slug, was: $text", text.contains("slug: email"))
        assertTrue("detail should show model, was: $text", text.contains("model: openai/gpt-4o"))
        assertTrue("detail should show system prompt, was: $text", text.contains("system_prompt: be terse"))
        assertTrue("detail should list params, was: $text", text.contains("temperature: 0.3"))
    }

    fun testEditorEnablesAndPopulatesWhenEditing() {
        val root = createPanel()
        state.setPresets(listOf(preset("email", model = "openai/gpt-4o", extra = mapOf("temperature" to 0.3))))

        assertTrue("editor fields start disabled", editorFields(root).none { it.isEnabled })

        state.beginEdit("email")

        val enabledFields = editorFields(root).filter { it.isEnabled }
        assertTrue("editing should enable the editor fields", enabledFields.isNotEmpty())
        assertTrue("model field should be populated", enabledFields.any { it.text == "openai/gpt-4o" })
        assertTrue("temperature field should be populated", enabledFields.any { it.text == "0.3" })
        assertTrue("staging an editor marks the page modified", panel!!.isModified())
    }

    fun testCancelEditClearsEditorAndModifiedFlag() {
        val root = createPanel()
        state.setPresets(listOf(preset("email", model = "openai/gpt-4o")))
        state.beginEdit("email")
        assertTrue(panel!!.isModified())

        panel!!.reset()

        assertNull(state.editor)
        assertFalse(panel!!.isModified())
        assertTrue("editor fields disabled after cancel", editorFields(root).none { it.isEnabled })
    }

    fun testLoadErrorPopulatesEmptyStateText() {
        val root = createPanel()

        state.setLoadError("boom")

        assertEquals(EmptyState.LOAD_FAILED, state.emptyState())
        val empty = emptyText(root)
        assertTrue(
            "empty text should reflect a load failure, was: $empty",
            empty.contains("Failed to load", ignoreCase = true)
        )
    }

    fun testNoPresetsShowsEmptyText() {
        val root = createPanel()

        state.setPresets(emptyList())

        assertEquals(EmptyState.NO_PRESETS, state.emptyState())
        val empty = emptyText(root)
        assertTrue("empty text should say no presets, was: $empty", empty.contains("No presets", ignoreCase = true))
    }
}
