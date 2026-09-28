package org.zhavoronkov.openrouter.toolwindow.chat

import com.google.gson.Gson
import com.google.gson.JsonParser
import com.google.gson.JsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.NullSource
import org.junit.jupiter.params.provider.ValueSource
import org.zhavoronkov.openrouter.models.ChatChoice
import org.zhavoronkov.openrouter.models.ChatCompletionResponse
import org.zhavoronkov.openrouter.models.ChatMessage
import org.zhavoronkov.openrouter.models.ChatUsage

@DisplayName("ChatExchange")
class ChatExchangeTest {

    private val messages = listOf(ChatMessage(role = "user", content = JsonPrimitive("hi")))

    @Test
    @DisplayName("a request carries the model, the conversation and the chat's fixed sampling settings")
    fun `a request carries the model the conversation and the fixed sampling settings`() {
        val request = ChatExchange.buildRequest("openai/gpt-5.2", messages, ChatRequestOptions())

        assertEquals("openai/gpt-5.2", request.model)
        assertEquals(messages, request.messages)
        assertEquals(4096, request.maxTokens)
        assertEquals(0.7, request.temperature)
        assertEquals(false, request.stream, "the chat window reads replies whole, not streamed")
    }

    @Test
    @DisplayName("nothing optional is sent when the user has changed nothing")
    fun `nothing optional is sent when the user has changed nothing`() {
        val request = ChatExchange.buildRequest("openai/gpt-5.2", messages, ChatRequestOptions())

        assertNull(request.reasoning, "reasoning must be absent, not a default-valued block")
        assertNull(request.verbosity)
        assertNull(request.plugins)
    }

    @ParameterizedTest
    @CsvSource(
        "None,    none",
        "Minimal, minimal",
        "Low,     low",
        "Medium,  medium",
        "High,    high",
        "XHigh,   xhigh",
    )
    @DisplayName("a chosen reasoning effort is sent as OpenRouter spells it")
    fun `a chosen reasoning effort is sent as OpenRouter spells it`(chosen: String, expected: String) {
        val request = ChatExchange.buildRequest("openai/gpt-5.2", messages, ChatRequestOptions(reasoning = chosen))

        assertEquals(expected, request.reasoning?.effort)
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = ["Default", "Something the combo never offered"])
    @DisplayName("an unrecognised reasoning selection sends no reasoning block at all")
    fun `an unrecognised reasoning selection sends no reasoning block at all`(chosen: String?) {
        val request = ChatExchange.buildRequest("openai/gpt-5.2", messages, ChatRequestOptions(reasoning = chosen))

        assertNull(request.reasoning, "an effort the plugin does not recognise must not reach OpenRouter")
    }

    @ParameterizedTest
    @CsvSource(
        "Low,    low",
        "Medium, medium",
        "High,   high",
        "XHigh,  xhigh",
        "Max,    max",
    )
    @DisplayName("a chosen verbosity is sent as OpenRouter spells it")
    fun `a chosen verbosity is sent as OpenRouter spells it`(chosen: String, expected: String) {
        val request = ChatExchange.buildRequest("openai/gpt-5.2", messages, ChatRequestOptions(verbosity = chosen))

        assertEquals(expected, request.verbosity)
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = ["Default"])
    @DisplayName("an unset verbosity sends no verbosity at all")
    fun `an unset verbosity sends no verbosity at all`(chosen: String?) {
        val request = ChatExchange.buildRequest("openai/gpt-5.2", messages, ChatRequestOptions(verbosity = chosen))

        assertNull(request.verbosity)
    }

    /**
     * Deliberately asymmetric with reasoning, and pinned so the asymmetry is a decision rather
     * than a surprise: verbosity passes anything but "Default" through, lower-cased, while an
     * unrecognised reasoning effort is dropped. The asymmetry is inherited, not chosen; this
     * fails if anyone changes one side without deciding about the other.
     */
    @Test
    @DisplayName("an unrecognised verbosity is passed through lower-cased, unlike reasoning")
    fun `an unrecognised verbosity is passed through lower-cased unlike reasoning`() {
        val options = ChatRequestOptions(verbosity = "Whatever", reasoning = "Whatever")
        val request = ChatExchange.buildRequest("openai/gpt-5.2", messages, options)

        assertEquals("whatever", request.verbosity)
        assertNull(request.reasoning)
    }

    // --- The Router's parameter block ---------------------------------------

    @Test
    @DisplayName("a Router's chosen parameter is sent as a plugin block")
    fun `a Router's chosen parameter is sent as a plugin block`() {
        val options = ChatRequestOptions(routerParam = "medium")

        val request = ChatExchange.buildRequest("openrouter/auto", messages, options)

        assertEquals(1, request.plugins?.size)
        assertEquals("auto-router", request.plugins?.first()?.id)
        assertEquals(mapOf("cost_tier" to "medium"), request.plugins?.first()?.params)
    }

