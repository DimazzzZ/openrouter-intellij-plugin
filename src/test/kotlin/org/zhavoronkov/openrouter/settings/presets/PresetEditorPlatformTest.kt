package org.zhavoronkov.openrouter.settings.presets

import com.google.gson.JsonParser
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import org.zhavoronkov.openrouter.models.OutputSchema
import org.zhavoronkov.openrouter.models.ProviderRoutingPreferences
import org.zhavoronkov.openrouter.models.ResponseFormats
import org.zhavoronkov.openrouter.presets.PresetEntry
import java.awt.Component
import java.awt.Container
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.SwingUtilities

/** The preset editor, driven the way the dialog drives it, without a modal dialog. */
class PresetEditorPlatformTest : BasePlatformTestCase() {

    private val schema = OutputSchema("answer", schema = """{"type":"object"}""")
    private var routed: ProviderRoutingPreferences? = null
    private var routingAnswer: ProviderRoutingPreferences? = ProviderRoutingPreferences(only = listOf("azure"))

    private fun editor(
        draft: PresetDraft = PresetDraft.empty("research"),
        isNew: Boolean = true,
        taken: List<String> = emptyList()
    ) = PresetEditor(draft, isNew, taken, listOf(schema)) { current ->
        routed = current
        routingAnswer
    }

    private fun entry(config: String, prompt: String? = null) =
        PresetEntry("research", "Research", prompt, JsonParser.parseString(config).asJsonObject)

    private fun labels(editor: PresetEditor): List<String> =
        UIUtil.findComponentsOfType(editor.component, JLabel::class.java).map { it.text }

    /** Nothing is hidden behind a menu: every setting is on screen, and empty means not set. */
    fun testANewPresetShowsEverySettingEmpty() {
        val editor = editor()

        listOf("Model:", "Web search:", "Output:", "Reasoning:", "Verbosity:", "Provider routing:",
            "Temperature:", "Top P:", "Max tokens:", "System prompt:").forEach {
            assertTrue("$it is shown", it in labels(editor))
        }
        listOf(editor.webSearch, editor.output, editor.reasoning, editor.verbosity).forEach {
            assertEquals(PresetEditor.NOT_SET, it.selectedItem)
        }
        assertEquals(PresetEditor.NOT_SET, editor.routingSummary.text)
        assertFalse(editor.clearRoutingLink.isVisible)
        assertEquals("{}", editor.result().config().toString())
    }

    fun testChoosingAValueSetsItAndNotSetTakesItOut() {
        val editor = editor()

        editor.webSearch.selectedItem = PresetEditor.ALLOWED
        editor.reasoning.selectedItem = "High"
        editor.maxTokens.text = "40"
        editor.model.text = "openai/gpt-5-nano"

        assertEquals(
            JsonParser.parseString(
                """{"model":"openai/gpt-5-nano","reasoning":{"effort":"high"},"max_tokens":40,
                    "tools":[{"type":"openrouter:web_search"}]}"""
            ),
            editor.result().config()
        )

        editor.webSearch.selectedItem = PresetEditor.NOT_SET
        editor.reasoning.selectedItem = PresetEditor.NOT_SET
        editor.maxTokens.text = ""
        editor.model.text = ""

        assertEquals("{}", editor.result().config().toString())
    }

    fun testEverySettingRoundTripsThroughTheFields() {
        val config = """{"model":"openai/gpt-5-nano","tools":[{"type":"openrouter:web_search"}],
            "response_format":{"type":"json_schema","json_schema":{"name":"answer","strict":true,"schema":{"type":"object"}}},
            "reasoning":{"effort":"low"},"verbosity":"high","provider":{"only":["azure"]},
            "temperature":0.2,"top_p":1,"max_tokens":40,"seed":7}"""

        val editor = editor(
            PresetDraft.of(entry(config, prompt = "Be brief.")),
            isNew = false,
            taken = listOf("research")
        )

        assertEquals("openai/gpt-5-nano", editor.model.text)
        assertEquals(PresetEditor.ALLOWED, editor.webSearch.selectedItem)
        assertEquals("answer", editor.output.selectedItem)
        assertEquals("Low", editor.reasoning.selectedItem)
        assertEquals("High", editor.verbosity.selectedItem)
        assertEquals("40", editor.maxTokens.text)
        assertEquals("Be brief.", editor.systemPrompt.text)
        assertNull(editor.problem())
        assertEquals(
            "an unchanged number keeps the JSON it came as",
            JsonParser.parseString(config),
            editor.result().config()
        )
    }

    fun testANumberThatIsNotOneStopsTheSave() {
        val editor = editor()

        editor.maxTokens.text = "forty"

        assertEquals("Max tokens must be a whole number", editor.problem())
        assertEquals("Max tokens must be a whole number", editor.status.text)

        editor.maxTokens.text = "40"
        assertNull(editor.problem())
        assertEquals("40", editor.result().config().get("max_tokens").toString())
    }

