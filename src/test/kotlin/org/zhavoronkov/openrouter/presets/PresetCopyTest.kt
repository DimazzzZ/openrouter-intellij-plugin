package org.zhavoronkov.openrouter.presets

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

@DisplayName("PresetCopy")
class PresetCopyTest {

    @TempDir
    lateinit var dir: Path

    private fun file() = dir.resolve("presets.json")
    private fun config(json: String): JsonObject = JsonParser.parseString(json).asJsonObject

    private var listed: List<PresetListing>? = listOf(PresetListing("research", "Research"))
    private val versions = mutableMapOf<String, PresetVersion?>(
        "research" to PresetVersion("Be brief.", config("""{"max_tokens":40,"tools":[{"type":"openrouter:web_search"}]}"""))
    )
    private var lists = 0
    private var now = 1_000_000L

    private fun copy(scope: CoroutineScope) = PresetCopy(
        file = file(),
        list = { lists++; listed },
        read = { versions[it] },
        scope = scope,
        clock = { now },
        missRefreshAfterMillis = 60_000
    )

    @Test
    @DisplayName("a copy never read is not known, which is not the same as having no presets")
    fun neverRead() = runTest {
        assertNull(copy(this).snapshot())
    }

    @Test
    @DisplayName("a read lists the presets and keeps each one's config as sent")
    fun read() = runTest {
        val copy = copy(this)

        assertTrue(copy.refresh())

        val research = copy.snapshot()!!.find("RESEARCH")!!
        assertEquals("Research", research.name)
        assertEquals("Be brief.", research.systemPrompt)
        assertEquals("40", research.config!!.get("max_tokens").toString())
    }

    @Test
    @DisplayName("the copy survives a restart, and is used when OpenRouter cannot be reached")
    fun offline() = runTest {
        copy(this).refresh()
        listed = null

        val restarted = copy(this)
        assertFalse(restarted.refresh(), "the list could not be read")

        assertEquals(listOf("research"), restarted.snapshot()!!.presets.map { it.slug })
        assertTrue(Files.exists(file()))
    }

    @Test
    @DisplayName("a preset whose version cannot be read keeps what the copy knew of it")
    fun versionUnreadable() = runTest {
        val copy = copy(this)
        copy.refresh()
        versions["research"] = null
        listed = listOf(PresetListing("research", "Research"), PresetListing("fresh", "Fresh"))

        copy.refresh()

        assertEquals("40", copy.snapshot()!!.find("research")!!.config!!.get("max_tokens").toString())
        assertNull(copy.snapshot()!!.find("fresh")!!.config, "listed, but what it sets is not known")
    }

    @Test
    @DisplayName("an unknown slug asks for one read, not one per lookup, and not right after a read")
    fun missRefresh() {
        val dispatcher = StandardTestDispatcher()
        val copy = copy(CoroutineScope(dispatcher))
        runBlocking { copy.refresh() }
        val afterFirstRead = lists

        copy.find("new")
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(afterFirstRead, lists, "the copy was read a moment ago")

        now += 60_000
        repeat(5) { copy.find("new") }
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(afterFirstRead + 1, lists)
    }

    @Test
    @DisplayName("a caller about to refuse an unknown slug can wait for the read it asks for")
    fun findAfterRead() = runTest {
        val copy = copy(this)
        copy.refresh()
        listed = listOf(PresetListing("research", "Research"), PresetListing("fresh", "Fresh"))
        versions["fresh"] = PresetVersion(null, config("""{"temperature":0.1}"""))
        now += 60_000

        assertEquals("fresh", copy.findAfterRead("fresh", timeoutMillis = 5_000)?.slug)
    }

    @Test
    @DisplayName("right after a read an unknown slug is not waited for: no read is asked for")
    fun findAfterReadThrottled() = runTest {
        val copy = copy(this)
        copy.refresh()
        val afterFirstRead = lists

        assertNull(copy.findAfterRead("fresh", timeoutMillis = 5_000))
        assertEquals(afterFirstRead, lists)
    }

