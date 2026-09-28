package org.zhavoronkov.openrouter.requests

/**
 * Names the Consumer behind a request from its User-Agent header.
 *
 * Known agents are matched by a fragment of their User-Agent, case-insensitively, so a version or
 * platform suffix does not matter. An agent this table does not know is shown by its User-Agent as
 * sent, cut to [MAX_LENGTH] - truthful, and exactly what is needed to add it here later.
 */
object ConsumerNames {

    const val UNKNOWN = "Unknown client"

    /** The longest User-Agent kept for an agent this table does not know. */
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

    fun fromUserAgent(userAgent: String?): String {
        val sent = userAgent?.trim().orEmpty()
        if (sent.isEmpty()) return UNKNOWN
        val lower = sent.lowercase()
        return KNOWN.firstOrNull { (fragment, _) -> fragment in lower }?.second
            ?: sent.take(MAX_LENGTH)
    }
}
