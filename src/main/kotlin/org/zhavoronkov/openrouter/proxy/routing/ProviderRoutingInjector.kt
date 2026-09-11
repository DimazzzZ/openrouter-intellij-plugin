package org.zhavoronkov.openrouter.proxy.routing

import com.google.gson.Gson
import com.google.gson.JsonObject
import org.zhavoronkov.openrouter.constants.OpenRouterConstants
import org.zhavoronkov.openrouter.services.settings.ProviderRoutingManager
import org.zhavoronkov.openrouter.utils.PluginLogger

/**
 * Applies the plugin's global provider-routing preferences to an outbound
 * chat-completion request body, preserving any client-sent `provider` or
 * `models[]` block untouched (only injects when absent).
 *
 * Extracted from [org.zhavoronkov.openrouter.proxy.servlets.ChatCompletionServlet]
 * so that the "inject only when absent" invariant can be exercised in unit tests
 * without spinning up the full settings service.
 */
object ProviderRoutingInjector {

    /**
     * Inject the manager's routing preferences into [rawJson] when routing is
     * enabled and the request omits the corresponding field.
     *
     * @param requestId used only for DEBUG log correlation; may be blank
     * @return true if any field was injected, false otherwise
     */
    fun inject(
        rawJson: JsonObject,
        routing: ProviderRoutingManager,
        gson: Gson,
        requestId: String = ""
    ): Boolean {
        if (!routing.enabled) return false

        var injectedAnything = false

        // Provider block: inject only when absent
        if (!rawJson.has(OpenRouterConstants.ProviderRoutingKeys.PROVIDER)) {
            routing.buildProviderJson(gson)?.let {
                rawJson.add(OpenRouterConstants.ProviderRoutingKeys.PROVIDER, it)
                PluginLogger.Service.debug(
                    "[Chat-$requestId] Injected settings-level provider routing block"
                )
                injectedAnything = true
            }
        } else {
            PluginLogger.Service.debug(
                "[Chat-$requestId] Client sent `provider`; skipping settings injection"
            )
        }

        // Fallback models: inject only when absent AND settings has any configured
        if (routing.fallbackModels.isNotEmpty()) {
            if (!rawJson.has(OpenRouterConstants.ProviderRoutingKeys.MODELS)) {
                rawJson.add(
                    OpenRouterConstants.ProviderRoutingKeys.MODELS,
                    gson.toJsonTree(routing.fallbackModels)
                )
                PluginLogger.Service.debug(
                    "[Chat-$requestId] Injected settings-level fallback models list"
                )
                injectedAnything = true
            } else {
                PluginLogger.Service.debug(
                    "[Chat-$requestId] Client sent `models[]`; skipping settings injection"
                )
            }
        }

        return injectedAnything
    }
}
