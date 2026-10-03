package org.zhavoronkov.openrouter.requests

/**
 * Names the Consumer behind a request from its User-Agent header.
 *
 * Known Consumers are matched by a fragment of their User-Agent, case-insensitively, so a version or
 * platform suffix does not matter. A Consumer this table does not know is shown by its User-Agent as
 * sent, cut to [MAX_LENGTH] - truthful, and exactly what is needed to add it here later.
 *
 * AI Assistant is deliberately not in the table: it sends `ktor-client`, the Ktor HTTP client's
 * default User-Agent, which any Ktor-based client sends too, so naming it would misname them.
 */
object ConsumerNames {

    const val UNKNOWN = "Unknown client"

    /** The longest User-Agent kept for a Consumer this table does not know. */
    const val MAX_LENGTH = 120

    /** Fragment of a User-Agent, lower case, to the name the Requests tab shows. First match wins. */
    private val KNOWN = listOf(
        "junie" to "Junie",
        "copilot" to "GitHub Copilot",
        "cline" to "Cline",
        "kilo" to "Kilo Code",
        "proxyai" to "ProxyAI",
        "codegpt" to "ProxyAI",
        "opencode" to "OpenCode",
        "curl/" to "curl"
    )

    /** The name the Requests tab shows for a request that sent [userAgent], or [UNKNOWN] for none. */
    fun fromUserAgent(userAgent: String?): String {
        val sent = userAgent?.trim().orEmpty()
        if (sent.isEmpty()) return UNKNOWN
        val lower = sent.lowercase()
        // Unreachable branch: every KNOWN name is a non-null literal, so the elvis falls back only when none matched
        return KNOWN.firstOrNull { (fragment, _) -> fragment in lower }?.second
            ?: sent.take(MAX_LENGTH)
    }
}
