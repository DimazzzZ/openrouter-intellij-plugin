package org.zhavoronkov.openrouter.toolwindow.chat

import com.intellij.openapi.ui.ComboBox
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.ui.components.JBCheckBox
import org.zhavoronkov.openrouter.models.OutputSchema

/** Picking a pair fills the send-parameters controls from its preset; "Save as Preset…" goes the other way. */
class ChatParamsPopupPresetPlatformTest : BasePlatformTestCase() {

    private val reasoning = ComboBox(ChatExchange.REASONING_CHOICES.toTypedArray())
    private val verbosity = ComboBox(ChatExchange.VERBOSITY_CHOICES.toTypedArray())
    private val webSearch = JBCheckBox()
    private lateinit var popup: ChatParamsPopup

    override fun setUp() {
        super.setUp()
        popup = ChatParamsPopup(reasoning, verbosity, ComboBox(arrayOf("")), webSearch)
        popup.setOutputModes(
            ChatExchange.outputModes(
                OutputModeContext(
                    "m",
                    listOf("response_format", "structured_outputs"),
                    listOf(OutputSchema("answer", schema = """{"type":"object"}"""))
                )
            )
        )
    }

    fun testAPairsPresetSetsEveryControl() {
        popup.applyControls(
            ChatControls(
                webSearch = true,
                outputMode = OutputMode.Schema("answer"),
                reasoning = "High",
                verbosity = "Low"
            )
        )

        val options = popup.requestOptions()
        assertEquals("High", options.reasoning)
        assertEquals("Low", options.verbosity)
        assertTrue(options.webSearch)
        assertEquals(OutputMode.Schema("answer"), options.outputMode)
    }

    /** What a pair sends is its preset, not whatever an earlier message left in a control. */
    fun testASettingThePresetLeavesUnsetGoesBackToItsDefault() {
        reasoning.selectedItem = "High"
        webSearch.isSelected = true
        popup.outputMode.selectedItem = OutputMode.PlainJson

        popup.applyControls(ChatControls(verbosity = "Max"))

        assertEquals(
            ChatRequestOptions(reasoning = "Default", verbosity = "Max", routerParam = "", webSearch = false),
            popup.requestOptions()
        )
    }

    /** Never sent with an output its model does not declare: the mode is kept, marked, and blocks sending. */
    fun testAPresetOutputTheModelCannotGiveIsKeptAndBlocksSending() {
        val context = OutputModeContext("plain/model", listOf("tools"), emptyList())
        popup.setOutputModes(ChatExchange.outputModes(context))

        popup.applyControls(ChatControls(outputMode = OutputMode.PlainJson))

        assertEquals(OutputMode.PlainJson, popup.requestOptions().outputMode)
        assertNotNull(ChatExchange.sendBlockedReason(popup.requestOptions().outputMode, context))
        assertFalse("the comment says why it is blocked", popup.outputComment.text.isNullOrBlank())
    }

    fun testLeavingAPairPutsEveryControlBackToItsDefault() {
        popup.applyControls(ChatControls(webSearch = true, outputMode = OutputMode.PlainJson, reasoning = "High"))

        popup.resetControls()

        val defaults = ChatRequestOptions(reasoning = "Default", verbosity = "Default", routerParam = "")
        assertEquals(defaults, popup.requestOptions())
    }

    fun testAControlChangedAfterPickingAPairIsWhatTheNextMessageSends() {
        popup.applyControls(ChatControls(reasoning = "High"))

        reasoning.selectedItem = "Low"

        assertEquals("Low", popup.requestOptions().reasoning)
    }

    fun testSaveAsPresetHandsOverToTheCaller() {
        var saved = false
        popup.onSaveAsPreset = { saved = true }

        popup.saveAsPreset.doClick()

        assertTrue("the link hands over to the caller", saved)
    }
}
