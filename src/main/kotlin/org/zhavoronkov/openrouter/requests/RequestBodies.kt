package org.zhavoronkov.openrouter.requests

import com.google.gson.Gson
import com.google.gson.JsonParseException
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID

/**
 * What one request carried, kept only when the user turned request bodies on: [received], the body
 * as its sender sent it (a Consumer's, through the proxy); [sent], the body as it went to
 * OpenRouter, after the proxy removed what a preset sets and added its defaults; [reply], the
 * reply as it came back - one JSON object per line for a streamed one, one per chunk; and
 * [failure], the failure as it was reported, an upstream error body included. Each is null when
 * the request never got that far, and each is cut to [MAX_LENGTH] characters.
 */
data class RequestBodies(
    val received: String? = null,
    val sent: String? = null,
    val reply: String? = null,
    val failure: String? = null
) {
    companion object {
        /** The longest a kept body may be, in characters; a longer one is cut with [CUT_MARKER]. */
        const val MAX_LENGTH = 1_000_000

        const val CUT_MARKER = "\n… cut: the rest was not kept"

        /** [text] as it is kept: at most [MAX_LENGTH] characters, marked when cut. */
        fun cut(text: String): String =
            if (text.length <= MAX_LENGTH) text else text.take(MAX_LENGTH) + CUT_MARKER
    }
}

/**
 * Where [RequestBodies] are kept: one JSON file per request in [dir], named by the id a
 * [RequestRecord] keeps in [RequestRecord.bodiesId], so the Requests list itself stays small and is
 * never held in memory with every body in it. A body is read only when the user asks to see it.
 *
 * Every call does its own file work, so none belongs on the EDT. A body that cannot be written or
 * read costs only that body: the request it describes is never failed for it.
 */
class RequestBodyStore(private val dir: Path) {

    private val gson = Gson()

    /** Keeps [bodies] under [id]. */
    fun save(id: String, bodies: RequestBodies) {
        try {
            Files.createDirectories(dir)
            Files.writeString(fileOf(id), gson.toJson(bodies), StandardCharsets.UTF_8)
        } catch (e: IOException) {
            Unit
        }
    }

    /** The bodies kept under [id], or null when there are none - deleted, never written, unreadable. */
    fun load(id: String): RequestBodies? = try {
        val file = fileOf(id)
        if (Files.exists(file)) {
            gson.fromJson(Files.readString(file, StandardCharsets.UTF_8), RequestBodies::class.java)
        } else {
            null
        }
    } catch (e: IOException) {
        null
    } catch (e: JsonParseException) {
        null
    } catch (e: IllegalArgumentException) {
        null
    }

    /** Deletes the bodies kept under each of [ids]. */
    fun delete(ids: Collection<String>) {
        ids.forEach { id ->
            try {
                Files.deleteIfExists(fileOf(id))
            } catch (e: IOException) {
                Unit
            } catch (e: IllegalArgumentException) {
                Unit
            }
        }
    }

    /** Deletes every kept body. */
    fun clear() {
        try {
            if (!Files.isDirectory(dir)) return
            Files.list(dir).use { files ->
                files.filter { it.fileName.toString().endsWith(SUFFIX) }.forEach(Files::deleteIfExists)
            }
        } catch (e: IOException) {
            Unit
        }
    }

    /** An id is a UUID the plugin made; anything else is refused, so an id can never name a path. */
    private fun fileOf(id: String): Path {
        require(ID.matches(id)) { "Not a request body id: $id" }
        return dir.resolve(id + SUFFIX)
    }

    companion object {
        private const val SUFFIX = ".json"
        private val ID = Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")

        /** A fresh id for a request's bodies. */
        fun newId(): String = UUID.randomUUID().toString()
    }
}
