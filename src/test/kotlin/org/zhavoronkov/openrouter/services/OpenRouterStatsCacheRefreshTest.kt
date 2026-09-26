package org.zhavoronkov.openrouter.services

import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.zhavoronkov.openrouter.services.settings.ApiKeySettingsManager
import org.zhavoronkov.openrouter.testing.OkHttpLeakSafeExtension
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Exercises the refresh path of [OpenRouterStatsCache] against a [MockWebServer] standing in for
 * OpenRouter.
 *
 * The cache takes its two collaborators and its scope as constructor parameters, each defaulted to
 * what it used to resolve or build itself, so this needs no platform: the settings service is a
 * mock and the API client is a real [OpenRouterService] pointed at the mock server. `refresh()`
 * hands back the job it launched, which is what lets these tests await the refresh instead of
 * polling `isLoading()`.
 *
 * The mock server answers by path rather than from a queue: the three requests a refresh issues go
 * out in parallel, so a FIFO queue would hand them each other's answers at random.
 */
@ExtendWith(OkHttpLeakSafeExtension::class)
@DisplayName("OpenRouterStatsCache refresh")
class OpenRouterStatsCacheRefreshTest {

    private lateinit var server: MockWebServer
    private lateinit var settingsService: OpenRouterSettingsService
    private lateinit var scope: CoroutineScope
    private val caches = mutableListOf<OpenRouterStatsCache>()
    private val services = mutableListOf<OpenRouterService>()

    /** Per-endpoint answers, overridable per test. */
    private var creditsResponse = MockResponse().setResponseCode(200)
        .setBody("""{"data":{"total_credits":100.0,"total_usage":25.0}}""")
    private var activityResponse = MockResponse().setResponseCode(200)
        .setBody("""{"data":[{"date":"2026-09-19","model":"openai/gpt-4o-mini","usage":1.5,"requests":3}]}""")
    private var apiKeysResponse = MockResponse().setResponseCode(200)
        .setBody(
            """{"data":[{"name":"k","label":"Key","limit":10.0,"usage":2.0,"disabled":false,""" +
                """"created_at":"2026-01-01T00:00:00Z","updated_at":null,"hash":"h"}]}"""
        )

