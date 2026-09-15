package org.zhavoronkov.openrouter.settings.presets

import org.zhavoronkov.openrouter.models.Preset

/**
 * View-model for the Presets settings page.
 *
 * Pure Kotlin: no Swing, no IntelliJ platform, no logging, so the whole page
 * logic runs in the fast unit tier (FavoriteModelsPageState is the reference).
 * The Swing panel renders from [visiblePresets], [statusText], [emptyState] and
 * the [editor] snapshot, and forwards user actions here.
 *
 * The fetched [presets] list is server-owned and read-only; there is no delete
 * (OpenRouter has no delete endpoint). Editing well-known config keys plus the
 * system prompt and submitting produces a new designated version via the service.
 */
class PresetsPageState {

    enum class EmptyState { NONE, NOT_CONFIGURED, LOAD_FAILED, NO_PRESETS }

    /** Config keys the editor exposes as first-class fields; everything else is preserved verbatim. */
    enum class WellKnownKey(val jsonKey: String) {
        MODEL("model"),
        TEMPERATURE("temperature"),
        TOP_P("top_p"),
        MAX_TOKENS("max_tokens")
    }

    /**
     * A staged edit of one preset's designated version. [passthrough] holds every
     * config key the editor does not surface as a well-known field, so unknown keys
     * survive a read/edit/write round-trip (AC2).
     */
    data class Editor(
        val slug: String,
        val systemPrompt: String?,
        val wellKnown: Map<WellKnownKey, Any?>,
        val passthrough: Map<String, Any?>
    ) {
        /** Recombine well-known + passthrough into the config map the write path sends. */
        fun toConfig(): Map<String, Any?> {
            val out = LinkedHashMap<String, Any?>(passthrough)
            for ((key, value) in wellKnown) {
                if (value != null) out[key.jsonKey] = value else out.remove(key.jsonKey)
            }
            return out
        }
    }

    var onChanged: (() -> Unit)? = null

    var isConfigured: Boolean = true
        set(value) {
            if (field != value) {
                field = value
                fireChanged()
            }
        }

    var loadError: String? = null
        private set

    var presets: List<Preset> = emptyList()
        private set

    var selectedSlug: String? = null
        set(value) {
            if (field != value) {
                field = value
                fireChanged()
            }
        }

    var editor: Editor? = null
        private set

    /** Replace the fetched list (clears any load error and keeps a valid selection). */
    fun setPresets(fetched: List<Preset>) {
        presets = fetched
        loadError = null
        if (selectedSlug != null && fetched.none { it.slug == selectedSlug }) {
            selectedSlug = null
        }
        fireChanged()
    }

    /** Record a fetch failure without discarding the last good list (spec behaviour 2). */
    fun setLoadError(message: String) {
        loadError = message
        fireChanged()
    }

    fun visiblePresets(): List<Preset> = presets

    fun selectedPreset(): Preset? = presets.firstOrNull { it.slug == selectedSlug }

    /** Split a preset's config into well-known fields + verbatim passthrough for editing. */
    fun beginEdit(slug: String) {
        val preset = presets.firstOrNull { it.slug == slug } ?: return
        val config = preset.designatedVersion?.config ?: emptyMap()
        val knownJsonKeys = WellKnownKey.entries.map { it.jsonKey }.toSet()
        val wellKnown = LinkedHashMap<WellKnownKey, Any?>()
        WellKnownKey.entries.forEach { wellKnown[it] = config[it.jsonKey] }
        val passthrough = config.filterKeys { it !in knownJsonKeys }
        editor = Editor(
            slug = slug,
            systemPrompt = preset.designatedVersion?.systemPrompt,
            wellKnown = wellKnown,
            passthrough = passthrough
        )
        selectedSlug = slug
        fireChanged()
    }

    /** Stage a new editor for a brand-new preset slug (create path). */
    fun beginCreate(slug: String) {
        editor = Editor(slug = slug, systemPrompt = null, wellKnown = emptyMap(), passthrough = emptyMap())
        fireChanged()
    }

    fun updateWellKnown(key: WellKnownKey, value: Any?) {
        val current = editor ?: return
        val merged = LinkedHashMap(current.wellKnown)
        merged[key] = value
        editor = current.copy(wellKnown = merged)
        fireChanged()
    }

    fun updateSystemPrompt(prompt: String?) {
        val current = editor ?: return
        editor = current.copy(systemPrompt = prompt?.ifBlank { null })
        fireChanged()
    }

    fun cancelEdit() {
        editor = null
        fireChanged()
    }

    /** True when submitting [editor] targets an existing slug (server adds a new version). */
    fun isNewVersionOfExisting(): Boolean {
        val slug = editor?.slug ?: return false
        return presets.any { it.slug == slug }
    }

    fun statusText(): String {
        loadError?.let { return "Error: " + it }
        if (!isConfigured) return "Add an API key to load your presets"
        val n = presets.size
        return if (n == 1) "1 preset" else n.toString() + " presets"
    }

    fun emptyState(): EmptyState = when {
        !isConfigured -> EmptyState.NOT_CONFIGURED
        loadError != null && presets.isEmpty() -> EmptyState.LOAD_FAILED
        presets.isEmpty() -> EmptyState.NO_PRESETS
        else -> EmptyState.NONE
    }

    private fun fireChanged() { onChanged?.invoke() }
}
