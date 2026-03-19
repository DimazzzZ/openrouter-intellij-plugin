package org.zhavoronkov.openrouter.utils

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * Tests for PasswordSafeKeyStorage.
 *
 * Note: Since PasswordSafe is not available in test environments,
 * these tests verify the in-memory fallback storage behavior,
 * which is the expected behavior in unit tests.
 */
@DisplayName("PasswordSafeKeyStorage Tests")
class PasswordSafeKeyStorageTest {

    @BeforeEach
    fun setUp() {
        // Reset cache state and clear all keys before each test to ensure isolation
        // resetCacheForTesting() clears the cache, preload flag, and in-memory storage
        PasswordSafeKeyStorage.resetCacheForTesting()
    }

    @Nested
    @DisplayName("API Key Storage Tests")
    inner class ApiKeyStorageTests {

        @Test
        @DisplayName("Should store and retrieve API key")
        fun testStoreAndRetrieveApiKey() {
            val testKey = "sk-or-v1-test-api-key-123"

            PasswordSafeKeyStorage.setApiKey(testKey)

            assertEquals(testKey, PasswordSafeKeyStorage.getApiKey())
        }

        @Test
        @DisplayName("Should return null when no API key is stored")
        fun testGetApiKeyWhenNotStored() {
            assertNull(PasswordSafeKeyStorage.getApiKey())
        }

        @Test
        @DisplayName("Should remove API key when blank string is passed")
        fun testRemoveApiKeyWithBlankString() {
            val testKey = "sk-or-v1-test-api-key"

            // Store a key first
            PasswordSafeKeyStorage.setApiKey(testKey)
            assertEquals(testKey, PasswordSafeKeyStorage.getApiKey())

            // Remove it with blank string
            PasswordSafeKeyStorage.setApiKey("   ")
            assertNull(PasswordSafeKeyStorage.getApiKey())
        }

        @Test
        @DisplayName("Should remove API key when empty string is passed")
        fun testRemoveApiKeyWithEmptyString() {
            val testKey = "sk-or-v1-test-api-key"

            // Store a key first
            PasswordSafeKeyStorage.setApiKey(testKey)
            assertEquals(testKey, PasswordSafeKeyStorage.getApiKey())

            // Remove it with empty string
            PasswordSafeKeyStorage.setApiKey("")
            assertNull(PasswordSafeKeyStorage.getApiKey())
        }

        @Test
        @DisplayName("Should overwrite existing API key")
        fun testOverwriteApiKey() {
            val firstKey = "sk-or-v1-first-key"
            val secondKey = "sk-or-v1-second-key"

            PasswordSafeKeyStorage.setApiKey(firstKey)
            assertEquals(firstKey, PasswordSafeKeyStorage.getApiKey())

            PasswordSafeKeyStorage.setApiKey(secondKey)
            assertEquals(secondKey, PasswordSafeKeyStorage.getApiKey())
        }
    }

    @Nested
    @DisplayName("Provisioning Key Storage Tests")
    inner class ProvisioningKeyStorageTests {

        @Test
        @DisplayName("Should store and retrieve provisioning key")
        fun testStoreAndRetrieveProvisioningKey() {
            val testKey = "sk-or-v1-test-provisioning-key-456"

            PasswordSafeKeyStorage.setProvisioningKey(testKey)

            assertEquals(testKey, PasswordSafeKeyStorage.getProvisioningKey())
        }

        @Test
        @DisplayName("Should return null when no provisioning key is stored")
        fun testGetProvisioningKeyWhenNotStored() {
            assertNull(PasswordSafeKeyStorage.getProvisioningKey())
        }

        @Test
        @DisplayName("Should remove provisioning key when blank string is passed")
        fun testRemoveProvisioningKeyWithBlankString() {
            val testKey = "sk-or-v1-test-provisioning-key"

            // Store a key first
            PasswordSafeKeyStorage.setProvisioningKey(testKey)
            assertEquals(testKey, PasswordSafeKeyStorage.getProvisioningKey())

            // Remove it with blank string
            PasswordSafeKeyStorage.setProvisioningKey("   ")
            assertNull(PasswordSafeKeyStorage.getProvisioningKey())
        }

        @Test
        @DisplayName("Should remove provisioning key when empty string is passed")
        fun testRemoveProvisioningKeyWithEmptyString() {
            val testKey = "sk-or-v1-test-provisioning-key"

            // Store a key first
            PasswordSafeKeyStorage.setProvisioningKey(testKey)
            assertEquals(testKey, PasswordSafeKeyStorage.getProvisioningKey())

            // Remove it with empty string
            PasswordSafeKeyStorage.setProvisioningKey("")
            assertNull(PasswordSafeKeyStorage.getProvisioningKey())
        }

        @Test
        @DisplayName("Should overwrite existing provisioning key")
        fun testOverwriteProvisioningKey() {
            val firstKey = "sk-or-v1-first-prov-key"
            val secondKey = "sk-or-v1-second-prov-key"

            PasswordSafeKeyStorage.setProvisioningKey(firstKey)
            assertEquals(firstKey, PasswordSafeKeyStorage.getProvisioningKey())

            PasswordSafeKeyStorage.setProvisioningKey(secondKey)
            assertEquals(secondKey, PasswordSafeKeyStorage.getProvisioningKey())
        }
    }

