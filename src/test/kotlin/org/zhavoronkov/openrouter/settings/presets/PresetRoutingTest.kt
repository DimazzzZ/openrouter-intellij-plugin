package org.zhavoronkov.openrouter.settings.presets

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.zhavoronkov.openrouter.models.ProviderRoutingPreferences

@DisplayName("PresetRouting")
class PresetRoutingTest {

    private fun block(json: String): JsonObject = JsonParser.parseString(json).asJsonObject

    @ParameterizedTest(name = "[{index}] {0}")
    @CsvSource(
        delimiter = '|',
        value = [
            """{"sort":"price","order":["azure"]} | price""",
            """{"sort":{"by":"price","partition":"none"}} | """,
            """{"sort":3} | """,
            """{"order":["azure"]} | """
        ]
    )
    @DisplayName("the form is shown a sort only when it is a plain string")
    fun sortShown(json: String, sort: String?) {
        assertEquals(sort, PresetRouting.preferencesOf(block(json)).sort)
    }

    @Test
    @DisplayName("a block the form's fields cannot be read from shows the form empty")
    fun unreadableBlock() {
        assertEquals(ProviderRoutingPreferences(), PresetRouting.preferencesOf(block("""{"only":"azure"}""")))
    }

    @Test
    @DisplayName("merging keeps the fields the form does not edit, and a sort object the form left unset")
    fun mergeKeepsTheRest() {
        val original = block("""{"sort":{"by":"price"},"max_price":{"prompt":1},"only":["azure"]}""")

        val merged = PresetRouting.merged(original, ProviderRoutingPreferences(ignore = listOf("deepinfra")))

        assertEquals(block("""{"max_price":{"prompt":1},"ignore":["deepinfra"],"sort":{"by":"price"}}"""), merged)
    }

    @Test
    @DisplayName("a sort the form sets replaces a sort object")
    fun mergeReplacesSort() {
        val original = block("""{"sort":{"by":"price"}}""")

        val merged = PresetRouting.merged(original, ProviderRoutingPreferences(sort = "latency"))

        assertEquals(block("""{"sort":"latency"}"""), merged)
    }

    @Test
    @DisplayName("the summary names each field once, lists as lists, and an object as an ellipsis")
    fun summary() {
        assertEquals("OpenRouter's default", PresetRouting.summary(JsonObject()))
        val routing = block("""{"only":["azure",{"x":1}],"allow_fallbacks":false,"max_price":{"prompt":1}}""")

        assertEquals("only azure, {\"x\":1} · allow fallbacks false · max price …", PresetRouting.summary(routing))
    }
}
