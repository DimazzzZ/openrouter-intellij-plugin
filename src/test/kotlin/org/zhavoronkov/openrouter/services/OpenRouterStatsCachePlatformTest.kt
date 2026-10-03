package org.zhavoronkov.openrouter.services

import com.google.gson.Gson
import com.intellij.openapi.application.ApplicationManager
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.zhavoronkov.openrouter.listeners.OpenRouterStatsListener
import org.zhavoronkov.openrouter.models.ActivityData
import org.zhavoronkov.openrouter.models.CreditsData
import org.zhavoronkov.openrouter.services.settings.ApiKeySettingsManager

/**
 * The stats cache under the platform: what it announces on the message bus as a refresh starts,
 * succeeds, fails or cannot be made, and the balance it hands the other plugins' providers.
 */
class OpenRouterStatsCachePlatformTest : BasePlatformTestCase() {

    private lateinit var server: MockWebServer
    private lateinit var settings: OpenRouterSettingsService
    private lateinit var scope: CoroutineScope
    private lateinit var api: OpenRouterService
    private val heard = mutableListOf<String>()

    override fun setUp() {
        super.setUp()
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path.orEmpty()
                return when {
                    path.contains("/credits") ->
                        MockResponse().setBody("""{"data":{"total_credits":10.0,"total_usage":2.5}}""")
                    path.contains("/activity") -> MockResponse().setBody("""{"data":[]}""")
                    path.contains("/keys") -> MockResponse().setBody("""{"data":[]}""")
                    else -> MockResponse().setResponseCode(404).setBody("{}")
                }
            }
        }
        server.start()
        settings = mock(OpenRouterSettingsService::class.java)
        `when`(settings.isConfigured()).thenReturn(true)
        `when`(settings.getProvisioningKey()).thenReturn("pk-test")
        `when`(settings.getApiKey()).thenReturn("sk-or-test")
        val keys = mock(ApiKeySettingsManager::class.java)
        `when`(keys.getStoredApiKey()).thenReturn("sk-or-test")
        `when`(settings.apiKeyManager).thenReturn(keys)
        api = OpenRouterService(
            gson = Gson(),
            settingsService = settings,
            baseUrlOverride = server.url("/api/v1").toString().removeSuffix("/")
        )
        scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        ApplicationManager.getApplication().messageBus.connect(testRootDisposable).subscribe(
            OpenRouterStatsListener.TOPIC,
            object : OpenRouterStatsListener {
                override fun onStatsUpdated(credits: CreditsData, activity: List<ActivityData>?) {
                    heard += "updated ${credits.totalUsage}"
                }

                override fun onStatsLoading() {
                    heard += "loading"
                }

                override fun onStatsError(errorMessage: String) {
                    heard += "error $errorMessage"
                }

                override fun onStatsUnavailable(reason: String) {
                    heard += "unavailable $reason"
                }
            }
        )
    }

    override fun tearDown() {
        try {
            scope.cancel()
            api.dispose()
            server.shutdown()
        } finally {
            super.tearDown()
        }
    }

    private fun cache() = OpenRouterStatsCache(settings, api, scope)

    private fun awaitHeard(count: Int) =
        PlatformTestUtil.waitWithEventsDispatching("heard only $heard", { heard.size >= count }, WAIT_SECONDS)

    fun testARefreshAnnouncesItsStartAndItsResult() {
        val cache = cache()

        runBlocking { cache.refresh()?.join() }
        awaitHeard(2)

        assertEquals(listOf("loading", "updated 2.5"), heard)
    }

    fun testAnUnconfiguredPluginAnnouncesWhyItDidNotRefresh() {
        `when`(settings.isConfigured()).thenReturn(false)

        cache().refresh()
        awaitHeard(1)

        assertEquals(listOf("error Not configured"), heard)
    }

    fun testARegularKeyAnnouncesThatAccountDataIsUnavailable() {
        `when`(settings.getProvisioningKey()).thenReturn("")

        cache().refresh()
        awaitHeard(1)

        assertEquals(listOf("unavailable Management Key required"), heard)
    }

    private companion object {
        const val WAIT_SECONDS = 10
    }
}
