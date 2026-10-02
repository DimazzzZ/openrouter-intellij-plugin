package org.zhavoronkov.openrouter.requests

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.zhavoronkov.openrouter.models.FixPage
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
    @DisplayName("a log named without a directory is written where it is named")
    fun `a log named without a directory is written where it is named`() {
        val relative = Path.of("request-log-${System.nanoTime()}.jsonl")
        try {
            RequestLog(relative, limit = { 10 }).add(record(1))

            assertEquals(listOf("m1"), RequestLog(relative, limit = { 10 }).recent().map { it.requestedModel })
        } finally {
            Files.deleteIfExists(relative)
        }
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

    /** A line written before records kept a preset's replacements still loads, with none. */
    @Test
    @DisplayName("a line from before presets loads with no preset and no replacements")
    fun `a line from before presets loads`() {
        val older = """{"startedAtMillis":1,"durationMillis":5,"source":"PROXY","sender":"Junie",""" +
            """"requestedModel":"m1","reply":{"webSearches":0}}"""
        Files.createDirectories(file().parent)
        Files.writeString(file(), older + "\n")

        val loaded = RequestLog(file(), limit = { 10 }).recent().single()

        assertEquals(null, loaded.preset)
        assertEquals(emptyList<String>(), loaded.replaced)
    }

    @Test
    @DisplayName("a record's preset and replacements survive a reload")
    fun `preset facts survive a reload`() {
        val withPreset = record(1).copy(
            requestedModel = "m1@preset/p",
            preset = "p",
            replaced = listOf("provider"),
            fixAt = FixPage.PRESETS
        )
        RequestLog(file(), limit = { 10 }).add(withPreset)

        assertEquals(listOf(withPreset), RequestLog(file(), limit = { 10 }).recent())
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

    @Test
    @DisplayName("a record dropped past the limit, or cleared, is handed on so its bodies go with it")
    fun `dropped records are handed on`() {
        val dropped = mutableListOf<RequestRecord>()
        val log = RequestLog(file(), limit = { 2 }, onDropped = { dropped += it })

        (1..3).forEach { log.add(record(it)) }
        assertEquals(listOf(record(1)), dropped)

        log.clear()
        assertEquals(listOf(record(1), record(2), record(3)), dropped)
    }

    @Test
    @DisplayName("a record's bodies id survives a restart")
    fun `a bodies id survives a restart`() {
        val id = "1f2e3d4c-0000-4000-8000-000000000001"
        RequestLog(file(), limit = { 10 }).add(record(1).copy(bodiesId = id))

        val reloaded = RequestLog(file(), limit = { 10 })
        assertEquals(id, reloaded.recent().single().bodiesId)
    }

    @Test
    @DisplayName("blank lines and lines without a reply or sender are skipped")
    fun `incomplete lines are skipped`() {
        RequestLog(file(), limit = { 10 }).apply { add(record(1)) }
        val noReply = """{"startedAtMillis":2,"source":"PROXY","sender":"Junie","requestedModel":"m2"}"""
        val noModel = """{"startedAtMillis":3,"source":"PROXY","sender":"Junie","reply":{}}"""
        Files.writeString(file(), Files.readString(file()) + "\n   \n$noReply\n$noModel\n")

        assertEquals(listOf("m1"), RequestLog(file(), limit = { 10 }).recent().map { it.requestedModel })
    }

    @Test
    @DisplayName("a log whose file cannot be read starts empty")
    fun `an unreadable file starts empty`() {
        Files.createDirectories(file())

        assertEquals(emptyList<RequestRecord>(), RequestLog(file(), limit = { 10 }).recent())
    }

    @Test
    @DisplayName("a log that cannot be written still keeps its records for the session")
    fun `an unwritable log still keeps records`() {
        val blocked = dir.resolve("not-a-directory")
        Files.writeString(blocked, "a file where the log's directory should be")
        val log = RequestLog(blocked.resolve("requests.jsonl"), limit = { 10 })

        log.add(record(1))
        log.clear()
        log.add(record(2))

        assertEquals(listOf("m2"), log.recent().map { it.requestedModel })
    }

    @Test
    @DisplayName("a line that is JSON null, or has no source, is skipped")
    fun `null and sourceless lines are skipped`() {
        RequestLog(file(), limit = { 10 }).apply { add(record(1)) }
        val noSource = """{"startedAtMillis":2,"sender":"Junie","requestedModel":"m2","reply":{}}"""
        Files.writeString(file(), Files.readString(file()) + "null\n$noSource\n")

        assertEquals(listOf("m1"), RequestLog(file(), limit = { 10 }).recent().map { it.requestedModel })
    }
}
