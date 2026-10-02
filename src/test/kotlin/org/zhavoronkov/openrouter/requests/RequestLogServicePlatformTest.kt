package org.zhavoronkov.openrouter.requests

import com.intellij.openapi.application.ApplicationManager
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.zhavoronkov.openrouter.services.OpenRouterSettingsService
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

/**
 * The application's Requests log: each change is made on its own thread and then published, so
 * these tests wait for the publication rather than for the call. The log lives in the test
 * sandbox's config directory and is cleared before and after each test.
 */
class RequestLogServicePlatformTest : BasePlatformTestCase() {

    private lateinit var service: RequestLogService

    /** What the log published, in order: a record added, null for a clear, "updated" for a fact filled in. */
    private val published = LinkedBlockingQueue<Any>()

    override fun setUp() {
        super.setUp()
        service = RequestLogService.getInstance()
        ApplicationManager.getApplication().messageBus.connect(testRootDisposable)
            .subscribe(
                RequestLogListener.TOPIC,
                object : RequestLogListener {
                    override fun changed(added: RequestRecord?) {
                        published += added ?: CLEARED
                    }

                    override fun updated() {
                        published += UPDATED
                    }
                }
            )
        service.clear()
        assertEquals(CLEARED, next())
    }

    override fun tearDown() {
        try {
            service.clear()
            next()
        } finally {
            super.tearDown()
        }
    }

    private fun next(): Any? = published.poll(WAIT_SECONDS, TimeUnit.SECONDS)

    private fun record(generationId: String? = null, bodiesId: String? = null) = RequestRecord(
        startedAtMillis = 1_000,
        durationMillis = 10,
        source = RequestSource.PROXY,
        sender = "Junie",
        requestedModel = "openai/gpt-4o",
        reply = ReplyFacts(generationId = generationId),
        bodiesId = bodiesId
    )

    fun testARecordIsKeptAndAnnounced() {
        val record = record()

        service.record(record)

        assertEquals(record, next())
        assertEquals(listOf(record), service.recent())
    }

    fun testBodiesSavedBeforeTheirRecordAreThereWhenItIsAnnounced() {
        val bodies = RequestBodies(sent = """{"model":"m"}""", reply = "hi")
        val id = RequestBodyStore.newId()

        service.saveBodies(id, bodies)
        service.record(record(bodiesId = id))
        next()

        assertEquals(bodies, service.bodies(id))
    }

    fun testAProviderFoundLaterIsFilledInAsAnUpdateOnlyForAKnownGeneration() {
        service.record(record(generationId = "gen-1"))
        next()

        service.fillProvider("gen-unknown", "Azure")
        service.fillProvider("gen-1", "Azure")

        assertEquals("only the known generation is announced", UPDATED, next())
        assertEquals("Azure", service.recent().single().reply.provider)
        assertNull(published.poll())
    }

    fun testClearingDropsTheRecordsAndTheirBodies() {
        val id = RequestBodyStore.newId()
        service.saveBodies(id, RequestBodies(reply = "hi"))
        service.record(record(bodiesId = id))
        next()
        assertNotNull(service.bodies(id))

        service.clear()

        assertEquals(CLEARED, next())
        assertEquals(emptyList<RequestRecord>(), service.recent())
        assertNull(service.bodies(id))
    }

    fun testKeepingBodiesFollowsTheSetting() {
        val preferences = OpenRouterSettingsService.getInstance().uiPreferencesManager
        val before = preferences.keepRequestBodies
        try {
            preferences.keepRequestBodies = true
            assertTrue(service.keepsBodies)
            preferences.keepRequestBodies = false
            assertFalse(service.keepsBodies)
        } finally {
            preferences.keepRequestBodies = before
        }
    }

    private companion object {
        const val WAIT_SECONDS = 5L
        const val CLEARED = "cleared"
        const val UPDATED = "updated"
    }
}
