package org.zhavoronkov.openrouter.aiassistant

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.zhavoronkov.openrouter.services.OpenRouterSettingsService
import org.mockito.Mockito.`when` as whenever

/**
 * Unit tests for [OpenRouterChatContextProvider].
 *
 * Uses the internal constructor to inject a mocked [OpenRouterSettingsService],
 * avoiding the platform-bound `ApplicationManager.getApplication().getService(...)`
 * call inside the no-arg constructor.
 */
@DisplayName("OpenRouter Chat Context Provider Tests")
class OpenRouterChatContextProviderTest {

    private fun configuredService(
        configured: Boolean,
        apiKey: String = "sk-test",
        provisioningKey: String = "prov-test"
    ): OpenRouterSettingsService {
        val svc = mock(OpenRouterSettingsService::class.java)
        whenever(svc.isConfigured()).thenReturn(configured)
        whenever(svc.getApiKey()).thenReturn(apiKey)
        whenever(svc.getProvisioningKey()).thenReturn(provisioningKey)
        return svc
    }

    @Nested
    @DisplayName("Identity")
    inner class IdentityTests {
        @Test
        fun `getProviderId returns openrouter`() {
            val provider = OpenRouterChatContextProvider(configuredService(true))
            assertEquals("openrouter", provider.getProviderId())
        }

        @Test
        fun `getProviderName returns OpenRouter`() {
            val provider = OpenRouterChatContextProvider(configuredService(true))
            assertEquals("OpenRouter", provider.getProviderName())
        }
    }

    @Nested
    @DisplayName("isAvailable")
    inner class IsAvailableTests {
        @Test
        fun `returns true when configured`() {
            val provider = OpenRouterChatContextProvider(configuredService(true))
            assertTrue(provider.isAvailable(null))
        }

        @Test
        fun `returns false when not configured`() {
            val provider = OpenRouterChatContextProvider(configuredService(false))
            assertFalse(provider.isAvailable(null))
        }
    }

    @Nested
    @DisplayName("getContextInfo")
    inner class GetContextInfoTests {
        @Test
        fun `returns all expected keys`() {
            val provider = OpenRouterChatContextProvider(configuredService(true, "sk-x", "prov-y"))
            val info = provider.getContextInfo(null)

            assertEquals("openrouter", info["providerId"])
            assertEquals("OpenRouter", info["providerName"])
            assertEquals(true, info["isConfigured"])
            assertEquals(true, info["hasProvisioningKey"])
            assertEquals(true, info["hasApiKey"])
            assertEquals(400, info["modelCount"])

            @Suppress("UNCHECKED_CAST")
            val features = info["supportedFeatures"] as List<String>
            assertTrue(features.contains("chat"))
            assertTrue(features.contains("completion"))
            assertTrue(features.contains("streaming"))
            assertTrue(features.contains("multiple_models"))
        }

        @Test
        fun `reports missing api key when blank`() {
            val provider = OpenRouterChatContextProvider(configuredService(false, apiKey = "", provisioningKey = ""))
            val info = provider.getContextInfo(null)

            assertEquals(false, info["isConfigured"])
            assertEquals(false, info["hasApiKey"])
            assertEquals(false, info["hasProvisioningKey"])
        }

        @Test
        fun `hasApiKey is true when only api key set`() {
            val provider = OpenRouterChatContextProvider(
                configuredService(true, apiKey = "sk-only", provisioningKey = "")
            )
            val info = provider.getContextInfo(null)

            assertEquals(true, info["hasApiKey"])
            assertEquals(false, info["hasProvisioningKey"])
        }
    }

    @Nested
    @DisplayName("getAvailableModels")
    inner class GetAvailableModelsTests {
        @Test
        fun `returns curated list when configured`() {
            val provider = OpenRouterChatContextProvider(configuredService(true))
            val models = provider.getAvailableModels()

            assertTrue(models.isNotEmpty())
            assertTrue(models.contains("openai/gpt-4o"))
            assertTrue(models.contains("openai/gpt-4o-mini"))
            assertTrue(models.contains("anthropic/claude-3-sonnet"))
            assertTrue(models.contains("google/gemini-pro"))
        }

        @Test
        fun `returns empty list when not configured`() {
            val provider = OpenRouterChatContextProvider(configuredService(false))
            assertTrue(provider.getAvailableModels().isEmpty())
        }

        @Test
        fun `curated list contains no duplicates`() {
            val provider = OpenRouterChatContextProvider(configuredService(true))
            val models = provider.getAvailableModels()
            assertEquals(models.size, models.toSet().size)
        }
    }

    @Nested
    @DisplayName("getDefaultModel")
    inner class GetDefaultModelTests {
        @Test
        fun `returns gpt-4o-mini when configured`() {
            val provider = OpenRouterChatContextProvider(configuredService(true))
            assertEquals("openai/gpt-4o-mini", provider.getDefaultModel())
        }

        @Test
        fun `returns null when not configured`() {
            val provider = OpenRouterChatContextProvider(configuredService(false))
            assertNull(provider.getDefaultModel())
        }

        @Test
        fun `default model is present in available models when configured`() {
            val provider = OpenRouterChatContextProvider(configuredService(true))
            val defaultModel = provider.getDefaultModel()
            assertNotNull(defaultModel)
            assertTrue(provider.getAvailableModels().contains(defaultModel))
        }
    }

    @Nested
    @DisplayName("No-arg constructor")
    inner class NoArgConstructorTests {
        // The no-arg constructor calls OpenRouterSettingsService.getInstance(),
        // which is platform-bound. We only assert that the injected constructor
        // is the reachable seam and that its behavior is deterministic given
        // the mocked service (covered by the other test classes here).
        //
        // Direct construction of the no-arg overload is not attempted because
        // it would require the IntelliJ TestApplication (see `platformTest`
        // in build.gradle.kts). See B3 exclusions in MEMORY.md.
    }
}
