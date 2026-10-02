package org.zhavoronkov.openrouter.toolwindow.chat

import org.zhavoronkov.openrouter.models.OutputSchema
import org.zhavoronkov.openrouter.presets.PresetEntry

/**
 * Keeps the send-parameters controls in line with the picked model: a pair's preset fills them,
 * and leaving a pair for a plain model puts them back to their defaults, so nothing of the preset
 * goes out with a model it was not picked for. The preset already applied is left alone, and with
 * it whatever the user changed since; one edited since it was applied fills them again.
 */
class ChatPresetFollower(
    /** Shows these controls in the send-parameters popup. */
    private val apply: (ChatControls) -> Unit,
    /** Puts the send-parameters popup back to its defaults. */
    private val reset: () -> Unit,
    /** The saved Output Schemas, read each time a preset is applied. */
    private val schemas: () -> List<OutputSchema>
) {

    /** The preset last applied to the controls, to tell a changed one - or a pair left - from none. */
    private var applied: PresetEntry? = null

    /**
     * Brings the controls in line with [preset], the picked pair's, or null for a plain model.
     * Answers whether it changed them.
     */
    fun follow(preset: PresetEntry?): Boolean {
        if (!ChatPresetControls.changed(preset, applied)) return false
        if (preset != null) apply(ChatPresetControls.of(preset, schemas())) else reset()
        applied = preset
        return true
    }
}
