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
import org.zhavoronkov.openrouter.models.OutputSchema

@DisplayName("ChatExchange")
class ChatExchangeTest {

    private fun modes(model: String, declared: List<String>?, schemas: List<OutputSchema> = emptyList()) =
        ChatExchange.outputModes(OutputModeContext(model, declared, schemas))

    private fun blocked(
        mode: OutputMode,
        model: String,
        declared: List<String>?,
        schemas: List<OutputSchema> = emptyList()
    ) = ChatExchange.sendBlockedReason(mode, OutputModeContext(model, declared, schemas))

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
     * unrecognised reasoning effort is dropped. This fails if either side changes without a
     * decision about the other.
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
    @DisplayName("Web Search sends OpenRouter's web search tool, carrying nothing but its type")
    fun `Web Search sends OpenRouter's web search tool`() {
        val request = ChatExchange.buildRequest("openai/gpt-5.2", messages, ChatRequestOptions(webSearch = true))

        assertEquals(
            JsonParser.parseString("""[{"type": "openrouter:web_search"}]"""),
            Gson().toJsonTree(request.tools),
            "with no tuning configured the tool must leave every choice to OpenRouter"
        )
    }

    /**
     * The deprecated `web` plugin is never sent: OpenRouter replaced it with the web search server
     * tool, and a Router's parameter block is the only thing left in `plugins`.
     */
    @ParameterizedTest(name = "{0}, routerParam={1}, webSearch={2}")
    @CsvSource(
        nullValues = ["NULL"],
        textBlock = """
        openai/gpt-5.2,  NULL,   false, NULL,                                          NULL
        openai/gpt-5.2,  NULL,   true,  NULL,                                          '[{"type":"openrouter:web_search"}]'
        openai/gpt-5.2,  medium, true,  NULL,                                          '[{"type":"openrouter:web_search"}]'
        openrouter/auto, medium, false, '[{"id":"auto-router","cost_tier":"medium"}]', NULL
        openrouter/auto, medium, true,  '[{"id":"auto-router","cost_tier":"medium"}]', '[{"type":"openrouter:web_search"}]'"""
    )
    @DisplayName("the plugins and tools for every combination of Web Search and Router selection")
    fun `the plugins and tools for every combination of Web Search and Router selection`(
        model: String,
        routerParam: String?,
        webSearch: Boolean,
        expectedPlugins: String?,
        expectedTools: String?
    ) {
        val options = ChatRequestOptions(routerParam = routerParam, webSearch = webSearch)

        val request = ChatExchange.buildRequest(model, messages, options)

        assertEquals(expectedPlugins?.let(JsonParser::parseString), request.plugins?.let { Gson().toJsonTree(it) })
        assertEquals(expectedTools?.let(JsonParser::parseString), request.tools?.let { Gson().toJsonTree(it) })
    }

    /**
     * The model decides whether to search, so asking for a search is not the same as one running;
     * the footer reports what the response says happened.
     */
    @Test
    @DisplayName("a reply whose response reports searches says so in its footer")
    fun `a reply whose response reports searches says so in its footer`() {
        val reply = response(
            """{"model": "openai/gpt-5.2", "provider": "OpenAI",
                "usage": {"cost": 0.01, "server_tool_use_details": {"web_search_requests": 2}}}"""
        )

        val summary = ChatExchange.summarizeReply(sent("openai/gpt-5.2", ChatRequestOptions(webSearch = true)), reply)

        assertEquals(2, summary.webSearches)
        assertEquals("openai/gpt-5.2 · OpenAI · \$0.01 · 2 web searches", summary.facts)
    }

    /** With a server tool the reply's provider field names OpenAI whatever served it. */
    @Test
    @DisplayName("a reply whose provider cannot be believed is summarised without one")
    fun `a reply whose provider cannot be believed is summarised without one`() {
        val reply = response("""{"model": "openai/gpt-5.2", "provider": "OpenAI", "usage": {"cost": 0.01}}""")

        val request = sent("openai/gpt-5.2", ChatRequestOptions())
        val summary = ChatExchange.summarizeReply(request, reply, replyNamesProvider = false)

        assertEquals("openai/gpt-5.2 · \$0.01", summary.facts)
    }

    @Test
    @DisplayName("one search is reported as one")
    fun `one search is reported as one`() {
        val summary = ReplySummary(requestedModel = "m", webSearches = 1)

        assertEquals("m · 1 web search", summary.facts)
    }

    @Test
    @DisplayName("a reply that searched nothing carries no search marker, even when a search was allowed")
    fun `a reply that searched nothing carries no search marker`() {
        val reply = response(
            """{"model": "openai/gpt-5.2", "usage": {"server_tool_use": {"web_search_requests": 0}}}"""
        )

        val summary = ChatExchange.summarizeReply(sent("openai/gpt-5.2", ChatRequestOptions(webSearch = true)), reply)

        assertEquals(0, summary.webSearches)
        assertFalse(summary.facts.contains("web"))
    }

