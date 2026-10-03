package org.zhavoronkov.openrouter.toolwindow.chat

/**
 * Keeps the send-parameters popup's Output mode control and the composer's Send button in step
 * with what the selected Model can serve.
 *
 * The decisions are [ChatExchange]'s; this only carries them to the two controls, so that a
 * change of Model, of saved schemas, or of the selection itself re-marks the popup and re-decides
 * Send in one place. It holds the [context] the controls were last brought in line with, which is
 * also what a send is checked against and built from.
 */
class OutputModeGate(private val popup: ChatParamsPopup, private val composer: ChatComposer) {

    private var modelContext: OutputModeContext? = null

    /**
     * What the controls were last brought in line with, or null before a Model is known - with
     * the popup's web search switch as it is now, since web search changes what can be served.
     */
    val context: OutputModeContext?
        get() = modelContext?.copy(webSearch = popup.requestOptions().webSearch)

    init {
        popup.onOutputModeChanged = {
            refresh()
            onSelectionChanged()
        }
        popup.onWebSearchChanged = {
            update(modelContext)
            onSelectionChanged()
        }
    }

    /** Set by the caller; invoked after the Output Mode selection changes and Send is re-decided. */
    var onSelectionChanged: () -> Unit = {}

    /** The Model or the saved schemas changed: re-mark the popup and re-decide Send. */
    fun update(context: OutputModeContext?) {
        modelContext = context
        // Unreachable branch: outputModes returns a non-null List, so only a null context falls back to Off
        val choices = this.context?.let(ChatExchange::outputModes) ?: listOf(OutputModeChoice(OutputMode.Off, null))
        popup.setOutputModes(choices)
        refresh()
    }

    /** Why the current selection cannot be sent, or null when it can - or when no Model is known. */
    fun blockedReason(): String? =
        context?.let { ChatExchange.sendBlockedReason(popup.requestOptions().outputMode, it) }

    private fun refresh() {
        composer.setSendBlocked(blockedReason())
    }
}
