package org.zhavoronkov.openrouter.settings.presets

import org.zhavoronkov.openrouter.models.ApiResult
import org.zhavoronkov.openrouter.presets.PresetCopyService
import org.zhavoronkov.openrouter.services.OpenRouterService

/** Writes a preset to OpenRouter - an existing slug gets a new version - and reads the copy again. */
object PresetWriter {

    /** @return the error OpenRouter gave, or null when the preset was saved. */
    suspend fun save(draft: PresetDraft): String? {
        val result = OpenRouterService.getInstance().createOrUpdatePreset(
            draft.slug,
            draft.config(),
            draft.systemPrompt
        )
        return when (result) {
            is ApiResult.Success -> {
                PresetCopyService.getInstance().copy.refresh()
                null
            }
            is ApiResult.Error -> result.message
        }
    }
}