    fun testATakenOrMalformedSlugStopsANewPreset() {
        val editor = editor(taken = listOf("research"))

        assertTrue(editor.problem()!!.contains("already exists"))

        editor.slug.text = "web-json"
        assertNull(editor.problem())
        assertEquals("web-json", editor.result().slug)

        editor.slug.text = "Web JSON"
        assertTrue(editor.problem()!!.contains("slug"))
    }

    fun testWebSearchWithPlainJsonIsWarnedInTheStatusLine() {
        val editor = editor()
        editor.webSearch.selectedItem = PresetEditor.ALLOWED

        editor.output.selectedItem = PresetEditor.PLAIN_JSON

        assertEquals(ResponseFormats.WEB_SEARCH_DROPS_JSON, editor.status.text)
        assertNull("a warning does not stop the save", editor.problem())
    }

    fun testTheStatusLineSaysWhatThePresetSets() {
        val editor = editor()

        editor.webSearch.selectedItem = PresetEditor.ALLOWED

        assertEquals("Sets: web search", editor.status.text)
    }

    fun testASavedSchemaThatChangedIsOfferedForUpdate() {
        val old = PresetDraft.empty("research").apply { setSchema(schema.copy(schema = """{"type":"array"}""")) }
        val editor = editor(old, isNew = false, taken = listOf("research"))

        assertTrue(editor.updateSchema.isVisible)
        editor.updateSchema.doClick()

        assertFalse(editor.updateSchema.isVisible)
        val format = editor.result().config().getAsJsonObject("response_format")
        assertEquals("""{"type":"object"}""", format.getAsJsonObject("json_schema").get("schema").toString())
    }

    fun testRoutingIsEditedInItsOwnDialogKeepsWhatTheFormDoesNotShowAndCanBeCleared() {
        val editor = editor(
            PresetDraft.of(entry("""{"provider":{"ignore":["openai"],"max_price":{"prompt":1}}}""")),
            isNew = false,
            taken = listOf("research")
        )

        editor.editRoutingLink.doClick()

        assertEquals(listOf("openai"), routed?.ignore)
        assertEquals(
            """{"max_price":{"prompt":1},"only":["azure"]}""",
            editor.result().config().get("provider").toString()
        )
        assertTrue(editor.routingSummary.text.contains("only azure"))
        assertTrue(editor.clearRoutingLink.isVisible)

        editor.clearRoutingLink.doClick()

        assertFalse(editor.result().config().has("provider"))
        assertEquals(PresetEditor.NOT_SET, editor.routingSummary.text)
    }

    fun testChoosingASavedSchemaAndAVerbositySetsThemAndNotSetTakesThemOut() {
        val editor = editor()

        editor.output.selectedItem = "answer"
        editor.verbosity.selectedItem = "High"

        val config = editor.result().config()
        val schemaName = config.getAsJsonObject("response_format").getAsJsonObject("json_schema")["name"].asString
        assertEquals("answer", schemaName)
        assertEquals("high", config["verbosity"].asString)

        editor.output.selectedItem = PresetEditor.NOT_SET
        editor.verbosity.selectedItem = PresetEditor.NOT_SET
        assertEquals("{}", editor.result().config().toString())
    }

    fun testASchemaThePluginHasNotSavedIsStillShownAsTheOutput() {
        val unsaved = """{"response_format":{"type":"json_schema","json_schema":{"name":"other","schema":{}}}}"""
        val plain = """{"response_format":{"type":"json_object"}}"""

        val editor = editor(PresetDraft.of(entry(unsaved)), isNew = false, taken = listOf("research"))

        assertEquals("other", editor.output.selectedItem)
        val listed = (0 until editor.output.itemCount).map { editor.output.getItemAt(it) }
        assertEquals(listOf(PresetEditor.NOT_SET, PresetEditor.PLAIN_JSON, "answer", "other"), listed)
        val plainEditor = editor(PresetDraft.of(entry(plain)), isNew = false, taken = listOf("research"))
        assertEquals(PresetEditor.PLAIN_JSON, plainEditor.output.selectedItem)
    }

    /** Choosing again the schema the plugin has not saved keeps it as the preset's output. */
    fun testChoosingAgainASchemaThePluginHasNotSavedKeepsIt() {
        val unsaved = """{"response_format":{"type":"json_schema","json_schema":{"name":"other","schema":{}}}}"""
        val editor = editor(PresetDraft.of(entry(unsaved)), isNew = false, taken = listOf("research"))

        editor.output.selectedItem = "other"

        assertEquals(JsonParser.parseString(unsaved), editor.result().config())
    }

