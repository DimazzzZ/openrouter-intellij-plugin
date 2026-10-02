package org.zhavoronkov.openrouter.requests

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.zhavoronkov.openrouter.models.ChatChoice
import org.zhavoronkov.openrouter.models.ChatCompletionResponse
import org.zhavoronkov.openrouter.models.ChatUsage
import org.zhavoronkov.openrouter.models.ServerToolUse

@DisplayName("ReplyFactsCollector")
class ReplyFactsCollectorTest {

    private fun json(text: String): JsonObject = JsonParser.parseString(text).asJsonObject

    @Test
    @DisplayName("a whole chat-completions response yields every fact at once")
    fun `a whole response yields every fact at once`() {
        val collector = ReplyFactsCollector()

        collector.observe(
            json(
                """{"id":"gen-1","model":"openai/gpt-5.2","provider":"OpenAI",
                    "choices":[{"message":{"role":"assistant","content":"hi"},"finish_reason":"stop"}],
                    "usage":{"prompt_tokens":1204,"completion_tokens":312,"cost":0.012,
                             "server_tool_use_details":{"web_search_requests":2}}}"""
            )
        )

        assertEquals(
            ReplyFacts(
                generationId = "gen-1",
                answeringModel = "openai/gpt-5.2",
                provider = "OpenAI",
                promptTokens = 1204,
                completionTokens = 312,
                cost = 0.012,
                finishReason = "stop",
                webSearches = 2
            ),
            collector.facts()
        )
    }

    @Test
    @DisplayName("web searches are also read under the older field name")
    fun `web searches are also read under the older field name`() {
        val collector = ReplyFactsCollector()

        collector.observe(json("""{"usage":{"server_tool_use":{"web_search_requests":1}}}"""))

        assertEquals(1, collector.facts().webSearches)
    }

    /**
     * A stream spreads the facts across chunks: the model and id on each, the finish reason on the
     * last content chunk, usage on a final chunk of its own. The collector keeps what each chunk
     * adds, so the record reads the same as for a whole response.
     */
    @Test
    @DisplayName("a stream's facts are gathered across its chunks")
    fun `a stream's facts are gathered across its chunks`() {
        val collector = ReplyFactsCollector()
        listOf(
            """{"id":"gen-2","model":"anthropic/claude-sonnet-4.5","provider":"Google Vertex",
                "choices":[{"delta":{"content":"he"},"finish_reason":null}]}""",
            """{"id":"gen-2","model":"anthropic/claude-sonnet-4.5",
                "choices":[{"delta":{"content":"llo"},"finish_reason":"length"}]}""",
            """{"id":"gen-2","model":"anthropic/claude-sonnet-4.5","choices":[],
                "usage":{"prompt_tokens":10,"completion_tokens":4096,"cost":0.03}}"""
        ).forEach { collector.observe(json(it)) }

        assertEquals(
            ReplyFacts(
                generationId = "gen-2",
                answeringModel = "anthropic/claude-sonnet-4.5",
                provider = "Google Vertex",
                promptTokens = 10,
                completionTokens = 4096,
                cost = 0.03,
                finishReason = "length",
                webSearches = 0
            ),
            collector.facts()
        )
    }

    @Test
    @DisplayName("a response that carries none of the facts yields empty facts, not a failure")
    fun `a response that carries none of the facts yields empty facts`() {
        val collector = ReplyFactsCollector()

        collector.observe(json("""{"object":"chat.completion.chunk","choices":[{"delta":{}}]}"""))

        assertEquals(ReplyFacts(), collector.facts())
    }

    /**
     * The chat has the response already parsed; it hands the collector that response serialised
     * back, so the typed model's field names must be the wire's for the facts to survive.
     */
    @Test
    @DisplayName("the chat's parsed response, serialised back, yields the same facts")
    fun `the chat's parsed response serialised back yields the same facts`() {
        val parsed = ChatCompletionResponse(
            id = "gen-3",
            model = "openai/gpt-5.2",
            provider = "OpenAI",
            choices = listOf(ChatChoice(finishReason = "length")),
            usage = ChatUsage(
                promptTokens = 5,
                completionTokens = 7,
                cost = 0.002,
                serverToolUse = ServerToolUse(webSearchRequests = 1)
            )
        )
        val collector = ReplyFactsCollector()

        collector.observe(Gson().toJsonTree(parsed).asJsonObject)

        assertEquals(
            ReplyFacts("gen-3", "openai/gpt-5.2", "OpenAI", 5, 7, 0.002, "length", 1),
            collector.facts()
        )
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @ValueSource(
        strings = [
            """{"id":7,"model":"  ","provider":{"name":"OpenAI"}}""",
            """{"choices":"stop","usage":"none"}""",
            """{"choices":["stop",{"finish_reason":3}]}""",
            """{"usage":{"prompt_tokens":"12","completion_tokens":[1],"cost":"0.1",
                "server_tool_use_details":{"web_search_requests":"2"}}}""",
            """{"usage":{"server_tool_use_details":"2","server_tool_use":3}}"""
        ]
    )
    @DisplayName("fields of another type, or blank, add nothing to the facts so far")
    fun `fields of another type add nothing`(text: String) {
        val collector = ReplyFactsCollector()
        collector.observe(json("""{"id":"gen-1","choices":[{"finish_reason":"stop"}],"usage":{"prompt_tokens":3}}"""))
        val before = collector.facts()

        collector.observe(json(text))

        assertEquals(before, collector.facts())
    }

    @Test
    @DisplayName("the older server_tool_use name is still read")
    fun `the older server tool use name is still read`() {
        val collector = ReplyFactsCollector()

        collector.observe(json("""{"usage":{"server_tool_use":{"web_search_requests":4}}}"""))

        assertEquals(4, collector.facts().webSearches)
    }
}