    @Test
    @DisplayName("a Router with nothing chosen sends no plugin block")
    fun `a Router with nothing chosen sends no plugin block`() {
        val request = ChatExchange.buildRequest("openrouter/auto", messages, ChatRequestOptions())

        assertNull(request.plugins, "an unset Router parameter must not become an empty plugins array")
    }

    /**
     * The parameter combo keeps whatever was last shown in it, so a value can still be sitting
     * there when the user picks an ordinary Model. It must not travel with the request.
     */
    @Test
    @DisplayName("an ordinary Model sends no plugin block even when a Router parameter is left over")
    fun `an ordinary Model sends no plugin block even when a Router parameter is left over`() {
        val options = ChatRequestOptions(routerParam = "medium")

        val request = ChatExchange.buildRequest("openai/gpt-5.2", messages, options)

        assertNull(request.plugins)
    }

    // Which slugs are Routers, and which of them take a parameter, is RouterCatalog's knowledge,
    // asserted in RouterRequestBuilderTest. What belongs here is that the user's selection reaches
    // it and that its answer reaches the request.

    // --- Web Search ------------------------------------------------------------

    @Test
    @DisplayName("Web Search sends the bare web plugin entry, carrying nothing but its id")
    fun `Web Search sends the bare web plugin entry`() {
        val request = ChatExchange.buildRequest("openai/gpt-5.2", messages, ChatRequestOptions(webSearch = true))

        assertEquals(
            JsonParser.parseString("""[{"id": "web"}]"""),
            Gson().toJsonTree(request.plugins),
            "with no tuning configured the entry must leave every choice to OpenRouter"
        )
    }

    /**
     * Every combination of the toggle and the Router selection, written as the plugins array
     * OpenRouter receives. The Router's block and the web entry share one array, and neither may
     * overwrite the other.
     */
    @ParameterizedTest(name = "{0}, routerParam={1}, webSearch={2}")
    @CsvSource(
        nullValues = ["NULL"],
        textBlock = """
        openai/gpt-5.2,  NULL,   false, NULL
        openai/gpt-5.2,  NULL,   true,  '[{"id":"web"}]'
        openai/gpt-5.2,  medium, false, NULL
        openai/gpt-5.2,  medium, true,  '[{"id":"web"}]'
        openrouter/auto, NULL,   false, NULL
        openrouter/auto, NULL,   true,  '[{"id":"web"}]'
        openrouter/auto, medium, false, '[{"id":"auto-router","cost_tier":"medium"}]'
        openrouter/auto, medium, true,  '[{"id":"auto-router","cost_tier":"medium"},{"id":"web"}]'"""
    )
    @DisplayName("the plugins array for every combination of Web Search and Router selection")
    fun `the plugins array for every combination of Web Search and Router selection`(
        model: String,
        routerParam: String?,
        webSearch: Boolean,
        expected: String?
    ) {
        val options = ChatRequestOptions(routerParam = routerParam, webSearch = webSearch)

        val plugins = ChatExchange.buildRequest(model, messages, options).plugins

        if (expected == null) {
            assertNull(plugins, "nothing to attach must leave the plugins field out, not send an empty array")
        } else {
            assertEquals(JsonParser.parseString(expected), Gson().toJsonTree(plugins))
        }
    }

    @Test
    @DisplayName("a reply to a request that web search says so in its footer")
    fun `a reply to a request that web search says so in its footer`() {
        val reply = ChatCompletionResponse(
            model = "openai/gpt-5.2",
            provider = "OpenAI",
            usage = ChatUsage(cost = 0.01)
        )

        val summary = ChatExchange.summarizeReply(sent("openai/gpt-5.2", ChatRequestOptions(webSearch = true)), reply)

        assertTrue(summary.searched)
        assertEquals("openai/gpt-5.2 · OpenAI · \$0.01 · web search", summary.facts)
    }

    @Test
    @DisplayName("a reply to a request that did not search the web carries no search marker")
    fun `a reply to a request that did not search the web carries no search marker`() {
        val reply = ChatCompletionResponse(model = "openrouter/auto")
        val request = sent("openrouter/auto", ChatRequestOptions(routerParam = "medium"))

        val summary = ChatExchange.summarizeReply(request, reply)

        assertFalse(summary.searched, "a Router's own plugin entry is not a web search")
        assertFalse(summary.facts.contains("web"))
    }

    // --- The vocabulary the controls offer -----------------------------------

