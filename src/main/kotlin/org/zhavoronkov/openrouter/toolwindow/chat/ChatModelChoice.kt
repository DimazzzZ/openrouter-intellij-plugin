package org.zhavoronkov.openrouter.toolwindow.chat

import org.zhavoronkov.openrouter.models.PresetPair
import org.zhavoronkov.openrouter.presets.PresetEntry
import org.zhavoronkov.openrouter.proxy.pairs.PairAvailability
import org.zhavoronkov.openrouter.proxy.pairs.PairProblem
import org.zhavoronkov.openrouter.settings.presets.PresetDraft
import org.zhavoronkov.openrouter.settings.presets.PresetSetting

/**
 * What the chat's model picker holds: [picked] as listed, which is also what a request is sent
 * to - a pair goes as it is and OpenRouter applies its preset - and [model], the model a pair
 * names, which the send-parameters controls are judged by. For a pair, [preset] is its preset as
 * the plugin last read it, and [problem] what stops sending it, decided as for a Consumer.
 */
data class ChatModelChoice(
    val picked: String,
    val model: String = PresetPair.modelOf(picked),
    val preset: PresetEntry? = null,
    val problem: PairProblem? = null
) {
    /**
     * A preset's routing - its provider block or its fallback models, as the proxy counts them -
     * replaces every routing default, the router's parameter included.
     */
    val presetRouting: Boolean get() = preset?.config?.let { it.has("provider") || it.has("models") } == true

    /** Whether the preset offers web search: the switch sends no tool, so it cannot take it away. */
    val presetSearches: Boolean get() = preset?.let { PresetDraft.of(it).has(PresetSetting.WEB_SEARCH) } == true

    companion object {
        fun of(picked: String, pairs: PairAvailability): ChatModelChoice {
            val pair = PresetPair.parse(picked) ?: return ChatModelChoice(picked)
            return ChatModelChoice(picked, pair.model, pairs.preset(pair), pairs.problem(pair))
        }
    }
}
