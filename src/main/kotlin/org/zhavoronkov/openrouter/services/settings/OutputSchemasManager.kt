package org.zhavoronkov.openrouter.services.settings

import org.zhavoronkov.openrouter.models.OpenRouterSettings
import org.zhavoronkov.openrouter.models.OutputSchema

/**
 * Manages the saved Output Schemas, following the same wrap-and-notify pattern as
 * [RouterDefaultsManager].
 *
 * Copies go in and out, never the stored instances: [OutputSchema] has writable properties for the
 * settings serializer's sake, and a caller editing what it was handed must not change what is
 * stored without going through [replaceAll].
 */
class OutputSchemasManager(
    private val settings: OpenRouterSettings,
    private val onStateChanged: () -> Unit
) {

    /** Every saved schema, in the order the Output Schemas page lists them. */
    fun all(): List<OutputSchema> = settings.outputSchemas.map { it.copy() }

    /** Replace every saved schema at once, notifying only when something changed. */
    fun replaceAll(schemas: List<OutputSchema>) {
        if (schemas == settings.outputSchemas) return
        settings.outputSchemas = schemas.map { it.copy() }.toMutableList()
        onStateChanged()
    }
}
