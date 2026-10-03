package org.zhavoronkov.openrouter.models

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.NullSource
import org.junit.jupiter.params.provider.ValueSource

@DisplayName("RequestChoices")
class RequestChoicesTest {

    @ParameterizedTest
    @CsvSource("Low, low", "Medium, medium", "XHigh, xhigh", "Max, max")
    @DisplayName("a listed verbosity is sent as its label in lower case")
    fun `a listed verbosity is sent in lower case`(label: String, value: String) {
        assertEquals(value, RequestChoices.verbosity(label))
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = ["", "Default", "low", "Loud"])
    @DisplayName("no verbosity, or one not listed, is sent as nothing")
    fun `an unlisted verbosity is sent as nothing`(label: String?) {
        assertNull(RequestChoices.verbosity(label))
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = ["Default", "high", "Max"])
    @DisplayName("a reasoning effort not listed is sent as nothing")
    fun `an unlisted reasoning effort is sent as nothing`(label: String?) {
        assertNull(RequestChoices.reasoningEffort(label))
    }
}
