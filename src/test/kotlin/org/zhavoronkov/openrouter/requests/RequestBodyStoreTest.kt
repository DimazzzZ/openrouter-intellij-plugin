package org.zhavoronkov.openrouter.requests

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

@DisplayName("RequestBodyStore")
class RequestBodyStoreTest {

    @TempDir
    lateinit var dir: Path

    private val bodies = RequestBodies(received = """{"messages":[{"role":"user","content":"hi"}]}""", reply = "{}")

    private fun store() = RequestBodyStore(dir.resolve("request-bodies"))

    @Test
    @DisplayName("bodies kept under an id are read back by it, by a store made later too")
    fun roundTrip() {
        val id = RequestBodyStore.newId()
        store().save(id, bodies)

        assertEquals(bodies, store().load(id))
    }

    @Test
    @DisplayName("an id with nothing kept reads as none")
    fun missing() {
        assertNull(store().load(RequestBodyStore.newId()))
    }

    @Test
    @DisplayName("deleted bodies are gone, and others stay")
    fun delete() {
        val store = store()
        val gone = RequestBodyStore.newId()
        val kept = RequestBodyStore.newId()
        store.save(gone, bodies)
        store.save(kept, bodies)

        store.delete(listOf(gone))

        assertNull(store.load(gone))
        assertEquals(bodies, store.load(kept))
    }

    @Test
    @DisplayName("clearing deletes every kept body")
    fun clear() {
        val store = store()
        val ids = List(3) { RequestBodyStore.newId() }
        ids.forEach { store.save(it, bodies) }

        store.clear()

        ids.forEach { assertNull(store.load(it)) }
    }

    @Test
    @DisplayName("an id that is not one the store made never names a file, here or elsewhere")
    fun foreignId() {
        val outside = dir.resolve("secret.json")
        Files.writeString(outside, """{"reply":"not yours"}""")

        assertNull(store().load("../secret"))
        store().delete(listOf("../secret"))

        assertTrue(Files.exists(outside))
    }

    @Test
    @DisplayName("a body longer than the limit is cut and says so")
    fun cut() {
        val long = "x".repeat(RequestBodies.MAX_LENGTH + 10)

        val cut = RequestBodies.cut(long)

        assertTrue(cut.endsWith(RequestBodies.CUT_MARKER))
        assertEquals(RequestBodies.MAX_LENGTH + RequestBodies.CUT_MARKER.length, cut.length)
        assertFalse(RequestBodies.cut("short").endsWith(RequestBodies.CUT_MARKER))
    }
}