    // --- Output Mode ----------------------------------------------------------

    @Test
    @DisplayName("Off sends no response format")
    fun `Off sends no response format`() {
        val options = ChatRequestOptions(outputMode = OutputMode.Off)

        val request = ChatExchange.buildRequest("openai/gpt-5.2", messages, options)

        assertNull(request.responseFormat)
    }

    @Test
    @DisplayName("plain JSON asks OpenRouter for a JSON object")
    fun `plain JSON asks OpenRouter for a JSON object`() {
        val options = ChatRequestOptions(outputMode = OutputMode.PlainJson)

        val request = ChatExchange.buildRequest("openai/gpt-5.2", messages, options)

        assertEquals(
            JsonParser.parseString("""{"type": "json_object"}"""),
            Gson().toJsonTree(request).asJsonObject["response_format"]
        )
    }

    /**
     * The four combinations of the two flags a model can declare, and a Model whose declarations are
     * not known. Plain JSON is gated on `response_format` alone: `structured_outputs` is a separate
     * capability, and the catalogue has models declaring it without `response_format`, which a
     * single shared gate would get wrong.
     */
    @ParameterizedTest(name = "{0}")
    @CsvSource(
        nullValues = ["NULL"],
        textBlock = """
        'response_format structured_outputs', true
        'response_format',                    true
        'structured_outputs',                 false
        'tools',                              false
        NULL,                                 false"""
    )
    @DisplayName("plain JSON is offered exactly when the Model declares response_format")
    fun `plain JSON is offered exactly when the Model declares response_format`(
        declared: String?,
        jsonOffered: Boolean
    ) {
        val choices = modes("some/model", declared?.split(' '))

        assertEquals(listOf(OutputMode.Off, OutputMode.PlainJson), choices.map { it.mode })
        assertNull(choices.first().unsupportedReason, "Off is always available")
        assertEquals(jsonOffered, choices.last().unsupportedReason == null)
    }

    @Test
    @DisplayName("an unavailable Output Mode says why, naming the Model")
    fun `an unavailable Output Mode says why naming the Model`() {
        val json = modes("some/model", listOf("tools"))
            .single { it.mode == OutputMode.PlainJson }

        assertEquals("some/model does not support JSON output", json.unsupportedReason)
    }

    /**
     * A Router, a preset or a Model still loading has no declarations to read. That is not the same
     * as lacking the capability, and the reason must not claim it is.
     */
    @Test
    @DisplayName("a Model whose declarations are not known is not said to lack JSON")
    fun `a Model whose declarations are not known is not said to lack JSON`() {
        val json = modes("openrouter/auto", null)
            .single { it.mode == OutputMode.PlainJson }

        assertEquals("JSON output support is not known for openrouter/auto", json.unsupportedReason)
    }

    @Test
    @DisplayName("a selection the Model can serve does not block sending")
    fun `a selection the Model can serve does not block sending`() {
        assertNull(blocked(OutputMode.Off, "some/model", null))
        assertNull(
            blocked(OutputMode.PlainJson, "some/model", listOf("response_format"))
        )
    }

    /**
     * Kept and blocked rather than reset: silently sending without the JSON the user asked for would
     * make them think the reply is constrained when it is not, and sending it anyway would only have
     * the server refuse it.
     */
    @Test
    @DisplayName("a selection the Model cannot serve blocks sending and says why")
    fun `a selection the Model cannot serve blocks sending and says why`() {
        assertEquals(
            "some/model does not support JSON output. Choose another output mode to send.",
            blocked(OutputMode.PlainJson, "some/model", listOf("tools"))
        )
    }

    // --- Output Schemas ---------------------------------------------------------

    private val features = OutputSchema(
        name = "features",
        strict = true,
        schema = """{"type": "object", "properties": {"name": {"type": "string"}}, "required": ["name"]}"""
    )
    private val summary = OutputSchema(name = "summary", strict = false, schema = """{"type": "object"}""")
    private val saved = listOf(features, summary)

    @Test
    @DisplayName("a saved schema is sent with its own name, strict flag and body")
    fun `a saved schema is sent with its own name strict flag and body`() {
        val options = ChatRequestOptions(outputMode = OutputMode.Schema("features"))

        val request = ChatExchange.buildRequest("openai/gpt-5.2", messages, options, schemas = saved)

        val expected = """
            {
              "type": "json_schema",
              "json_schema": {
                "name": "features",
                "strict": true,
                "schema": {"type": "object", "properties": {"name": {"type": "string"}}, "required": ["name"]}
              }
            }
        """
        assertEquals(JsonParser.parseString(expected), Gson().toJsonTree(request).asJsonObject["response_format"])
    }

