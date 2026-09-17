package org.zhavoronkov.openrouter.proxy.routing

import org.zhavoronkov.openrouter.models.PluginConfig

/**
 * Pure seam between the chat UI and a typed ChatCompletionRequest: turns a
 * model slug plus the user's raw param value (a String, or null when the
 * user hasn't chosen one) into the plugins list to attach.
 *
 * All router knowledge is read from [RouterCatalog]; there is no per-router
 * branch here. To support a new router, add a row to the catalog.
 *
 * Returns null (attach nothing) when:
 *  - the slug is not a router,
 *  - the router takes no parameter,
 *  - the router takes a parameter but the user chose no valid value
 *    (an unset control, an out-of-range float, or a value outside a closed
 *     enum). In every "no valid value" case we omit the plugin block so
 *    OpenRouter applies its own default rather than a rejected value.
 */
object RouterRequestBuilder {

    fun buildPlugins(modelSlug: String, rawValue: String?): List<PluginConfig>? {
        val def = RouterCatalog.find(modelSlug) ?: return null
        val param = def.param ?: return null
        val pluginId = def.pluginId ?: return null

        val coerced = coerce(param, rawValue) ?: return null
        return listOf(PluginConfig(id = pluginId, params = mapOf(param.key to coerced)))
    }

    /**
     * The sub-label to show under a router's reply, echoing which underlying
     * model OpenRouter resolved the router to. Returns null when there is
     * nothing useful to show: the requested model was not a router, the
     * response omitted a model, or the response merely echoed the router slug.
     */
    fun resolvedModelLabel(requestedModel: String, responseModel: String?): String? {
        if (!RouterCatalog.isRouter(requestedModel)) return null
        val resolved = responseModel?.takeIf { it.isNotBlank() } ?: return null
        if (resolved == requestedModel) return null
        return "Routed to $resolved"
    }

    /**
     * Decides how the chat UI should update its router-param control when it
     * recomputes state for [selectedModel], given the param key the control
     * currently reflects ([shownParamKey], null when hidden).
     *
     * The recompute runs on both selection changes AND non-selection paths
     * (favorites refresh, async init). Rebuilding the combo model resets the
     * user's chosen value, so we must rebuild only when the effective param
     * actually changes; otherwise a stray refresh would silently discard a
     * value the user already picked (dropping the plugin from the request).
     */
    fun paramControlUpdate(shownParamKey: String?, selectedModel: String): ParamControlUpdate {
        val paramKey = RouterCatalog.find(selectedModel)?.param?.key
            ?: return ParamControlUpdate(visible = false, rebuild = false, paramKey = null)
        val rebuild = shownParamKey != paramKey
        return ParamControlUpdate(visible = true, rebuild = rebuild, paramKey = paramKey)
    }

    /**
     * Outcome of [paramControlUpdate]: whether the control is [visible], whether
     * the caller must [rebuild] the combo model (and thus reset the value), and
     * the [paramKey] now shown (null when hidden) for the caller to remember.
     */
    data class ParamControlUpdate(
        val visible: Boolean,
        val rebuild: Boolean,
        val paramKey: String?
    )

    /**
     * Validate and coerce [rawValue] against [param]. Returns the wire value
     * (String for enums, Double for float ranges) or null when the value is
     * absent/invalid.
     */
    private fun coerce(param: RouterParam, rawValue: String?): Any? {
        val value = rawValue?.takeIf { it.isNotBlank() } ?: return null
        return when (param) {
            is RouterParam.Enum ->
                if (param.editable || value in param.values) value else null
            is RouterParam.FloatRange -> {
                val d = value.toDoubleOrNull() ?: return null
                if (d in param.min..param.max) d else null
            }
        }
    }
}
