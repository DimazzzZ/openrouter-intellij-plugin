package org.zhavoronkov.openrouter.utils

import com.google.gson.JsonParser
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.ValueSource

@DisplayName("JsonAccess")
class JsonAccessTest {

    @ParameterizedTest
    @CsvSource("true, true", "false, false")
    @DisplayName("a boolean primitive reads as its value")
    fun booleans(json: String, expected: Boolean) {
        assertEquals(expected, JsonParser.parseString(json).asBooleanOrNull())
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @ValueSource(strings = ["\"true\"", "1", "null", "{}", "[true]"])
    @DisplayName("anything but a boolean primitive reads as no boolean")
    fun notBooleans(json: String) {
        assertNull(JsonParser.parseString(json).asBooleanOrNull())
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @ValueSource(strings = ["7", "true", "null", "{}", "[\"a\"]"])
    @DisplayName("anything but a string primitive reads as no string")
    fun notStrings(json: String) {
        assertNull(JsonParser.parseString(json).asStringOrNull())
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @ValueSource(strings = ["7", "\"a\"", "null", "[]"])
    @DisplayName("anything but an object reads as no object")
    fun notObjects(json: String) {
        assertNull(JsonParser.parseString(json).asObjectOrNull())
    }
}
