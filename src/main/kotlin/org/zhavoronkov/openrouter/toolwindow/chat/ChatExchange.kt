package org.zhavoronkov.openrouter.toolwindow.chat

import com.google.gson.Gson
import org.zhavoronkov.openrouter.models.ChatCompletionRequest
import org.zhavoronkov.openrouter.models.ChatCompletionResponse
import org.zhavoronkov.openrouter.models.ChatMessage
import org.zhavoronkov.openrouter.models.ChatTool
import org.zhavoronkov.openrouter.models.JsonSchemaFormat
import org.zhavoronkov.openrouter.models.OutputSchema
import org.zhavoronkov.openrouter.models.ReasoningConfig
import org.zhavoronkov.openrouter.models.ResponseFormat
import org.zhavoronkov.openrouter.models.WebSearchSettings
import org.zhavoronkov.openrouter.proxy.routing.RouterRequestBuilder
import org.zhavoronkov.openrouter.requests.stopWarning
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
    val routerParam: String? = null,
    val webSearch: Boolean = false,
    val outputMode: OutputMode = OutputMode.Off
)

/**
 * What shape the user asked a reply to take. [label] is how the send-parameters popup names it.
 */
sealed class OutputMode(val label: String) {
    /** No `response_format`: the model answers however it likes. */
    data object Off : OutputMode("Off")

    /** A JSON object, with no schema for it to follow. */
    data object PlainJson : OutputMode("JSON (no schema)")

    /**
     * A reply in the shape of the saved Output Schema called [name]. Only the name is held: the
     * schema itself is read from the saved ones when the request is built, so an edit on the
     * settings page applies to the next message, and a schema deleted there is noticed rather
     * than sent from a stale copy.
     *
     * Two are equal when their names are equal without regard to case, since that is how saved
     * schema names are kept unique; a schema renamed only in case is still the same selection.
     */
    class Schema(val name: String) : OutputMode(name) {
        override fun equals(other: Any?): Boolean = other is Schema && OutputSchema.sameName(other.name, name)

        override fun hashCode(): Int = OutputSchema.nameKey(name).hashCode()

        override fun toString(): String = "Schema($name)"
    }
}

/**
 * What decides which Output Modes can be served: the selected [model], what it declares in the
 * catalogue - [supportedParameters], or null when that is not known - and the saved [schemas].
 */
data class OutputModeContext(
    val model: String,
    val supportedParameters: List<String>?,
    val schemas: List<OutputSchema>
)

/**
 * One entry the Output Mode control offers, and why the selected Model cannot serve it, or null
 * when it can.
 */
data class OutputModeChoice(val mode: OutputMode, val unsupportedReason: String?) {
    val supported: Boolean get() = unsupportedReason == null
}

