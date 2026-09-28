package org.zhavoronkov.openrouter.requests

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.NullSource
import org.junit.jupiter.params.provider.ValueSource

@DisplayName("ConsumerNames")
class ConsumerNamesTest {

    @ParameterizedTest
    @CsvSource(
        delimiter = '|',
        textBlock = """
        Junie/1.2 (IntelliJ IDEA 2026.3)                  | Junie
        GitHubCopilotChat/0.30.1                          | GitHub Copilot
        Cline/3.20.0                                      | Cline
        Kilo-Code/4.1                                     | Kilo Code
        ProxyAI/3.4.2 (JetBrains)                         | ProxyAI
        opencode/0.9.1 ai-sdk/provider-utils/2.1          | OpenCode
        curl/8.7.1                                        | curl"""
    )
    @DisplayName("a known agent is named from its User-Agent, whatever else the header carries")
    fun `a known agent is named from its User-Agent`(userAgent: String, expected: String) {
        assertEquals(expected, ConsumerNames.fromUserAgent(userAgent))
    }

    /**
     * An agent this build does not know is shown as it identifies itself, so the tab stays truthful
     * and the table can be extended from what users actually see.
     */
    @Test
    @DisplayName("an unknown agent is shown as it identifies itself")
    fun `an unknown agent is shown as it identifies itself`() {
        assertEquals(
            "SomeNewAgent/0.1 (linux; x64) extra/9",
            ConsumerNames.fromUserAgent("  SomeNewAgent/0.1 (linux; x64) extra/9 ")
        )
    }

    @Test
    @DisplayName("an overlong User-Agent is cut short")
    fun `an overlong User-Agent is cut short`() {
        val name = ConsumerNames.fromUserAgent("x".repeat(500))

        assertEquals(ConsumerNames.MAX_LENGTH, name.length)
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = ["", "   "])
    @DisplayName("a request without a User-Agent is from an unknown Consumer")
    fun `a request without a User-Agent is from an unknown Consumer`(userAgent: String?) {
        assertEquals("Unknown client", ConsumerNames.fromUserAgent(userAgent))
    }
}
