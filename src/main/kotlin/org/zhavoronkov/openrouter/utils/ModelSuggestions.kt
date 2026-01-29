package org.zhavoronkov.openrouter.utils

/**
 * Centralized model suggestions for different capabilities.
 * This ensures consistency across error messages and makes it easy to update model recommendations.
 */
object ModelSuggestions {

    /**
     * Vision-capable models that support image input
     */
    val VISION_MODELS = listOf(
        "openai/gpt-4o",
        "openai/gpt-4o-mini",
        "anthropic/claude-4.5-sonnet",
        "google/gemini-2.5-pro"
    )

    /**
     * Audio-capable models that support audio input
     */
    val AUDIO_MODELS = listOf(
        "openai/gpt-4o-audio-preview",
        "google/gemini-2.0-flash-exp",
        "google/gemini-2.5-pro"
    )

    /**
     * Video-capable models that support video input
     */
    val VIDEO_MODELS = listOf(
        "google/gemini-2.0-flash-exp",
        "google/gemini-2.5-pro",
        "openai/gpt-4o (for video frames)"
    )

    /**
     * File/document-capable models that support PDF and document input
     */
    val FILE_MODELS = listOf(
        "google/gemini-2.5-pro",
        "anthropic/claude-4.5-sonnet",
        "openai/gpt-4o"
    )

    /**
     * General purpose models recommended as alternatives
     */
    val GENERAL_MODELS = listOf(
        "openai/gpt-4o-mini (fast, affordable)",
        "anthropic/claude-3.5-sonnet (high quality)",
        "google/gemini-pro-1.5 (large context)"
    )

    /**
     * Format a list of models as a bulleted list
     */
    fun formatModelList(models: List<String>): String {
        return models.joinToString("\n") { "- $it" }
    }

    /**
     * Create a suggestion section with header and model list
     */
    fun createSuggestionSection(header: String, models: List<String>): String = buildString {
        append(header)
        append("\n")
        append(formatModelList(models))
    }
}

