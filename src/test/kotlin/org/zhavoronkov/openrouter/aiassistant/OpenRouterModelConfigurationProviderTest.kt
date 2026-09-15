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
 * Unit tests for [OpenRouterModelConfigurationProvider].
 *
 * Injects mocks for both [OpenRouterSettingsService] and [OpenRouterModelProvider]
 * so tests do not rely on the platform-bound `ApplicationManager.getApplication()`
 * call inside the no-arg constructor.
 *
 * Note: `openSettings(project)` is intentionally not covered here because it
 * calls `ShowSettingsUtil.getInstance().showSettingsDialog(...)`, which needs a
 * live IntelliJ platform (see `platformTest` in build.gradle.kts).
 */
@DisplayName("OpenRouter Model Configuration Provider Tests")
class OpenRouterModelConfigurationProviderTest {

    private fun mockedService(configured: Boolean = true): OpenRouterSettingsService {
        val svc = mock(OpenRouterSettingsService::class.java)
        whenever(svc.isConfigured()).thenReturn(configured)
        whenever(svc.getApiKey()).thenReturn(if (configured) "sk-test" else "")
        return svc
    }

    private fun mockedModelProvider(
        available: Boolean = true,
        models: List<OpenRouterAIModel> = listOf(
            OpenRouterAIModel(
                id = "openai/gpt-4o",
                name = "GPT-4o",
                description = "OpenAI GPT-4o",
                provider = "OpenRouter",
                contextLength = 128000,
                supportsChat = true,
                supportsCompletion = true,
                supportsStreaming = true
            ),
            OpenRouterAIModel(
                id = "anthropic/claude-3-sonnet",
                name = "Claude 3 Sonnet",
                description = "Anthropic model",
                provider = "OpenRouter",
                contextLength = 200000,
                supportsChat = true,
                supportsCompletion = true,
                supportsStreaming = true
            )
        ),
        connectionOk: Boolean = true
    ): OpenRouterModelProvider {
        val mp = mock(OpenRouterModelProvider::class.java)
        whenever(mp.getProviderName()).thenReturn("OpenRouter")
        whenever(mp.getProviderDisplayName()).thenReturn("OpenRouter")
        whenever(mp.getProviderDescription()).thenReturn("Access 400+ AI models through OpenRouter.ai")
        whenever(mp.isAvailable()).thenReturn(available)
        whenever(mp.getAvailableModels()).thenReturn(models)
        whenever(mp.getConfigurationStatus()).thenReturn(if (available) "Ready" else "Not configured")
        whenever(mp.testConnection()).thenReturn(connectionOk)
        // Match models by id for getModel()
        for (m in models) {
            whenever(mp.getModel(m.id)).thenReturn(m)
        }
        whenever(mp.getModel("unknown/model")).thenReturn(null)
        return mp
    }

    private fun provider(
        configured: Boolean = true,
        available: Boolean = true,
        connectionOk: Boolean = true,
        models: List<OpenRouterAIModel>? = null
    ): OpenRouterModelConfigurationProvider {
        val svc = mockedService(configured)
        val mp = if (models != null) {
            mockedModelProvider(available = available, models = models, connectionOk = connectionOk)
        } else {
            mockedModelProvider(available = available, connectionOk = connectionOk)
        }
        return OpenRouterModelConfigurationProvider(svc, mp)
    }

    @Nested
    @DisplayName("Identity")
    inner class IdentityTests {
        @Test
        fun `getProviderId returns openrouter`() {
            assertEquals("openrouter", provider().getProviderId())
        }

        @Test
        fun `getProviderInfo returns display name and description`() {
            val info = provider().getProviderInfo()
            assertEquals("OpenRouter", info.displayName)
            assertTrue(info.description.isNotEmpty())
        }
    }

    @Nested
    @DisplayName("isConfigured")
    inner class IsConfiguredTests {
        @Test
        fun `true when both service and model provider are ready`() {
            assertTrue(provider(configured = true, available = true).isConfigured())
        }

        @Test
        fun `false when service not configured`() {
            assertFalse(provider(configured = false, available = true).isConfigured())
        }

        @Test
        fun `false when model provider not available`() {
            assertFalse(provider(configured = true, available = false).isConfigured())
        }

        @Test
        fun `false when neither is ready`() {
            assertFalse(provider(configured = false, available = false).isConfigured())
        }
    }

    @Nested
    @DisplayName("Configuration status and instructions")
    inner class ConfigurationStatusTests {
        @Test
        fun `status message delegates to model provider`() {
            assertEquals("Ready", provider(available = true).getConfigurationStatusMessage())
            assertEquals("Not configured", provider(available = false).getConfigurationStatusMessage())
        }

        @Test
        fun `instructions contain provisioning guidance`() {
            val instructions = provider().getConfigurationInstructions()
            assertTrue(instructions.contains("Provisioning Key"))
            assertTrue(instructions.contains("openrouter.ai"))
            assertTrue(instructions.contains("Settings"))
        }
    }

