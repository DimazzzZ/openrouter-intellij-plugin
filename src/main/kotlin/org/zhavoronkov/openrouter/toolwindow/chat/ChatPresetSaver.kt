package org.zhavoronkov.openrouter.toolwindow.chat

import org.zhavoronkov.openrouter.settings.presets.PresetDraft

/**
 * "Save as Preset…": which preset the send-parameters controls are saved as. The dialog takes any
 * slug, since OpenRouter gives an existing one a new version; that replaces the preset's settings,
 * so it happens only once the user confirms.
 */
class ChatPresetSaver(
    /** Opens the preset dialog on [draft], refusing [takenSlugs]; null when it was cancelled. */
    private val edit: (draft: PresetDraft, takenSlugs: List<String>) -> PresetDraft?,
    /** Asks whether the preset [slug] names may be replaced by a new version. */
    private val confirmReplace: (slug: String) -> Boolean
) {

    /** The preset to save from [draft], or null when nothing is to be saved; [taken] are the slugs that exist. */
    fun choose(draft: PresetDraft, taken: List<String>): PresetDraft? {
        val edited = edit(draft, emptyList()) ?: return null
        val exists = taken.any { it.equals(edited.slug, ignoreCase = true) }
        return edited.takeIf { !exists || confirmReplace(edited.slug) }
    }
}
