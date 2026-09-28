package org.zhavoronkov.openrouter.toolwindow.chat

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * The point of this writer is that the caller does not wait for the disk, so the test has to.
 * Polling rather than a latch: the writer deliberately exposes no completion hook, because a
 * caller that could wait for it would be back on the EDT holding the same cost.
 */
@DisplayName("ChatFileWriter")
class ChatFileWriterTest {

    @Test
    @DisplayName("the contents reach the file")
    fun `the contents reach the file`(@TempDir dir: File) {
        val file = File(dir, "chats.json")

        ChatFileWriter.write(file, """{"a":1}""")

        assertEquals("""{"a":1}""", await(file))
    }

    @Test
    @DisplayName("two writes of the same file land in the order they were made, so the newer wins")
    fun `writes keep their order`(@TempDir dir: File) {
        val file = File(dir, "chats.json")

        ChatFileWriter.write(file, "older")
        ChatFileWriter.write(file, "newer")

        assertEquals("newer", await(file, expected = "newer"))
    }

    @Test
    @DisplayName("a write to an unreachable path is swallowed rather than thrown at the caller")
    fun `an unwritable path does not throw`(@TempDir dir: File) {
        // The caller is a UI handler that has already updated the model; failing the save must not
        // take the action down with it.
        ChatFileWriter.write(File(dir, "no-such-directory/chats.json"), "{}")
    }

    private fun await(file: File, expected: String? = null): String {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(TIMEOUT_SECONDS)
        while (System.nanoTime() < deadline) {
            if (file.exists()) {
                val text = file.readText()
                if (expected == null || text == expected) return text
            }
            Thread.sleep(POLL_MILLIS)
        }
        return if (file.exists()) file.readText() else "<file was never written>"
    }

    private companion object {
        const val TIMEOUT_SECONDS = 5L
        const val POLL_MILLIS = 10L
    }
}
