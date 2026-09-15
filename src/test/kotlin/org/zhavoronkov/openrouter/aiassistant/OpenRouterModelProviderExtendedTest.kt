package org.zhavoronkov.openrouter.aiassistant

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.mockito.Mockito.mock
import org.zhavoronkov.openrouter.models.ApiResult
import org.zhavoronkov.openrouter.models.OpenRouterModelInfo
import org.zhavoronkov.openrouter.services.FavoriteModelsService
import org.zhavoronkov.openrouter.services.OpenRouterService
import org.zhavoronkov.openrouter.services.OpenRouterSettingsService

/**
 * Extended unit tests for [OpenRouterModelProvider] covering:
 * - identity accessors,
 * - `isAvailable` short-circuit branches,
 * - `testConnection` error paths,
 * - `getConfigurationStatus` all three branches,
 * - `getModelDisplayName` / `getModelDescription` model-id branches (exercised
 *   transitively through `getAvailableModels`),
 * - fallback list when the /models endpoint returns empty or throws,
 * - `OpenRouterAIModel.ModelPricing` data class.
 */
@DisplayName("OpenRouter Model Provider Extended Tests")
class OpenRouterModelProviderExtendedTest {

    private fun mocks(
        configured: Boolean = true,
        apiKey: String = "sk-test",
        favoriteModels: List<OpenRouterModelInfo>? = emptyList(),
        favoriteModelsThrows: Boolean = false,
        testConnectionResult: ApiResult<Boolean> = ApiResult.Success(true, 200)
    ): Triple<OpenRouterService, OpenRouterSettingsService, FavoriteModelsService> {
        val settingsService = mock(OpenRouterSettingsService::class.java)
        val favoriteModelsService = mock(FavoriteModelsService::class.java)
        val openRouterService = mock(OpenRouterService::class.java)
        Mockito.`when`(settingsService.isConfigured()).thenReturn(configured)
        Mockito.`when`(settingsService.getApiKey()).thenReturn(apiKey)
        runBlocking {
            if (favoriteModelsThrows) {
                Mockito.`when`(favoriteModelsService.getAvailableModels(forceRefresh = false))
                    .thenThrow(RuntimeException("simulated /models failure"))
            } else {
                Mockito.`when`(favoriteModelsService.getAvailableModels(forceRefresh = false))
                    .thenReturn(favoriteModels)
            }
            Mockito.`when`(openRouterService.testConnection()).thenReturn(testConnectionResult)
        }
        return Triple(openRouterService, settingsService, favoriteModelsService)
    }

    private fun provider(
        configured: Boolean = true,
        apiKey: String = "sk-test",
        favoriteModels: List<OpenRouterModelInfo>? = emptyList(),
        favoriteModelsThrows: Boolean = false,
        testConnectionResult: ApiResult<Boolean> = ApiResult.Success(true, 200)
    ): OpenRouterModelProvider {
        val (ors, ss, fms) = mocks(configured, apiKey, favoriteModels, favoriteModelsThrows, testConnectionResult)
        return OpenRouterModelProvider(ors, ss, fms)
    }

    @Nested
    @DisplayName("Identity accessors")
    inner class IdentityTests {
        @Test
        fun `getProviderName returns OpenRouter`() {
            assertEquals("OpenRouter", provider().getProviderName())
        }

        @Test
        fun `getProviderDisplayName mentions 400+ models`() {
            val name = provider().getProviderDisplayName()
            assertTrue(name.contains("400"))
            assertTrue(name.contains("OpenRouter"))
        }

        @Test
        fun `getProviderDescription is non-empty`() {
            val desc = provider().getProviderDescription()
            assertTrue(desc.isNotEmpty())
        }
    }

