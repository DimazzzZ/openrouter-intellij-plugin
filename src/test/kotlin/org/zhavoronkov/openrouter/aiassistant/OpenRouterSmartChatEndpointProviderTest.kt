package org.zhavoronkov.openrouter.aiassistant

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.zhavoronkov.openrouter.services.OpenRouterSettingsService
import org.mockito.Mockito.`when` as whenever

/**
 * Unit tests for [OpenRouterSmartChatEndpointProvider].
 *
 * Uses the internal constructor to inject a mocked [OpenRouterSettingsService],
 * avoiding the platform-bound `ApplicationManager.getApplication().getService(...)`
 * call inside the no-arg constructor.
 */
@DisplayName("OpenRouter Smart Chat Endpoint Provider Tests")
class OpenRouterSmartChatEndpointProviderTest {

    private fun configuredService(
        configured: Boolean,
        apiKey: String = "sk-test"
    ): OpenRouterSettingsService {
        val svc = mock(OpenRouterSettingsService::class.java)
        whenever(svc.isConfigured()).thenReturn(configured)
        whenever(svc.getApiKey()).thenReturn(apiKey)
        return svc
    }

    @Nested
    @DisplayName("Identity")
    inner class IdentityTests {
        @Test
        fun `getProviderId returns openrouter`() {
            val provider = OpenRouterSmartChatEndpointProvider(configuredService(true))
            assertEquals("openrouter", provider.getProviderId())
        }

        @Test
        fun `getProviderName returns OpenRouter`() {
            val provider = OpenRouterSmartChatEndpointProvider(configuredService(true))
            assertEquals("OpenRouter", provider.getProviderName())
        }

        @Test
        fun `getProviderDescription is non-empty`() {
            val provider = OpenRouterSmartChatEndpointProvider(configuredService(true))
            val desc = provider.getProviderDescription()
            assertTrue(desc.isNotEmpty())
            assertTrue(desc.contains("400"))
        }
    }

    @Nested
    @DisplayName("isAvailable")
    inner class IsAvailableTests {
        @Test
        fun `returns true when configured`() {
            val provider = OpenRouterSmartChatEndpointProvider(configuredService(true))
            assertTrue(provider.isAvailable(null))
        }

        @Test
        fun `returns false when not configured`() {
            val provider = OpenRouterSmartChatEndpointProvider(configuredService(false))
            assertFalse(provider.isAvailable(null))
        }
    }

    @Nested
    @DisplayName("getChatEndpoint")
    inner class GetChatEndpointTests {
        @Test
        fun `returns correct OpenRouter endpoint`() {
            val provider = OpenRouterSmartChatEndpointProvider(configuredService(true))
            val endpoint = provider.getChatEndpoint()
            assertEquals("https://openrouter.ai/api/v1/chat/completions", endpoint)
        }
    }

    @Nested
    @DisplayName("getApiKey")
    inner class GetApiKeyTests {
        @Test
        fun `returns api key when set`() {
            val provider = OpenRouterSmartChatEndpointProvider(configuredService(true, "sk-test-123"))
            assertEquals("sk-test-123", provider.getApiKey())
        }

        @Test
        fun `returns null when blank`() {
            val provider = OpenRouterSmartChatEndpointProvider(configuredService(true, ""))
            assertEquals(null, provider.getApiKey())
        }

        @Test
        fun `returns non-null when service still exposes key even if not-configured flag is false`() {
            // getApiKey() reflects the settings service's raw apiKey value —
            // it does NOT re-check isConfigured(). The "not configured" gate
            // lives in isAvailable() / validateConfiguration().
            val provider = OpenRouterSmartChatEndpointProvider(configuredService(false, "sk-test"))
            assertEquals("sk-test", provider.getApiKey())
        }
    }

