package org.zhavoronkov.openrouter.toolwindow.chat

import com.intellij.util.concurrency.AppExecutorUtil
import org.zhavoronkov.openrouter.utils.PluginLogger
import java.io.File
import java.io.IOException

/**
 * Writes the chat window's own files off the EDT.
 *
 * The chat history is saved after every finished reply, and again whenever a chat is created or
 * deleted - all of which happen on the EDT, because they are button handlers and completion
 * callbacks. Writing the file there blocks the UI for as long as the disk takes, and the file
 * grows with the conversation. The platform does not complain about this the way it does about the
 * credential store, since `SlowOperations` instruments its own APIs and plain `java.io` is not one
 * of them - so it was costing frames silently.
 *
 * Callers serialise on their own thread and hand over a finished string. That keeps the snapshot
 * consistent: the model is read while the caller still holds the EDT, so a write can never capture
 * a half-applied change.
 *
 * One thread, so two saves of the same file cannot land out of order and leave the older content
 * on disk.
 */
internal object ChatFileWriter {

    private val executor =
        AppExecutorUtil.createBoundedApplicationPoolExecutor("OpenRouter Chat Files", 1)

    fun write(file: File, contents: String) {
        executor.execute {
            try {
                file.writeText(contents)
            } catch (e: IOException) {
                PluginLogger.warn("Failed to write ${file.name}: ${e.message}")
            }
        }
    }
}
