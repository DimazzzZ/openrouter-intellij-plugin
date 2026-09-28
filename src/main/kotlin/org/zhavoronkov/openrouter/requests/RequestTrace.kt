package org.zhavoronkov.openrouter.requests

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.JsonSyntaxException

/**
 * Follows one request from the moment it arrives to the moment its reply is done, and hands the
 * result to [record] exactly once.
 *
 * The code that sends the request feeds it what the reply carries ([observe]) and any failure
 * ([fail]); [finish] is called when handling ends, however it ends, and emits the record. Calling
 * [finish] again does nothing, so it can sit in a `finally` beside earlier exits. The first
 * failure reported is kept: it is the one closest to the cause. A request that ends with neither
 * a reply nor a failure - an unexpected exception, a cancelled send - is recorded as [NO_REPLY].
 *
 * An error is stored as a message, never as a body: an upstream body can echo the request back,
 * so [fail] keeps only the `error.message` of a JSON body it finds in the text, and at most
 * [MAX_ERROR_LENGTH] characters.
 */
class RequestTrace(
    private val source: RequestSource,
    private val sender: String,
    private var requestedModel: String = "",
    private val clock: () -> Long = System::currentTimeMillis,
    private val record: (RequestRecord) -> Unit,
    /** Asked, after the record, to find the provider of a generation whose reply could not say. */
    private val lookUpProvider: (generationId: String) -> Unit = {}
) {
    private val startedAt = clock()
    private val collector = ReplyFactsCollector()
    private var error: String? = null
    private var observed = false
    private var finished = false
    private var replyNamesProvider = true

    /** The id the sender asked for, once its request has been read. */
    fun requestedModel(model: String) {
        requestedModel = model
    }

    /**
     * The request as sent: when [ReplyProvider] says its reply's provider cannot be believed, the
     * record carries none, and the generation's is looked up once the record is made.
     */
    fun sent(request: JsonObject) {
        replyNamesProvider = ReplyProvider.trusted(request)
    }

    fun observe(json: JsonObject) {
        observed = true
        collector.observe(json)
    }

    fun fail(message: String) {
        if (error == null) error = messageOnly(message).take(MAX_ERROR_LENGTH)
    }

    fun finish() {
        if (finished) return
        finished = true
        val facts = collector.facts().let { if (replyNamesProvider) it else it.copy(provider = null) }
        record(
            RequestRecord(
                startedAtMillis = startedAt,
                durationMillis = clock() - startedAt,
                source = source,
                sender = sender,
                requestedModel = requestedModel,
                reply = facts,
                error = error ?: NO_REPLY.takeUnless { observed }
            )
        )
        if (!replyNamesProvider) facts.generationId?.let(lookUpProvider)
    }

    companion object {
        const val NO_REPLY = "Ended without a reply"
        const val MAX_ERROR_LENGTH = 300

        /** [text] with a JSON body in it replaced by that body's `error.message`, when it has one. */
        private fun messageOnly(text: String): String {
            val bodyStart = text.indexOf('{')
            if (bodyStart < 0) return text
            val message = try {
                JsonParser.parseString(text.substring(bodyStart)).takeIf { it.isJsonObject }
                    ?.asJsonObject?.getAsJsonObject("error")?.get("message")
                    ?.takeIf { it.isJsonPrimitive }?.asString
            } catch (e: JsonSyntaxException) {
                null
            } catch (e: ClassCastException) {
                null
            } ?: return text
            return text.substring(0, bodyStart) + message
        }
    }
}
