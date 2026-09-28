package org.zhavoronkov.openrouter.toolwindow.chat

import org.zhavoronkov.openrouter.models.ChatCompletionRequest
import org.zhavoronkov.openrouter.models.ChatCompletionResponse
import org.zhavoronkov.openrouter.models.ChatMessage
import org.zhavoronkov.openrouter.models.ReasoningConfig
import org.zhavoronkov.openrouter.proxy.routing.RouterRequestBuilder
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import java.util.Locale

/**
 * What the user has chosen in the send-parameters popup for the next message.
 *
 * The fields carry the popup's own wording, because that wording is the vocabulary: the combo
 * boxes are populated from [ChatExchange.REASONING_CHOICES] and [ChatExchange.VERBOSITY_CHOICES],
 * and [ChatExchange.UNCHANGED] is how they spell "the user changed nothing". Translating those
 * labels into what OpenRouter wants belongs to [ChatExchange], which owns both the labels and the
 * translation so the two cannot disagree.
 */
data class ChatRequestOptions(
    val reasoning: String? = null,
    val verbosity: String? = null,
    val routerParam: String? = null
)

/**
 * What a reply says about how it was produced, as shown in the footer under it.
 *
 * The fields are the facts as OpenRouter reported them, never footer wording: [requestedModel] is
 * what the chat asked for and [respondingModel] what the response named, and [answeringModel],
 * [facts] and [warning] are rendered from them each time they are read. Saved chats store this
 * class as it is, so its property names are a storage format, and keeping wording out of it is
 * what lets the footer's wording change without stranding a saved message.
 *
 * [provider] and [cost] are null when the response did not carry them, so the footer leaves them
 * out instead of showing a zero or a blank. [finishReason] is OpenRouter's normalised reason, kept
 * whatever it is; [warning] is what the footer says about it, and is null for a normal stop so
 * that a warning only ever appears when there is something to warn about.
 */
data class ReplySummary(
    val requestedModel: String,
    val respondingModel: String? = null,
    val provider: String? = null,
    val cost: Double? = null,
    val finishReason: String? = null
) {
    /**
     * Which model actually answered.
     *
     * A Router's reply keeps [RouterRequestBuilder.resolvedModelLabel]'s wording, because there the
     * interesting fact is that a Router was asked and something else answered. Every other reply
     * gets the plain slug - the same fact, without a detour to report. A conversation that ran
     * across several models is unreadable otherwise: nothing on the message says which one
     * produced it, and the model picker only ever shows the one selected now.
     */
    val answeringModel: String
        get() = RouterRequestBuilder.resolvedModelLabel(requestedModel, respondingModel)
            ?: respondingModel?.takeIf { it.isNotBlank() }
            ?: requestedModel

    /** The footer's line of facts: the answering model, then the provider and cost when known. */
    val facts: String
        get() = listOfNotNull(answeringModel, provider, cost?.let(::formatCost)).joinToString(FACT_SEPARATOR)

    /**
     * What the footer warns about a reply that did not stop normally, or null when it did.
     *
     * `tool_calls` counts as normal: the model stopped because it chose to, just as with `stop`.
     * A reason not listed here is still named rather than hidden, since a reason OpenRouter adds
     * later is as much a reply that ended early as the ones listed.
     */
    val warning: String?
        get() = when (finishReason) {
            null, "stop", "tool_calls" -> null
            "length" -> "Cut off at the token limit"
            "content_filter" -> "Stopped by a content filter"
            "error" -> "Stopped by an error at the provider"
            else -> "Stopped early ($finishReason)"
        }

    private companion object {
        const val FACT_SEPARATOR = " · "
        const val COST_SIGNIFICANT_FIGURES = 2

        /**
         * At most two significant figures below a dollar, because a single reply usually costs a
         * fraction of a cent and a fixed number of decimals shows most of them as zero; cents from a
         * dollar up. Rounded before choosing, so a cost that rounds up to a dollar is shown in cents
         * too. Rounded from the cost's shortest decimal spelling, not its binary value, so a round
         * cost keeps its own digits (0.003 is `$0.003`, not `$0.0030`) while a rounded one shows
         * the two figures it was rounded to (0.0199 is `$0.020`).
         */
        fun formatCost(cost: Double): String {
            if (cost == 0.0) return "$0"
            val rounded = BigDecimal.valueOf(cost).round(MathContext(COST_SIGNIFICANT_FIGURES, RoundingMode.HALF_UP))
            return if (rounded >= BigDecimal.ONE) {
                String.format(Locale.US, "$%.2f", cost)
            } else {
                "$" + rounded.toPlainString()
            }
        }
    }
}

