package org.zhavoronkov.openrouter.toolwindow.chat

import com.google.gson.Gson
import com.google.gson.JsonParser
import com.google.gson.JsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.NullSource
import org.junit.jupiter.params.provider.ValueSource
import org.zhavoronkov.openrouter.models.ChatMessage
import org.zhavoronkov.openrouter.models.WebSearchEngine
import org.zhavoronkov.openrouter.models.WebSearchSettings

@DisplayName("WebSearchSettings")
class WebSearchSettingsTest {

    private val messages = listOf(ChatMessage(role = "user", content = JsonPrimitive("hi")))

    /** The web entry as OpenRouter receives it, for a message with Web Search on. */
    private fun webEntry(settings: WebSearchSettings, model: String = "openai/gpt-5.2") =
        Gson().toJsonTree(
            ChatExchange.buildRequest(model, messages, ChatRequestOptions(webSearch = true), settings).plugins
        ).asJsonArray.single { it.asJsonObject["id"].asString == "web" }

    @Test
    @DisplayName("a default configuration sends the bare web entry")
    fun `a default configuration sends the bare web entry`() {
        assertEquals(JsonParser.parseString("""{"id": "web"}"""), webEntry(WebSearchSettings()))
    }

    @Test
    @DisplayName("every configured value appears in the web entry")
    fun `every configured value appears in the web entry`() {
        val settings = WebSearchSettings(
            engine = WebSearchEngine.EXA,
            maxResults = 8,
            includeDomains = listOf("docs.gradle.org", "*.jetbrains.com"),
            excludeDomains = listOf("pinterest.com"),
            mode = "deep"
        )

        val expected = """
            {
              "id": "web",
              "engine": "exa",
              "max_results": 8,
              "include_domains": ["docs.gradle.org", "*.jetbrains.com"],
              "exclude_domains": ["pinterest.com"],
              "mode": "deep"
            }
        """
        assertEquals(JsonParser.parseString(expected), webEntry(settings))
    }

    @Test
    @DisplayName("an automatic engine is left out, letting OpenRouter choose")
    fun `an automatic engine is left out`() {
        val entry = webEntry(WebSearchSettings(engine = null, maxResults = 3))

        assertEquals(JsonParser.parseString("""{"id": "web", "max_results": 3}"""), entry)
    }

    @Test
    @DisplayName("a result count equal to OpenRouter's default is left out")
    fun `a result count equal to the default is left out`() {
        val entry = webEntry(WebSearchSettings(maxResults = WebSearchSettings.DEFAULT_MAX_RESULTS))

        assertEquals(JsonParser.parseString("""{"id": "web"}"""), entry)
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
            JsonParser.parseString("""{"id": "web", "engine": "parallel"}"""),
            webEntry(WebSearchSettings(engine = WebSearchEngine.PARALLEL, mode = "deep"))
        )
        assertEquals(
            JsonParser.parseString("""{"id": "web"}"""),
            webEntry(WebSearchSettings(engine = null, mode = "deep")),
            "with the engine left to OpenRouter no mode can be known to apply"
        )
    }

    @Test
    @DisplayName("an engine's own default mode is left out like every other default")
    fun `an engine's own default mode is left out`() {
        assertEquals(
            JsonParser.parseString("""{"id": "web", "engine": "exa"}"""),
            webEntry(WebSearchSettings(engine = WebSearchEngine.EXA, mode = "auto"))
        )
        assertEquals(
            JsonParser.parseString("""{"id": "web", "engine": "parallel"}"""),
            webEntry(WebSearchSettings(engine = WebSearchEngine.PARALLEL, mode = "basic"))
        )
    }

    @Test
    @DisplayName("only Exa and Parallel offer modes, each its own, without its default")
    fun `only Exa and Parallel offer modes`() {
        assertEquals(
            listOf("instant", "fast", "deep-lite", "deep", "deep-reasoning"),
            WebSearchEngine.EXA.selectableModes
        )
        assertEquals(listOf("turbo", "fast", "advanced"), WebSearchEngine.PARALLEL.selectableModes)
        assertEquals(listOf(WebSearchEngine.EXA, WebSearchEngine.PARALLEL), WebSearchEngine.withModes)
        listOf(WebSearchEngine.NATIVE, WebSearchEngine.FIRECRAWL, WebSearchEngine.PERPLEXITY).forEach {
            assertEquals(emptyList<String>(), it.selectableModes, "engine $it")
        }
    }

    @Test
    @DisplayName("the web entry sits beside a Router's parameter block")
    fun `the web entry sits beside a Router's parameter block`() {
        val options = ChatRequestOptions(routerParam = "medium", webSearch = true)
        val tuned = WebSearchSettings(engine = WebSearchEngine.EXA)

        val request = ChatExchange.buildRequest("openrouter/auto", messages, options, tuned)

        assertEquals(
            JsonParser.parseString("""[{"id":"auto-router","cost_tier":"medium"},{"id":"web","engine":"exa"}]"""),
            Gson().toJsonTree(request.plugins)
        )
    }

    @Test
    @DisplayName("tuning alone never turns a search on")
    fun `tuning alone never turns a search on`() {
        val tuned = WebSearchSettings(engine = WebSearchEngine.EXA)

        val request = ChatExchange.buildRequest("openai/gpt-5.2", messages, ChatRequestOptions(), tuned)

        assertEquals(null, request.plugins)
    }

    // --- Domain lists as typed ------------------------------------------------

    @ParameterizedTest
    @ValueSource(
        strings = [
            "docs.gradle.org, *.jetbrains.com",
            "docs.gradle.org\n*.jetbrains.com",
            "docs.gradle.org *.jetbrains.com",
            "  docs.gradle.org ,, *.jetbrains.com , docs.gradle.org ",
        ]
    )
    @DisplayName("a typed domain list splits on commas and whitespace, dropping blanks and repeats")
    fun `a typed domain list splits on commas and new lines`(typed: String) {
        assertEquals(listOf("docs.gradle.org", "*.jetbrains.com"), WebSearchSettings.parseDomains(typed))
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = ["", "  ", " , \n "])
    @DisplayName("an empty domain list is no list at all")
    fun `an empty domain list is no list at all`(typed: String?) {
        assertEquals(emptyList<String>(), WebSearchSettings.parseDomains(typed))
    }
}
