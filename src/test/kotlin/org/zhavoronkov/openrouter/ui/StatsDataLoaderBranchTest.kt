package org.zhavoronkov.openrouter.ui

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.kotlin.whenever
import org.zhavoronkov.openrouter.models.ActivityData
import org.zhavoronkov.openrouter.models.ActivityResponse
import org.zhavoronkov.openrouter.models.ApiKeyInfo
import org.zhavoronkov.openrouter.models.ApiKeysListResponse
import org.zhavoronkov.openrouter.models.ApiResult
import org.zhavoronkov.openrouter.models.CreditsData
import org.zhavoronkov.openrouter.models.CreditsResponse
import org.zhavoronkov.openrouter.services.OpenRouterService
import org.zhavoronkov.openrouter.services.OpenRouterSettingsService
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Exercises the branch-heavy loading logic of [StatsDataLoader].
 *
 * These tests run under the fast `:test` task where `ApplicationManager.getApplication()`
 * is null, so [StatsDataLoader] takes the direct-call and `warn` branches — both reachable
 * here without the IntelliJ platform. The [OpenRouterService] suspend API is stubbed with
 * the mockito-kotlin + runBlocking pattern already used by OpenRouterStatsPopupThreadingTest.
 */
@DisplayName("StatsDataLoader Branch Tests")
class StatsDataLoaderBranchTest {

    private fun settingsMock(configured: Boolean, provisioningKey: String): OpenRouterSettingsService {
        val m = mock(OpenRouterSettingsService::class.java)
        whenever(m.isConfigured()).thenReturn(configured)
        whenever(m.getProvisioningKey()).thenReturn(provisioningKey)
        return m
    }

    private fun routerMock(
        apiKeys: ApiResult<ApiKeysListResponse>,
        credits: ApiResult<CreditsResponse>,
        activity: ApiResult<ActivityResponse>
    ): OpenRouterService = mock(OpenRouterService::class.java).also { m ->
        runBlocking {
            whenever(m.getApiKeysList()).thenReturn(apiKeys)
            whenever(m.getCredits()).thenReturn(credits)
            whenever(m.getActivity()).thenReturn(activity)
        }
    }

    private fun okApiKeys() = ApiResult.Success(
        ApiKeysListResponse(
            listOf(
                ApiKeyInfo(
                    name = "k", label = "l", limit = null, usage = 0.0, disabled = false,
                    createdAt = "2025-01-01", updatedAt = null, hash = "h"
                )
            )
        ),
        200
    )

    private fun okCredits() = ApiResult.Success(CreditsResponse(CreditsData(10.0, 2.5)), 200)

    private fun okActivity() = ApiResult.Success(
        ActivityResponse(
            listOf(
                ActivityData(
                    date = "2025-01-01", model = "m", modelPermaslug = null, endpointId = null,
                    providerName = null, usage = 1.0, byokUsageInference = null, requests = 1,
                    promptTokens = null, completionTokens = null, reasoningTokens = null
                )
            )
        ),
        200
    )

    private fun runLoad(
        settings: OpenRouterSettingsService?,
        router: OpenRouterService?
    ): StatsDataLoader.LoadResult? {
        val loader = StatsDataLoader(settings, router)
        val latch = CountDownLatch(1)
        var result: StatsDataLoader.LoadResult? = null
        loader.loadData { r ->
            result = r
            latch.countDown()
        }
        latch.await(5, TimeUnit.SECONDS)
        return result
    }

    @Nested
    @DisplayName("Guard branches")
    inner class GuardBranches {

        @Test
        fun `null services yield Error`() {
            val result = runLoad(null, null)
            assertTrue(result is StatsDataLoader.LoadResult.Error)
        }

        @Test
        fun `unconfigured settings yield NotConfigured`() {
            val settings = settingsMock(configured = false, provisioningKey = "pk")
            val router = routerMock(okApiKeys(), okCredits(), okActivity())
            val result = runLoad(settings, router)
            assertTrue(result is StatsDataLoader.LoadResult.NotConfigured)
        }

        @Test
        fun `blank provisioning key yields ProvisioningKeyMissing`() {
            val settings = settingsMock(configured = true, provisioningKey = "   ")
            val router = routerMock(okApiKeys(), okCredits(), okActivity())
            val result = runLoad(settings, router)
            assertTrue(result is StatsDataLoader.LoadResult.ProvisioningKeyMissing)
        }
    }

    @Nested
    @DisplayName("Async result handling")
    inner class AsyncResults {

        @Test
        fun `both success with activity yields Success carrying activity`() {
            val settings = settingsMock(configured = true, provisioningKey = "pk")
            val router = routerMock(okApiKeys(), okCredits(), okActivity())
            val result = runLoad(settings, router)
            assertTrue(result is StatsDataLoader.LoadResult.Success)
            val data = (result as StatsDataLoader.LoadResult.Success).data
            assertNotNull(data.activityResponse)
            assertEquals(1, data.apiKeysResponse.data.size)
        }

        @Test
        fun `both success with activity error yields Success and null activity`() {
            val settings = settingsMock(configured = true, provisioningKey = "pk")
            val router = routerMock(okApiKeys(), okCredits(), ApiResult.Error("activity boom", 500))
            val result = runLoad(settings, router)
            assertTrue(result is StatsDataLoader.LoadResult.Success)
            val data = (result as StatsDataLoader.LoadResult.Success).data
            assertEquals(null, data.activityResponse)
        }

        @Test
        fun `api keys error yields Error naming api keys`() {
            val settings = settingsMock(configured = true, provisioningKey = "pk")
            val router = routerMock(ApiResult.Error("keys boom", 401), okCredits(), okActivity())
            val result = runLoad(settings, router)
            assertTrue(result is StatsDataLoader.LoadResult.Error)
            assertTrue((result as StatsDataLoader.LoadResult.Error).message.contains("API keys"))
        }

        @Test
        fun `credits error yields Error naming credits`() {
            val settings = settingsMock(configured = true, provisioningKey = "pk")
            val router = routerMock(okApiKeys(), ApiResult.Error("credits boom", 402), okActivity())
            val result = runLoad(settings, router)
            assertTrue(result is StatsDataLoader.LoadResult.Error)
            assertTrue((result as StatsDataLoader.LoadResult.Error).message.contains("credits"))
        }

        @Test
        fun `credits error takes precedence when activity also errors`() {
            val settings = settingsMock(configured = true, provisioningKey = "pk")
            val router = routerMock(
                okApiKeys(), ApiResult.Error("credits boom", 500), ApiResult.Error("activity boom", 500)
            )
            val result = runLoad(settings, router)
            assertTrue(result is StatsDataLoader.LoadResult.Error)
        }
    }
}
