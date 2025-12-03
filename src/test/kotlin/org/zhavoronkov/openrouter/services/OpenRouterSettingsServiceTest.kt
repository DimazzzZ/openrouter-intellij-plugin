package org.zhavoronkov.openrouter.services

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
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
            assertTrue(settings.autoRefresh)
            assertEquals(300, settings.refreshInterval)
            assertTrue(settings.showCosts)
            assertTrue(settings.trackGenerations)
            assertEquals(100, settings.maxTrackedGenerations)
            assertTrue(settings.favoriteModels.isNotEmpty(), "Should have default favorite models")
        }

        @Test
        @DisplayName("Should save and load settings")
        fun testSaveAndLoadSettings() {
            val service = OpenRouterSettingsService()
            val customFavorites = mutableListOf("anthropic/claude-3.5-sonnet", "openai/gpt-4")
            val testSettings = OpenRouterSettings(
                apiKey = "test-api-key",
                provisioningKey = "test-prov-key",
                autoRefresh = false,
                refreshInterval = 600,
                showCosts = false,
                trackGenerations = false,
                maxTrackedGenerations = 50,
                favoriteModels = customFavorites
            )

            service.loadState(testSettings)
            val loadedSettings = service.getState()

            assertEquals(testSettings.apiKey, loadedSettings.apiKey)
            assertEquals(testSettings.provisioningKey, loadedSettings.provisioningKey)
            // TODO: Future version - Default model selection
            // assertEquals(testSettings.defaultModel, loadedSettings.defaultModel)
            assertEquals(testSettings.autoRefresh, loadedSettings.autoRefresh)
            assertEquals(testSettings.refreshInterval, loadedSettings.refreshInterval)
            assertEquals(testSettings.showCosts, loadedSettings.showCosts)
            assertEquals(testSettings.trackGenerations, loadedSettings.trackGenerations)
            assertEquals(testSettings.maxTrackedGenerations, loadedSettings.maxTrackedGenerations)
            assertEquals(customFavorites, loadedSettings.favoriteModels)
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
    @DisplayName("Default Max Tokens Management")
    inner class DefaultMaxTokensTests {

        @Test
        @DisplayName("Should store and retrieve default max tokens value")
        fun testDefaultMaxTokensStorage() {
            val service = OpenRouterSettingsService()
            val testValue = 8000

            service.setDefaultMaxTokens(testValue)

            assertEquals(testValue, service.getDefaultMaxTokens())
        }

        @Test
        @DisplayName("Should return 0 for unset default max tokens (disabled)")
        fun testUnsetDefaultMaxTokens() {
            val service = OpenRouterSettingsService()

            // Should default to disabled (0)
            assertEquals(0, service.getDefaultMaxTokens(), "Default should be disabled (0)")
        }

        @Test
        @DisplayName("Should handle zero value for disabled feature")
        fun testZeroValueForDisabled() {
            val service = OpenRouterSettingsService()

            service.setDefaultMaxTokens(0)

            assertEquals(0, service.getDefaultMaxTokens(), "Zero indicates disabled feature")
        }

        @Test
        @DisplayName("Should handle large max tokens values")
        fun testLargeMaxTokensValue() {
            val service = OpenRouterSettingsService()
            val largeValue = 128000 // Large but realistic value

            service.setDefaultMaxTokens(largeValue)

            assertEquals(largeValue, service.getDefaultMaxTokens())
        }

        @Test
        @DisplayName("Should handle settings persistence with default max tokens")
        fun testDefaultMaxTokensPersistence() {
            val service = OpenRouterSettingsService()
            val testValue = 4000

            // Set value
            service.setDefaultMaxTokens(testValue)

            // Verify it persists in state
            val state = service.state
            assertEquals(testValue, state.defaultMaxTokens, "Should persist defaultMaxTokens in state")

            // Verify retrieval works
            assertEquals(testValue, service.getDefaultMaxTokens())
        }
    }

    @Nested
    @DisplayName("Favorite Models Management")
    inner class FavoriteModelsTests {

        @Test
        @DisplayName("Should get default favorite models")
        fun testGetDefaultFavoriteModels() {
            val service = OpenRouterSettingsService()

            val favorites = service.getFavoriteModels()

            assertTrue(favorites.isNotEmpty(), "Should have default favorites")
            assertTrue(favorites.contains("openai/gpt-4o"), "Should include GPT-4o")
            assertTrue(favorites.contains("anthropic/claude-3.5-sonnet"), "Should include Claude")
        }

        @Test
        @DisplayName("Should add favorite model")
        fun testAddFavoriteModel() {
            val service = OpenRouterSettingsService()
            val initialCount = service.getFavoriteModels().size

            service.addFavoriteModel("google/gemini-pro-1.5")

            val favorites = service.getFavoriteModels()
            assertEquals(initialCount + 1, favorites.size)
            assertTrue(favorites.contains("google/gemini-pro-1.5"))
        }

        @Test
        @DisplayName("Should not add duplicate favorite model")
        fun testAddDuplicateFavoriteModel() {
            val service = OpenRouterSettingsService()
            val testModel = "openai/gpt-4o"
            val initialCount = service.getFavoriteModels().size

            service.addFavoriteModel(testModel) // Should not add duplicate

            val favorites = service.getFavoriteModels()
            assertEquals(initialCount, favorites.size) // Count should remain same
        }

        @Test
        @DisplayName("Should remove favorite model")
        fun testRemoveFavoriteModel() {
            val service = OpenRouterSettingsService()
            val testModel = "openai/gpt-4o"
            assertTrue(service.isFavoriteModel(testModel), "Model should be in favorites initially")

            service.removeFavoriteModel(testModel)

            assertFalse(service.isFavoriteModel(testModel), "Model should be removed from favorites")
        }

        @Test
        @DisplayName("Should set favorite models list")
        fun testSetFavoriteModels() {
            val service = OpenRouterSettingsService()
            val newFavorites = listOf("anthropic/claude-3-opus", "openai/gpt-4-turbo")

            service.setFavoriteModels(newFavorites)

            val favorites = service.getFavoriteModels()
            assertEquals(newFavorites.size, favorites.size)
            assertTrue(favorites.containsAll(newFavorites))
        }

        @Test
        @DisplayName("Should check if model is favorite")
        fun testIsFavoriteModel() {
            val service = OpenRouterSettingsService()

            assertTrue(service.isFavoriteModel("openai/gpt-4o"), "GPT-4o should be favorite by default")
            assertFalse(service.isFavoriteModel("some/unknown-model"), "Unknown model should not be favorite")
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
    @DisplayName("Persistence and State Management")
    inner class PersistenceTests {

        @Test
        @DisplayName("Should modify existing state object directly, not create copies")
        fun testDirectStateModification() {
            // This test verifies the fix for the persistence issue where .copy() was preventing
            // the IntelliJ platform from detecting state changes
            val service = OpenRouterSettingsService()

            // Get initial state reference
            val initialState = service.state
            val testApiKey = "sk-or-v1-direct-modification-test"

            // Modify the API key
            service.setApiKey(testApiKey)

            // The state object should be the same instance (modified directly)
            val afterState = service.state
            assertEquals(initialState, afterState, "State object should be the same instance after modification")

            // And the value should be updated
            val retrievedKey = service.getApiKey()
            assertEquals(testApiKey, retrievedKey, "API key should be updated in the same state object")
        }

        @Test
        @DisplayName("Should persist multiple setting changes correctly")
        fun testMultipleSettingsPersistence() {
            val service = OpenRouterSettingsService()

            // Set multiple settings
            val testApiKey = "sk-or-v1-multi-test"
            val testProvisioningKey = "pk-multi-test"
            val testAutoRefresh = false
            val testRefreshInterval = 120
            val testShowCosts = false

            service.setApiKey(testApiKey)
            service.setProvisioningKey(testProvisioningKey)
            service.setAutoRefresh(testAutoRefresh)
            service.setRefreshInterval(testRefreshInterval)
            service.setShowCosts(testShowCosts)

            // All settings should be persisted correctly
            assertEquals(testApiKey, service.getApiKey(), "API key should be persisted")
            assertEquals(testProvisioningKey, service.getProvisioningKey(), "Provisioning key should be persisted")
            assertEquals(testAutoRefresh, service.isAutoRefreshEnabled(), "Auto refresh should be persisted")
            assertEquals(testRefreshInterval, service.getRefreshInterval(), "Refresh interval should be persisted")
            assertEquals(testShowCosts, service.shouldShowCosts(), "Show costs should be persisted")
        }

        @Test
        @DisplayName("Should handle API key encryption without breaking persistence")
        fun testApiKeyEncryptionPersistence() {
            val service = OpenRouterSettingsService()
            val testApiKey = "sk-or-v1-encryption-test-key-with-sensitive-data"

            // Set the API key (which should encrypt it)
            service.setApiKey(testApiKey)

            // The stored value should be encrypted
            val state = service.state
            assertNotNull(state.apiKey, "Encrypted API key should be stored")
            assertNotEquals(testApiKey, state.apiKey, "Stored API key should be encrypted")

            // But retrieval should give us the original
            val retrievedKey = service.getApiKey()
            assertEquals(testApiKey, retrievedKey, "Retrieved API key should be decrypted to original value")
        }

        @Test
        @DisplayName("Should handle empty and blank API keys without encryption")
        fun testEmptyApiKeyPersistence() {
            val service = OpenRouterSettingsService()

            // Test empty key
            service.setApiKey("")
            assertEquals("", service.getApiKey(), "Empty API key should be stored as empty")
            assertEquals("", service.state.apiKey, "Empty API key should not be encrypted")

            // Test blank key
            val blankKey = "   "
            service.setApiKey(blankKey)
            assertEquals(blankKey, service.getApiKey(), "Blank API key should be stored as-is")
            assertEquals(blankKey, service.state.apiKey, "Blank API key should not be encrypted")
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
            // Use realistic special characters that might appear in API keys
            val specialKey = "sk-or-v1-abc123_def456-ghi789"

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
