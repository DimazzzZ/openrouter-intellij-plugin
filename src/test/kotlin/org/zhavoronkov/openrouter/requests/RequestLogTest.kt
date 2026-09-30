package org.zhavoronkov.openrouter.requests

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

@DisplayName("RequestLog")
class RequestLogTest {

    @TempDir
    lateinit var dir: Path

    private fun file() = dir.resolve("requests.jsonl")

    private fun record(n: Int) = RequestRecord(
        startedAtMillis = n.toLong(),
        durationMillis = 10,
        source = RequestSource.PROXY,
        sender = "Junie",
        requestedModel = "m$n",
        reply = ReplyFacts(answeringModel = "m$n", cost = 0.001 * n)
    )

    @Test
    @DisplayName("a provider found later is filled into its record and kept over a restart")
    fun `a provider found later is filled in`() {
        val log = RequestLog(file(), limit = { 10 })
        log.add(record(1).copy(reply = ReplyFacts(generationId = "gen-1")))
        log.add(record(2).copy(reply = ReplyFacts(generationId = "gen-2")))

        assertTrue(log.fillProvider("gen-1", "Azure"))
        assertFalse(log.fillProvider("gen-9", "Azure"), "no record has that generation")
        assertFalse(log.fillProvider("gen-1", "Azure"), "nothing changed the second time")

        val reloaded = RequestLog(file(), limit = { 10 }).recent()
        assertEquals(listOf(null, "Azure"), reloaded.map { it.reply.provider })
    }

    @Test
    @DisplayName("records read back newest first")
    fun `records read back newest first`() {
        val log = RequestLog(file(), limit = { 10 })

        (1..3).forEach { log.add(record(it)) }

        assertEquals(listOf("m3", "m2", "m1"), log.recent().map { it.requestedModel })
    }

    @Test
    @DisplayName("only the most recent records up to the limit are kept")
    fun `only the most recent records up to the limit are kept`() {
        val log = RequestLog(file(), limit = { 3 })

        (1..10).forEach { log.add(record(it)) }

        assertEquals(listOf("m10", "m9", "m8"), log.recent().map { it.requestedModel })
    }

    @Test
    @DisplayName("records survive a restart")
    fun `records survive a restart`() {
        RequestLog(file(), limit = { 10 }).apply { (1..2).forEach { add(record(it)) } }

        val reopened = RequestLog(file(), limit = { 10 })

        assertEquals(listOf(record(2), record(1)), reopened.recent())
    }

    /** A limit lowered in settings applies to what is already stored, the next time the log is read. */
    @Test
    @DisplayName("a restart honours a lower limit")
    fun `a restart honours a lower limit`() {
        RequestLog(file(), limit = { 10 }).apply { (1..5).forEach { add(record(it)) } }

        assertEquals(listOf("m5", "m4"), RequestLog(file(), limit = { 2 }).recent().map { it.requestedModel })
    }

    @Test
    @DisplayName("clearing removes every record, on disk too")
    fun `clearing removes every record`() {
        val log = RequestLog(file(), limit = { 10 })
        (1..3).forEach { log.add(record(it)) }

        log.clear()

        assertEquals(emptyList<RequestRecord>(), log.recent())
        assertEquals(emptyList<RequestRecord>(), RequestLog(file(), limit = { 10 }).recent())
    }

    /** A page renamed or removed since a line was written loads as none, not as a lost record. */
    @Test
    @DisplayName("a line naming a fix page this version does not know loads without one")
    fun `an unknown fix page loads as none`() {
        val line = """{"startedAtMillis":1,"durationMillis":5,"source":"PROXY","sender":"Junie",""" +
            """"requestedModel":"m1","reply":{"webSearches":0},"error":"refused","fixAt":"GONE_PAGE"}"""
        Files.createDirectories(file().parent)
        Files.writeString(file(), line + "\n")

        val loaded = RequestLog(file(), limit = { 10 }).recent().single()

        assertEquals("refused", loaded.error)
        assertEquals(null, loaded.fixAt)
    }

    /** Gson fills a missing field with null whatever Kotlin declares, so such a line is skipped. */
    @Test
    @DisplayName("a line missing a field is skipped rather than loaded with a null in it")
    fun `a line missing a field is skipped`() {
        RequestLog(file(), limit = { 10 }).apply { add(record(1)) }
        val missingSender = """{"startedAtMillis":2,"source":"PROXY","requestedModel":"m2"}"""
        Files.writeString(file(), Files.readString(file()) + missingSender + "\n")

        assertEquals(listOf("m1"), RequestLog(file(), limit = { 10 }).recent().map { it.requestedModel })
    }

    @Test
    @DisplayName("a damaged line is skipped rather than losing the log")
    fun `a damaged line is skipped`() {
        RequestLog(file(), limit = { 10 }).apply { add(record(1)) }
        Files.writeString(file(), Files.readString(file()) + "{not json\n")

        assertEquals(listOf("m1"), RequestLog(file(), limit = { 10 }).recent().map { it.requestedModel })
    }

    /** Facts only: nothing a request or a reply said is ever written. */
    @Test
    @DisplayName("the stored form holds facts and no text")
    fun `the stored form holds facts and no text`() {
        val log = RequestLog(file(), limit = { 10 })

        log.add(record(1))

        val stored = Files.readString(file())
        listOf("messages", "content", "prompt", "Authorization").forEach {
            assertFalse(stored.contains(it), "'$it' must never reach the log: $stored")
        }
    }
}
