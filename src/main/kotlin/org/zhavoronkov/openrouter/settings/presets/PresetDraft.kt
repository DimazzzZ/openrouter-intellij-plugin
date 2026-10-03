package org.zhavoronkov.openrouter.settings.presets

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import org.zhavoronkov.openrouter.models.EntryNames
import org.zhavoronkov.openrouter.models.OutputSchema
import org.zhavoronkov.openrouter.models.RequestChoices
import org.zhavoronkov.openrouter.models.ResponseFormats
import org.zhavoronkov.openrouter.presets.PresetEntry
import org.zhavoronkov.openrouter.utils.asBooleanOrNull
import org.zhavoronkov.openrouter.utils.asObjectOrNull
import org.zhavoronkov.openrouter.utils.asStringOrNull

/** A setting a preset can have, in the order the dialog shows them; [configKey] is its request field. */
enum class PresetSetting(val title: String, val configKey: String?) {
    MODEL("Model", "model"),
    WEB_SEARCH("Web search", null),
    OUTPUT("Output", ResponseFormats.FIELD),
    REASONING("Reasoning", "reasoning"),
    VERBOSITY("Verbosity", "verbosity"),
    PROVIDER_ROUTING("Provider routing", "provider"),
    TEMPERATURE("Temperature", "temperature"),
    TOP_P("Top P", "top_p"),
    MAX_TOKENS("Max tokens", "max_tokens"),
    SYSTEM_PROMPT("System prompt", null)
}

/**
 * One preset as the dialog edits it: only the settings it has, each kept as the JSON OpenRouter
 * sent, so that what the dialog does not touch goes back unchanged. Every config field the dialog
 * does not know, and every tool other than web search, is kept aside and written back as it was.
 */
