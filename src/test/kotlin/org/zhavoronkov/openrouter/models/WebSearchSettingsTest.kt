package org.zhavoronkov.openrouter.models

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.NullSource
import org.junit.jupiter.params.provider.ValueSource

@DisplayName("WebSearchSettings")
class WebSearchSettingsTest {

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
