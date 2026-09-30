package org.zhavoronkov.openrouter.proxy.errors

import org.zhavoronkov.openrouter.models.FixPage
import org.zhavoronkov.openrouter.proxy.pairs.PairProblem

/**
 * An error the plugin itself answers a Consumer with, before anything is sent: an OpenAI-shaped
 * body, so every Consumer shows its message, with a [code] of its own per reason and a one-line
 * message that says what is wrong and, from [fixAt], where to fix it, prefixed so a user can tell
 * it from OpenRouter's own errors. Each reason has exactly one page that fixes it.
 */
data class ClearError(
    val code: String,
    val problem: String,
    val fixAt: FixPage,
    private val advice: String = "fix it in"
) {

    /** The one line a Consumer shows: what is wrong, and where to fix it. */
    val message: String get() = "$problem; $advice ${fixAt.path}"

    /** [message] as a Consumer receives it, prefixed as the plugin's own. */
    val prefixedMessage: String get() = PREFIX + message

    /** The body a Consumer receives: OpenAI's error shape. */
    fun body(): Map<String, Any> = mapOf(
        "error" to mapOf(
            "message" to prefixedMessage,
            "type" to "invalid_request_error",
            "code" to code
        )
    )

    companion object {
        const val PREFIX = "OpenRouter plugin: "

        /** The clear error for a pair that cannot be sent with its preset; both are fixed on the Presets page. */
        fun of(problem: PairProblem): ClearError = when (problem) {
            is PairProblem.MissingPreset -> ClearError("preset_not_found", problem.message, FixPage.PRESETS)
            is PairProblem.OutputNotServable -> ClearError("output_not_supported", problem.message, FixPage.PRESETS)
            is PairProblem.WebSearchDropsJson ->
                ClearError("output_dropped_by_web_search", problem.message, FixPage.PRESETS)
        }
    }
}
