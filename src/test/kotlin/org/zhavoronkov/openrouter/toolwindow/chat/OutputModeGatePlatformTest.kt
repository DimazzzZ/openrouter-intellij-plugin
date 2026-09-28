package org.zhavoronkov.openrouter.toolwindow.chat

import com.intellij.openapi.ui.ComboBox
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.ui.components.JBCheckBox
import org.zhavoronkov.openrouter.models.OutputSchema

/**
 * The gate between the Output mode control and Send, driven with a real popup and a real composer
 * the way the chat drives them: a change of Model or of saved schemas, or a change of selection,
 * must re-mark the popup and disable or re-enable Send together.
 */
class OutputModeGatePlatformTest : BasePlatformTestCase() {

    private lateinit var popup: ChatParamsPopup
    private lateinit var composer: ChatComposer
    private lateinit var gate: OutputModeGate

    private val features = OutputSchema("features", schema = """{"type":"object"}""")

    override fun setUp() {
        super.setUp()
        popup = ChatParamsPopup(ComboBox(arrayOf("a")), ComboBox(arrayOf("b")), ComboBox(arrayOf("c")), JBCheckBox())
        composer = ChatComposer().apply { attachModelCombo(ComboBox(arrayOf("m"))) }
        gate = OutputModeGate(popup, composer)
    }

    private fun capable(schemas: List<OutputSchema> = listOf(features)) =
        OutputModeContext("capable/model", listOf("response_format", "structured_outputs"), schemas)

    private val incapable = OutputModeContext("plain/model", listOf("tools"), listOf(features))

    fun testSendIsDisabledWhenTheModelChangesUnderASelectionAndReEnabledOnceResolved() {
        gate.update(capable())
        popup.outputMode.selectedItem = OutputMode.PlainJson
        assertTrue("a servable selection must leave Send enabled", composer.canSend)

        gate.update(incapable)
        assertFalse("switching to a Model that cannot serve it must disable Send", composer.canSend)
        assertEquals(
            "plain/model does not support JSON output. Choose another output mode to send.",
            gate.blockedReason()
        )

        popup.outputMode.selectedItem = OutputMode.Off
        assertTrue("choosing a mode the Model can serve must re-enable Send", composer.canSend)
    }

    fun testChoosingAModelThatServesTheSelectionReEnablesSend() {
        gate.update(capable())
        popup.outputMode.selectedItem = OutputMode.Schema("features")
        gate.update(incapable)
        assertFalse(composer.canSend)

        gate.update(capable())

        assertTrue("a Model that can serve the kept selection must re-enable Send", composer.canSend)
        assertEquals(OutputMode.Schema("features"), popup.requestOptions().outputMode)
    }

    fun testDeletingTheSelectedSchemaDisablesSend() {
        gate.update(capable())
        popup.outputMode.selectedItem = OutputMode.Schema("features")

        gate.update(capable(schemas = emptyList()))

        assertFalse(composer.canSend)
        assertEquals("features is no longer available. Choose another output mode to send.", gate.blockedReason())
    }

    fun testAChangeOfSelectionIsReported() {
        var reported = 0
        gate.onSelectionChanged = { reported++ }
        gate.update(capable())

        popup.outputMode.selectedItem = OutputMode.PlainJson

        assertTrue("the caller refreshes the gear badge from this", reported > 0)
    }

    fun testWithNoModelKnownNothingIsBlocked() {
        gate.update(null)

        assertNull(gate.blockedReason())
        assertTrue(composer.canSend)
    }
}