    @Nested
    @DisplayName("getAvailableModels")
    inner class GetAvailableModelsTests {
        @Test
        fun `returns curated model list when configured`() {
            val provider = OpenRouterSmartChatEndpointProvider(configuredService(true))
            val models = provider.getAvailableModels(null)

            assertTrue(models.isNotEmpty())
            assertEquals(7, models.size) // 7 curated models

            // Check structure of first model
            val first = models.first()
            assertEquals("openai/gpt-4o", first.id)
            assertEquals("GPT-4o", first.name)
            assertEquals("OpenRouter", first.provider)
            assertEquals(128000, first.contextLength)
            assertTrue(first.supportsStreaming)
        }

        @Test
        fun `returns empty list when not configured`() {
            val provider = OpenRouterSmartChatEndpointProvider(configuredService(false))
            assertTrue(provider.getAvailableModels(null).isEmpty())
        }

        @Test
        fun `all models have required fields`() {
            val provider = OpenRouterSmartChatEndpointProvider(configuredService(true))
            val models = provider.getAvailableModels(null)

            for (model in models) {
                assertTrue(model.id.isNotBlank())
                assertTrue(model.name.isNotBlank())
                assertTrue(model.description.isNotBlank())
                assertEquals("OpenRouter", model.provider)
                assertTrue(model.contextLength > 0)
                assertTrue(model.supportsStreaming)
            }
        }

        @Test
        fun `includes popular models`() {
            val provider = OpenRouterSmartChatEndpointProvider(configuredService(true))
            val models = provider.getAvailableModels(null)
            val ids = models.map { it.id }

            assertTrue(ids.contains("openai/gpt-4o"))
            assertTrue(ids.contains("anthropic/claude-3-sonnet"))
            assertTrue(ids.contains("meta-llama/llama-3.1-8b-instruct"))
        }
    }

    @Nested
    @DisplayName("getDefaultModel")
    inner class GetDefaultModelTests {
        @Test
        fun `returns gpt-4o-mini`() {
            val provider = OpenRouterSmartChatEndpointProvider(configuredService(true))
            assertEquals("openai/gpt-4o-mini", provider.getDefaultModel())
        }
    }

    @Nested
    @DisplayName("getHeaders")
    inner class GetHeadersTests {
        @Test
        fun `returns headers map when configured`() {
            val provider = OpenRouterSmartChatEndpointProvider(configuredService(true, "sk-test"))
            val headers = provider.getHeaders()
            assertNotNull(headers)
            assertTrue(headers.isNotEmpty())
        }

        @Test
        fun `headers include authorization when api key present`() {
            val provider = OpenRouterSmartChatEndpointProvider(configuredService(true, "sk-test"))
            val headers = provider.getHeaders()
            assertTrue(headers.containsKey("Authorization") || headers.containsKey("authorization"))
        }
    }

    @Nested
    @DisplayName("validateConfiguration")
    inner class ValidateConfigurationTests {
        @Test
        fun `returns valid when configured with api key`() {
            val provider = OpenRouterSmartChatEndpointProvider(configuredService(true, "sk-test"))
            val result = provider.validateConfiguration(null)

            assertTrue(result.isValid)
            assertTrue(result.message.isNotEmpty())
        }

        @Test
        fun `returns invalid when not configured`() {
            val provider = OpenRouterSmartChatEndpointProvider(configuredService(false))
            val result = provider.validateConfiguration(null)

            assertFalse(result.isValid)
            assertTrue(result.message.isNotEmpty())
            assertTrue(result.details.containsKey("needsSetup"))
        }

        @Test
        fun `returns invalid when api key is blank`() {
            val provider = OpenRouterSmartChatEndpointProvider(configuredService(true, ""))
            val result = provider.validateConfiguration(null)

            assertFalse(result.isValid)
            assertTrue(result.message.isNotEmpty())
        }

        @Test
        fun `validation result includes details map`() {
            val provider = OpenRouterSmartChatEndpointProvider(configuredService(true, "sk-test"))
            val result = provider.validateConfiguration(null)

            assertTrue(result.details.containsKey("needsSetup"))
            assertEquals(false, result.details["needsSetup"])
        }
    }
}
