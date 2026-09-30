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
}
