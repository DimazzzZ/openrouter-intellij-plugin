package org.zhavoronkov.openrouter.proxy.servlets

/**
 * Response-header name filter for OpenRouter-specific metadata.
 *
 * OpenRouter surfaces routing decisions, model used, and the generation ID on
 * `x-openrouter-*` headers, plus a top-level `openrouter-id`. Both proxy
 * servlets (streaming and non-streaming) log this metadata verbatim; this
 * predicate exists to (a) share a single definition of "is a metadata
 * header" and (b) keep the call site under detekt's 120-char line limit
 * without an ad-hoc line break.
 */
internal fun isOpenRouterMetadataHeader(name: String): Boolean =
    name.startsWith("x-openrouter", ignoreCase = true) ||
        name.equals("openrouter-id", ignoreCase = true)