    @BeforeEach
    fun setUp() {
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path.orEmpty()
                return when {
                    path.contains("/credits") -> creditsResponse
                    path.contains("/activity") -> activityResponse
                    path.contains("/keys") -> apiKeysResponse
                    else -> MockResponse().setResponseCode(404).setBody("{}")
                }
            }
        }
        server.start()

        settingsService = mock(OpenRouterSettingsService::class.java)
        `when`(settingsService.isConfigured()).thenReturn(true)
        `when`(settingsService.getProvisioningKey()).thenReturn("pk-test")
        `when`(settingsService.getApiKey()).thenReturn("sk-or-test")
        val apiKeyManager = mock(ApiKeySettingsManager::class.java)
        `when`(apiKeyManager.getStoredApiKey()).thenReturn("sk-or-test")
        `when`(settingsService.apiKeyManager).thenReturn(apiKeyManager)

        scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    }

    @AfterEach
    fun tearDown() {
        scope.cancel()
        caches.forEach { it.dispose() }
        services.forEach { it.dispose() }
        server.shutdown()
    }

    private fun cache(): OpenRouterStatsCache {
        val service = OpenRouterService(
            gson = Gson(),
            settingsService = settingsService,
            baseUrlOverride = server.url("/api/v1").toString().removeSuffix("/")
        ).also { services += it }
        return OpenRouterStatsCache(
            settingsServiceOverride = settingsService,
            openRouterServiceOverride = service,
            scope = scope
        ).also { caches += it }
    }

    private fun OpenRouterStatsCache.refreshAndAwait() = runBlocking { refresh()?.join() }

    @Nested
    @DisplayName("Preconditions")
    inner class Preconditions {

        @Test
        @DisplayName("an unconfigured plugin does not refresh and says so")
        fun notConfigured() {
            `when`(settingsService.isConfigured()).thenReturn(false)
            val cache = cache()

            val job = cache.refresh()

            assertNull(job, "no work should be launched")
            assertEquals("Not configured", cache.getLastError())
            assertEquals(0, server.requestCount)
        }

        @Test
        @DisplayName("a configured plugin without a Management Key does not spend requests to learn it")
        fun noManagementKey() {
            `when`(settingsService.getProvisioningKey()).thenReturn("")
            val cache = cache()

            val job = cache.refresh()

            assertNull(job, "no work should be launched")
            assertEquals("Management Key required", cache.getLastError())
            assertEquals(0, server.requestCount, "a missing key is an answer, not a reason to ask")
        }
    }

    @Nested
    @DisplayName("A successful refresh")
    inner class SuccessfulRefresh {

        @Test
        @DisplayName("fills credits, activity and keys, and clears the previous error")
        fun fillsEveryCache() {
            val cache = cache()

            cache.refreshAndAwait()

            assertEquals(100.0, cache.getCachedCredits()?.totalCredits)
            assertEquals(25.0, cache.getCachedCredits()?.totalUsage)
            assertEquals(1, cache.getCachedActivity()?.size)
            assertEquals(1, cache.getCachedApiKeys()?.data?.size)
            assertNull(cache.getLastError())
            assertTrue(cache.hasCachedData())
            assertTrue(cache.getLastUpdateTimestamp() > 0)
        }

        @Test
        @DisplayName("leaves the loading flag down once it is over")
        fun clearsLoadingFlag() {
            val cache = cache()

            cache.refreshAndAwait()

            assertFalse(cache.isLoading())
        }
    }

    @Nested
    @DisplayName("Partial failures")
    inner class PartialFailures {

        @Test
        @DisplayName("activity is optional - losing it still leaves a usable balance")
        fun activityIsOptional() {
            activityResponse = MockResponse().setResponseCode(403).setBody("""{"error":{"message":"nope"}}""")
            val cache = cache()

            cache.refreshAndAwait()

            assertNotNull(cache.getCachedCredits(), "credits must survive a failed activity call")
            assertNull(cache.getCachedActivity())
            assertNull(cache.getLastError(), "an optional gap is not an error for the whole refresh")
        }

        @Test
        @DisplayName("the key list is optional too")
        fun apiKeysAreOptional() {
            apiKeysResponse = MockResponse().setResponseCode(403).setBody("""{"error":{"message":"nope"}}""")
            val cache = cache()

            cache.refreshAndAwait()

            assertNotNull(cache.getCachedCredits())
            assertNull(cache.getCachedApiKeys())
            assertNull(cache.getLastError())
        }

        @Test
        @DisplayName("credits are not optional - losing them fails the refresh and names why")
        fun creditsAreRequired() {
            creditsResponse = MockResponse().setResponseCode(402)
                .setBody("""{"error":{"message":"Insufficient credits"}}""")
            val cache = cache()

            cache.refreshAndAwait()

            assertNull(cache.getCachedCredits())
            assertFalse(cache.hasCachedData())
            assertEquals("Insufficient credits", cache.getLastError())
        }

        @Test
        @DisplayName("a failed refresh leaves whatever was already cached in place")
        fun keepsPreviousDataOnFailure() {
            val cache = cache()
            cache.refreshAndAwait()
            val firstTimestamp = cache.getLastUpdateTimestamp()

            creditsResponse = MockResponse().setResponseCode(500).setBody("""{"error":{"message":"boom"}}""")
            cache.refreshAndAwait()

            assertEquals(100.0, cache.getCachedCredits()?.totalCredits, "stale beats empty on the status bar")
            assertEquals(firstTimestamp, cache.getLastUpdateTimestamp(), "a failure must not look like an update")
            assertEquals("boom", cache.getLastError())
        }
    }

    @Nested
    @DisplayName("Cache lifecycle")
    inner class Lifecycle {

        @Test
        @DisplayName("clearing drops everything a refresh had filled")
        fun clearingDropsEverything() {
            val cache = cache()
            cache.refreshAndAwait()

            cache.clearCache()

            assertNull(cache.getCachedCredits())
            assertNull(cache.getCachedActivity())
            assertNull(cache.getCachedApiKeys())
            assertNull(cache.getLastError())
            assertEquals(0L, cache.getLastUpdateTimestamp())
            assertFalse(cache.hasCachedData())
        }

        @Test
        @DisplayName("a refresh after a clear refills the cache")
        fun refreshAfterClearRefills() {
            val cache = cache()
            cache.refreshAndAwait()
            cache.clearCache()

            cache.refreshAndAwait()

            assertEquals(100.0, cache.getCachedCredits()?.totalCredits)
        }
    }

    @Nested
    @DisplayName("Concurrency guard")
    inner class ConcurrencyGuard {

        @Test
        @DisplayName("a refresh already in flight is not started a second time")
        fun secondRefreshIsSkipped() {
            val release = CountDownLatch(1)
            val creditsArrived = CountDownLatch(1)
            server.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    val path = request.path.orEmpty()
                    if (path.contains("/credits")) {
                        creditsArrived.countDown()
                        release.await(LATCH_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                        return creditsResponse
                    }
                    return when {
                        path.contains("/activity") -> activityResponse
                        path.contains("/keys") -> apiKeysResponse
                        else -> MockResponse().setResponseCode(404).setBody("{}")
                    }
                }
            }
            val cache = cache()

            val first = cache.refresh()
            assertTrue(
                creditsArrived.await(LATCH_TIMEOUT_SECONDS, TimeUnit.SECONDS),
                "the first refresh should have reached the server"
            )
            val second = cache.refresh()
            release.countDown()
            runBlocking { first?.join() }

            assertNotNull(first, "the first refresh launches work")
            assertNull(second, "a refresh already in flight must not be started again")
            assertEquals(100.0, cache.getCachedCredits()?.totalCredits)
        }
    }

    /**
     * The API client answers failures as [org.zhavoronkov.openrouter.models.ApiResult.Error] rather
     * than by throwing, so these arms are only reachable through a client that does throw - which is
     * exactly the case they exist for.
     */
    @Nested
    @DisplayName("A client that throws instead of answering")
    inner class ThrowingClient {

        private fun cacheWith(failure: Throwable): OpenRouterStatsCache {
            val service = mock(OpenRouterService::class.java)
            // thenThrow refuses a checked exception the suspend fun's JVM signature does not
            // declare; an Answer that throws reaches the same catch without that restriction.
            `when`(runBlocking { service.getCredits() }).thenAnswer { throw failure }
            return OpenRouterStatsCache(
                settingsServiceOverride = settingsService,
                openRouterServiceOverride = service,
                scope = scope
            ).also { caches += it }
        }

        @Test
        @DisplayName("an IO failure is reported as a network error, not propagated")
        fun ioFailure() {
            val cache = cacheWith(IOException("socket closed"))

            cache.refreshAndAwait()

            assertEquals("Network error: socket closed", cache.getLastError())
            assertFalse(cache.isLoading(), "the loading flag must come down even on failure")
        }

        @Test
        @DisplayName("an illegal state is reported as an error, not propagated")
        fun illegalState() {
            val cache = cacheWith(IllegalStateException("service gone"))

            cache.refreshAndAwait()

            assertEquals("Error: service gone", cache.getLastError())
        }

        @Test
        @DisplayName("an illegal argument is reported as an error, not propagated")
        fun illegalArgument() {
            val cache = cacheWith(IllegalArgumentException("bad input"))

            cache.refreshAndAwait()

            assertEquals("Invalid argument: bad input", cache.getLastError())
        }

        @Test
        @DisplayName("any other runtime failure is reported as unexpected, not propagated")
        fun runtimeFailure() {
            val cache = cacheWith(NullPointerException("boom"))

            cache.refreshAndAwait()

            assertEquals("Unexpected error: boom", cache.getLastError())
        }
    }

    private companion object {
        const val LATCH_TIMEOUT_SECONDS = 5L
    }
}
