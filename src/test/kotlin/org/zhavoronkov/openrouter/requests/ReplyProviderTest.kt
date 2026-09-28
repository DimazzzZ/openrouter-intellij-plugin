package org.zhavoronkov.openrouter.requests

import com.google.gson.JsonParser
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

/** Measured against OpenRouter, `.research/openrouter-presets-apply-config.md`. */
@DisplayName("ReplyProvider")
class ReplyProviderTest {

    private fun trusted(json: String) = ReplyProvider.trusted(JsonParser.parseString(json).asJsonObject)

    @Test
    @DisplayName("a request with no server tool, preset or legacy search is believed")
    fun plainRequest() {
        assertTrue(trusted("""{"model":"openai/gpt-5-nano","tools":[{"type":"function","function":{"name":"f"}}]}"""))
    }

    @ParameterizedTest
    @ValueSource(
        strings = [
            """{"model":"m","tools":[{"type":"openrouter:web_search"}]}""",
            """{"model":"m","tools":[{"type":"function"},{"type":"openrouter:advisor"}]}""",
            """{"model":"m@preset/research"}""",
            """{"model":"@preset/research"}""",
            """{"model":"m","preset":"@preset/research"}""",
            """{"model":"m:online"}""",
            """{"model":"m","plugins":[{"id":"web"}]}"""
        ]
    )
    @DisplayName("a request that may use a server tool is not believed")
    fun serverTools(json: String) {
        assertFalse(trusted(json), json)
    }

    @Test
    @DisplayName("the generation record is asked until it names a provider")
    fun lookupRetries() = runTest {
        val answers = ArrayDeque(listOf(null, "", "Azure"))
        var asked = 0
        val lookup = GenerationProviderLookup(
            fetch = { asked++; answers.removeFirst() },
            delaysMillis = listOf(1, 1, 1, 1)
        )

        assertEquals("Azure", lookup.providerOf("gen-1"))
        assertEquals(3, asked, "a blank provider is not an answer")
    }

    @Test
    @DisplayName("a record that never names one gives up after the last try")
    fun lookupGivesUp() = runTest {
        var asked = 0
        val lookup = GenerationProviderLookup(fetch = { asked++; null }, delaysMillis = listOf(1, 1))

        assertNull(lookup.providerOf("gen-1"))
        assertEquals(2, asked)
    }
}