    @Test
    @DisplayName("a schema's strict flag is sent as it was saved, false included")
    fun `a schema's strict flag is sent as it was saved false included`() {
        val options = ChatRequestOptions(outputMode = OutputMode.Schema("summary"))

        val request = ChatExchange.buildRequest("openai/gpt-5.2", messages, options, schemas = saved)

        val jsonSchema = Gson().toJsonTree(request).asJsonObject["response_format"].asJsonObject["json_schema"]
        assertEquals(JsonPrimitive(false), jsonSchema.asJsonObject["strict"])
        assertEquals(JsonPrimitive("summary"), jsonSchema.asJsonObject["name"])
    }

    /**
     * Every capability combination. The two gates are independent: `response_format` gates plain
     * JSON and `structured_outputs` gates the schemas, and the catalogue has models declaring
     * either without the other, which a shared gate would get wrong in both directions.
     */
    @ParameterizedTest(name = "{0}")
    @CsvSource(
        nullValues = ["NULL"],
        textBlock = """
        'response_format structured_outputs', true,  true
        'response_format',                    true,  false
        'structured_outputs',                 false, true
        'tools',                              false, false
        NULL,                                 false, false"""
    )
    @DisplayName("which Output Modes are offered for each capability combination")
    fun `which Output Modes are offered for each capability combination`(
        declared: String?,
        jsonOffered: Boolean,
        schemasOffered: Boolean
    ) {
        val choices = modes("some/model", declared?.split(' '), saved)

        assertEquals(
            listOf(OutputMode.Off, OutputMode.PlainJson, OutputMode.Schema("features"), OutputMode.Schema("summary")),
            choices.map { it.mode },
            "every saved schema is listed by name, after Off and plain JSON"
        )
        assertTrue(choices[0].supported, "Off is always available")
        assertEquals(jsonOffered, choices[1].supported)
        assertEquals(listOf(schemasOffered, schemasOffered), choices.drop(2).map { it.supported })
    }

    @Test
    @DisplayName("a schema the Model cannot take says why")
    fun `a schema the Model cannot take says why`() {
        val choice = modes("some/model", listOf("response_format"), saved)
            .single { it.mode == OutputMode.Schema("features") }

        assertEquals("some/model does not support schema-constrained output", choice.unsupportedReason)
    }

    @Test
    @DisplayName("a saved body that parses but is not an object is not offered either")
    fun `a saved body that parses but is not an object is not offered either`() {
        val notAnObject = OutputSchema(name = "list", schema = "[]")

        val choice = modes("some/model", listOf("structured_outputs"), listOf(notAnObject)).last()

        assertFalse(choice.supported)
    }

    @Test
    @DisplayName("a schema whose saved body no longer parses is not offered")
    fun `a schema whose saved body no longer parses is not offered`() {
        val broken = OutputSchema(name = "broken", schema = "{ not json")

        val choice = modes("some/model", listOf("structured_outputs"), listOf(broken)).last()

        assertEquals("broken is not a valid JSON object; fix it in Settings", choice.unsupportedReason)
    }

    @Test
    @DisplayName("switching to a Model that cannot take the selected schema blocks sending")
    fun `switching to a Model that cannot take the selected schema blocks sending`() {
        val selected = OutputMode.Schema("features")

        assertNull(blocked(selected, "able/model", listOf("structured_outputs"), saved))
        assertEquals(
            "plain/model does not support schema-constrained output. Choose another output mode to send.",
            blocked(selected, "plain/model", listOf("response_format"), saved)
        )
    }

    /**
     * Deleting the schema that is selected must not leave a request naming a schema that no longer
     * exists; the selection stays, and sending waits for the user to choose again.
     */
    @Test
    @DisplayName("a selected schema that was deleted blocks sending rather than being sent")
    fun `a selected schema that was deleted blocks sending rather than being sent`() {
        val deleted = OutputMode.Schema("gone")

        assertEquals(
            "gone is no longer available. Choose another output mode to send.",
            blocked(deleted, "able/model", listOf("structured_outputs"), saved)
        )
    }

    /** Names are unique without regard to case, so a selection finds its schema the same way. */
    @Test
    @DisplayName("a selected schema is found by name whatever the case")
    fun `a selected schema is found by name whatever the case`() {
        val options = ChatRequestOptions(outputMode = OutputMode.Schema("Features"))

        assertNull(
            blocked(options.outputMode, "able/model", listOf("structured_outputs"), saved)
        )
        val request = ChatExchange.buildRequest("able/model", messages, options, schemas = saved)
        assertEquals("features", request.responseFormat?.jsonSchema?.name)
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
