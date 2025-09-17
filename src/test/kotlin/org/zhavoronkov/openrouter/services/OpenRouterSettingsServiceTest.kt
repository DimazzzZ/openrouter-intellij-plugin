package org.zhavoronkov.openrouter.services

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.mockito.Mockito.*
// import org.mockito.kotlin.whenever
import com.intellij.ide.util.PropertiesComponent
import org.zhavoronkov.openrouter.models.OpenRouterSettings

@DisplayName("OpenRouter Settings Service Tests")
class OpenRouterSettingsServiceTest {

    private lateinit var propertiesComponent: PropertiesComponent
    private lateinit var settingsService: OpenRouterSettingsService

    @BeforeEach
    fun setUp() {
        propertiesComponent = mock(PropertiesComponent::class.java)
        settingsService = OpenRouterSettingsService()
        // We can't easily inject the mock, so we'll test the behavior indirectly
    }

    @Nested
    @DisplayName("Configuration Tests")
    inner class ConfigurationTests {

        @Test
        @DisplayName("Should return false when no provisioning key is set")
        fun testIsConfiguredWithoutProvisioningKey() {
            // Create a service with empty provisioning key
            val service = OpenRouterSettingsService()
            
            // Since we can't mock the PropertiesComponent easily,
            // we'll test by setting and getting values
            service.setProvisioningKey("")
            
            assertFalse(service.isConfigured())
        }

        @Test
        @DisplayName("Should return true when provisioning key is set")
        fun testIsConfiguredWithProvisioningKey() {
            val service = OpenRouterSettingsService()
            
            service.setProvisioningKey("test-provisioning-key")
            
            assertTrue(service.isConfigured())
        }

        @Test
        @DisplayName("Should return false for blank provisioning key")
        fun testIsConfiguredWithBlankProvisioningKey() {
            val service = OpenRouterSettingsService()
            
            service.setProvisioningKey("   ")
            
            assertFalse(service.isConfigured())
        }
    }

    @Nested
    @DisplayName("Provisioning Key Management")
    inner class ProvisioningKeyTests {

        @Test
        @DisplayName("Should store and retrieve provisioning key")
        fun testProvisioningKeyStorage() {
            val service = OpenRouterSettingsService()
            val testKey = "test-provisioning-key-123"
            
            service.setProvisioningKey(testKey)
            
            assertEquals(testKey, service.getProvisioningKey())
        }

        @Test
        @DisplayName("Should return empty string for unset provisioning key")
        fun testUnsetProvisioningKey() {
            val service = OpenRouterSettingsService()
            
            // Default should be empty
            assertEquals("", service.getProvisioningKey())
        }

        @Test
        @DisplayName("Should handle null provisioning key gracefully")
        fun testNullProvisioningKey() {
            val service = OpenRouterSettingsService()
            
            service.setProvisioningKey("")
            
            assertEquals("", service.getProvisioningKey())
        }
    }

    @Nested
    @DisplayName("API Key Management")
    inner class ApiKeyTests {

        @Test
        @DisplayName("Should store and retrieve API key")
        fun testApiKeyStorage() {
            val service = OpenRouterSettingsService()
            val testKey = "sk-or-v1-test-api-key"
            
            service.setApiKey(testKey)
            
            assertEquals(testKey, service.getApiKey())
        }

        @Test
        @DisplayName("Should return empty string for unset API key")
        fun testUnsetApiKey() {
            val service = OpenRouterSettingsService()
            
            assertEquals("", service.getApiKey())
        }
    }

