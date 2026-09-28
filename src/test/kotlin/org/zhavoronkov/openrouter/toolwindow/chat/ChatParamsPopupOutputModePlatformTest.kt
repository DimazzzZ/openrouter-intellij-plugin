package org.zhavoronkov.openrouter.toolwindow.chat

import com.intellij.openapi.ui.ComboBox
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.ui.components.JBCheckBox
import com.intellij.util.ui.UIUtil
import org.zhavoronkov.openrouter.models.OutputSchema
import java.awt.Container
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JList

/**
 * The Output Mode control in the send-parameters popup. What it offers is decided by
 * [ChatExchange.outputModes]; these tests drive it with that answer and check what a user can
 * pick, what they are told, and what reaches the request.
 */
class ChatParamsPopupOutputModePlatformTest : BasePlatformTestCase() {

    private fun modes(model: String, declared: List<String>?, schemas: List<OutputSchema> = emptyList()) =
        ChatExchange.outputModes(OutputModeContext(model, declared, schemas))

    private lateinit var popup: ChatParamsPopup

    private val jsonCapable = modes("capable/model", listOf("response_format"))
    private val notJsonCapable = modes("plain/model", listOf("tools"))

    override fun setUp() {
        super.setUp()
        popup = ChatParamsPopup(ComboBox(arrayOf("a")), ComboBox(arrayOf("b")), ComboBox(arrayOf("c")), JBCheckBox())
    }

