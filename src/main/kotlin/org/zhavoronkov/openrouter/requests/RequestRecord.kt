package org.zhavoronkov.openrouter.requests

import org.zhavoronkov.openrouter.models.FixPage

/** Where a recorded request came from: a Consumer through the proxy, or the plugin's own chat. */
enum class RequestSource { PROXY, CHAT }

/**
 * What a reply said about how it was produced, in no API shape's spelling - the same facts the
 * chat's Reply Summary shows, read from whichever response format carried them. Every field is
 * null (or zero searches) when the response did not report it.
 */
data class ReplyFacts(
    val generationId: String? = null,
    val answeringModel: String? = null,
    val provider: String? = null,
    val promptTokens: Int? = null,
    val completionTokens: Int? = null,
    val cost: Double? = null,
    val finishReason: String? = null,
    val webSearches: Int = 0
)

/**
 * One request the plugin sent to OpenRouter, as the Requests tab shows it.
 *
 * Facts only: never the prompt, the reply or a header. [sender] is who sent it - a Consumer named
 * from its User-Agent, or the chat. [requestedModel] is the id the sender asked for, before the
 * proxy did anything to it - a pair's id, when a Consumer picked one; [preset] is then the
 * preset it was sent with, and [replaced] the Consumer's own request fields removed so that the
 * preset's held. [reply] is what came back, and [error] is set instead when the request failed - with
 * [fixAt], the settings page that fixes it, when the plugin itself refused the request. Stored as
 * it is, so its property names are a storage format.
 *
 * The bodies - the prompt, the reply - are never part of the record: when the user turned request
 * bodies on, they are kept apart in a [RequestBodyStore], and [bodiesId] names them.
 */
data class RequestRecord(
    val startedAtMillis: Long,
    val durationMillis: Long,
    val source: RequestSource,
    val sender: String,
    val requestedModel: String,
    val reply: ReplyFacts = ReplyFacts(),
    val error: String? = null,
    val preset: String? = null,
    val replaced: List<String> = emptyList(),
    val fixAt: FixPage? = null,
    val bodiesId: String? = null
)

/** Why [this] deserves a look - its error, or a reply that did not stop normally - or null. */
val RequestRecord.warning: String? get() = error ?: stopWarning(reply.finishReason)

/**
 * [warning] for a Consumer's request, which the user may not have seen, and null for the chat's
 * own: its warning is already shown under the reply, where the user is looking.
 */
val RequestRecord.unseenWarning: String? get() = warning.takeIf { source == RequestSource.PROXY }

/**
 * What to say about a reply that did not stop normally, or null when it did - one wording for the
 * chat's footer, the Requests tab and the warning balloon.
 *
 * `tool_calls` counts as normal: the model stopped because it chose to, just as with `stop`. A
 * reason not listed here is still named rather than hidden, since a reason OpenRouter adds later is
 * as much a reply that ended early as the ones listed.
 */
fun stopWarning(finishReason: String?): String? = when (finishReason) {
    null, "stop", "tool_calls" -> null
    "length" -> "Cut off at the token limit"
    "content_filter" -> "Stopped by a content filter"
    "error" -> "Stopped by an error at the provider"
    else -> "Stopped early ($finishReason)"
}
