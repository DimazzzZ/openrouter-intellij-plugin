package org.zhavoronkov.openrouter.settings.favorites

import org.zhavoronkov.openrouter.models.ModelArchitecture
import org.zhavoronkov.openrouter.models.ModelPricing
import org.zhavoronkov.openrouter.models.OpenRouterModelInfo

/**
 * Shared catalog fixture for the favorites page tests.
 * Ids are chosen so provider, context, capability and variant filters each split the set.
 */
object FavoriteModelsFixtures {

    fun model(
        id: String,
        name: String = id,
        description: String? = null,
        contextLength: Int? = 128_000,
        inputModalities: List<String> = listOf("text"),
        outputModalities: List<String> = listOf("text"),
        supportedParameters: List<String> = emptyList(),
        prompt: String? = "0.000001",
        completion: String? = "0.000002",
    ) = OpenRouterModelInfo(
        id = id,
        name = name,
        created = 0L,
        description = description,
        architecture = ModelArchitecture(inputModalities = inputModalities, outputModalities = outputModalities),
        pricing = ModelPricing(prompt = prompt, completion = completion),
        contextLength = contextLength,
        supportedParameters = supportedParameters,
    )

    val GPT4O = model(
        "openai/gpt-4o",
        name = "GPT-4o",
        inputModalities = listOf("text", "image"),
        supportedParameters = listOf("tools"),
        prompt = "0.0000025",
        completion = "0.00001",
    )
    val GPT4O_MINI = model(
        "openai/gpt-4o-mini",
        name = "GPT-4o mini",
        inputModalities = listOf("text", "image"),
        supportedParameters = listOf("tools"),
        prompt = "0.00000015",
        completion = "0.0000006",
    )
    val SONNET = model(
        "anthropic/claude-3.5-sonnet",
        name = "Claude 3.5 Sonnet",
        contextLength = 200_000,
        supportedParameters = listOf("tools", "reasoning"),
        prompt = "0.000003",
        completion = "0.000015",
    )
    val GROK = model("x-ai/grok-4-fast", name = "Grok 4 Fast", prompt = "0.0000002", completion = "0.0000005")
    val GROK_FREE = model("x-ai/grok-4-fast:free", name = "Grok 4 Fast (free)", prompt = "0", completion = "0")
    val BRAND_NEW = model("some/model:brand-new", name = "Brand new")
    val GEMINI_AUDIO = model(
        "google/gemini-2.5-pro",
        name = "Gemini 2.5 Pro",
        contextLength = 1_000_000,
        inputModalities = listOf("text", "image", "audio"),
    )
    val LLAMA_SMALL = model("meta-llama/llama-3.1-8b-instruct", name = "Llama 3.1 8B", contextLength = 8_000)
    val UNKNOWN_CONTEXT = model(
        "mistralai/mystery",
        name = "Mystery",
        contextLength = null,
        prompt = null,
        completion = null,
    )

    val CATALOG: List<OpenRouterModelInfo> = listOf(
        GPT4O, GPT4O_MINI, SONNET, GROK, GROK_FREE, BRAND_NEW, GEMINI_AUDIO, LLAMA_SMALL, UNKNOWN_CONTEXT,
    )
}
