package org.zhavoronkov.openrouter.toolwindow.chat

import com.google.gson.Gson
import com.google.gson.JsonParser
import com.google.gson.JsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.zhavoronkov.openrouter.models.ChatMessage
import org.zhavoronkov.openrouter.models.WebSearchEngine
import org.zhavoronkov.openrouter.models.WebSearchSettings

@DisplayName("ChatExchange with Web Search")
class ChatExchangeWebSearchTest {

    private val messages = listOf(ChatMessage(role = "user", content = JsonPrimitive("hi")))

    /** The web search tool as OpenRouter receives it, for a message with Web Search on. */
    private fun webTool(settings: WebSearchSettings, model: String = "openai/gpt-5.2") =
        Gson().toJsonTree(
            ChatExchange.buildRequest(model, messages, ChatRequestOptions(webSearch = true), settings).tools
        ).asJsonArray.single { it.asJsonObject["type"].asString == "openrouter:web_search" }

    @Test
    @DisplayName("a default configuration sends the bare tool, with no parameters at all")
    fun `a default configuration sends the bare tool`() {
        assertEquals(JsonParser.parseString("""{"type": "openrouter:web_search"}"""), webTool(WebSearchSettings()))
    }

    @Test
    @DisplayName("every configured value appears in the tool's parameters, in the tool's spelling")
    fun `every configured value appears in the tool's parameters`() {
        val settings = WebSearchSettings(
            engine = WebSearchEngine.EXA,
            maxResults = 8,
            includeDomains = listOf("docs.gradle.org", "*.jetbrains.com"),
            excludeDomains = listOf("pinterest.com"),
            mode = "deep"
        )

        val expected = """
            {
              "type": "openrouter:web_search",
              "parameters": {
                "engine": "exa",
                "max_results": 8,
                "allowed_domains": ["docs.gradle.org", "*.jetbrains.com"],
                "excluded_domains": ["pinterest.com"],
                "mode": "deep"
              }
            }
        """
        assertEquals(JsonParser.parseString(expected), webTool(settings))
    }

    @Test
    @DisplayName("an automatic engine is left out, letting OpenRouter choose")
    fun `an automatic engine is left out`() {
        val tool = webTool(WebSearchSettings(engine = null, maxResults = 3))

        assertEquals(
            JsonParser.parseString("""{"type": "openrouter:web_search", "parameters": {"max_results": 3}}"""),
            tool
        )
    }

    @Test
    @DisplayName("a result count equal to OpenRouter's default is left out")
    fun `a result count equal to the default is left out`() {
        val tool = webTool(WebSearchSettings(maxResults = WebSearchSettings.DEFAULT_MAX_RESULTS))

        assertEquals(JsonParser.parseString("""{"type": "openrouter:web_search"}"""), tool)
    }

    /**
     * Mode belongs to the engine: Exa and Parallel each accept their own set, and no other engine
     * documents one. A mode kept from an engine the user has since switched away from must not
     * travel with the request, where it would be meaningless or refused.
     */
    @Test
    @DisplayName("a mode that does not belong to the chosen engine is left out")
    fun `a mode that does not belong to the chosen engine is left out`() {
        assertEquals(
            JsonParser.parseString("""{"type": "openrouter:web_search", "parameters": {"engine": "parallel"}}"""),
            webTool(WebSearchSettings(engine = WebSearchEngine.PARALLEL, mode = "deep"))
        )
        assertEquals(
            JsonParser.parseString("""{"type": "openrouter:web_search"}"""),
            webTool(WebSearchSettings(engine = null, mode = "deep")),
            "with the engine left to OpenRouter no mode can be known to apply"
        )
    }

    @Test
    @DisplayName("an engine's own default mode is left out like every other default")
    fun `an engine's own default mode is left out`() {
        assertEquals(
            JsonParser.parseString("""{"type": "openrouter:web_search", "parameters": {"engine": "exa"}}"""),
            webTool(WebSearchSettings(engine = WebSearchEngine.EXA, mode = "auto"))
        )
    }

    @Test
    @DisplayName("tuning alone never turns a search on")
    fun `tuning alone never turns a search on`() {
        val tuned = WebSearchSettings(engine = WebSearchEngine.EXA)

        val request = ChatExchange.buildRequest("openai/gpt-5.2", messages, ChatRequestOptions(), tuned)

        assertNull(request.tools)
        assertNull(request.plugins)
    }
}
