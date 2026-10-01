package org.zhavoronkov.openrouter.models

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("ResponseFormats")
class ResponseFormatsTest {

    @Test
    @DisplayName("web search drops plain JSON and may drop a schema")
    fun webSearchEffect() {
        assertEquals(ResponseFormats.WebSearchEffect.DROPS_JSON, ResponseFormats.webSearchEffect(true, schema = false))
        assertEquals(
            ResponseFormats.WebSearchEffect.MAY_DROP_SCHEMA,
            ResponseFormats.webSearchEffect(true, schema = true)
        )
    }

    @Test
    @DisplayName("nothing is dropped without web search, or when no output format is asked for")
    fun noWebSearchEffect() {
        assertNull(ResponseFormats.webSearchEffect(false, schema = false))
        assertNull(ResponseFormats.webSearchEffect(false, schema = true))
        assertNull(ResponseFormats.webSearchEffect(true, schema = null))
    }
}
