package org.zhavoronkov.openrouter.toolwindow.status

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("StatusLineText")
class StatusLineTextTest {

    @Test
    @DisplayName("text that already fits is returned untouched")
    fun `text that fits is untouched`() {
        assertEquals("Ready", StatusLineText.truncate("Ready", maxLength = 20))
    }

    @Test
    @DisplayName("text exactly at the budget is returned untouched")
    fun `text exactly at the budget is untouched`() {
        val text = "x".repeat(20)
        assertEquals(text, StatusLineText.truncate(text, maxLength = 20))
    }

    // --- Defect B: an over-long status line must be bounded and signal it was shortened ---------

    @Test
    @DisplayName("over-long text is shortened to the budget and ends with an ellipsis")
    fun `over-long text is truncated with an ellipsis`() {
        val rawJsonError = "Couldn't refresh: " +
            """{"error":{"message":"Only management keys can fetch credits for an account","code":403}}"""

        val truncated = StatusLineText.truncate(rawJsonError, maxLength = 40)

        assertEquals(40, truncated.length)
        assertTrue(truncated.endsWith("…"), "must signal that it was shortened: $truncated")
        assertTrue(rawJsonError.startsWith(truncated.removeSuffix("…")), "must keep the head: $truncated")
    }

    @Test
    @DisplayName("the default budget bounds a realistic over-long server message")
    fun `default budget bounds a realistic message`() {
        val rawJsonError = "Couldn't refresh: " +
            """{"error":{"message":"Only management keys can fetch credits for an account","code":403}}"""

        val truncated = StatusLineText.truncate(rawJsonError)

        assertTrue(truncated.length <= StatusLineText.MAX_LENGTH, "must be bounded: $truncated")
        assertTrue(truncated.endsWith("…"), "must signal that it was shortened: $truncated")
        assertTrue(truncated.length < rawJsonError.length, "must actually be shorter than the original")
    }

    @Test
    @DisplayName("a tiny budget never crashes and still signals truncation")
    fun `a tiny budget never crashes`() {
        val truncated = StatusLineText.truncate("Couldn't refresh: something went wrong", maxLength = 1)

        assertEquals("…", truncated)
    }
}
