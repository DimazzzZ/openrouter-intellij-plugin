package org.zhavoronkov.openrouter.models

/**
 * The settings page that fixes a request the plugin refused: named by the refusal's message, kept
 * on its Requests entry, and opened by the warning balloon's action. Each refusal reason has
 * exactly one. [title] is how the action names it; [path] is how a message a Consumer shows does.
 * Stored on a Request record by name, so its entries are a storage format.
 */
enum class FixPage(val title: String, val path: String) {
    FAVORITE_MODELS("Favorite Models", "Settings → Tools → OpenRouter → Favorite Models"),
    OUTPUT_SCHEMAS("Output Schemas", "Settings → Tools → OpenRouter → Output Schemas"),

    // The Data Region is chosen in the main page's Enterprise group, not on a page of its own
    DATA_REGION("OpenRouter Settings", "Settings → Tools → OpenRouter, Enterprise group")
}