    @Test
    @DisplayName("both controls offer Default first, so nothing is chosen until the user chooses")
    fun `both controls offer Default first so nothing is chosen until the user chooses`() {
        assertEquals(ChatExchange.UNCHANGED, ChatExchange.REASONING_CHOICES.first())
        assertEquals(ChatExchange.UNCHANGED, ChatExchange.VERBOSITY_CHOICES.first())
    }

    /**
     * A label the controls can show but the request cannot express is dropped silently rather than
     * failing to compile, which is why the labels and their translation live in one place. This
     * fails the moment they drift apart.
     */
    @Test
    @DisplayName("every reasoning effort the controls offer reaches the request")
    fun `every reasoning effort the controls offer reaches the request`() {
        ChatExchange.REASONING_CHOICES.filterNot { it == ChatExchange.UNCHANGED }.forEach { choice ->
            val request =
                ChatExchange.buildRequest("openai/gpt-5.2", messages, ChatRequestOptions(reasoning = choice))

            assertEquals(choice.lowercase(), request.reasoning?.effort, "'${'$'}choice' reached OpenRouter as nothing")
        }
    }

    @Test
    @DisplayName("every verbosity the controls offer reaches the request")
    fun `every verbosity the controls offer reaches the request`() {
        ChatExchange.VERBOSITY_CHOICES.filterNot { it == ChatExchange.UNCHANGED }.forEach { choice ->
            val request =
                ChatExchange.buildRequest("openai/gpt-5.2", messages, ChatRequestOptions(verbosity = choice))

            assertEquals(choice.lowercase(), request.verbosity, "'${'$'}choice' reached OpenRouter as nothing")
        }
    }

    // --- The wire shape ------------------------------------------------------

    /**
     * Pins the whole request as OpenRouter receives it, so nothing here can quietly change what
     * goes out. The expected document is written from OpenRouter's chat-completions contract - the
     * field names it documents - rather than read back from the plugin's own output, so it can
     * disagree with the code; trees are compared rather than strings, because the order Gson
     * happens to emit fields in is not part of the contract.
     *
     * A bare Gson is deliberate: PluginConfig carries its own adapter so a Router's parameters
     * flatten next to the plugin id under any Gson, and this asserts that they do.
     */
    @Test
    @DisplayName("the assembled request matches OpenRouter's chat-completions contract")
    fun `the assembled request matches OpenRouter's chat-completions contract`() {
        val options = ChatRequestOptions(reasoning = "High", verbosity = "Low", routerParam = "medium")

        val request = ChatExchange.buildRequest("openrouter/auto", messages, options)

        val expected = """
            {
              "model": "openrouter/auto",
              "messages": [{"role": "user", "content": "hi"}],
              "temperature": 0.7,
              "max_tokens": 4096,
              "stream": false,
              "reasoning": {"effort": "high"},
              "verbosity": "low",
              "plugins": [{"id": "auto-router", "cost_tier": "medium"}]
            }
        """
        assertEquals(JsonParser.parseString(expected), Gson().toJsonTree(request))
    }

    @Test
    @DisplayName("a request with nothing chosen omits every optional field rather than nulling it")
    fun `a request with nothing chosen omits every optional field rather than nulling it`() {
        val request = ChatExchange.buildRequest("openai/gpt-5.2", messages, ChatRequestOptions())

        val expected = """
            {
              "model": "openai/gpt-5.2",
              "messages": [{"role": "user", "content": "hi"}],
              "temperature": 0.7,
              "max_tokens": 4096,
              "stream": false
            }
        """
        assertEquals(JsonParser.parseString(expected), Gson().toJsonTree(request))
    }

    // --- Reading a reply back ------------------------------------------------

    private fun sent(model: String, options: ChatRequestOptions = ChatRequestOptions()) =
        ChatExchange.buildRequest(model, messages, options)

    private fun response(json: String): ChatCompletionResponse =
        Gson().fromJson(json, ChatCompletionResponse::class.java)

    /**
     * Written as OpenRouter returns a completion, so the test covers reading the two fields off the
     * wire as well as what the summary makes of them. `usage.cost` arrives without anything in the
     * request asking for it.
     */
    @Test
    @DisplayName("a reply reports the model that answered, the provider that served it and what it cost")
    fun `a reply reports the model the provider and the cost`() {
        val reply = response(
            """
            {
              "model": "anthropic/claude-sonnet-4.5",
              "provider": "Google Vertex",
              "choices": [{"message": {"role": "assistant", "content": "hi"}, "finish_reason": "stop"}],
              "usage": {"prompt_tokens": 10, "completion_tokens": 5, "total_tokens": 15, "cost": 0.00042}
            }
            """
        )

        val summary = ChatExchange.summarizeReply(sent("anthropic/claude-sonnet-4.5"), reply)

        assertEquals(
            ReplySummary(
                requestedModel = "anthropic/claude-sonnet-4.5",
                respondingModel = "anthropic/claude-sonnet-4.5",
                provider = "Google Vertex",
                cost = 0.00042,
                finishReason = "stop"
            ),
            summary
        )
        assertEquals("anthropic/claude-sonnet-4.5 · Google Vertex · \$0.00042", summary.facts)
    }