    /** Leaving the unsaved schema and choosing it again brings the preset's own schema back, as shown. */
    fun testAnUnsavedSchemaChosenAgainAfterAnotherOutputComesBack() {
        val unsaved = """{"response_format":{"type":"json_schema","json_schema":{"name":"other","schema":{}}}}"""
        val editor = editor(PresetDraft.of(entry(unsaved)), isNew = false, taken = listOf("research"))

        editor.output.selectedItem = PresetEditor.PLAIN_JSON
        editor.output.selectedItem = "other"

        assertEquals(JsonParser.parseString(unsaved), editor.result().config())
    }

    /** A schema output without a name has nothing to list it by; it shows as not set and is kept as it came. */
    fun testASchemaOutputWithoutANameShowsAsNotSet() {
        val unnamed = """{"response_format":{"type":"json_schema","json_schema":{"schema":{}}}}"""

        val editor = editor(PresetDraft.of(entry(unnamed)), isNew = false, taken = listOf("research"))

        assertEquals(PresetEditor.NOT_SET, editor.output.selectedItem)
        assertEquals(JsonParser.parseString(unnamed), editor.result().config())
    }

    /** A `provider` that is not an object is routing nothing can read: the form opens on OpenRouter's defaults. */
    fun testRoutingThatIsNotAnObjectOpensTheFormOnTheDefaults() {
        val editor = editor(PresetDraft.of(entry("""{"provider":"azure"}""")), isNew = false)
        routingAnswer = null

        editor.editRoutingLink.doClick()

        assertEquals(ProviderRoutingPreferences(), routed)
        assertEquals("OpenRouter's default", editor.routingSummary.text)
        assertTrue("it can still be cleared", editor.clearRoutingLink.isVisible)
    }

    fun testRoutingCancelledChangesNothingAndRoutingEmptiedIsTakenOut() {
        val editor = editor(PresetDraft.of(entry("""{"provider":{"only":["azure"]}}""")), isNew = false)

        routingAnswer = null
        editor.editRoutingLink.doClick()
        assertEquals("""{"only":["azure"]}""", editor.result().config().get("provider").toString())

        routingAnswer = ProviderRoutingPreferences()
        editor.editRoutingLink.doClick()
        assertFalse(editor.result().config().has("provider"))
    }

    fun testAPromptOfOnlySpacesSetsNoPrompt() {
        val editor = editor()

        editor.systemPrompt.text = "Be brief."
        assertEquals("Be brief.", editor.result().systemPrompt)
        editor.systemPrompt.text = "   "
        assertNull(editor.result().systemPrompt)
    }

    fun testFractionalNumbersAreAcceptedWhereTheyMayBe() {
        val editor = editor()

        editor.temperature.text = "warm"
        assertEquals("Temperature must be a number", editor.problem())
        editor.temperature.text = "0.5"
        editor.topP.text = "0.9"
        editor.maxTokens.text = "4.5"
        assertEquals("Max tokens must be a whole number", editor.problem())
        editor.maxTokens.text = ""

        assertNull(editor.problem())
        val config = editor.result().config()
        assertEquals("0.5", config["temperature"].toString())
        assertEquals("0.9", config["top_p"].toString())
        assertFalse(config.has("max_tokens"))
    }

    fun testANumberSettingOfAnotherShapeShowsEmptyAndIsDroppedOnSave() {
        val editor = editor(PresetDraft.of(entry("""{"temperature":{"min":0.1}}""")), isNew = false)

        assertEquals("", editor.temperature.text)
        assertFalse(editor.result().config().has("temperature"))
    }

    /** The dialog is a fixed width: nothing may reach past it, or it would scroll sideways. */
    fun testEverySettingFitsTheDialogsWidth() {
        val editor = editor()
        editor.model.text = "anthropic/claude-sonnet-4.5-20250929:thinking:extended-context-and-more"
        editor.systemPrompt.text = "A long prompt ".repeat(40)
        editor.webSearch.selectedItem = PresetEditor.ALLOWED
        editor.editRoutingLink.doClick()
        val width = JBUI.scale(PresetDialog.WIDTH)

        editor.component.setSize(width, editor.component.preferredSize.height)
        layOut(editor.component)

        UIUtil.findComponentsOfType(editor.component, JComponent::class.java)
            .filter { it.isVisible && it !== editor.component }
            .forEach {
                val right = SwingUtilities.convertPoint(it.parent, it.x + it.width, 0, editor.component).x
                assertTrue("${it.javaClass.simpleName} ends at $right in a $width px dialog", right <= width)
            }
    }

    private fun layOut(component: Component) {
        component.doLayout()
        (component as? Container)?.components?.forEach(::layOut)
    }
}
