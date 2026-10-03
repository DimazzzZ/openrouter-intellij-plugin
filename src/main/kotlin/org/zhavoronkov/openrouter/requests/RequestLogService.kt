package org.zhavoronkov.openrouter.requests

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.components.Service
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.messages.Topic
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.zhavoronkov.openrouter.services.OpenRouterService
import org.zhavoronkov.openrouter.services.OpenRouterSettingsService
import org.zhavoronkov.openrouter.utils.ExcludeFromCoverage
import java.nio.file.Path
import java.util.concurrent.Executor

/** Told whenever the Requests log changes, so the Requests tab can follow it. */
fun interface RequestLogListener {
    /** A record was added, or the log was cleared (then [added] is null). */
    fun changed(added: RequestRecord?)

    /** A record already in the log learned a fact later - the provider its reply could not name. */
    fun updated() = Unit

    companion object {
        val TOPIC: Topic<RequestLogListener> =
            Topic.create("OpenRouter Requests log changed", RequestLogListener::class.java)
    }
}

/**
 * The application's one [RequestLog], stored next to the chat history, and the place every part of
 * the plugin reports a finished request to.
 *
 * [record] and [clear] return at once from any thread, the EDT included: the file work and the
 * message-bus publish happen on one background thread of their own, so a reply is never held up
 * by its record and records land in the order they were reported. [recent] reads the file on
 * first use, so it too stays off the EDT.
 */
@Service(Service.Level.APP)
class RequestLogService {

    private val writer: Executor = AppExecutorUtil.createBoundedApplicationPoolExecutor("OpenRouter Requests", 1)

    private val bodyStore: RequestBodyStore by lazy { RequestBodyStore(defaultFile().resolveSibling(BODIES_DIR)) }

    private val log: RequestLog by lazy {
        RequestLog(
            defaultFile(),
            limit = { OpenRouterSettingsService.getInstance().uiPreferencesManager.requestLogLimit },
            onDropped = { dropped -> bodyStore.delete(dropped.mapNotNull { it.bodiesId }) }
        )
    }

    /** Whether a request starting now keeps its bodies: the user turned request bodies on. */
    val keepsBodies: Boolean get() = OpenRouterSettingsService.getInstance().uiPreferencesManager.keepRequestBodies

    /**
     * Keeps [bodies] under [id], off the calling thread - on the thread records are added on, and
     * before the record naming them, which is reported after.
     */
    fun saveBodies(id: String, bodies: RequestBodies) = writer.execute { bodyStore.save(id, bodies) }

    /** The bodies kept under [id], or null; read on the calling thread, so never call it on the EDT. */
    fun bodies(id: String): RequestBodies? = bodyStore.load(id)

    /** Keeps [record] and tells the Requests tab, off the calling thread. */
    fun record(record: RequestRecord) = writer.execute {
        log.add(record)
        publish(record)
    }

    /** Every kept record, newest first. */
    fun recent(): List<RequestRecord> = log.recent()

    private val lookups = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val providerLookup = GenerationProviderLookup(fetch = ::fetchGenerationProvider)

    @ExcludeFromCoverage("asks OpenRouter over the network; GenerationProviderLookup is tested with its own fetch")
    private suspend fun fetchGenerationProvider(id: String): String? =
        OpenRouterService.getInstance().getGenerationProvider(id)

    /** Looks up, in the background, the provider of [generationId]'s record, and fills it in. */
    @ExcludeFromCoverage("waits up to half a minute on OpenRouter's generation record, over the network")
    fun fillProviderLater(generationId: String) {
        lookups.launch { providerLookup.providerOf(generationId)?.let { fillProvider(generationId, it) } }
    }

    /**
     * Sets the provider of [generationId]'s record. Published as an update, not an addition, so no
     * warning is announced or counted a second time.
     */
    fun fillProvider(generationId: String, provider: String) = writer.execute {
        if (log.fillProvider(generationId, provider)) {
            ApplicationManager.getApplication().messageBus.syncPublisher(RequestLogListener.TOPIC).updated()
        }
    }

    /** Drops every record and tells the Requests tab, off the calling thread. */
    fun clear() = writer.execute {
        log.clear()
        // Also any body whose record was lost - a write cut short, a hand edit
        bodyStore.clear()
        publish(null)
    }

    private fun publish(added: RequestRecord?) {
        // An application service: there is always an application while it exists
        ApplicationManager.getApplication().messageBus.syncPublisher(RequestLogListener.TOPIC).changed(added)
    }

    companion object {
        private const val FILE_NAME = "requests.jsonl"
        private const val BODIES_DIR = "request-bodies"

        fun getInstance(): RequestLogService =
            ApplicationManager.getApplication().getService(RequestLogService::class.java)

        private fun defaultFile(): Path = Path.of(PathManager.getConfigPath(), "openrouter", FILE_NAME)
    }
}
