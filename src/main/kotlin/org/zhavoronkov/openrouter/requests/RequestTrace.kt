package org.zhavoronkov.openrouter.requests

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.JsonSyntaxException
import org.zhavoronkov.openrouter.models.FixPage

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
 *
 * Only when [keepBodies] - the user turned request bodies on - does the trace also keep what the
 * request carried ([RequestBodies]): the body [received], the body [sent], every reply [observe]d
 * and the first failure as reported, and hands them to [saveBodies] once, from [finish], under the
 * id the record then keeps.
 */
class RequestTrace(
    private val source: RequestSource,
    private val sender: String,
    private var requestedModel: String = "",
    private val clock: () -> Long = System::currentTimeMillis,
    private val record: (RequestRecord) -> Unit,
    /** Asked, after the record, to find the provider of a generation whose reply could not say. */
    private val lookUpProvider: (generationId: String) -> Unit = {},
    private val keepBodies: Boolean = false,
    private val saveBodies: (id: String, bodies: RequestBodies) -> Unit = { _, _ -> }
) {
    private val startedAt = clock()
    private val collector = ReplyFactsCollector()
    private var error: String? = null
    private var observed = false
    private var preset: String? = null
    private var replaced: List<String> = emptyList()
    private var fixAt: FixPage? = null
    private var finished = false
    private var replyNamesProvider = true
    private var receivedBody: String? = null
    private var sentBody: String? = null
    private val replyBody = StringBuilder()
    private var failureBody: String? = null

    /** The id the sender asked for, once its request has been read. */
    fun requestedModel(model: String) {
        requestedModel = model
    }

    /** The preset a pair was sent with, and the Consumer's fields removed so that the preset's held. */
    fun preset(slug: String, replacedFields: List<String>) {
        preset = slug
        replaced = replacedFields
    }

    /**
     * The request as sent: when [ReplyProvider] says its reply's provider cannot be believed, the
     * record carries none, and the generation's is looked up once the record is made.
     */
    fun sent(request: JsonObject, presetConfig: (slug: String) -> JsonObject? = { null }) {
        replyNamesProvider = ReplyProvider.trusted(request, presetConfig)
        if (keepBodies) sentBody = RequestBodies.cut(request.toString())
    }

    /** The body as its sender sent it, before anything was done to it. */
    fun received(body: String) {
        if (keepBodies) receivedBody = RequestBodies.cut(body)
    }

    /** One reply, or one chunk of a streamed one, read for the facts it reports. */
    fun observe(json: JsonObject) {
        observed = true
        collector.observe(json)
        if (keepBodies && replyBody.length <= RequestBodies.MAX_LENGTH) {
            if (replyBody.isNotEmpty()) replyBody.append('\n')
            replyBody.append(json.toString())
        }
    }

    /** The request failed with [message]; only the first failure is kept. */
    fun fail(message: String) {
        if (error != null) return
        error = messageOnly(message).take(MAX_ERROR_LENGTH)
        if (keepBodies) failureBody = RequestBodies.cut(message)
    }

    /** The plugin refused the request itself, with [message]; [page] is the settings page that fixes it. */
    fun refuse(message: String, page: FixPage) {
        if (error != null) return
        fail(message)
        fixAt = page
    }

    /** Handling has ended: emits the record, once, with whatever was observed and the first failure. */
    fun finish() {
        if (finished) return
        finished = true
        val facts = collector.facts().let { if (replyNamesProvider) it else it.copy(provider = null) }
        val bodiesId = keptBodies()?.let { bodies -> RequestBodyStore.newId().also { saveBodies(it, bodies) } }
        record(
            RequestRecord(
                startedAtMillis = startedAt,
                durationMillis = clock() - startedAt,
                source = source,
                sender = sender,
                requestedModel = requestedModel,
                reply = facts,
                error = error ?: NO_REPLY.takeUnless { observed },
                preset = preset,
                replaced = replaced,
                fixAt = fixAt,
                bodiesId = bodiesId
            )
        )
        if (!replyNamesProvider) facts.generationId?.let(lookUpProvider)
    }

    /** What the request carried, when bodies are kept and it carried anything. */
    private fun keptBodies(): RequestBodies? {
        if (!keepBodies) return null
        val bodies = RequestBodies(
            received = receivedBody,
            sent = sentBody,
            reply = replyBody.takeIf { it.isNotEmpty() }?.let { RequestBodies.cut(it.toString()) },
            failure = failureBody
        )
        return bodies.takeUnless { it == RequestBodies() }
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
