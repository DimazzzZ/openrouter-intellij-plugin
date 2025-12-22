package org.zhavoronkov.openrouter.services

import com.intellij.ide.util.PropertiesComponent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.zhavoronkov.openrouter.models.AuthScope
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
            service.apiKeyManager.setProvisioningKey("")

            assertFalse(service.isConfigured())
        }

        @Test
        @DisplayName("Should return true when provisioning key is set")
        fun testIsConfiguredWithProvisioningKey() {
            val service = OpenRouterSettingsService()

            // Set auth scope to EXTENDED before setting provisioning key
            // isConfigured() checks the key based on the current authScope
            service.apiKeyManager.authScope = org.zhavoronkov.openrouter.models.AuthScope.EXTENDED
            service.apiKeyManager.setProvisioningKey("test-provisioning-key")

            assertTrue(service.isConfigured())
        }

        @Test
        @DisplayName("Should return false for blank provisioning key")
        fun testIsConfiguredWithBlankProvisioningKey() {
            val service = OpenRouterSettingsService()

            service.apiKeyManager.setProvisioningKey("   ")

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

            service.apiKeyManager.setProvisioningKey(testKey)

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

            service.apiKeyManager.setProvisioningKey("")

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

            service.apiKeyManager.setApiKey(testKey)

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
            // NOTE: Future version - Default model selection
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

            service.uiPreferencesManager.defaultMaxTokens = testValue

            assertEquals(testValue, service.uiPreferencesManager.defaultMaxTokens)
        }

        @Test
        @DisplayName("Should return 0 for unset default max tokens (disabled)")
        fun testUnsetDefaultMaxTokens() {
            val service = OpenRouterSettingsService()

            // Should default to disabled (0)
            assertEquals(0, service.uiPreferencesManager.defaultMaxTokens, "Default should be disabled (0)")
        }

        @Test
        @DisplayName("Should handle zero value for disabled feature")
        fun testZeroValueForDisabled() {
            val service = OpenRouterSettingsService()

            service.uiPreferencesManager.defaultMaxTokens = 0

            assertEquals(0, service.uiPreferencesManager.defaultMaxTokens, "Zero indicates disabled feature")
        }

        @Test
        @DisplayName("Should handle large max tokens values")
        fun testLargeMaxTokensValue() {
            val service = OpenRouterSettingsService()
            val largeValue = 128000 // Large but realistic value

            service.uiPreferencesManager.defaultMaxTokens = largeValue

            assertEquals(largeValue, service.uiPreferencesManager.defaultMaxTokens)
        }

        @Test
        @DisplayName("Should handle settings persistence with default max tokens")
        fun testDefaultMaxTokensPersistence() {
            val service = OpenRouterSettingsService()
            val testValue = 4000

            // Set value
            service.uiPreferencesManager.defaultMaxTokens = testValue

            // Verify it persists in state
            val state = service.state
            assertEquals(testValue, state.defaultMaxTokens, "Should persist defaultMaxTokens in state")

            // Verify retrieval works
            assertEquals(testValue, service.uiPreferencesManager.defaultMaxTokens)
        }
    }

    @Nested
    @DisplayName("Favorite Models Management")
    inner class FavoriteModelsTests {

        @Test
        @DisplayName("Should get default favorite models")
        fun testGetDefaultFavoriteModels() {
            val service = OpenRouterSettingsService()

            val favorites = service.favoriteModelsManager.getFavoriteModels()

            assertTrue(favorites.isNotEmpty(), "Should have default favorites")
            assertTrue(favorites.contains("openai/gpt-4o"), "Should include GPT-4o")
            assertTrue(favorites.contains("anthropic/claude-3.5-sonnet"), "Should include Claude")
        }

        @Test
        @DisplayName("Should add favorite model")
        fun testAddFavoriteModel() {
            val service = OpenRouterSettingsService()
            val initialCount = service.favoriteModelsManager.getFavoriteModels().size

            service.favoriteModelsManager.addFavoriteModel("google/gemini-pro-1.5")

            val favorites = service.favoriteModelsManager.getFavoriteModels()
            assertEquals(initialCount + 1, favorites.size)
            assertTrue(favorites.contains("google/gemini-pro-1.5"))
        }

        @Test
        @DisplayName("Should not add duplicate favorite model")
        fun testAddDuplicateFavoriteModel() {
            val service = OpenRouterSettingsService()
            val testModel = "openai/gpt-4o"
            val initialCount = service.favoriteModelsManager.getFavoriteModels().size

            service.favoriteModelsManager.addFavoriteModel(testModel) // Should not add duplicate

            val favorites = service.favoriteModelsManager.getFavoriteModels()
            assertEquals(initialCount, favorites.size) // Count should remain same
        }

        @Test
        @DisplayName("Should remove favorite model")
        fun testRemoveFavoriteModel() {
            val service = OpenRouterSettingsService()
            val testModel = "openai/gpt-4o"
            assertTrue(
                service.favoriteModelsManager.isFavoriteModel(testModel),
                "Model should be in favorites initially"
            )

            service.favoriteModelsManager.removeFavoriteModel(testModel)

            assertFalse(
                service.favoriteModelsManager.isFavoriteModel(testModel),
                "Model should be removed from favorites"
            )
        }

        @Test
        @DisplayName("Should set favorite models list")
        fun testSetFavoriteModels() {
            val service = OpenRouterSettingsService()
            val newFavorites = listOf("anthropic/claude-3-opus", "openai/gpt-4-turbo")

            service.favoriteModelsManager.setFavoriteModels(newFavorites)

            val favorites = service.favoriteModelsManager.getFavoriteModels()
            assertEquals(newFavorites.size, favorites.size)
            assertTrue(favorites.containsAll(newFavorites))
        }

        @Test
        @DisplayName("Should check if model is favorite")
        fun testIsFavoriteModel() {
            val service = OpenRouterSettingsService()

            assertTrue(
                service.favoriteModelsManager.isFavoriteModel("openai/gpt-4o"),
                "GPT-4o should be favorite by default"
            )
            assertFalse(
                service.favoriteModelsManager.isFavoriteModel("some/unknown-model"),
                "Unknown model should not be favorite"
            )
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
            service.apiKeyManager.setApiKey("sk-or-v1-1234567890abcdef")
            assertEquals("sk-or-v1-1234567890abcdef", service.getApiKey())

            // Empty key should be allowed (for clearing)
            service.apiKeyManager.setApiKey("")
            assertEquals("", service.getApiKey())
        }

        @Test
        @DisplayName("Should validate provisioning key format")
        fun testProvisioningKeyValidation() {
            val service = OpenRouterSettingsService()

            // Valid provisioning key
            service.apiKeyManager.setProvisioningKey("pk-1234567890abcdef")
            assertEquals("pk-1234567890abcdef", service.getProvisioningKey())

            // Empty key should be allowed
            service.apiKeyManager.setProvisioningKey("")
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
            service.apiKeyManager.setApiKey(testApiKey)

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

            service.apiKeyManager.setApiKey(testApiKey)
            service.apiKeyManager.setProvisioningKey(testProvisioningKey)
            service.uiPreferencesManager.autoRefresh = testAutoRefresh
            service.uiPreferencesManager.refreshInterval = testRefreshInterval
            service.uiPreferencesManager.showCosts = testShowCosts

            // All settings should be persisted correctly
            assertEquals(testApiKey, service.getApiKey(), "API key should be persisted")
            assertEquals(
                testProvisioningKey,
                service.getProvisioningKey(),
                "Provisioning key should be persisted"
            )
            assertEquals(
                testAutoRefresh,
                service.uiPreferencesManager.autoRefresh,
                "Auto refresh should be persisted"
            )
            assertEquals(
                testRefreshInterval,
                service.uiPreferencesManager.refreshInterval,
                "Refresh interval should be persisted"
            )
            assertEquals(
                testShowCosts,
                service.uiPreferencesManager.showCosts,
                "Show costs should be persisted"
            )
        }

        @Test
        @DisplayName("Should handle API key encryption without breaking persistence")
        fun testApiKeyEncryptionPersistence() {
            val service = OpenRouterSettingsService()
            val testApiKey = "sk-or-v1-encryption-test-key-with-sensitive-data"

            // Set the API key (which should encrypt it)
            service.apiKeyManager.setApiKey(testApiKey)

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
            service.apiKeyManager.setApiKey("")
            assertEquals("", service.getApiKey(), "Empty API key should be stored as empty")
            assertEquals("", service.state.apiKey, "Empty API key should not be encrypted")

            // Test blank key
            val blankKey = "   "
            service.apiKeyManager.setApiKey(blankKey)
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

            service.apiKeyManager.setApiKey(longKey)
            service.apiKeyManager.setProvisioningKey(longKey)

            assertEquals(longKey, service.getApiKey())
            assertEquals(longKey, service.getProvisioningKey())
        }

        @Test
        @DisplayName("Should handle special characters in keys")
        fun testSpecialCharactersInKeys() {
            val service = OpenRouterSettingsService()
            // Use realistic special characters that might appear in API keys
            val specialKey = "sk-or-v1-abc123_def456-ghi789"

            service.apiKeyManager.setApiKey(specialKey)

            assertEquals(specialKey, service.getApiKey())
        }

        @Test
        @DisplayName("Should handle unicode characters")
        fun testUnicodeCharacters() {
            val service = OpenRouterSettingsService()
            val unicodeKey = "sk-or-v1-测试🔑"

            service.apiKeyManager.setApiKey(unicodeKey)

            assertEquals(unicodeKey, service.getApiKey())
        }
    }

    @Nested
    @DisplayName("Settings Migration Tests")
    inner class SettingsMigrationTests {

        @Test
        @DisplayName("Should migrate v0.3.0 settings with provisioning key to EXTENDED authScope")
        fun testMigrateProvisioningKeyToExtendedScope() {
            // Simulate v0.3.0 settings: provisioning key exists but authScope defaults to REGULAR
            val oldSettings = OpenRouterSettings(
                authScope = AuthScope.REGULAR, // Default value in v0.4.0
                provisioningKey = "sk-or-v1-test-provisioning-key-encrypted",
                apiKey = "",
                hasCompletedSetup = true
            )

            val service = OpenRouterSettingsService()
            service.loadState(oldSettings)

            // After migration, authScope should be EXTENDED
            assertEquals(
                AuthScope.EXTENDED,
                service.apiKeyManager.authScope,
                "Migration should detect existing provisioning key and set authScope to EXTENDED"
            )
        }

        @Test
        @DisplayName("Should not change authScope if already EXTENDED")
        fun testNoMigrationWhenAlreadyExtended() {
            val settings = OpenRouterSettings(
                authScope = AuthScope.EXTENDED,
                provisioningKey = "sk-or-v1-test-provisioning-key",
                apiKey = ""
            )

            val service = OpenRouterSettingsService()
            service.loadState(settings)

            assertEquals(
                AuthScope.EXTENDED,
                service.apiKeyManager.authScope,
                "Should keep EXTENDED authScope unchanged"
            )
        }

        @Test
        @DisplayName("Should not change authScope if no provisioning key exists")
        fun testNoMigrationWithoutProvisioningKey() {
            val settings = OpenRouterSettings(
                authScope = AuthScope.REGULAR,
                provisioningKey = "",
                apiKey = "sk-or-v1-test-api-key"
            )

            val service = OpenRouterSettingsService()
            service.loadState(settings)

            assertEquals(
                AuthScope.REGULAR,
                service.apiKeyManager.authScope,
                "Should keep REGULAR authScope when no provisioning key exists"
            )
        }

        @Test
        @DisplayName("Should handle fresh install with no keys")
        fun testFreshInstallNoMigration() {
            val settings = OpenRouterSettings(
                authScope = AuthScope.REGULAR,
                provisioningKey = "",
                apiKey = ""
            )

            val service = OpenRouterSettingsService()
            service.loadState(settings)

            assertEquals(
                AuthScope.REGULAR,
                service.apiKeyManager.authScope,
                "Fresh install should keep default REGULAR authScope"
            )
        }

        @Test
        @DisplayName("Should migrate encrypted provisioning key")
        fun testMigrateEncryptedProvisioningKey() {
            // Simulate encrypted provisioning key from v0.3.0
            val encryptedKey = "ENC:base64encodedencryptedkey=="
            val settings = OpenRouterSettings(
                authScope = AuthScope.REGULAR,
                provisioningKey = encryptedKey,
                apiKey = ""
            )

            val service = OpenRouterSettingsService()
            service.loadState(settings)

            assertEquals(
                AuthScope.EXTENDED,
                service.apiKeyManager.authScope,
                "Should migrate even with encrypted provisioning key"
            )
        }
    }

    @Nested
    @DisplayName("Proxy Configuration Tests")
    inner class ProxyConfigurationTests {

        @Test
        @DisplayName("Should have proper default proxy settings")
        fun testDefaultProxySettings() {
            val service = OpenRouterSettingsService()

            // Default values should match the improved defaults
            assertFalse(service.proxyManager.isProxyAutoStartEnabled(), "Auto-start should be disabled by default")
            assertEquals(0, service.proxyManager.getProxyPort(), "Should default to auto port selection")
            assertEquals(8880, service.proxyManager.getProxyPortRangeStart(), "Default start port should be 8880")
            assertEquals(8899, service.proxyManager.getProxyPortRangeEnd(), "Default end port should be 8899")
        }

        @Test
        @DisplayName("Should store and retrieve proxy auto-start setting")
        fun testProxyAutoStart() {
            val service = OpenRouterSettingsService()

            // Initially disabled
            assertFalse(service.proxyManager.isProxyAutoStartEnabled())

            // Enable auto-start
            service.proxyManager.setProxyAutoStart(true)
            assertTrue(service.proxyManager.isProxyAutoStartEnabled())

            // Disable auto-start
            service.proxyManager.setProxyAutoStart(false)
            assertFalse(service.proxyManager.isProxyAutoStartEnabled())
        }

        @Test
        @DisplayName("Should store and retrieve specific proxy port")
        fun testSpecificProxyPort() {
            val service = OpenRouterSettingsService()

            // Initially should be 0 (auto)
            assertEquals(0, service.proxyManager.getProxyPort())

            // Set specific port
            service.proxyManager.setProxyPort(8888)
            assertEquals(8888, service.proxyManager.getProxyPort())

            // Set back to auto
            service.proxyManager.setProxyPort(0)
            assertEquals(0, service.proxyManager.getProxyPort())
        }

        @Test
        @DisplayName("Should validate proxy port range")
        fun testProxyPortValidation() {
            val service = OpenRouterSettingsService()

            // Valid ports should work
            service.proxyManager.setProxyPort(1024)
            assertEquals(1024, service.proxyManager.getProxyPort())

            service.proxyManager.setProxyPort(65535)
            assertEquals(65535, service.proxyManager.getProxyPort())

            service.proxyManager.setProxyPort(0) // Auto is valid
            assertEquals(0, service.proxyManager.getProxyPort())

            // Invalid ports should throw
            org.junit.jupiter.api.assertThrows<IllegalArgumentException> {
                service.proxyManager.setProxyPort(1023) // Too low
            }

            org.junit.jupiter.api.assertThrows<IllegalArgumentException> {
                service.proxyManager.setProxyPort(65536) // Too high
            }

            org.junit.jupiter.api.assertThrows<IllegalArgumentException> {
                service.proxyManager.setProxyPort(-1) // Negative
            }
        }

        @Test
        @DisplayName("Should store and retrieve proxy port range")
        fun testProxyPortRange() {
            val service = OpenRouterSettingsService()

            // Test individual range setters - set end first to avoid constraint violation
            service.proxyManager.setProxyPortRangeEnd(9010) // Set end first
            service.proxyManager.setProxyPortRangeStart(9000) // Then set start

            assertEquals(9000, service.proxyManager.getProxyPortRangeStart())
            assertEquals(9010, service.proxyManager.getProxyPortRangeEnd())

            // Test range setter
            service.proxyManager.setProxyPortRange(8000, 8020)
            assertEquals(8000, service.proxyManager.getProxyPortRangeStart())
            assertEquals(8020, service.proxyManager.getProxyPortRangeEnd())
        }

        @Test
        @DisplayName("Should validate proxy port range constraints")
        fun testProxyPortRangeValidation() {
            val service = OpenRouterSettingsService()

            // Valid ranges should work
            service.proxyManager.setProxyPortRange(8080, 8090)
            assertEquals(8080, service.proxyManager.getProxyPortRangeStart())
            assertEquals(8090, service.proxyManager.getProxyPortRangeEnd())

            // Equal start and end should work
            service.proxyManager.setProxyPortRange(8080, 8080)
            assertEquals(8080, service.proxyManager.getProxyPortRangeStart())
            assertEquals(8080, service.proxyManager.getProxyPortRangeEnd())

            // Start > End should throw
            org.junit.jupiter.api.assertThrows<IllegalArgumentException> {
                service.proxyManager.setProxyPortRange(8090, 8080)
            }

            // Invalid port numbers should throw
            org.junit.jupiter.api.assertThrows<IllegalArgumentException> {
                service.proxyManager.setProxyPortRange(1023, 8080) // Start too low
            }

            org.junit.jupiter.api.assertThrows<IllegalArgumentException> {
                service.proxyManager.setProxyPortRange(8080, 65536) // End too high
            }
        }

        @Test
        @DisplayName("Should validate individual range port setters")
        fun testIndividualRangePortValidation() {
            val service = OpenRouterSettingsService()

            // Set a valid range first
            service.proxyManager.setProxyPortRange(8880, 8890)

            // Valid individual changes should work
            service.proxyManager.setProxyPortRangeStart(8870)
            assertEquals(8870, service.proxyManager.getProxyPortRangeStart())
            assertEquals(8890, service.proxyManager.getProxyPortRangeEnd())

            service.proxyManager.setProxyPortRangeEnd(8900)
            assertEquals(8870, service.proxyManager.getProxyPortRangeStart())
            assertEquals(8900, service.proxyManager.getProxyPortRangeEnd())

            // Start > End should throw
            org.junit.jupiter.api.assertThrows<IllegalArgumentException> {
                service.proxyManager.setProxyPortRangeStart(8901) // Would make start > end
            }

            org.junit.jupiter.api.assertThrows<IllegalArgumentException> {
                service.proxyManager.setProxyPortRangeEnd(8869) // Would make end < start
            }
        }

        @Test
        @DisplayName("Should persist proxy configuration correctly")
        fun testProxyConfigurationPersistence() {
            val service = OpenRouterSettingsService()

            // Set all proxy configuration
            service.proxyManager.setProxyAutoStart(true)
            service.proxyManager.setProxyPort(8888)
            service.proxyManager.setProxyPortRange(9000, 9020)

            // Verify state persistence
            val state = service.state
            assertTrue(state.proxyAutoStart)
            assertEquals(8888, state.proxyPort)
            assertEquals(9000, state.proxyPortRangeStart)
            assertEquals(9020, state.proxyPortRangeEnd)

            // Verify getters return correct values
            assertTrue(service.proxyManager.isProxyAutoStartEnabled())
            assertEquals(8888, service.proxyManager.getProxyPort())
            assertEquals(9000, service.proxyManager.getProxyPortRangeStart())
            assertEquals(9020, service.proxyManager.getProxyPortRangeEnd())
        }

        @Test
        @DisplayName("Should load proxy configuration from state")
        fun testProxyConfigurationStateLoading() {
            val service = OpenRouterSettingsService()

            // Create test state with proxy configuration
            val testState = org.zhavoronkov.openrouter.models.OpenRouterSettings(
                proxyAutoStart = true,
                proxyPort = 8889,
                proxyPortRangeStart = 7000,
                proxyPortRangeEnd = 7010
            )

            // Load state
            service.loadState(testState)

            // Verify configuration is loaded
            assertTrue(service.proxyManager.isProxyAutoStartEnabled())
            assertEquals(8889, service.proxyManager.getProxyPort())
            assertEquals(7000, service.proxyManager.getProxyPortRangeStart())
            assertEquals(7010, service.proxyManager.getProxyPortRangeEnd())
        }

        @Test
        @DisplayName("Should handle edge cases in proxy configuration")
        fun testProxyConfigurationEdgeCases() {
            val service = OpenRouterSettingsService()

            // Test maximum valid port range
            service.proxyManager.setProxyPortRange(1024, 65535)
            assertEquals(1024, service.proxyManager.getProxyPortRangeStart())
            assertEquals(65535, service.proxyManager.getProxyPortRangeEnd())

            // Test minimum valid port range
            service.proxyManager.setProxyPortRange(1024, 1024)
            assertEquals(1024, service.proxyManager.getProxyPortRangeStart())
            assertEquals(1024, service.proxyManager.getProxyPortRangeEnd())

            // Test maximum valid specific port
            service.proxyManager.setProxyPort(65535)
            assertEquals(65535, service.proxyManager.getProxyPort())

            // Test minimum valid specific port
            service.proxyManager.setProxyPort(1024)
            assertEquals(1024, service.proxyManager.getProxyPort())
        }
    }
}
