package org.zhavoronkov.openrouter.models

/**
 * A pair: a model and the OpenRouter preset it is sent with, written as one model id in
 * OpenRouter's own syntax - `<model>@preset/<slug>`, for example `openai/gpt-5.2@preset/research`.
 * OpenRouter applies the preset to the model the id names; the request's model wins over one the
 * preset sets.
 *
 * This is the one place that knows the syntax; everything else asks it. [model] is any id the
 * catalogue or the plugin knows - a Latest Model and a variant included, since the suffix comes
 * after both. A whole preset, `@preset/<slug>` with no model in front, is not a pair: it is a
 * model of its own, and keeps its own handling. A Consumer that splits an id on its first `/`
 * still sees the model's author.
 */
data class PresetPair(val model: String, val preset: String) {

    /** The pair as a model id, the form it is stored in and a Consumer asks for. */
    val id: String get() = model + SEPARATOR + preset

    companion object {
        const val SEPARATOR = "@preset/"

        /**
         * The pair [id] names, or null when it is an ordinary model id or a whole preset. Split at
         * the first [SEPARATOR], so a model part never holds one; the slug must be spelled by the
         * entry name rule, with no surrounding space, since the id is matched as it is sent.
         */
        fun parse(id: String): PresetPair? {
            val at = id.indexOf(SEPARATOR)
            if (at <= 0) return null
            val model = id.substring(0, at)
            val preset = id.substring(at + SEPARATOR.length)
            if (model.isBlank() || preset != preset.trim() || !EntryNames.isWellFormed(preset)) return null
            return PresetPair(model, preset)
        }

        /** Whether [id] is a pair rather than a model. */
        fun isPair(id: String): Boolean = parse(id) != null

        /** The model [id] sends: a pair's model, or [id] itself when it is not a pair. */
        fun modelOf(id: String): String = parse(id)?.model ?: id
    }
}
