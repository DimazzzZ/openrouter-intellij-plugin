package org.zhavoronkov.openrouter.services.settings

import org.zhavoronkov.openrouter.models.OpenRouterSettings

/**
 * Manages user-configured OpenRouter presets.
 * Presets are named configurations created in the OpenRouter web UI
 * that can be referenced in API requests using @preset/slug format.
 *
 * Built-in presets (openrouter/auto, openrouter/free) are always available
 * and don't need to be configured here.
 *
 * @see <a href="https://openrouter.ai/docs/guides/features/presets">OpenRouter Presets Documentation</a>
 */
class PresetsManager(
    private val settings: OpenRouterSettings,
    private val notifyChange: () -> Unit
) {
    companion object {
        /**
         * Built-in presets that are always available
         * These are model IDs, not preset slugs, but provide similar functionality
         */
        val BUILT_IN_PRESETS = listOf(
            BuiltInPreset(
                id = "openrouter/auto",
                name = "Auto Router",
                description = "Automatically routes to the best model based on prompt complexity"
            ),
            BuiltInPreset(
                id = "openrouter/free",
                name = "Free Models",
                description = "Routes to free-tier models only"
            )
        )

        /**
         * Prefix used for custom presets in API requests
         */
        const val PRESET_PREFIX = "@preset/"
    }

    /**
     * Get all custom presets configured by the user
     * @return List of preset slugs (without @preset/ prefix)
     */
    fun getCustomPresets(): List<String> {
        return settings.customPresets.toList()
    }

    /**
     * Add a custom preset
     * @param presetSlug The preset slug (e.g., "email-copywriter") without @preset/ prefix
     * @return true if added, false if already exists
     */
    fun addCustomPreset(presetSlug: String): Boolean {
        val normalizedSlug = normalizePresetSlug(presetSlug)
        if (normalizedSlug.isBlank() || settings.customPresets.contains(normalizedSlug)) {
            return false
        }
        settings.customPresets.add(normalizedSlug)
        notifyChange()
        return true
    }

    /**
     * Remove a custom preset
     * @param presetSlug The preset slug to remove
     * @return true if removed, false if not found
     */
    fun removeCustomPreset(presetSlug: String): Boolean {
        val normalizedSlug = normalizePresetSlug(presetSlug)
        val removed = settings.customPresets.remove(normalizedSlug)
        if (removed) {
            notifyChange()
        }
        return removed
    }

    /**
     * Set all custom presets (replaces existing list)
     * @param presets List of preset slugs
     */
    fun setCustomPresets(presets: List<String>) {
        settings.customPresets.clear()
        settings.customPresets.addAll(presets.map { normalizePresetSlug(it) }.filter { it.isNotBlank() })
        notifyChange()
    }

    /**
     * Check if a preset is configured
     * @param presetSlug The preset slug to check
     * @return true if preset is configured
     */
    fun hasCustomPreset(presetSlug: String): Boolean {
        return settings.customPresets.contains(normalizePresetSlug(presetSlug))
    }

    /**
     * Clear all custom presets
     */
    fun clearCustomPresets() {
        settings.customPresets.clear()
        notifyChange()
    }

    /**
     * Get the full model ID for a preset (with @preset/ prefix)
     * @param presetSlug The preset slug
     * @return Full preset model ID (e.g., "@preset/email-copywriter")
     */
    fun getPresetModelId(presetSlug: String): String {
        return "$PRESET_PREFIX${normalizePresetSlug(presetSlug)}"
    }

    /**
     * Check if a model ID is a preset reference
     * @param modelId The model ID to check
     * @return true if this is a preset reference
     */
    fun isPresetModelId(modelId: String): Boolean {
        return modelId.startsWith(PRESET_PREFIX)
    }

    /**
     * Extract preset slug from a preset model ID
     * @param modelId The preset model ID (e.g., "@preset/email-copywriter")
     * @return The preset slug (e.g., "email-copywriter"), or null if not a preset
     */
    fun extractPresetSlug(modelId: String): String? {
        return if (isPresetModelId(modelId)) {
            modelId.removePrefix(PRESET_PREFIX)
        } else {
            null
        }
    }

    /**
     * Normalize a preset slug (remove @preset/ prefix if present, trim whitespace)
     */
    private fun normalizePresetSlug(slug: String): String {
        return slug
            .removePrefix(PRESET_PREFIX)
            .trim()
            .lowercase()
            .replace(Regex("[^a-z0-9-]"), "-")
            .replace(Regex("-+"), "-")
            .trim('-')
    }

    /**
     * Represents a built-in preset/router model
     */
    data class BuiltInPreset(
        val id: String,
        val name: String,
        val description: String
    )
}