    @Nested
    @DisplayName("clearAll Tests")
    inner class ClearAllTests {

        @Test
        @DisplayName("Should clear both API key and provisioning key")
        fun testClearAllRemovesBothKeys() {
            val apiKey = "sk-or-v1-api-key"
            val provKey = "sk-or-v1-prov-key"

            // Store both keys
            PasswordSafeKeyStorage.setApiKey(apiKey)
            PasswordSafeKeyStorage.setProvisioningKey(provKey)

            // Verify both are stored
            assertEquals(apiKey, PasswordSafeKeyStorage.getApiKey())
            assertEquals(provKey, PasswordSafeKeyStorage.getProvisioningKey())

            // Clear all
            PasswordSafeKeyStorage.clearAll()

            // Verify both are removed
            assertNull(PasswordSafeKeyStorage.getApiKey())
            assertNull(PasswordSafeKeyStorage.getProvisioningKey())
        }

        @Test
        @DisplayName("Should not fail when clearing already empty storage")
        fun testClearAllOnEmptyStorage() {
            // Should not throw any exceptions
            PasswordSafeKeyStorage.clearAll()

            assertNull(PasswordSafeKeyStorage.getApiKey())
            assertNull(PasswordSafeKeyStorage.getProvisioningKey())
        }
    }

    @Nested
    @DisplayName("Key Independence Tests")
    inner class KeyIndependenceTests {

        @Test
        @DisplayName("API key and provisioning key should be stored independently")
        fun testKeysAreIndependent() {
            val apiKey = "sk-or-v1-api-key"
            val provKey = "sk-or-v1-prov-key"

            // Store API key
            PasswordSafeKeyStorage.setApiKey(apiKey)

            // API key should be stored, provisioning key should be null
            assertEquals(apiKey, PasswordSafeKeyStorage.getApiKey())
            assertNull(PasswordSafeKeyStorage.getProvisioningKey())

            // Store provisioning key
            PasswordSafeKeyStorage.setProvisioningKey(provKey)

            // Both should be stored
            assertEquals(apiKey, PasswordSafeKeyStorage.getApiKey())
            assertEquals(provKey, PasswordSafeKeyStorage.getProvisioningKey())
        }

        @Test
        @DisplayName("Clearing API key should not affect provisioning key")
        fun testClearApiKeyNotAffectProvisioningKey() {
            val apiKey = "sk-or-v1-api-key"
            val provKey = "sk-or-v1-prov-key"

            // Store both keys
            PasswordSafeKeyStorage.setApiKey(apiKey)
            PasswordSafeKeyStorage.setProvisioningKey(provKey)

            // Clear only API key
            PasswordSafeKeyStorage.setApiKey("")

            // API key should be cleared, provisioning key should remain
            assertNull(PasswordSafeKeyStorage.getApiKey())
            assertEquals(provKey, PasswordSafeKeyStorage.getProvisioningKey())
        }

        @Test
        @DisplayName("Clearing provisioning key should not affect API key")
        fun testClearProvisioningKeyNotAffectApiKey() {
            val apiKey = "sk-or-v1-api-key"
            val provKey = "sk-or-v1-prov-key"

            // Store both keys
            PasswordSafeKeyStorage.setApiKey(apiKey)
            PasswordSafeKeyStorage.setProvisioningKey(provKey)

            // Clear only provisioning key
            PasswordSafeKeyStorage.setProvisioningKey("")

            // Provisioning key should be cleared, API key should remain
            assertEquals(apiKey, PasswordSafeKeyStorage.getApiKey())
            assertNull(PasswordSafeKeyStorage.getProvisioningKey())
        }
    }

    @Nested
    @DisplayName("Edge Cases Tests")
    inner class EdgeCasesTests {

        @Test
        @DisplayName("Should handle very long keys")
        fun testVeryLongKeys() {
            val longKey = "sk-or-v1-" + "a".repeat(1000)

            PasswordSafeKeyStorage.setApiKey(longKey)
            PasswordSafeKeyStorage.setProvisioningKey(longKey)

            assertEquals(longKey, PasswordSafeKeyStorage.getApiKey())
            assertEquals(longKey, PasswordSafeKeyStorage.getProvisioningKey())
        }

        @Test
        @DisplayName("Should handle special characters in keys")
        fun testSpecialCharactersInKeys() {
            val specialKey = "sk-or-v1-abc123_def456-ghi789+jkl012/mno345="

            PasswordSafeKeyStorage.setApiKey(specialKey)
            PasswordSafeKeyStorage.setProvisioningKey(specialKey)

            assertEquals(specialKey, PasswordSafeKeyStorage.getApiKey())
            assertEquals(specialKey, PasswordSafeKeyStorage.getProvisioningKey())
        }

        @Test
        @DisplayName("Should handle unicode characters in keys")
        fun testUnicodeCharactersInKeys() {
            val unicodeKey = "sk-or-v1-测试🔑ключ"

            PasswordSafeKeyStorage.setApiKey(unicodeKey)
            PasswordSafeKeyStorage.setProvisioningKey(unicodeKey)

            assertEquals(unicodeKey, PasswordSafeKeyStorage.getApiKey())
            assertEquals(unicodeKey, PasswordSafeKeyStorage.getProvisioningKey())
        }

        @Test
        @DisplayName("Should handle key with only whitespace content (not blank)")
        fun testKeyWithWhitespaceContent() {
            // Note: Keys that are blank are removed, so this tests a key
            // that contains whitespace but isn't blank itself
            val keyWithWhitespace = "sk-or-v1-test key with spaces"

            PasswordSafeKeyStorage.setApiKey(keyWithWhitespace)

            assertEquals(keyWithWhitespace, PasswordSafeKeyStorage.getApiKey())
        }

        @Test
        @DisplayName("Should handle newlines in keys")
        fun testNewlinesInKeys() {
            val keyWithNewlines = "sk-or-v1-line1\nline2\rline3"

            PasswordSafeKeyStorage.setApiKey(keyWithNewlines)

            assertEquals(keyWithNewlines, PasswordSafeKeyStorage.getApiKey())
        }
    }
}
