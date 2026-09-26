package org.zhavoronkov.openrouter.proxy.routing

/**
 * Declarative table of OpenRouter routers supported as first-class citizens
 * by this plugin. A "router" is an openrouter-namespaced model slug that
 * OpenRouter itself resolves to an underlying model at request time.
 *
 * Every downstream feature — the plugins-injector, the chat UI parameter
 * panel, settings persistence — reads from this table and must not carry
 * router-specific branches. To add a router, append a [RouterDefinition]
 * here; nothing else needs to know its name.
 *
 * V1 targets (with a tunable parameter):
 *  - openrouter/auto        (auto-router, cost_tier)
 *  - openrouter/fusion      (fusion, preset — editable)
 *  - openrouter/pareto-code (pareto-router, min_coding_score)
 *
 * V1 also lists two parameter-less router slugs so the UI treats them as
 * routers rather than ordinary models:
 *  - openrouter/fusion-flash (a fast entry point into Fusion)
 *  - openrouter/free         (routes to free-tier models)
 *
 * Beta slugs (`openrouter/auto-beta`, `openrouter/bodybuilder`) are
 * intentionally out of scope for v1.
 */
object RouterCatalog {

    val all: List<RouterDefinition> = listOf(
        RouterDefinition(
            modelSlug = "openrouter/auto",
            displayName = "Auto Router",
            pluginId = "auto-router",
            param = RouterParam.Enum(
                key = "cost_tier",
                values = listOf("low", "medium", "high", "xhigh", "max"),
                editable = false
            )
        ),
        RouterDefinition(
            modelSlug = "openrouter/fusion",
            displayName = "Fusion",
            pluginId = "fusion",
            param = RouterParam.Enum(
                key = "preset",
                values = listOf("general-fast"), // suggestion only; editable
                editable = true,
                // The API key stays `preset` (OpenRouter's), but the UI label
                // reads "Fusion preset" so users don't mistake it for one of
                // their saved @preset/ server presets. See RouterParam.label.
                labelOverride = "Fusion preset"
            )
        ),
        RouterDefinition(
            modelSlug = "openrouter/pareto-code",
            displayName = "Pareto (code)",
            pluginId = "pareto-router",
            param = RouterParam.FloatRange(
                key = "min_coding_score",
                min = 0.0,
                max = 1.0
            )
        ),
        RouterDefinition(
            modelSlug = "openrouter/fusion-flash",
            displayName = "Fusion Flash",
            pluginId = null,
            param = null
        ),
        RouterDefinition(
            modelSlug = "openrouter/free",
            displayName = "Free Models",
            pluginId = null,
            param = null
        )
    )

    private val bySlug: Map<String, RouterDefinition> = all.associateBy { it.modelSlug }

    fun isRouter(modelSlug: String): Boolean = bySlug.containsKey(modelSlug)

    fun find(modelSlug: String): RouterDefinition? = bySlug[modelSlug]

    /** Every router model slug, in catalog order — the seed for the chat model list and the proxy /v1/models listing. */
    val slugs: List<String> get() = all.map { it.modelSlug }
}

/**
 * One row of [RouterCatalog].
 *
 * @param modelSlug the value that goes into `request.model`
 * @param displayName human-readable label for the chat model list
 * @param pluginId the OpenRouter plugin id to emit in `plugins[].id`; null
 *  when the router accepts no parameters and needs no plugin block
 * @param param the single tunable parameter, or null when there is none
 */
data class RouterDefinition(
    val modelSlug: String,
    val displayName: String,
    val pluginId: String?,
    val param: RouterParam?
) {
    init {
        // A router either takes a parameter (and needs a plugin id to carry
        // it) or takes none (and emits no plugin block). Mixing the two would
        // let RouterRequestBuilder silently swallow a param, so forbid it at
        // construction: the catalog is the single source of truth (see class
        // doc) and every row must uphold this invariant.
        require((pluginId == null) == (param == null)) {
            "RouterDefinition '$modelSlug': pluginId and param must both be null or both non-null " +
                "(pluginId=$pluginId, param=$param)"
        }
    }
}

/**
 * A router parameter is one of three shapes:
 *   - Enum with a closed list of allowed values (Auto's cost_tier),
 *   - FloatRange with min/max (Pareto's min_coding_score),
 *   - Enum with editable=true, where [values] are suggestions and any
 *     string is accepted (Fusion's preset).
 */
sealed class RouterParam {
    abstract val key: String

    /**
     * Optional explicit UI label. When set it wins over the [key]-derived
     * label — used by Fusion so the control reads "Fusion preset" instead of
     * the bare API key "Preset", making clear it is a Fusion routing profile
     * and not one of the user's saved @preset/ server presets.
     */
    open val labelOverride: String? get() = null

    /**
     * Human label for the UI control: [labelOverride] when present, otherwise
     * derived from [key] (e.g. "cost_tier" -> "Cost tier").
     */
    val label: String
        get() = labelOverride ?: key.replace('_', ' ').replaceFirstChar { it.uppercase() }

    /** Whether the UI control accepts free-typed text (editable enums, ranges). */
    abstract val freeText: Boolean

    /** Dropdown suggestions; a leading blank entry means "no selection". */
    abstract val suggestions: List<String>

    /** Tooltip describing the accepted values. */
    abstract val description: String

    data class Enum(
        override val key: String,
        val values: List<String>,
        val editable: Boolean = false,
        override val labelOverride: String? = null
    ) : RouterParam() {
        override val freeText: Boolean get() = editable
        override val suggestions: List<String> get() = listOf("") + values
        override val description: String
            get() = if (editable) {
                "Suggested: ${values.joinToString(", ")} (or type your own)"
            } else {
                "One of: ${values.joinToString(", ")}"
            }
    }

    data class FloatRange(
        override val key: String,
        val min: Double,
        val max: Double
    ) : RouterParam() {
        override val freeText: Boolean get() = true
        override val suggestions: List<String> get() = listOf("", "$min", "$max")
        override val description: String get() = "A number from $min to $max"
    }
}
