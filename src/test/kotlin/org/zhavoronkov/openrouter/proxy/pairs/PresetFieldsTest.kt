package org.zhavoronkov.openrouter.proxy.pairs

import com.google.gson.JsonParser
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class PresetFieldsTest {

    @Test
    @DisplayName("the Consumer's fields the preset sets are removed and named; its messages, model and tools stay")
    fun strip() {
        val body = JsonParser.parseString(
            """{"model":"m@preset/p","messages":[],"temperature":1,"provider":{"sort":"price"},
                "tools":[{"type":"function"}],"stream":true,"user":"u"}"""
        ).asJsonObject
        val config = JsonParser.parseString(
            """{"temperature":0.2,"provider":{"only":["azure"]},"tools":[{"type":"openrouter:web_search"}],"max_tokens":40}"""
        ).asJsonObject

        val removed = PresetFields.strip(body, config)

        assertEquals(listOf("temperature", "provider"), removed)
        assertEquals(setOf("model", "messages", "tools", "stream", "user"), body.keySet())
    }

    @Test
    @DisplayName("a preset of the Consumer's own, beside the pair's, is removed")
    fun ownPreset() {
        val body = JsonParser.parseString("""{"model":"m@preset/p","preset":"@preset/other"}""").asJsonObject

        assertEquals(listOf("preset"), PresetFields.strip(body, JsonParser.parseString("{}").asJsonObject))
    }
}
