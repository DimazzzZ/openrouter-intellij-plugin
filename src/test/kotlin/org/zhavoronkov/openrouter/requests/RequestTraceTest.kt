package org.zhavoronkov.openrouter.requests

import com.google.gson.JsonParser
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("RequestTrace")
class RequestTraceTest {

    private val recorded = mutableListOf<RequestRecord>()
    private var now = 1_000L

    private val lookedUp = mutableListOf<String>()

    private fun trace() = RequestTrace(
        source = RequestSource.PROXY,
        sender = "Junie",
        requestedModel = "openai/gpt-5.2",
        clock = { now },
        record = { recorded += it },
        lookUpProvider = { lookedUp += it }
    )

    private val reply = JsonParser.parseString("""{"id":"gen-1","model":"m","provider":"OpenAI"}""").asJsonObject

    @Test
    @DisplayName("a reply to a request with a server tool is recorded without its provider, which is looked up")
    fun serverToolProvider() {
        val trace = trace()
        trace.sent(JsonParser.parseString("""{"model":"m","tools":[{"type":"openrouter:web_search"}]}""").asJsonObject)
        trace.observe(reply)

        trace.finish()

        assertNull(recorded.single().reply.provider)
        assertEquals(listOf("gen-1"), lookedUp)
    }

    @Test
    @DisplayName("a reply to a plain request keeps its provider, and nothing is looked up")
    fun plainProvider() {
        val trace = trace()
        trace.sent(JsonParser.parseString("""{"model":"m"}""").asJsonObject)
        trace.observe(reply)

        trace.finish()

        assertEquals("OpenAI", recorded.single().reply.provider)
        assertEquals(emptyList<String>(), lookedUp)
    }

    @Test
    @DisplayName("a finished request is recorded once, with its facts and how long it took")
    fun `a finished request is recorded once`() {
        val trace = trace()
        trace.observe(JsonParser.parseString("""{"model":"openai/gpt-5.2","usage":{"cost":0.01}}""").asJsonObject)
        now = 4_400L

        trace.finish()
        trace.finish()

        val record = recorded.single()
        assertEquals(1_000L, record.startedAtMillis)
        assertEquals(3_400L, record.durationMillis)
        assertEquals(RequestSource.PROXY, record.source)
        assertEquals("Junie", record.sender)
        assertEquals("openai/gpt-5.2", record.requestedModel)
        assertEquals(0.01, record.reply.cost)
        assertNull(record.error)
    }

    /**
     * A request can end without anything saying why - an unexpected exception, a cancelled send.
     * It must not be recorded as a success with empty facts.
     */
    @Test
    @DisplayName("a request that ends with no reply and no error is recorded as ending without a reply")
    fun `a request that ends with no reply and no error is recorded as ending without a reply`() {
        val trace = trace()

        trace.finish()

        assertEquals(RequestTrace.NO_REPLY, recorded.single().error)
    }

    /**
     * An upstream body can echo what was sent - a moderation refusal carries the flagged input - so
     * only the error's own message is kept, never the body around it.
     */
    @Test
    @DisplayName("an error carrying a JSON body keeps only the body's own error message")
    fun `an error carrying a JSON body keeps only its error message`() {
        val trace = trace()

        trace.fail(
            """OpenRouter API error 403: {"error":{"message":"Input flagged by moderation",
               "metadata":{"flagged_input":"my secret prompt text"}}}"""
        )
        trace.finish()

        assertEquals("OpenRouter API error 403: Input flagged by moderation", recorded.single().error)
    }

    @Test
    @DisplayName("an overlong error is cut short")
    fun `an overlong error is cut short`() {
        val trace = trace()

        trace.fail("e".repeat(5_000))
        trace.finish()

        assertEquals(RequestTrace.MAX_ERROR_LENGTH, recorded.single().error!!.length)
    }

    @Test
    @DisplayName("a failed request is recorded with its error, the first one reported winning")
    fun `a failed request is recorded with its error`() {
        val trace = trace()

        trace.fail("OpenRouter API error: 402 insufficient credits")
        trace.fail("a later, less precise error")
        trace.finish()

        assertEquals("OpenRouter API error: 402 insufficient credits", recorded.single().error)
    }

    private val saved = mutableMapOf<String, RequestBodies>()

    private fun keepingTrace() = RequestTrace(
        source = RequestSource.PROXY,
        sender = "Junie",
        requestedModel = "openai/gpt-5.2",
        clock = { now },
        record = { recorded += it },
        keepBodies = true,
        saveBodies = { id, bodies -> saved[id] = bodies }
    )

    @Test
    @DisplayName("with bodies off nothing a request carried is kept")
    fun bodiesOff() {
        val trace = trace()
        trace.received("""{"messages":[]}""")
        trace.sent(JsonParser.parseString("""{"model":"m"}""").asJsonObject)
        trace.observe(reply)

        trace.finish()

        assertNull(recorded.single().bodiesId)
    }

    @Test
    @DisplayName("with bodies on, the request received and sent, each reply chunk and the failure are kept")
    fun bodiesOn() {
        val trace = keepingTrace()
        trace.received("""{"model":"m@preset/p","temperature":1}""")
        trace.sent(JsonParser.parseString("""{"model":"m@preset/p"}""").asJsonObject)
        trace.observe(JsonParser.parseString("""{"id":"gen-1","choices":[{"delta":{"content":"Hel"}}]}""").asJsonObject)
        trace.observe(JsonParser.parseString("""{"id":"gen-1","choices":[{"delta":{"content":"lo"}}]}""").asJsonObject)
        trace.fail("""OpenRouter API error 500: {"error":{"message":"boom","code":500}}""")

        trace.finish()

        val id = recorded.single().bodiesId!!
        assertEquals(
            RequestBodies(
                received = """{"model":"m@preset/p","temperature":1}""",
                sent = """{"model":"m@preset/p"}""",
                reply = """{"id":"gen-1","choices":[{"delta":{"content":"Hel"}}]}""" + "\n" +
                    """{"id":"gen-1","choices":[{"delta":{"content":"lo"}}]}""",
                failure = """OpenRouter API error 500: {"error":{"message":"boom","code":500}}"""
            ),
            saved.getValue(id)
        )
        val error = recorded.single().error
        assertEquals("OpenRouter API error 500: boom", error, "the record still keeps only the message")
    }

    @Test
    @DisplayName("a request that carried nothing keeps no bodies")
    fun nothingCarried() {
        keepingTrace().finish()

        assertNull(recorded.single().bodiesId)
        assertEquals(emptyMap<String, RequestBodies>(), saved)
    }
}
