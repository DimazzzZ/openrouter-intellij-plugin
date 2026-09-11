package org.zhavoronkov.openrouter.services.settings

import com.google.gson.Gson
import com.google.gson.JsonObject
import org.zhavoronkov.openrouter.models.OpenRouterSettings
import org.zhavoronkov.openrouter.models.ProviderRoutingPreferences

/**
 * Manages provider routing preferences for the proxy's global injection.
 * Follows the same pattern as [UIPreferencesManager]: wraps [OpenRouterSettings]
 * fields with change notification.
 */
class ProviderRoutingManager(
    private val settings: OpenRouterSettings,
    private val onStateChanged: () -> Unit
) {

    var enabled: Boolean
        get() = settings.providerRoutingEnabled
        set(value) {
            settings.providerRoutingEnabled = value
            onStateChanged()
        }

    var order: MutableList<String>
        get() = settings.providerOrder
        set(value) {
            settings.providerOrder = value
            onStateChanged()
        }

    var allowFallbacks: Boolean
        get() = settings.providerAllowFallbacks
        set(value) {
            settings.providerAllowFallbacks = value
            onStateChanged()
        }

    var sort: String
        get() = settings.providerSort
        set(value) {
            settings.providerSort = value
            onStateChanged()
        }

    var requireParameters: Boolean
        get() = settings.providerRequireParameters
        set(value) {
            settings.providerRequireParameters = value
            onStateChanged()
        }

    var dataCollection: String
        get() = settings.providerDataCollection
        set(value) {
            settings.providerDataCollection = value
            onStateChanged()
        }

    var quantizations: MutableList<String>
        get() = settings.providerQuantizations
        set(value) {
            settings.providerQuantizations = value
            onStateChanged()
        }

    var only: MutableList<String>
        get() = settings.providerOnly
        set(value) {
            settings.providerOnly = value
            onStateChanged()
        }

    var ignore: MutableList<String>
        get() = settings.providerIgnore
        set(value) {
            settings.providerIgnore = value
            onStateChanged()
        }

    var fallbackModels: MutableList<String>
        get() = settings.fallbackModels
        set(value) {
            settings.fallbackModels = value
            onStateChanged()
        }

    /**
     * Build a [ProviderRoutingPreferences] from the current settings.
     * Returns null if no routing fields are meaningfully set (so the injector can skip).
     */
    fun toPreferences(): ProviderRoutingPreferences? {
        val signals = listOf(
            order.isNotEmpty(),
            sort.isNotBlank(),
            dataCollection.isNotBlank(),
            quantizations.isNotEmpty(),
            only.isNotEmpty(),
            ignore.isNotEmpty(),
            !allowFallbacks,
            requireParameters
        )

        if (signals.none { it }) {
            return null
        }

        return ProviderRoutingPreferences(
            order = order.ifEmpty { null },
            allowFallbacks = if (!allowFallbacks) false else null,
            sort = sort.ifBlank { null },
            requireParameters = if (requireParameters) true else null,
            dataCollection = dataCollection.ifBlank { null },
            quantizations = quantizations.ifEmpty { null },
            only = only.ifEmpty { null },
            ignore = ignore.ifEmpty { null }
        )
    }

    /**
     * Build the provider routing as a [JsonObject] for raw-JSON injection
     * in [org.zhavoronkov.openrouter.proxy.servlets.ChatCompletionServlet.applyConfiguredDefaults].
     * Returns null if no routing is configured.
     */
    fun buildProviderJson(gson: Gson): JsonObject? {
        val prefs = toPreferences() ?: return null
        return gson.toJsonTree(prefs).asJsonObject
    }
}
