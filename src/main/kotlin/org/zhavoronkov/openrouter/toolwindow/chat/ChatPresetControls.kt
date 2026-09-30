package org.zhavoronkov.openrouter.toolwindow.chat

import org.zhavoronkov.openrouter.models.EntryNames
import org.zhavoronkov.openrouter.models.OutputSchema
import org.zhavoronkov.openrouter.models.RequestChoices
import org.zhavoronkov.openrouter.presets.PresetEntry
import org.zhavoronkov.openrouter.settings.presets.PresetDraft
import org.zhavoronkov.openrouter.settings.presets.PresetSetting

/** What the send-parameters controls show: web search, the Output mode, reasoning and verbosity labels. */
data class ChatControls(
    val webSearch: Boolean = false,
    val outputMode: OutputMode = OutputMode.Off,
    val reasoning: String = ChatExchange.UNCHANGED,
    val verbosity: String = ChatExchange.UNCHANGED
)

/**
 * Between a preset and the chat's send-parameters controls, both ways.
 *
 * Picking a pair shows its preset in the controls; the chat sends the pair's id and the controls,
 * and OpenRouter lets a request's field override the preset's, so a control the user changes for
 * one message wins there while unchanged ones send the preset's own values. A preset's schema the
 * plugin has not saved cannot be named in the Output mode control, which then shows Off and sends
 * none - and the preset's own schema applies.
 */
object ChatPresetControls {

    fun of(preset: PresetEntry, schemas: List<OutputSchema>): ChatControls {
        val draft = PresetDraft.of(preset)
        val output = when {
            !draft.has(PresetSetting.OUTPUT) -> OutputMode.Off
            draft.plainJson -> OutputMode.PlainJson
            else ->
                draft.outputSchemaName
                    ?.let { name -> schemas.firstOrNull { EntryNames.same(it.name, name) } }
                    ?.let { OutputMode.Schema(it.name) }
                    ?: OutputMode.Off
        }
        return ChatControls(
            webSearch = draft.has(PresetSetting.WEB_SEARCH),
            outputMode = output,
            reasoning = draft.reasoningLabel ?: ChatExchange.UNCHANGED,
            verbosity = draft.verbosityLabel ?: ChatExchange.UNCHANGED
        )
    }

    /** The controls as a new preset, for "Save as Preset…": a control at its default sets nothing. */
    fun draftOf(options: ChatRequestOptions, schemas: List<OutputSchema>): PresetDraft {
        val draft = PresetDraft.empty("")
        if (options.webSearch) draft.add(PresetSetting.WEB_SEARCH)
        when (val mode = options.outputMode) {
            OutputMode.Off -> Unit
            OutputMode.PlainJson -> draft.add(PresetSetting.OUTPUT)
            is OutputMode.Schema -> schemas.firstOrNull { EntryNames.same(it.name, mode.name) }?.let {
                draft.add(PresetSetting.OUTPUT)
                draft.setSchema(it)
            }
        }
        options.reasoning?.takeIf { RequestChoices.reasoningEffort(it) != null }?.let {
            draft.add(PresetSetting.REASONING)
            draft.reasoningLabel = it
        }
        options.verbosity?.takeIf { RequestChoices.verbosity(it) != null }?.let {
            draft.add(PresetSetting.VERBOSITY)
            draft.verbosityLabel = it
        }
        return draft
    }

    /** Whether [preset]'s config differs from [other]'s: a preset edited since it was applied. */
    fun changed(preset: PresetEntry?, other: PresetEntry?): Boolean =
        preset?.slug != other?.slug || preset?.config != other?.config
}
