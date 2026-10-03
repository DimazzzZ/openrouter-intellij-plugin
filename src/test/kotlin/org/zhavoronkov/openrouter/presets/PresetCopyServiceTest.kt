package org.zhavoronkov.openrouter.presets

import com.google.gson.JsonParser
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

@DisplayName("PresetCopyService.versionOf")
class PresetCopyServiceTest {

    private fun version(json: String) = PresetCopyService.versionOf(JsonParser.parseString(json).asJsonObject)

    @Test
    @DisplayName("a designated version keeps its prompt and its config as sent")
    fun promptAndConfig() {
        val read = version("""{"system_prompt":"Be brief.","config":{"max_tokens":40}}""")

        assertEquals("Be brief.", read.systemPrompt)
        assertEquals("40", read.config["max_tokens"].toString())
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @ValueSource(strings = ["""{}""", """{"system_prompt":{"text":"x"},"config":"none"}""", """{"config":null}"""])
    @DisplayName("a prompt or config of another shape reads as none")
    fun misshapen(json: String) {
        val read = version(json)

        assertNull(read.systemPrompt)
        assertEquals("{}", read.config.toString())
    }
}
