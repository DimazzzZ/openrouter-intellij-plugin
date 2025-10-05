package org.zhavoronkov.openrouter.settings

/**
 * Predefined model presets for quick selection
 */
object ModelPresets {

    /**
     * Popular models across all providers
     * These are the most commonly used models for coding and general tasks
     */
    val POPULAR_MODELS = listOf(
        "openai/gpt-4o",
        "openai/gpt-4o-mini",
        "anthropic/claude-3.5-sonnet",
        "anthropic/claude-3-haiku",
        "google/gemini-pro-1.5",
        "google/gemini-flash-1.5",
        "meta-llama/llama-3.1-70b-instruct",
        "qwen/qwen-2.5-72b-instruct"
    )

    /**
     * All OpenAI models
     */
    val OPENAI_MODELS = listOf(
        "openai/gpt-4o",
        "openai/gpt-4o-mini",
        "openai/gpt-4-turbo",
        "openai/gpt-4",
        "openai/gpt-3.5-turbo"
    )

    /**
     * All Anthropic Claude models
     */
    val ANTHROPIC_MODELS = listOf(
        "anthropic/claude-3.5-sonnet",
        "anthropic/claude-3-opus",
        "anthropic/claude-3-sonnet",
        "anthropic/claude-3-haiku",
        "anthropic/claude-2.1",
        "anthropic/claude-2"
    )

    /**
     * All Google Gemini models
     */
    val GOOGLE_MODELS = listOf(
        "google/gemini-pro-1.5",
        "google/gemini-flash-1.5",
        "google/gemini-pro"
    )

    /**
     * Meta Llama models
     */
    val META_MODELS = listOf(
        "meta-llama/llama-3.1-405b-instruct",
        "meta-llama/llama-3.1-70b-instruct",
        "meta-llama/llama-3.1-8b-instruct"
    )

    /**
     * Cost-effective models (good performance at lower cost)
     */
    val COST_EFFECTIVE_MODELS = listOf(
        "openai/gpt-4o-mini",
        "anthropic/claude-3-haiku",
        "google/gemini-flash-1.5",
        "meta-llama/llama-3.1-8b-instruct",
        "qwen/qwen-2.5-7b-instruct"
    )

    /**
     * Multimodal models (vision, audio, etc.)
     */
    val MULTIMODAL_MODELS = listOf(
        "openai/gpt-4o",
        "anthropic/claude-3.5-sonnet",
        "anthropic/claude-3-opus",
        "google/gemini-pro-1.5",
        "google/gemini-flash-1.5"
    )

    /**
     * Coding-focused models
     */
    val CODING_MODELS = listOf(
        "openai/gpt-4o",
        "anthropic/claude-3.5-sonnet",
        "qwen/qwen-2.5-72b-instruct",
        "deepseek/deepseek-coder"
    )

    /**
     * Preset definition for UI
     */
    data class Preset(
        val name: String,
        val description: String,
        val modelIds: List<String>
    )

    /**
     * All available presets
     */
    val ALL_PRESETS = listOf(
        Preset(
            name = "Popular Models",
            description = "Most commonly used models for coding and general tasks",
            modelIds = POPULAR_MODELS
        ),
        Preset(
            name = "OpenAI",
            description = "All OpenAI GPT models",
            modelIds = OPENAI_MODELS
        ),
        Preset(
            name = "Anthropic",
            description = "All Anthropic Claude models",
            modelIds = ANTHROPIC_MODELS
        ),
        Preset(
            name = "Google",
            description = "All Google Gemini models",
            modelIds = GOOGLE_MODELS
        ),
        Preset(
            name = "Meta",
            description = "Meta Llama models",
            modelIds = META_MODELS
        ),
        Preset(
            name = "Cost-Effective",
            description = "Good performance at lower cost",
            modelIds = COST_EFFECTIVE_MODELS
        ),
        Preset(
            name = "Multimodal",
            description = "Models with vision and audio capabilities",
            modelIds = MULTIMODAL_MODELS
        ),
        Preset(
            name = "Coding",
            description = "Best models for code generation and analysis",
            modelIds = CODING_MODELS
        )
    )

    /**
     * Get preset by name
     */
    fun getPreset(name: String): Preset? {
        return ALL_PRESETS.find { it.name == name }
    }

    /**
     * Get model IDs for a preset
     */
    fun getModelIds(presetName: String): List<String> {
        return getPreset(presetName)?.modelIds ?: emptyList()
    }
}

