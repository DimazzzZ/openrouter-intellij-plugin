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

    private val log: RequestLog by lazy {
        RequestLog(defaultFile()) { OpenRouterSettingsService.getInstance().uiPreferencesManager.requestLogLimit }
    }

    /** Keeps [record] and tells the Requests tab, off the calling thread. */
    fun record(record: RequestRecord) = writer.execute {
        log.add(record)
        publish(record)
    }

    /** Every kept record, newest first. */
    fun recent(): List<RequestRecord> = log.recent()

    private val lookups = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val providerLookup = GenerationProviderLookup(fetch = { id ->
        OpenRouterService.getInstance().getGenerationProvider(id)
    })

    /** Looks up, in the background, the provider of [generationId]'s record, and fills it in. */
    fun fillProviderLater(generationId: String) {
        lookups.launch { providerLookup.providerOf(generationId)?.let { fillProvider(generationId, it) } }
    }

    /**
     * Sets the provider of [generationId]'s record. Published as an update, not an addition, so no
     * warning is announced or counted a second time.
     */
    fun fillProvider(generationId: String, provider: String) = writer.execute {
        if (log.fillProvider(generationId, provider)) {
            ApplicationManager.getApplication()?.messageBus?.syncPublisher(RequestLogListener.TOPIC)?.updated()
        }
    }

    /** Drops every record and tells the Requests tab, off the calling thread. */
    fun clear() = writer.execute {
        log.clear()
        publish(null)
    }

    private fun publish(added: RequestRecord?) {
        ApplicationManager.getApplication()?.messageBus?.syncPublisher(RequestLogListener.TOPIC)?.changed(added)
    }

    companion object {
        private const val FILE_NAME = "requests.jsonl"

        fun getInstance(): RequestLogService =
            ApplicationManager.getApplication().getService(RequestLogService::class.java)

        private fun defaultFile(): Path = Path.of(PathManager.getConfigPath(), "openrouter", FILE_NAME)
    }
}