/**
 * What the chat window sends to OpenRouter, what it makes of the reply, and the vocabulary its
 * controls offer.
 *
 * Everything here is a pure function of the user's selections and of the reply, so it runs in the fast headless
 * test task - the same reason [MessageSegmenter] is pure and lives beside the chat UI rather than
 * inside it. A request assembled inside a Swing class cannot be read back without a running IDE,
 * and therefore cannot be asserted on at all.
 *
 * This is also the single place that knows what the send-parameters controls may offer: the combo
 * boxes are populated from [REASONING_CHOICES] and [VERBOSITY_CHOICES] rather than from lists of
 * their own. Holding the labels and their translation together is what makes adding a choice one
 * edit; split apart, a label with no translation is silently dropped from the request instead of
 * failing to compile.
 *
 * Both directions live here: what goes out, and what the reply that comes back says about itself -
 * which model answered, which provider served it, what it cost and why it stopped.
 */
object ChatExchange {

    fun buildRequest(
        model: String,
        messages: List<ChatMessage>,
        options: ChatRequestOptions
    ): ChatCompletionRequest = ChatCompletionRequest(
        model = model,
        messages = messages,
        maxTokens = MAX_TOKENS,
        temperature = TEMPERATURE,
        stream = false,
        reasoning = reasoningConfig(options.reasoning),
        verbosity = verbosity(options.verbosity),
        plugins = RouterRequestBuilder.buildPlugins(model, options.routerParam)
    )

    /** What [response] says about how it was produced, for a request that asked for [requestedModel]. */
    fun summarizeReply(requestedModel: String, response: ChatCompletionResponse): ReplySummary =
        ReplySummary(
            requestedModel = requestedModel,
            respondingModel = response.model,
            provider = response.provider?.takeIf { it.isNotBlank() },
            cost = response.usage?.cost,
            finishReason = response.choices?.firstOrNull()?.finishReason
        )

    /**
     * Unlike [reasoningConfig] this checks nothing but "did the user change it", passing anything
     * else through lower-cased. The asymmetry is inherited rather than chosen, and is pinned by a
     * test so that changing it is a decision someone makes rather than one that happens.
     */
    private fun verbosity(chosen: String?): String? =
        chosen?.takeIf { it != UNCHANGED }?.lowercase()

    /**
     * Efforts are listed rather than lowercased blindly: every one of them happens to be its own
     * label in lower case, but that is a coincidence of the current list, and an unrecognised
     * value must reach OpenRouter as nothing rather than as a guess.
     */
    private fun reasoningConfig(chosen: String?): ReasoningConfig? =
        REASONING_EFFORTS[chosen]?.let { ReasoningConfig(effort = it) }

    private val REASONING_EFFORTS = mapOf(
        "None" to "none",
        "Minimal" to "minimal",
        "Low" to "low",
        "Medium" to "medium",
        "High" to "high",
        "XHigh" to "xhigh"
    )

    /** The chat window reads replies whole; the streaming path belongs to the Proxy Server. */
    private const val MAX_TOKENS = 4096
    private const val TEMPERATURE = 0.7

    /** How both combo boxes spell "the user changed nothing", and the first choice each offers. */
    const val UNCHANGED = "Default"

    /** Every reasoning effort the popup offers, in the order it offers them. */
    val REASONING_CHOICES: List<String> = listOf(UNCHANGED) + REASONING_EFFORTS.keys

    /** Every verbosity the popup offers, in the order it offers them. */
    val VERBOSITY_CHOICES: List<String> = listOf(UNCHANGED, "Low", "Medium", "High", "XHigh", "Max")
}