    @Nested
    @DisplayName("isAvailable")
    inner class IsAvailableTests {
        @Test
        fun `true when configured and api key present`() {
            assertTrue(provider(configured = true, apiKey = "sk-x").isAvailable())
        }

        @Test
        fun `false when configured but api key blank`() {
            assertFalse(provider(configured = true, apiKey = "").isAvailable())
        }

        @Test
        fun `false when not configured`() {
            assertFalse(provider(configured = false).isAvailable())
        }

        @Test
        fun `false when isConfigured throws IllegalStateException`() {
            val settingsService = mock(OpenRouterSettingsService::class.java)
            val favoriteModelsService = mock(FavoriteModelsService::class.java)
            val openRouterService = mock(OpenRouterService::class.java)
            Mockito.`when`(settingsService.isConfigured()).thenThrow(IllegalStateException("boom"))
            val p = OpenRouterModelProvider(openRouterService, settingsService, favoriteModelsService)
            assertFalse(p.isAvailable())
        }

        @Test
        fun `false when getApiKey throws IllegalArgumentException`() {
            val settingsService = mock(OpenRouterSettingsService::class.java)
            val favoriteModelsService = mock(FavoriteModelsService::class.java)
            val openRouterService = mock(OpenRouterService::class.java)
            Mockito.`when`(settingsService.isConfigured()).thenReturn(true)
            Mockito.`when`(settingsService.getApiKey()).thenThrow(IllegalArgumentException("bad key"))
            val p = OpenRouterModelProvider(openRouterService, settingsService, favoriteModelsService)
            assertFalse(p.isAvailable())
        }
    }

    @Nested
    @DisplayName("getModel")
    inner class GetModelTests {
        @Test
        fun `returns null for unknown id`() {
            val model = OpenRouterModelInfo(
                id = "openai/gpt-4o",
                name = "GPT-4o",
                created = 1L,
                description = null,
                architecture = null,
                topProvider = null,
                pricing = null,
                contextLength = 128000,
                perRequestLimits = null
            )
            val p = provider(favoriteModels = listOf(model))
            assertNull(p.getModel("does/not-exist"))
        }

        @Test
        fun `returns matching model when id present`() {
            val model = OpenRouterModelInfo(
                id = "openai/gpt-4o",
                name = "GPT-4o",
                created = 1L,
                description = "desc",
                architecture = null,
                topProvider = null,
                pricing = null,
                contextLength = 128000,
                perRequestLimits = null
            )
            val p = provider(favoriteModels = listOf(model))
            val found = p.getModel("openai/gpt-4o")
            assertNotNull(found)
            assertEquals("openai/gpt-4o", found?.id)
        }
    }

    @Nested
    @DisplayName("testConnection")
    inner class TestConnectionTests {
        @Test
        fun `false when result is ApiResult Error`() {
            val p = provider(testConnectionResult = ApiResult.Error("boom", 500))
            assertFalse(p.testConnection())
        }

        @Test
        fun `true when result is ApiResult Success(true)`() = runBlocking {
            val p = provider(testConnectionResult = ApiResult.Success(true, 200))
            assertTrue(p.testConnection())
        }

        @Test
        fun `false when result is ApiResult Success(false)`() {
            val p = provider(testConnectionResult = ApiResult.Success(false, 200))
            assertFalse(p.testConnection())
        }

        @Test
        fun `false when not available`() {
            val p = provider(configured = false)
            assertFalse(p.testConnection())
        }

        @Test
        fun `false when testConnection throws IllegalStateException`() {
            val settingsService = mock(OpenRouterSettingsService::class.java)
            val favoriteModelsService = mock(FavoriteModelsService::class.java)
            val openRouterService = mock(OpenRouterService::class.java)
            Mockito.`when`(settingsService.isConfigured()).thenReturn(true)
            Mockito.`when`(settingsService.getApiKey()).thenReturn("sk-test")
            runBlocking {
                Mockito.`when`(openRouterService.testConnection())
                    .thenThrow(IllegalStateException("service down"))
            }
            val p = OpenRouterModelProvider(openRouterService, settingsService, favoriteModelsService)
            assertFalse(p.testConnection())
        }

        @Test
        fun `false when testConnection throws IllegalArgumentException`() {
            val settingsService = mock(OpenRouterSettingsService::class.java)
            val favoriteModelsService = mock(FavoriteModelsService::class.java)
            val openRouterService = mock(OpenRouterService::class.java)
            Mockito.`when`(settingsService.isConfigured()).thenReturn(true)
            Mockito.`when`(settingsService.getApiKey()).thenReturn("sk-test")
            runBlocking {
                Mockito.`when`(openRouterService.testConnection())
                    .thenThrow(IllegalArgumentException("bad arg"))
            }
            val p = OpenRouterModelProvider(openRouterService, settingsService, favoriteModelsService)
            assertFalse(p.testConnection())
        }
    }