    @Test
    @DisplayName("a Router's reply keeps the Routed to wording")
    fun `a Router's reply keeps the Routed to wording`() {
        val reply = ChatCompletionResponse(model = "anthropic/claude-sonnet-4.5", provider = "Anthropic")

        val summary = ChatExchange.summarizeReply(sent("openrouter/auto"), reply)

        assertEquals("Routed to anthropic/claude-sonnet-4.5", summary.answeringModel)
        assertEquals("Routed to anthropic/claude-sonnet-4.5 · Anthropic", summary.facts)
    }

    @Test
    @DisplayName("a directly chosen Model's reply reports its own slug")
    fun `a directly chosen Model's reply reports its own slug`() {
        val reply = ChatCompletionResponse(model = "openai/gpt-5.2")

        val summary = ChatExchange.summarizeReply(sent("openai/gpt-5.2"), reply)

        assertEquals("openai/gpt-5.2", summary.answeringModel)
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = ["", "   "])
    @DisplayName("a reply that does not name its model reports the model that was asked for")
    fun `a reply that does not name its model reports the model that was asked for`(responseModel: String?) {
        val reply = ChatCompletionResponse(model = responseModel)

        val summary = ChatExchange.summarizeReply(sent("openai/gpt-5.2"), reply)

        assertEquals("openai/gpt-5.2", summary.answeringModel)
    }

    @Test
    @DisplayName("provider and cost are left out when the reply does not carry them")
    fun `provider and cost are left out when the reply does not carry them`() {
        val reply = ChatCompletionResponse(model = "openai/gpt-5.2", provider = " ", usage = ChatUsage(totalTokens = 3))

        val summary = ChatExchange.summarizeReply(sent("openai/gpt-5.2"), reply)

        assertNull(summary.provider)
        assertNull(summary.cost)
        assertEquals("openai/gpt-5.2", summary.facts, "absent facts must not show up as zero or as a blank")
    }

    /** Free models really do cost nothing, and saying so is a fact rather than a missing one. */
    @Test
    @DisplayName("a reported cost of zero is shown, not taken for a missing cost")
    fun `a reported cost of zero is shown`() {
        val reply = ChatCompletionResponse(model = "openai/gpt-oss:free", usage = ChatUsage(cost = 0.0))

        val summary = ChatExchange.summarizeReply(sent("openai/gpt-oss:free"), reply)

        assertEquals("openai/gpt-oss:free · \$0", summary.facts)
    }

    @ParameterizedTest
    @CsvSource(
        "0.000012,  \$0.000012",
        "0.00042,   \$0.00042",
        "0.0012345, \$0.0012",
        "0.0199,    \$0.020",
        "0.003,     \$0.003",
        "0.02,      \$0.02",
        "0.25,      \$0.25",
        "1.5,       \$1.50",
        "0.996,     \$1.00",
        "12.345,    \$12.35",
    )
    @DisplayName("a cost is shown to two significant figures below a dollar, and to the cent above")
    fun `a cost is shown to two significant figures below a dollar`(cost: Double, expected: String) {
        assertEquals("m · $expected", ReplySummary(requestedModel = "m", cost = cost).facts)
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = ["stop", "tool_calls"])
    @DisplayName("a reply that stopped normally carries no warning")
    fun `a reply that stopped normally carries no warning`(finishReason: String?) {
        val reply = ChatCompletionResponse(choices = listOf(ChatChoice(finishReason = finishReason)))

        assertNull(ChatExchange.summarizeReply(sent("openai/gpt-5.2"), reply).warning)
    }

    @ParameterizedTest
    @CsvSource(
        "length,         Cut off at the token limit",
        "content_filter, Stopped by a content filter",
        "error,          Stopped by an error at the provider",
        "something_new,  Stopped early (something_new)",
    )
    @DisplayName("a reply that did not stop normally warns, naming why")
    fun `a reply that did not stop normally warns naming why`(finishReason: String, expected: String) {
        val reply = ChatCompletionResponse(choices = listOf(ChatChoice(finishReason = finishReason)))

        val summary = ChatExchange.summarizeReply(sent("openai/gpt-5.2"), reply)

        assertEquals(finishReason, summary.finishReason)
        assertEquals(expected, summary.warning)
    }
}
