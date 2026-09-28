package org.zhavoronkov.openrouter.requests

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class WarningBurstTest {

    private var now = 0L
    private val burst = WarningBurst(clock = { now }, windowMillis = 1_000)

    private fun record(
        finishReason: String? = "length",
        error: String? = null,
        source: RequestSource = RequestSource.PROXY,
        sender: String = "Junie"
    ) = RequestRecord(now, 10, source, sender, "m", ReplyFacts(finishReason = finishReason), error)

    @Test
    @DisplayName("a normal stop raises nothing")
    fun normalStop() {
        assertNull(burst.onRecord(record(finishReason = "stop")))
        assertNull(burst.onRecord(record(finishReason = "tool_calls")))
    }

    @Test
    @DisplayName("the chat's own replies raise nothing: their warning is already under the reply")
    fun chatIsNotAnnounced() {
        assertNull(burst.onRecord(record(source = RequestSource.CHAT, sender = "Chat")))
    }

    @Test
    @DisplayName("the first warning raises a balloon naming the reason")
    fun firstRaises() {
        val first = record()

        assertEquals(WarningAnnouncement.Raise(first, "Cut off at the token limit"), burst.onRecord(first))
    }

    @Test
    @DisplayName("an error is announced by its message")
    fun errorRaises() {
        assertEquals("Insufficient credits", burst.onRecord(record(error = "Insufficient credits"))?.reason)
    }

    @Test
    @DisplayName("later warnings in the burst fold into it, counted")
    fun laterOnesFold() {
        burst.onRecord(record())
        now = 400
        val second = record(error = "boom")
        assertEquals(WarningAnnouncement.Fold(second, "boom", 1), burst.onRecord(second))
        now = 999
        assertEquals(2, (burst.onRecord(record()) as WarningAnnouncement.Fold).more)
    }

    @Test
    @DisplayName("a warning after the burst raises a balloon of its own, counting afresh")
    fun afterTheBurst() {
        burst.onRecord(record())
        now = 500
        burst.onRecord(record())

        now = 1_000
        assertEquals(WarningAnnouncement.Raise::class, burst.onRecord(record())!!::class)
        now = 1_100
        assertEquals(1, (burst.onRecord(record()) as WarningAnnouncement.Fold).more)
    }
}