    private fun buildForm(): JComponent {
        val method = ChatParamsPopup::class.java.getDeclaredMethod("buildForm")
        method.isAccessible = true
        return method.invoke(popup) as JComponent
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

    private fun entries(): List<OutputMode> =
        (0 until popup.outputMode.itemCount).map { popup.outputMode.getItemAt(it) }

    /** The entry as the open drop-down draws it. */
    private fun renderedEntry(index: Int): JLabel {
        val renderer = popup.outputMode.renderer
        return renderer.getListCellRendererComponent(JList(), entries()[index], index, false, false) as JLabel
    }

    fun testTheFormOffersOffAndPlainJson() {
        popup.setOutputModes(jsonCapable)

        val texts = labels(buildForm())
        assertTrue("expected an 'Output mode:' row, got $texts", texts.contains("Output mode:"))
        assertEquals(listOf(OutputMode.Off, OutputMode.PlainJson), entries())
        assertEquals(OutputMode.Off, popup.requestOptions().outputMode)
    }

    fun testChoosingPlainJsonReachesTheRequest() {
        popup.setOutputModes(jsonCapable)

        popup.outputMode.selectedItem = OutputMode.PlainJson

        assertEquals(OutputMode.PlainJson, popup.requestOptions().outputMode)
        assertEquals("", popup.outputComment.text)
    }

    fun testAnEntryTheModelCannotServeIsGreyedOutExplainedAndCannotBePicked() {
        popup.setOutputModes(notJsonCapable)

        popup.outputMode.selectedItem = OutputMode.PlainJson

        assertEquals("an unsupported entry must not be pickable", OutputMode.Off, popup.outputMode.selectedItem)
        assertEquals(UIUtil.getLabelDisabledForeground(), renderedEntry(1).foreground)
        assertEquals("plain/model does not support JSON output", renderedEntry(1).toolTipText)
        assertEquals(ChatParamsPopup.OUTPUT_UNAVAILABLE_TEXT, popup.outputComment.text)
    }

    /**
     * A selection made for one Model and then carried to a Model that cannot serve it is kept and
     * marked. Resetting it would silently send a reply the user thinks is constrained when it is not.
     */
    fun testASelectionTheNewModelCannotServeIsKeptAndMarked() {
        popup.setOutputModes(jsonCapable)
        popup.outputMode.selectedItem = OutputMode.PlainJson

        popup.setOutputModes(notJsonCapable)

        assertEquals(OutputMode.PlainJson, popup.outputMode.selectedItem)
        assertEquals(OutputMode.PlainJson, popup.requestOptions().outputMode)
        assertEquals(ChatParamsPopup.OUTPUT_BLOCKED_TEXT, popup.outputComment.text)
        assertEquals(CHAT_WARNING_FOREGROUND, popup.outputComment.foreground)
    }

    fun testChoosingASupportedModeResolvesTheMark() {
        var changes = 0
        popup.onOutputModeChanged = { changes++ }
        popup.setOutputModes(jsonCapable)
        popup.outputMode.selectedItem = OutputMode.PlainJson
        popup.setOutputModes(notJsonCapable)

        popup.outputMode.selectedItem = OutputMode.Off

        assertEquals(OutputMode.Off, popup.requestOptions().outputMode)
        assertEquals(ChatParamsPopup.OUTPUT_UNAVAILABLE_TEXT, popup.outputComment.text)
        assertTrue("the caller must hear about every change so it can re-check sending", changes >= 2)
    }

    fun testAChosenOutputModeShowsOnTheGearBadge() {
        popup.setOutputModes(jsonCapable)
        assertFalse(popup.hasNonDefaultSelection())

        popup.outputMode.selectedItem = OutputMode.PlainJson

        assertTrue(popup.hasNonDefaultSelection())
        assertEquals("Output mode: JSON (no schema)", popup.activeSummary())
    }

    // --- Saved Output Schemas ------------------------------------------------

    private val features = OutputSchema("features", strict = true, schema = """{"type":"object"}""")
    private val summary = OutputSchema("summary", strict = false, schema = """{"type":"object"}""")
    private val saved = listOf(features, summary)

    private fun supported(): List<Boolean> =
        entries().indices.map { renderedEntry(it).foreground != UIUtil.getLabelDisabledForeground() }

    fun testEverySavedSchemaIsListedByNameAfterOffAndPlainJson() {
        popup.setOutputModes(modes("m", listOf("response_format", "structured_outputs"), saved))

        assertEquals(
            listOf(OutputMode.Off, OutputMode.PlainJson, OutputMode.Schema("features"), OutputMode.Schema("summary")),
            entries()
        )
        val labels = entries().indices.map { renderedEntry(it).text }
        assertEquals(listOf("Off", "JSON (no schema)", "features", "summary"), labels)
    }

    /** The case a single shared gate gets wrong: schemas yes, plain JSON no. */
    fun testAModelDeclaringOnlySchemasOffersTheSchemasAndDisablesPlainJson() {
        popup.setOutputModes(modes("m", listOf("structured_outputs"), saved))

        assertEquals(listOf(true, false, true, true), supported())
        popup.outputMode.selectedItem = OutputMode.Schema("summary")
        assertEquals(OutputMode.Schema("summary"), popup.requestOptions().outputMode)
    }

    fun testAModelDeclaringOnlyPlainJsonDisablesTheSchemas() {
        popup.setOutputModes(modes("m", listOf("response_format"), saved))

        assertEquals(listOf(true, true, false, false), supported())
        popup.outputMode.selectedItem = OutputMode.Schema("features")
        assertEquals("a schema must not be pickable here", OutputMode.Off, popup.requestOptions().outputMode)
    }

    fun testAModelDeclaringNeitherLeavesOnlyOff() {
        popup.setOutputModes(modes("m", listOf("tools"), saved))

        assertEquals(listOf(true, false, false, false), supported())
    }

    fun testSwitchingToAModelThatCannotTakeTheSelectedSchemaKeepsAndMarksIt() {
        popup.setOutputModes(modes("m", listOf("structured_outputs"), saved))
        popup.outputMode.selectedItem = OutputMode.Schema("features")

        popup.setOutputModes(modes("other", listOf("response_format"), saved))

        assertEquals(OutputMode.Schema("features"), popup.requestOptions().outputMode)
        assertEquals(ChatParamsPopup.OUTPUT_BLOCKED_TEXT, popup.outputComment.text)
    }

    /**
     * Deleting the selected schema on the settings page must leave something the user can see and
     * resolve: the entry stays, marked, until another mode is chosen - and once it is, it is gone.
     */
    fun testDeletingTheSelectedSchemaLeavesItMarkedUntilResolved() {
        popup.setOutputModes(modes("m", listOf("structured_outputs"), saved))
        popup.outputMode.selectedItem = OutputMode.Schema("features")

        popup.setOutputModes(modes("m", listOf("structured_outputs"), listOf(summary)))

        assertEquals(OutputMode.Schema("features"), popup.requestOptions().outputMode)
        assertTrue("the deleted schema stays listed so it can be seen", OutputMode.Schema("features") in entries())
        assertEquals(ChatParamsPopup.OUTPUT_BLOCKED_TEXT, popup.outputComment.text)

        popup.outputMode.selectedItem = OutputMode.Schema("summary")
        popup.setOutputModes(modes("m", listOf("structured_outputs"), listOf(summary)))
        assertFalse(
            "once resolved, the deleted schema is no longer offered",
            OutputMode.Schema("features") in entries()
        )
    }
}
