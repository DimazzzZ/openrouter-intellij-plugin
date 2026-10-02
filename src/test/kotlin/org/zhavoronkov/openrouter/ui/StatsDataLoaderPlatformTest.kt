package org.zhavoronkov.openrouter.ui

import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import kotlinx.coroutines.runBlocking
import org.mockito.Mockito.mock
import org.mockito.kotlin.whenever
import org.zhavoronkov.openrouter.models.ApiKeysListResponse
import org.zhavoronkov.openrouter.models.ApiResult
import org.zhavoronkov.openrouter.models.CreditsData
import org.zhavoronkov.openrouter.models.CreditsResponse
import org.zhavoronkov.openrouter.services.OpenRouterService
import org.zhavoronkov.openrouter.services.OpenRouterSettingsService
import java.io.IOException

/** The loader with an application: its answer, a failure included, arrives on the EDT. */
class StatsDataLoaderPlatformTest : BasePlatformTestCase() {

    private val settings: OpenRouterSettingsService = mock(OpenRouterSettingsService::class.java).also {
        whenever(it.isConfigured()).thenReturn(true)
        whenever(it.getProvisioningKey()).thenReturn("pk-test")
    }

    private fun router(throwing: Throwable? = null): OpenRouterService =
        mock(OpenRouterService::class.java).also { m ->
            runBlocking {
                if (throwing == null) {
                    whenever(m.getApiKeysList()).thenReturn(ApiResult.Success(ApiKeysListResponse(emptyList()), 200))
                } else {
                    whenever(m.getApiKeysList()).thenAnswer { throw throwing }
                }
                whenever(m.getCredits()).thenReturn(ApiResult.Success(CreditsResponse(CreditsData(10.0, 2.5)), 200))
                whenever(m.getActivity()).thenReturn(ApiResult.Error("no activity"))
            }
        }

    private fun load(router: OpenRouterService): StatsDataLoader.LoadResult {
        var result: StatsDataLoader.LoadResult? = null
        StatsDataLoader(settings, router).loadData { result = it }
        PlatformTestUtil.waitWithEventsDispatching("no answer", { result != null }, WAIT_SECONDS)
        return result!!
    }

    fun testTheAnswerArrivesOnTheEdtWithoutTheActivityItCouldNotRead() {
        val result = load(router()) as StatsDataLoader.LoadResult.Success

        assertEquals(2.5, result.data.creditsResponse.data.totalUsage)
        assertNull("activity could not be read", result.data.activityResponse)
    }

    /** Logged as an error with an application; openrouter.testMode turns that into a debug line here. */
    fun testAFailureIsAnsweredWithTheGenericErrorOnTheEdt() {
        val result = load(router(IOException("connection reset")))

        assertEquals(StatsDataLoader.LoadResult.Error("Failed to load data"), result)
    }

    private companion object {
        const val WAIT_SECONDS = 10
    }
}