/**
 * What a reply says about how it was produced, as shown in the footer under it.
 *
 * The fields are facts, never footer wording: [requestedModel] is what the chat asked for, and
 * [respondingModel], [provider], [cost], [finishReason] and [webSearches] what OpenRouter reported
 * back. [answeringModel], [facts] and [warning] are
 * rendered from them each time they are read. Saved chats store this class as it is, so its
 * property names are a storage format, and keeping wording out of it is what lets the footer's
 * wording change without stranding a saved message.
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
    val finishReason: String? = null,
    val webSearches: Int = 0
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

    /**
     * The footer's line of facts: the answering model, then the provider and cost when known, then
     * how many web searches ran - so an answer drawn from the web can be told from one drawn from
     * the model. Searches are counted from the response, not the request: with Web Search on the
     * model decides whether to search, and may not.
     */
    val facts: String
        get() = listOfNotNull(
            answeringModel,
            provider,
            cost?.let(::formatCost),
            searchesMarker()
        ).joinToString(FACT_SEPARATOR)

    private fun searchesMarker(): String? = when {
        webSearches <= 0 -> null
        webSearches == 1 -> "1 web search"
        else -> "$webSearches web searches"
    }

    /** What the footer warns about a reply that did not stop normally, or null when it did. */
    val warning: String? get() = stopWarning(finishReason)

    companion object {
        private const val FACT_SEPARATOR = " · "
        private const val COST_SIGNIFICANT_FIGURES = 2

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
 * Everything here is a pure function of the user's selections and of the reply, so it runs in
 * the fast headless test task - the same reason [MessageSegmenter] is pure and lives beside the
 * chat UI rather than inside it. A request assembled inside a Swing class cannot be read back without a running IDE,
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

    /**
     * [webSearch] is how a search is tuned, applied only when [options] turns Web Search on for
     * this message; its defaults leave every choice to OpenRouter.
     */
    fun buildRequest(
        model: String,
        messages: List<ChatMessage>,
        options: ChatRequestOptions,
        webSearch: WebSearchSettings = WebSearchSettings(),
        schemas: List<OutputSchema> = emptyList()
    ): ChatCompletionRequest = ChatCompletionRequest(
        model = model,
        messages = messages,
        maxTokens = MAX_TOKENS,
        temperature = TEMPERATURE,
        // The chat window reads replies whole; the streaming path belongs to the Proxy Server.
        stream = false,
        reasoning = reasoningConfig(options.reasoning),
        verbosity = verbosity(options.verbosity),
        plugins = RouterRequestBuilder.buildPlugins(model, options.routerParam),
        tools = webSearchTools(options, webSearch),
        responseFormat = responseFormat(options.outputMode, schemas)
    )

    /**
     * A selected schema that cannot be found, or whose body does not parse, fails here rather than
     * being dropped from the request: [sendBlockedReason] refuses both before a request is built,
     * so reaching this means that check was skipped, and sending without the schema would be the
     * silent downgrade it exists to prevent.
     */
    private fun responseFormat(mode: OutputMode, schemas: List<OutputSchema>): ResponseFormat? = when (mode) {
        OutputMode.Off -> null
        OutputMode.PlainJson -> ResponseFormat(type = "json_object")
        is OutputMode.Schema -> {
            val schema = requireNotNull(findSchema(mode, schemas)) { "No saved Output Schema named ${mode.name}" }
            val body = requireNotNull(schema.parsedBody()) { "The Output Schema ${schema.name} is not a JSON object" }
            ResponseFormat(
                type = "json_schema",
                jsonSchema = JsonSchemaFormat(name = schema.name, strict = schema.strict, schema = body)
            )
        }
    }

    /** Names are unique without regard to case, so that is how a selection finds its schema. */
    private fun findSchema(mode: OutputMode.Schema, schemas: List<OutputSchema>): OutputSchema? =
        schemas.firstOrNull { OutputSchema.sameName(it.name, mode.name) }

    /**
     * Every Output Mode the popup offers for [context], in the order it offers them, each marked
     * with whether the Model can serve it, the saved schemas listed by name after Off and plain
     * JSON.
     *
     * This is the one place that knows the gating, and the popup renders its answer rather than
     * deriving its own. The two gates are independent: plain JSON is gated on `response_format`
     * and every saved schema on `structured_outputs`, neither a superset of the other, and the
     * catalogue has models declaring either one without the other.
     *
     * When the Model's declarations are not known - a Router, a preset, or any Model while the
     * catalogue is still loading - only Off is offered, and the reason says the support is not
     * known rather than that it is missing. A Router picks its Model per request, so nothing can
     * promise that the one it picks will honour a response format; sending one anyway would be
     * the request the server may refuse.
     */
    fun outputModes(context: OutputModeContext): List<OutputModeChoice> {
        val (model, declared, schemas) = context
        val jsonReason = capabilityReason(model, declared, RESPONSE_FORMAT, "JSON output")
        val schemaReason = capabilityReason(model, declared, STRUCTURED_OUTPUTS, "schema-constrained output")
        return listOf(
            OutputModeChoice(OutputMode.Off, null),
            OutputModeChoice(OutputMode.PlainJson, jsonReason)
        ) + schemas.map { schema ->
            // The settings page saves only a JSON object, but a settings file edited by hand, or a
            // newer build's, can hold anything; a body that is not one is never sent.
            val bodyReason = if (schema.parsedBody() == null) "${schema.name} $BROKEN_SCHEMA" else null
            OutputModeChoice(OutputMode.Schema(schema.name), bodyReason ?: schemaReason)
        }
    }

    private fun capabilityReason(model: String, declared: List<String>?, parameter: String, what: String): String? =
        when {
            declared == null -> "${what.replaceFirstChar { it.uppercase() }} support is not known for $model"
            parameter in declared -> null
            else -> "$model does not support $what"
        }

    /**
     * The reason given for a selection that is no longer among the modes offered at all. It blocks
     * sending like any other unservable selection, so a mode that has gone away is never sent.
     */
    fun noLongerOfferedReason(mode: OutputMode): String = "${mode.label} is no longer available"

    /**
     * Why a message cannot be sent with [selected] in [context], or null when it can.
     *
     * A selection that was valid can stop being so when the Model changes. It is kept and sending
     * is blocked, rather than the selection being reset or the request sent anyway: reset, the user
     * believes the reply is constrained when it is not; sent, the server refuses it.
     */
    fun sendBlockedReason(selected: OutputMode, context: OutputModeContext): String? {
        val choice = outputModes(context).firstOrNull { it.mode == selected }
        val reason = if (choice == null) noLongerOfferedReason(selected) else choice.unsupportedReason
        return reason?.let { "$it. Choose another output mode to send." }
    }

    /**
     * OpenRouter's web search server tool when [options] turns Web Search on, carrying whatever
     * [webSearch] tunes away from OpenRouter's defaults - so an untouched configuration sends the
     * bare tool. The chat window offers no function tools of its own, so this is the whole array.
     *
     * The deprecated `web` plugin is not used: OpenRouter replaced it with this tool, which lets
     * the model decide whether and how often to search.
     */
    private fun webSearchTools(options: ChatRequestOptions, webSearch: WebSearchSettings): List<ChatTool>? {
        if (!options.webSearch) return null
        val parameters = webSearch.toolParameters().takeIf { it.isNotEmpty() }
        return listOf(ChatTool(type = WEB_SEARCH_TOOL, parameters = parameters?.let(GSON::toJsonTree)))
    }

    /**
     * What [response] says about how it was produced, given the [request] that asked for it.
     *
     * How many web searches ran is read from the response's usage rather than inferred from the
     * request: allowing a search does not mean one happened. The response's provider is left out
     * when [replyNamesProvider] is false - see [org.zhavoronkov.openrouter.requests.ReplyProvider].
     */
    fun summarizeReply(
        request: ChatCompletionRequest,
        response: ChatCompletionResponse,
        replyNamesProvider: Boolean = true
    ): ReplySummary =
        ReplySummary(
            requestedModel = request.model,
            respondingModel = response.model,
            webSearches = response.usage?.serverToolUse?.webSearchRequests ?: 0,
            provider = response.provider?.takeIf { it.isNotBlank() && replyNamesProvider },
            cost = response.usage?.cost,
            finishReason = response.choices?.firstOrNull()?.finishReason
        )

    /**
     * Unlike [reasoningConfig] this checks nothing but "did the user change it", passing anything
     * else through lower-cased. The asymmetry is pinned by a test, so that changing either side
     * is a decision about both rather than an accident on one.
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

    /** The longest reply the chat window asks for. */
    private const val MAX_TOKENS = 4096
    private const val TEMPERATURE = 0.7

    /** The `supported_parameters` entry that declares plain JSON output. */
    private const val RESPONSE_FORMAT = "response_format"

    /** Why a saved schema whose body is not a JSON object is not offered. */
    private const val BROKEN_SCHEMA = "is not a valid JSON object; fix it in Settings"

    /** The `supported_parameters` entry that declares schema-constrained output. */
    private const val STRUCTURED_OUTPUTS = "structured_outputs"

    /** OpenRouter's web search server tool; see its guide in OpenRouter's documentation. */
    private const val WEB_SEARCH_TOOL = "openrouter:web_search"

    private val GSON = Gson()

    /** How both combo boxes spell "the user changed nothing", and the first choice each offers. */
    const val UNCHANGED = "Default"

    /** Every reasoning effort the popup offers, in the order it offers them. */
    val REASONING_CHOICES: List<String> = listOf(UNCHANGED) + REASONING_EFFORTS.keys

    /** Every verbosity the popup offers, in the order it offers them. */
    val VERBOSITY_CHOICES: List<String> = listOf(UNCHANGED, "Low", "Medium", "High", "XHigh", "Max")
}