    @Nested
    @DisplayName("getConfigurationStatus")
    inner class ConfigurationStatusTests {
        @Test
        fun `not-configured branch`() {
            val status = provider(configured = false).getConfigurationStatus()
            assertTrue(status.contains("Not configured"))
        }

        @Test
        fun `configuration-issue branch when testConnection fails`() {
            val p = provider(testConnectionResult = ApiResult.Success(false, 200))
            val status = p.getConfigurationStatus()
            assertTrue(status.contains("Configuration issue"))
        }

        @Test
        fun `ready branch reports model count`() {
            val model = OpenRouterModelInfo(
                id = "openai/gpt-4o",
                name = "GPT-4o",
                created = 1L,
                description = null,
                architecture = null,
                topProvider = null,
                pricing = null,
                contextLength = 128000,
                perRequestLimits = null
            )
            val p = provider(favoriteModels = listOf(model))
            val status = p.getConfigurationStatus()
            assertTrue(status.startsWith("Ready"))
            assertTrue(status.contains("1 models"))
        }
    }

    @Nested
    @DisplayName("fetchAvailableModels fallbacks")
    inner class FetchAvailableModelsFallbackTests {
        @Test
        fun `falls back to default models when service returns null`() {
            val p = provider(favoriteModels = null)
            val models = p.getAvailableModels()

            assertTrue(models.isNotEmpty())
            assertTrue(models.any { it.id == "openai/gpt-4o" })
            assertTrue(models.any { it.id == "anthropic/claude-3.5-sonnet" })
        }

        @Test
        fun `falls back to default models when service throws`() {
            val p = provider(favoriteModelsThrows = true)
            val models = p.getAvailableModels()

            assertTrue(models.isNotEmpty())
            assertTrue(models.any { it.id == "openai/gpt-4o" })
        }

        // Helper: a model with a blank name + null description forces the provider
        // to derive both via getModelDisplayName()/getModelDescription().
        private fun blankModel(id: String) = OpenRouterModelInfo(
            id = id,
            name = "",
            created = 1L,
            description = null,
            architecture = null,
            topProvider = null,
            pricing = null,
            contextLength = null,
            perRequestLimits = null
        )

        @Test
        fun `derives curated display names for known model ids when name is blank`() {
            val ids = listOf(
                "openai/gpt-4o-mini",
                "openai/gpt-4o",
                "anthropic/claude-3.5-sonnet",
                "anthropic/claude-3-haiku",
                "google/gemini-pro-1.5",
                "meta-llama/llama-3.1-70b-instruct",
                "microsoft/wizardlm-2-8x22b",
                "qwen/qwen-2.5-72b-instruct"
            )
            val p = provider(favoriteModels = ids.map { blankModel(it) })
            val byId = p.getAvailableModels().associateBy { it.id }

            assertEquals("GPT-4o Mini", byId["openai/gpt-4o-mini"]?.name)
            assertEquals("GPT-4o", byId["openai/gpt-4o"]?.name)
            assertEquals("Claude 3.5 Sonnet", byId["anthropic/claude-3.5-sonnet"]?.name)
            assertEquals("Claude 3 Haiku", byId["anthropic/claude-3-haiku"]?.name)
            assertEquals("Gemini Pro 1.5", byId["google/gemini-pro-1.5"]?.name)
            assertEquals("Llama 3.1 70B", byId["meta-llama/llama-3.1-70b-instruct"]?.name)
            assertEquals("WizardLM-2 8x22B", byId["microsoft/wizardlm-2-8x22b"]?.name)
            assertEquals("Qwen 2.5 72B", byId["qwen/qwen-2.5-72b-instruct"]?.name)
        }

        @Test
        fun `derives curated descriptions for known model ids when description is null`() {
            val ids = listOf(
                "openai/gpt-4o",
                "anthropic/claude-3.5-sonnet",
                "anthropic/claude-3-haiku",
                "google/gemini-pro-1.5",
                "meta-llama/llama-3.1-70b-instruct",
                "microsoft/wizardlm-2-8x22b",
                "qwen/qwen-2.5-72b-instruct"
            )
            val p = provider(favoriteModels = ids.map { blankModel(it) })
            val byId = p.getAvailableModels().associateBy { it.id }

            assertTrue(byId["openai/gpt-4o"]?.description!!.contains("OpenAI"))
            assertTrue(byId["anthropic/claude-3.5-sonnet"]?.description!!.contains("Anthropic"))
            assertTrue(byId["anthropic/claude-3-haiku"]?.description!!.contains("Anthropic"))
            assertTrue(byId["google/gemini-pro-1.5"]?.description!!.contains("Google"))
            assertTrue(byId["meta-llama/llama-3.1-70b-instruct"]?.description!!.contains("Meta"))
            assertTrue(byId["microsoft/wizardlm-2-8x22b"]?.description!!.contains("Microsoft"))
            assertTrue(byId["qwen/qwen-2.5-72b-instruct"]?.description!!.contains("Alibaba"))
        }

        @Test
        fun `unknown model id gets slug-based display name and generic description`() {
            val custom = OpenRouterModelInfo(
                id = "acme/some-cool-model",
                name = "",
                created = 1L,
                description = null,
                architecture = null,
                topProvider = null,
                pricing = null,
                contextLength = null,
                perRequestLimits = null
            )
            val p = provider(favoriteModels = listOf(custom))
            val model = p.getModel("acme/some-cool-model")

            assertNotNull(model)
            // slug after "/" with hyphens turned into spaces, each word capitalized
            assertEquals("Some Cool Model", model?.name)
            assertEquals("AI model available through OpenRouter", model?.description)
        }

        @Test
        fun `existing name and description are preserved when non-blank`() {
            val custom = OpenRouterModelInfo(
                id = "custom/model",
                name = "Custom Display",
                created = 1L,
                description = "Custom description",
                architecture = null,
                topProvider = null,
                pricing = null,
                contextLength = 8192,
                perRequestLimits = null
            )
            val p = provider(favoriteModels = listOf(custom))
            val model = p.getModel("custom/model")

            assertEquals("Custom Display", model?.name)
            assertEquals("Custom description", model?.description)
            assertEquals(8192, model?.contextLength)
        }
    }

    @Nested
    @DisplayName("OpenRouterAIModel.ModelPricing")
    inner class ModelPricingTests {
        @Test
        fun `data class stores input and output costs`() {
            val pricing = OpenRouterAIModel.ModelPricing(
                inputCostPer1kTokens = 0.01,
                outputCostPer1kTokens = 0.02
            )
            assertEquals(0.01, pricing.inputCostPer1kTokens)
            assertEquals(0.02, pricing.outputCostPer1kTokens)
        }

        @Test
        fun `equal pricings compare equal`() {
            val a = OpenRouterAIModel.ModelPricing(0.01, 0.02)
            val b = OpenRouterAIModel.ModelPricing(0.01, 0.02)
            assertEquals(a, b)
        }
    }
}
