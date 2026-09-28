package org.zhavoronkov.openrouter.requests

import com.google.gson.Gson
import com.google.gson.JsonParseException
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption

/**
 * The stored list of [RequestRecord]s behind the Requests tab: the most recent [limit] of them,
 * kept on disk in [file] as one JSON object per line so that adding one appends a line rather
 * than rewriting the file.
 *
 * The file is compacted back to [limit] lines once it holds twice that many, through a temporary
 * file moved over it, so a crash mid-compaction leaves the old file whole. A line that does not
 * parse into a complete record - a write cut short, a hand edit - is skipped rather than losing
 * the rest. Safe to call
 * from any thread; every call does its own file work, so none belongs on the EDT.
 */
class RequestLog(private val file: Path, private val limit: () -> Int) {

    private val gson = Gson()
    private val records = ArrayDeque<RequestRecord>()
    private var linesOnDisk = 0

    init {
        load()
    }

    /** Every kept record, newest first. */
    @Synchronized
    fun recent(): List<RequestRecord> = records.reversed()

    @Synchronized
    fun add(record: RequestRecord) {
        val kept = limit().coerceAtLeast(1)
        records.addLast(record)
        trimTo(kept)
        writeSafely {
            Files.writeString(
                file,
                gson.toJson(record) + "\n",
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.APPEND
            )
        }
        linesOnDisk++
        if (linesOnDisk > 2 * kept) compact()
    }

    /**
     * Sets the provider of the record for [generationId], found after the record was made.
     *
     * @return whether a record changed.
     */
    @Synchronized
    fun fillProvider(generationId: String, provider: String): Boolean {
        val index = records.indexOfLast { it.reply.generationId == generationId }
        if (index < 0 || records[index].reply.provider == provider) return false
        records[index] = records[index].let { it.copy(reply = it.reply.copy(provider = provider)) }
        compact()
        return true
    }

    @Synchronized
    fun clear() {
        records.clear()
        compact()
    }

    private fun load() {
        val lines = try {
            if (Files.exists(file)) Files.readAllLines(file, StandardCharsets.UTF_8) else emptyList()
        } catch (e: IOException) {
            emptyList()
        }
        linesOnDisk = lines.size
        lines.mapNotNullTo(records) { parse(it) }
        trimTo(limit().coerceAtLeast(1))
    }

    /**
     * Gson fills a field missing from a line with null whatever the Kotlin type says, so every
     * non-null field is checked before the record is trusted.
     */
    @Suppress("SENSELESS_COMPARISON")
    private fun parse(line: String): RequestRecord? = try {
        line.takeIf { it.isNotBlank() }?.let { gson.fromJson(it, RequestRecord::class.java) }
            ?.takeIf {
                it.source != null && it.sender != null && it.requestedModel != null && it.reply != null
            }
    } catch (e: JsonParseException) {
        null
    }

    private fun trimTo(kept: Int) {
        while (records.size > kept) records.removeFirst()
    }

    private fun compact() {
        writeSafely {
            val temporary = file.resolveSibling("${file.fileName}.tmp")
            Files.writeString(temporary, records.joinToString("") { gson.toJson(it) + "\n" }, StandardCharsets.UTF_8)
            Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        }
        linesOnDisk = records.size
    }

    private fun writeSafely(write: () -> Unit) {
        try {
            file.parent?.let(Files::createDirectories)
            write()
        } catch (e: IOException) {
            // A log that cannot be written must not break the request it describes.
            Unit
        }
    }
}