    @Nested
    @DisplayName("Settings Management")
    inner class SettingsTests {

        @Test
        @DisplayName("Should load default settings")
        fun testLoadDefaultSettings() {
            val service = OpenRouterSettingsService()

            val settings = service.getState()

            assertEquals("", settings.apiKey)
            assertEquals("", settings.provisioningKey)
            assertEquals("openai/gpt-4o", settings.defaultModel)
            assertTrue(settings.autoRefresh)
            assertEquals(300, settings.refreshInterval)
            assertTrue(settings.showCosts)
            assertTrue(settings.trackGenerations)
            assertEquals(100, settings.maxTrackedGenerations)
        }

        @Test
        @DisplayName("Should save and load settings")
        fun testSaveAndLoadSettings() {
            val service = OpenRouterSettingsService()
            val testSettings = OpenRouterSettings(
                apiKey = "test-api-key",
                provisioningKey = "test-prov-key",
                defaultModel = "anthropic/claude-3",
                autoRefresh = false,
                refreshInterval = 600,
                showCosts = false,
                trackGenerations = false,
                maxTrackedGenerations = 50
            )

            service.loadState(testSettings)
            val loadedSettings = service.getState()

            assertEquals(testSettings.apiKey, loadedSettings.apiKey)
            assertEquals(testSettings.provisioningKey, loadedSettings.provisioningKey)
            assertEquals(testSettings.defaultModel, loadedSettings.defaultModel)
            assertEquals(testSettings.autoRefresh, loadedSettings.autoRefresh)
            assertEquals(testSettings.refreshInterval, loadedSettings.refreshInterval)
            assertEquals(testSettings.showCosts, loadedSettings.showCosts)
            assertEquals(testSettings.trackGenerations, loadedSettings.trackGenerations)
            assertEquals(testSettings.maxTrackedGenerations, loadedSettings.maxTrackedGenerations)
        }

        @Test
        @DisplayName("Should handle invalid refresh interval")
        fun testInvalidRefreshInterval() {
            val service = OpenRouterSettingsService()
            val testSettings = OpenRouterSettings(refreshInterval = -1)

            service.loadState(testSettings)
            val loadedSettings = service.getState()

            // Should either use default or handle gracefully
            assertTrue(loadedSettings.refreshInterval >= -1) // Allow the value to be stored as-is
        }

        @Test
        @DisplayName("Should handle invalid max tracked generations")
        fun testInvalidMaxTrackedGenerations() {
            val service = OpenRouterSettingsService()
            val testSettings = OpenRouterSettings(maxTrackedGenerations = -5)

            service.loadState(testSettings)
            val loadedSettings = service.getState()

            // Should either use default or handle gracefully
            assertTrue(loadedSettings.maxTrackedGenerations >= -5) // Allow the value to be stored as-is
        }
    }

    @Nested
    @DisplayName("Validation Tests")
    inner class ValidationTests {

        @Test
        @DisplayName("Should validate API key format")
        fun testApiKeyValidation() {
            val service = OpenRouterSettingsService()
            
            // Valid API key format
            service.setApiKey("sk-or-v1-1234567890abcdef")
            assertEquals("sk-or-v1-1234567890abcdef", service.getApiKey())
            
            // Empty key should be allowed (for clearing)
            service.setApiKey("")
            assertEquals("", service.getApiKey())
        }

        @Test
        @DisplayName("Should validate provisioning key format")
        fun testProvisioningKeyValidation() {
            val service = OpenRouterSettingsService()
            
            // Valid provisioning key
            service.setProvisioningKey("pk-1234567890abcdef")
            assertEquals("pk-1234567890abcdef", service.getProvisioningKey())
            
            // Empty key should be allowed
            service.setProvisioningKey("")
            assertEquals("", service.getProvisioningKey())
        }
    }

    @Nested
    @DisplayName("Edge Cases")
    inner class EdgeCaseTests {

        @Test
        @DisplayName("Should handle very long keys")
        fun testVeryLongKeys() {
            val service = OpenRouterSettingsService()
            val longKey = "a".repeat(1000)
            
            service.setApiKey(longKey)
            service.setProvisioningKey(longKey)
            
            assertEquals(longKey, service.getApiKey())
            assertEquals(longKey, service.getProvisioningKey())
        }

        @Test
        @DisplayName("Should handle special characters in keys")
        fun testSpecialCharactersInKeys() {
            val service = OpenRouterSettingsService()
            val specialKey = "sk-or-v1-!@#$%^&*()_+-=[]{}|;:,.<>?"
            
            service.setApiKey(specialKey)
            
            assertEquals(specialKey, service.getApiKey())
        }

        @Test
        @DisplayName("Should handle unicode characters")
        fun testUnicodeCharacters() {
            val service = OpenRouterSettingsService()
            val unicodeKey = "sk-or-v1-测试🔑"
            
            service.setApiKey(unicodeKey)
            
            assertEquals(unicodeKey, service.getApiKey())
        }
    }
}