    @Test
    @DisplayName("listeners hear every read that succeeds, and stop when removed")
    fun listeners() = runTest {
        val copy = copy(this)
        var heard = 0
        val stop = copy.addListener { heard++ }

        copy.refresh()
        listed = null
        copy.refresh()
        assertEquals(1, heard, "a read that could not list is not news")

        listed = listOf(PresetListing("research", "Research"))
        stop()
        copy.refresh()
        assertEquals(1, heard)
    }

    @Test
    @DisplayName("a damaged file is read as no copy rather than failing")
    fun damagedFile() = runTest {
        Files.writeString(file(), "{not json")

        assertNull(copy(this).snapshot())
    }

    @Test
    @DisplayName("a file with no preset list, or entries without a slug or name, keeps only what it can")
    fun incompleteFile() = runTest {
        Files.writeString(file(), """{"readAtMillis":1}""")
        assertNull(copy(this).snapshot())

        Files.writeString(
            file(),
            """{"readAtMillis":1,"presets":[{"slug":"research","name":"Research"},{"name":"No slug"},{"slug":"x"}]}"""
        )
        assertEquals(listOf("research"), copy(this).snapshot()!!.presets.map { it.slug })
    }

    @Test
    @DisplayName("a copy whose file cannot be read, or written, still serves the session")
    fun unusableFile() = runTest {
        Files.createDirectories(file())
        val copy = copy(this)
        assertNull(copy.snapshot())
        assertNull(copy.find("research"), "nothing to find before the first read")

        assertTrue(copy.refresh(), "the read succeeds although it cannot be saved")
        assertEquals("Research", copy.find("research")!!.name)
    }

    @Test
    @DisplayName("a slug the copy has is found at once, without asking for a read")
    fun findAfterReadKnown() = runTest {
        val copy = copy(this)
        copy.refresh()
        val afterFirstRead = lists
        now += 60_000

        assertEquals("Research", copy.findAfterRead("research", timeoutMillis = 5_000)?.name)
        assertEquals(afterFirstRead, lists)
    }

    @Test
    @DisplayName("waiting on a read of a copy never read, that cannot list, finds nothing")
    fun findAfterReadNeverRead() = runTest {
        listed = null
        val copy = copy(this)
        copy.refreshLater()

        assertNull(copy.findAfterRead("research", timeoutMillis = 5_000))
        assertNull(copy.snapshot())
        assertEquals(1, lists, "the read asked for ran, and was waited for")
    }

    @Test
    @DisplayName("a background read that has finished is not waited for again")
    fun findAfterReadFinishedRead() = runTest {
        val copy = copy(this)
        copy.refreshLater()
        testScheduler.advanceUntilIdle()
        val afterFirstRead = lists

        assertNull(copy.findAfterRead("fresh", timeoutMillis = 5_000))
        assertEquals(afterFirstRead, lists, "right after a read no other is asked for")
    }

    @Test
    @DisplayName("a background read asked for after the last one finished runs again")
    fun refreshLaterAgain() {
        val dispatcher = StandardTestDispatcher()
        val copy = copy(CoroutineScope(dispatcher))

        copy.refreshLater()
        copy.refreshLater()
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(1, lists, "a read already running is not started twice")

        copy.refreshLater()
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(2, lists)
    }

    @Test
    @DisplayName("an empty file is read as no copy")
    fun emptyFile() = runTest {
        Files.writeString(file(), "")

        assertNull(copy(this).snapshot())
    }

    @Test
    @DisplayName("a file named without a directory is saved where the IDE runs, with no directory to create")
    fun fileWithoutDirectory() = runTest {
        val bare = Path.of("presets-copy-test-${System.nanoTime()}.json")
        try {
            val copy = PresetCopy(file = bare, list = { listed }, read = { versions[it] }, scope = this)

            assertTrue(copy.refresh())
            assertTrue(Files.exists(bare))
        } finally {
            Files.deleteIfExists(bare)
        }
    }
}
