package org.zhavoronkov.openrouter.presets

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParseException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.concurrent.CopyOnWriteArrayList

/**
 * One of the user's OpenRouter presets as last read: its [slug] and [name], and its designated
 * version's [systemPrompt] and [config] - the request fields the preset sets, kept as the JSON
 * OpenRouter sent so that nothing is reshaped on its way back. [config] is null when the preset is
 * listed but its version could not be read; such a preset is known to exist, not what it sets.
 */
data class PresetEntry(
    val slug: String,
    val name: String,
    val systemPrompt: String? = null,
    val config: JsonObject? = null
)

/** Every preset as read at [readAtMillis]. */
data class PresetSnapshot(val readAtMillis: Long, val presets: List<PresetEntry>) {
    fun find(slug: String): PresetEntry? = presets.firstOrNull { it.slug.equals(slug, ignoreCase = true) }
}

/** A preset's slug and name, as the list endpoint gives them. */
data class PresetListing(val slug: String, val name: String)

/** A preset's designated version, as the per-preset endpoint gives it. */
data class PresetVersion(val systemPrompt: String?, val config: JsonObject)

/**
 * The plugin's copy of the user's OpenRouter presets and what each one sets, kept on disk so that
 * pairs keep working when OpenRouter cannot be reached.
 *
 * The list endpoint gives no configs, so a read lists the presets and then reads each one's
 * designated version. A read that cannot list keeps the copy as it was; a preset whose version
 * cannot be read keeps what the copy knew of it. [snapshot] is null until a read has succeeded
 * once - "not known", which callers must tell apart from "no presets".
 */
class PresetCopy(
    private val file: Path,
    private val list: suspend () -> List<PresetListing>?,
    private val read: suspend (slug: String) -> PresetVersion?,
    private val scope: CoroutineScope,
    private val clock: () -> Long = System::currentTimeMillis,
    /** How long after a read a lookup of an unknown slug may ask for another. */
    private val missRefreshAfterMillis: Long = MISS_REFRESH_AFTER_MILLIS
) {
    private val gson = Gson()

    @Volatile
    private var current: PresetSnapshot? = load()

    private var refreshing: Job? = null

    /** One read at a time: two would race on the file, and the later could lose the newer list. */
    private val reading = Mutex()

    private val listeners = CopyOnWriteArrayList<() -> Unit>()

    /**
     * Calls [listener] after every read that succeeds, on the reading thread; returns what removes
     * it. The chat follows a preset edited elsewhere through it, and Favorite Models re-judges pairs.
     */
    fun addListener(listener: () -> Unit): () -> Unit {
        listeners += listener
        return { listeners -= listener }
    }

    /** The copy as last read, or null when it has never been read. */
    fun snapshot(): PresetSnapshot? = current

    /**
     * The preset [slug] names, or null when the copy does not have it. A slug the copy does not
     * have may have been created since the last read, so it asks for a read in the background -
     * at most one every [missRefreshAfterMillis], however many requests name it.
     */
    fun find(slug: String): PresetEntry? {
        val snapshot = current ?: return null
        return snapshot.find(slug) ?: run {
            if (clock() - snapshot.readAtMillis >= missRefreshAfterMillis) refreshLater()
            null
        }
    }

    /**
     * [find], for a caller about to refuse a request for [slug]: when the lookup asks for a read,
     * it waits up to [timeoutMillis] for it, so a preset made on OpenRouter since the last read
     * is found rather than refused once. Right after a read nothing is asked for or waited for.
     */
    suspend fun findAfterRead(slug: String, timeoutMillis: Long): PresetEntry? {
        find(slug)?.let { return it }
        val read = runningRead() ?: return null
        withTimeoutOrNull(timeoutMillis) { read.join() }
        return current?.find(slug)
    }

    @Synchronized
    private fun runningRead(): Job? = refreshing?.takeIf { it.isActive }

    /** Reads the presets again in the background, unless a read is already running. */
    @Synchronized
    fun refreshLater() {
        if (refreshing?.isActive == true) return
        refreshing = scope.launch { refresh() }
    }

    /** Reads the presets now. @return whether the list could be read. */
    suspend fun refresh(): Boolean = reading.withLock { read() }.also { read ->
        if (read) listeners.forEach { it() }
    }

    private suspend fun read(): Boolean {
        val listed = list() ?: return false
        val previous = current
        val presets = listed.map { listing ->
            val version = read(listing.slug)
            val known = previous?.find(listing.slug)
            if (version != null) {
                PresetEntry(listing.slug, listing.name, version.systemPrompt, version.config)
            } else {
                PresetEntry(listing.slug, listing.name, known?.systemPrompt, known?.config)
            }
        }
        val snapshot = PresetSnapshot(clock(), presets)
        current = snapshot
        save(snapshot)
        return true
    }

    /** Gson fills a field missing from the file with null whatever the Kotlin type says. */
    @Suppress("SENSELESS_COMPARISON")
    private fun load(): PresetSnapshot? = try {
        if (Files.exists(file)) {
            gson.fromJson(Files.readString(file, StandardCharsets.UTF_8), PresetSnapshot::class.java)
                ?.takeIf { it.presets != null }
                ?.let { it.copy(presets = it.presets.filter { entry -> entry.slug != null && entry.name != null }) }
        } else {
            null
        }
    } catch (e: IOException) {
        null
    } catch (e: JsonParseException) {
        null
    }

    private fun save(snapshot: PresetSnapshot) {
        try {
            file.parent?.let(Files::createDirectories)
            val temporary = file.resolveSibling("${file.fileName}.tmp")
            Files.writeString(temporary, gson.toJson(snapshot), StandardCharsets.UTF_8)
            Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        } catch (e: IOException) {
            // A copy that cannot be saved still serves this session
            Unit
        }
    }

    companion object {
        const val MISS_REFRESH_AFTER_MILLIS = 60_000L
    }
}