class PresetDraft private constructor(
    /** The preset's slug; a new preset's changes while its name is typed. */
    var slug: String,
    private val base: JsonObject,
    systemPrompt: String?
) {
    private val values = LinkedHashMap<PresetSetting, JsonElement>()

    init {
        PresetSetting.entries.forEach { setting ->
            setting.configKey?.let { key -> base.get(key)?.takeUnless { it.isJsonNull }?.let { values[setting] = it } }
        }
        webSearchTool(base)?.let { values[PresetSetting.WEB_SEARCH] = it }
        systemPrompt?.takeIf { it.isNotEmpty() }?.let { values[PresetSetting.SYSTEM_PROMPT] = JsonPrimitive(it) }
    }

    /** The settings this preset has, in the dialog's order. */
    val settings: List<PresetSetting> get() = PresetSetting.entries.filter { it in values }

    fun has(setting: PresetSetting): Boolean = setting in values

    operator fun get(setting: PresetSetting): JsonElement? = values[setting]

    /** Adds [setting] with its starting value; a setting it has is left as it is. */
    fun add(setting: PresetSetting) {
        if (setting !in values) values[setting] = defaultOf(setting)
    }

    /** Drops [setting], so the preset no longer sets it. */
    fun remove(setting: PresetSetting) {
        values.remove(setting)
    }

    operator fun set(setting: PresetSetting, value: JsonElement) {
        values[setting] = value
    }

    val systemPrompt: String? get() = values[PresetSetting.SYSTEM_PROMPT]?.asString

    /** The config to save: what the preset kept aside, then every setting it has. */
    fun config(): JsonObject {
        val out = base.deepCopy()
        PresetSetting.entries.mapNotNull { it.configKey }.forEach(out::remove)
        val otherTools = base.getAsJsonArrayOrNull(TOOLS)?.filterNot(::isWebSearch).orEmpty()
        out.remove(TOOLS)
        values.forEach { (setting, value) -> setting.configKey?.let { out.add(it, value.deepCopy()) } }
        val tools = otherTools + listOfNotNull(values[PresetSetting.WEB_SEARCH])
        if (tools.isNotEmpty()) out.add(TOOLS, JsonArray().apply { tools.forEach { add(it.deepCopy()) } })
        return out
    }

    // --- Typed views for the dialog -----------------------------------------------------------

    /** The saved schema's name a `json_schema` output was copied from, or null for plain JSON or none. */
    val outputSchemaName: String?
        get() = output()?.getAsJsonObjectOrNull(ResponseFormats.JSON_SCHEMA)?.get("name")?.asStringOrNull()

    val plainJson: Boolean get() = output()?.get("type")?.asStringOrNull() == ResponseFormats.JSON_OBJECT

    /** Sets the output to plain JSON, replacing a schema. */
    fun setPlainJson() {
        values[PresetSetting.OUTPUT] = JsonObject().apply { addProperty("type", ResponseFormats.JSON_OBJECT) }
    }

    /** Copies [schema]'s body in: OpenRouter cannot refer to the plugin's saved schemas. */
    fun setSchema(schema: OutputSchema) {
        val body = schema.parsedBody() ?: return
        values[PresetSetting.OUTPUT] = JsonObject().apply {
            addProperty("type", ResponseFormats.JSON_SCHEMA)
            add(
                ResponseFormats.JSON_SCHEMA,
                JsonObject().apply {
                    addProperty("name", schema.name)
                    addProperty("strict", schema.strict)
                    add("schema", body)
                }
            )
        }
    }

    /**
     * The saved schema this preset's output was copied from, when that schema has changed since -
     * the dialog offers to copy it again - or null when it has not, or is not saved.
     */
    fun staleSchema(saved: List<OutputSchema>): OutputSchema? {
        val copied = output()?.getAsJsonObjectOrNull(ResponseFormats.JSON_SCHEMA) ?: return null
        val name = copied.get("name")?.asStringOrNull() ?: return null
        val schema = saved.firstOrNull { EntryNames.same(it.name, name) } ?: return null
        val sameStrict = schema.strict == copied.get("strict")?.asBooleanOrNull()
        val same = schema.parsedBody() == copied.get("schema") && sameStrict
        return schema.takeUnless { same }
    }

    /** The reasoning effort's label, or null when it names none the dialog lists. */
    var reasoningLabel: String?
        get() {
            val effort = values[PresetSetting.REASONING]?.asObjectOrNull()?.get("effort")?.asStringOrNull()
            return RequestChoices.REASONING_EFFORTS.entries.firstOrNull { it.value == effort }?.key
        }
        set(label) {
            val effort = RequestChoices.reasoningEffort(label) ?: return
            // Keeps what else the reasoning block says - a token budget, say
            // Unreachable branch: deepCopy never returns null, so only a missing or non-object block starts a new one
            val block = values[PresetSetting.REASONING]?.asObjectOrNull()?.deepCopy() ?: JsonObject()
            block.addProperty("effort", effort)
            values[PresetSetting.REASONING] = block
        }

    var verbosityLabel: String?
        get() {
            val value = values[PresetSetting.VERBOSITY]?.asStringOrNull()
            return RequestChoices.VERBOSITIES.firstOrNull { it.lowercase() == value }
        }
        set(label) {
            RequestChoices.verbosity(label)?.let { values[PresetSetting.VERBOSITY] = JsonPrimitive(it) }
        }

    /** One line saying what the preset sets, for the page's list. */
    val summary: String
        get() = settings.joinToString(", ") { setting ->
            when {
                setting == PresetSetting.OUTPUT && plainJson -> "JSON output"
                // Unreachable branch: the let block returns a non-null String; only a null name says "output format"
                setting == PresetSetting.OUTPUT -> outputSchemaName?.let { "schema $it" } ?: "output format"
                setting == PresetSetting.REASONING -> "reasoning ${reasoningLabel ?: "set"}".lowercase()
                else -> setting.title.lowercase()
            }
        }.ifEmpty { NOTHING_SET }

    /** What OpenRouter will drop from this preset, or null - the chat's rule for web search. */
    val webSearchWarning: String?
        get() {
            val schema = if (has(PresetSetting.OUTPUT)) !plainJson else null
            // Unreachable branch: both WebSearchEffect values and null have an arm, so the added else never runs
            return when (ResponseFormats.webSearchEffect(has(PresetSetting.WEB_SEARCH), schema)) {
                ResponseFormats.WebSearchEffect.DROPS_JSON -> ResponseFormats.WEB_SEARCH_DROPS_JSON
                ResponseFormats.WebSearchEffect.MAY_DROP_SCHEMA -> ResponseFormats.WEB_SEARCH_SCHEMA_WARNING
                null -> null
            }
        }

    /** Why the preset cannot be saved, or null when it can. */
    fun problem(isNew: Boolean, takenSlugs: List<String>): String? = when {
        !SLUG.matches(slug) -> "A slug is lower-case letters, digits and hyphens"
        isNew && takenSlugs.any { it.equals(slug, ignoreCase = true) } -> "A preset named '$slug' already exists"
        // Unreachable branch: has(MODEL) means values holds a MODEL entry, and values holds no nulls
        has(PresetSetting.MODEL) && values[PresetSetting.MODEL]?.asStringOrNull().isNullOrBlank() ->
            "Enter a model, or remove the Model setting"
        else -> null
    }

    private fun output(): JsonObject? = values[PresetSetting.OUTPUT]?.asObjectOrNull()

    companion object {
        private const val TOOLS = "tools"
        const val NOTHING_SET = "nothing yet"
        private val SLUG = Regex("[a-z0-9]+(-[a-z0-9]+)*")

        fun of(entry: PresetEntry): PresetDraft = PresetDraft(
            entry.slug,
            entry.config ?: JsonObject(),
            entry.systemPrompt
        )

        fun empty(slug: String): PresetDraft = PresetDraft(slug, JsonObject(), null)

        /** [base]'s web search tool, parameters and all, or null when it has none. */
        private fun webSearchTool(base: JsonObject): JsonElement? =
            base.getAsJsonArrayOrNull(TOOLS)?.firstOrNull(::isWebSearch)

        private fun isWebSearch(tool: JsonElement): Boolean = ResponseFormats.isWebSearchTool(tool)

        private fun defaultOf(setting: PresetSetting): JsonElement = when (setting) {
            PresetSetting.MODEL -> JsonPrimitive("")
            PresetSetting.WEB_SEARCH -> JsonObject().apply { addProperty("type", ResponseFormats.WEB_SEARCH_TOOL) }
            PresetSetting.OUTPUT -> JsonObject().apply { addProperty("type", ResponseFormats.JSON_OBJECT) }
            PresetSetting.REASONING -> JsonObject().apply { addProperty("effort", "medium") }
            PresetSetting.VERBOSITY -> JsonPrimitive("medium")
            PresetSetting.PROVIDER_ROUTING -> JsonObject()
            PresetSetting.TEMPERATURE -> JsonPrimitive(DEFAULT_TEMPERATURE)
            PresetSetting.TOP_P -> JsonPrimitive(1)
            PresetSetting.MAX_TOKENS -> JsonPrimitive(DEFAULT_MAX_TOKENS)
            PresetSetting.SYSTEM_PROMPT -> JsonPrimitive("")
        }

        private const val DEFAULT_TEMPERATURE = 0.7
        private const val DEFAULT_MAX_TOKENS = 4096

        private fun JsonObject.getAsJsonObjectOrNull(key: String): JsonObject? = get(key)?.asObjectOrNull()

        private fun JsonObject.getAsJsonArrayOrNull(key: String): JsonArray? =
            get(key)?.takeIf { it.isJsonArray }?.asJsonArray
    }
}
