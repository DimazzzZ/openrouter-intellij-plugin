package org.zhavoronkov.openrouter.toolwindow.composer

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("MiddleEllipsis")
class MiddleEllipsisTest {

    /** Every character is 7px wide — enough to make the arithmetic obvious. */
    private val measure: (String) -> Int = { it.length * 7 }

    @Test
    @DisplayName("text that already fits is returned untouched")
    fun `text that already fits is returned untouched`() {
        assertEquals("openrouter/auto", MiddleEllipsis.fit("openrouter/auto", 200, measure))
    }

    @Test
    @DisplayName("truncation keeps the head, which is where the provider lives")
    fun `truncation keeps the head`() {
        val fitted = MiddleEllipsis.fit("openrouter/auto", 70, measure)

        assertEquals(10, fitted.length)
        assertTrue(fitted.startsWith("openr"), "got: $fitted")
        assertTrue(fitted.endsWith("auto"), "got: $fitted")
        assertTrue(fitted.contains('…'))
        assertTrue(measure(fitted) <= 70)
    }

    @Test
    @DisplayName("two models differing only in provider stay distinguishable")
    fun `models differing only in provider stay distinguishable`() {
        val a = MiddleEllipsis.fit("anthropic/claude-sonnet-4.5", 84, measure)
        val b = MiddleEllipsis.fit("openai/claude-sonnet-4.5", 84, measure)

        assertTrue(a != b, "head truncation would have collapsed these: $a / $b")
    }

    @Test
    @DisplayName("an impossible width yields an empty string, never a crash")
    fun `an impossible width yields an empty string`() {
        assertEquals("", MiddleEllipsis.fit("openrouter/auto", 3, measure))
    }

    @Test
    @DisplayName("empty input is returned as is")
    fun `empty input is returned as is`() {
        assertEquals("", MiddleEllipsis.fit("", 100, measure))
    }
}
