package org.zhavoronkov.openrouter.services.settings

import org.zhavoronkov.openrouter.models.OpenRouterSettings
import org.zhavoronkov.openrouter.proxy.routing.RouterCatalog

/**
 * Manages persisted per-router default parameter values (the value the chat UI
 * would otherwise ask the user to pick each conversation, and the value the
 * proxy injects when a request omits `plugins`).
 *
 * Follows the same wrap-and-notify pattern as [ProviderRoutingManager]. All
 * router knowledge is read from [RouterCatalog]; this manager stores raw
 * strings keyed by router slug and never validates them itself (validation and
 * coercion live in RouterRequestBuilder, the single source of param truth).
 */
class RouterDefaultsManager(
    private val settings: OpenRouterSettings,
    private val onStateChanged: () -> Unit
) {

    /** The saved default for [modelSlug], or null when none is set. */
    fun get(modelSlug: String): String? = settings.routerDefaults[modelSlug]

    /** Set or clear the default for [modelSlug]. A blank value clears it. */
    fun set(modelSlug: String, rawValue: String?) {
        val value = rawValue?.takeIf { it.isNotBlank() }
        val current = settings.routerDefaults[modelSlug]
        if (value == current) return
        if (value == null) {
            settings.routerDefaults.remove(modelSlug)
        } else {
            settings.routerDefaults[modelSlug] = value
        }
        onStateChanged()
    }

    /** Immutable snapshot of every saved default, in catalog order. */
    fun all(): Map<String, String> = RouterCatalog.slugs
        .mapNotNull { slug -> settings.routerDefaults[slug]?.let { slug to it } }
        .toMap()

    /** Replace all defaults at once (used by the settings panel's apply). */
    fun replaceAll(values: Map<String, String>) {
        val cleaned = values.mapNotNull { (slug, v) ->
            v.takeIf { it.isNotBlank() }?.let { slug to it }
        }.toMap()
        if (cleaned == settings.routerDefaults) return
        settings.routerDefaults = cleaned.toMutableMap()
        onStateChanged()
    }
}
