package org.zhavoronkov.openrouter.proxy.routing

import com.google.gson.Gson
import com.google.gson.JsonObject
import org.zhavoronkov.openrouter.services.settings.RouterDefaultsManager
import org.zhavoronkov.openrouter.utils.PluginLogger

/**
 * Applies the plugin's persisted per-router default parameter to an outbound
 * chat-completion request body, preserving any client-sent `plugins` block
 * untouched (only injects when absent).
 *
 * Mirrors [ProviderRoutingInjector]: extracted from the servlet so the
 * "inject only when absent" invariant can be unit-tested without the full
 * settings service. All router knowledge is read from [RouterCatalog] via
 * [RouterRequestBuilder]; there is no per-router branch here.
 */
object RouterPluginsInjector {

    /**
     * Inject the saved default `plugins` block for the request's router model
     * when the request omits `plugins` and a valid default exists.
     *
     * @param requestId used only for DEBUG log correlation; may be blank
     * @return true if a plugins block was injected, false otherwise
     */
    fun inject(
        rawJson: JsonObject,
        defaults: RouterDefaultsManager,
        gson: Gson,
        requestId: String = ""
    ): Boolean {
        // Respect a client-sent plugins block verbatim.
        if (rawJson.has(PLUGINS_KEY)) {
            PluginLogger.Service.debug(
                "[Chat-$requestId] Client sent `plugins`; skipping router-default injection"
            )
            return false
        }

        val model = rawJson.get(MODEL_KEY)?.takeIf { it.isJsonPrimitive }?.asString ?: return false
        if (!RouterCatalog.isRouter(model)) return false

        val saved = defaults.get(model) ?: return false
        val plugins = RouterRequestBuilder.buildPlugins(model, saved) ?: return false

        rawJson.add(PLUGINS_KEY, gson.toJsonTree(plugins))
        PluginLogger.Service.debug(
            "[Chat-$requestId] Injected saved router default for $model"
        )
        return true
    }

    private const val PLUGINS_KEY = "plugins"
    private const val MODEL_KEY = "model"
}
