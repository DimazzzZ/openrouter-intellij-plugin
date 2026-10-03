package org.zhavoronkov.openrouter.models

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

@DisplayName("EntryNames")
class EntryNamesTest {

    @ParameterizedTest
    @CsvSource(
        "Research,      research",
        "'  Research ', research",
        "deep-DIVE_2,   deep-dive_2"
    )
    @DisplayName("the key two names are compared by ignores surrounding space and case")
    fun `the key ignores surrounding space and case`(name: String, key: String) {
        assertEquals(key, EntryNames.key(name))
    }

    @ParameterizedTest
    @CsvSource(
        "Research,   ' research '",
        "Deep-Dive,  deep-dive"
    )
    @DisplayName("names that are the same entry share a key, so they hash alike")
    fun `same names share a key`(a: String, b: String) {
        assertTrue(EntryNames.same(a, b))
        assertEquals(EntryNames.key(a), EntryNames.key(b))
    }

    @ParameterizedTest
    @CsvSource("research, review", "deep, deeper")
    @DisplayName("different names have different keys")
    fun `different names have different keys`(a: String, b: String) {
        assertNotEquals(EntryNames.key(a), EntryNames.key(b))
    }
}