    @Nested
    @DisplayName("getModelConfigurations")
    inner class GetModelConfigurationsTests {
        @Test
        fun `returns configurations when configured`() {
            val configs = provider(configured = true, available = true).getModelConfigurations()

            assertEquals(2, configs.size)
            val first = configs.first { it.id == "openai/gpt-4o" }
            assertEquals("GPT-4o", first.displayName)
            assertEquals("OpenRouter", first.provider)
            assertTrue(first.isAvailable)
            assertFalse(first.configurationRequired)
            assertTrue(first.capabilities.supportsChat)
            assertTrue(first.capabilities.supportsCompletion)
            assertTrue(first.capabilities.supportsStreaming)
            assertEquals(128000, first.capabilities.maxContextLength)
        }

        @Test
        fun `returns empty when not configured`() {
            val configs = provider(configured = false, available = false).getModelConfigurations()
            assertTrue(configs.isEmpty())
        }

        @Test
        fun `uses default context length when model has null contextLength`() {
            val custom = listOf(
                OpenRouterAIModel(
                    id = "custom/no-context",
                    name = "No Context",
                    description = "Model with null contextLength",
                    provider = "OpenRouter",
                    contextLength = null,
                    supportsChat = true,
                    supportsCompletion = false,
                    supportsStreaming = true
                )
            )
            val configs = provider(models = custom).getModelConfigurations()
            assertEquals(1, configs.size)
            assertEquals(8192, configs.first().capabilities.maxContextLength)
        }
    }

    @Nested
    @DisplayName("getModelConfiguration")
    inner class GetModelConfigurationTests {
        @Test
        fun `returns matching configuration by id`() {
            val config = provider().getModelConfiguration("openai/gpt-4o")
            assertNotNull(config)
            assertEquals("openai/gpt-4o", config?.id)
        }

        @Test
        fun `returns null for unknown id`() {
            assertNull(provider().getModelConfiguration("does/not-exist"))
        }

        @Test
        fun `returns null when not configured`() {
            assertNull(provider(configured = false, available = false).getModelConfiguration("openai/gpt-4o"))
        }
    }

    @Nested
    @DisplayName("validateModelConfiguration")
    inner class ValidateModelConfigurationTests {
        @Test
        fun `valid when configured, model exists, and connection ok`() {
            val result = provider().validateModelConfiguration("openai/gpt-4o")
            assertTrue(result.isValid)
        }

        @Test
        fun `invalid when not configured`() {
            val result = provider(configured = false, available = false).validateModelConfiguration("openai/gpt-4o")
            assertFalse(result.isValid)
            assertTrue(result.message.contains("not configured", ignoreCase = true))
        }

        @Test
        fun `invalid when model does not exist`() {
            val result = provider().validateModelConfiguration("unknown/model")
            assertFalse(result.isValid)
            assertTrue(result.message.contains("not available"))
        }

        @Test
        fun `invalid when connection test fails`() {
            val result = provider(connectionOk = false).validateModelConfiguration("openai/gpt-4o")
            assertFalse(result.isValid)
            assertTrue(result.message.contains("Connection test failed"))
        }
    }

    @Nested
    @DisplayName("getProviderSettings")
    inner class GetProviderSettingsTests {
        @Test
        fun `returns full settings map when configured`() {
            val settings = provider().getProviderSettings()

            assertEquals(true, settings["configured"])
            assertEquals("Ready", settings["status"])
            assertEquals(2, settings["availableModels"])
            assertEquals(false, settings["needsConfiguration"])
            assertEquals("settings://Tools/OpenRouter", settings["configurationUrl"])
        }

        @Test
        fun `reports needsConfiguration when not configured`() {
            val settings = provider(configured = false, available = false).getProviderSettings()

            assertEquals(false, settings["configured"])
            assertEquals(true, settings["needsConfiguration"])
            assertEquals(0, settings["availableModels"])
        }
    }

    @Nested
    @DisplayName("onProviderStateChanged")
    inner class OnProviderStateChangedTests {
        @Test
        fun `activated=true when configured does not throw`() {
            provider().onProviderStateChanged(true)
        }

        @Test
        fun `activated=true when NOT configured logs setup prompt`() {
            provider(configured = false, available = false).onProviderStateChanged(true)
        }

        @Test
        fun `activated=false does not throw`() {
            provider().onProviderStateChanged(false)
        }
    }
}
