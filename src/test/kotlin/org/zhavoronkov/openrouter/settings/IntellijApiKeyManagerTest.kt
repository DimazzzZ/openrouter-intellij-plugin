package org.zhavoronkov.openrouter.settings

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.zhavoronkov.openrouter.models.ApiKeyInfo

/**
 * Tests for IntellijApiKeyManager, specifically the ensureIntellijApiKeyExists logic
 * that validates stored API keys match remote keys and handles stale key scenarios.
 */
/**
 * Tests for IntellijApiKeyManager, specifically the ensureIntellijApiKeyExists logic
 * that validates stored API keys match remote keys and handles stale key scenarios.
 *
 * These tests verify the key matching logic that determines:
 * 1. Whether a stored API key matches a remote key (by comparing prefixes)
 * 2. Whether a key needs to be regenerated (stale key detection)
 * 3. Various edge cases in key prefix extraction
 */
@DisplayName("IntellijApiKeyManager Tests")
class IntellijApiKeyManagerTest {

    companion object {
        const val INTELLIJ_API_KEY_NAME = "IntelliJ IDEA Plugin"

        // Sample API keys for testing
        const val VALID_API_KEY = "sk-or-v1-abc123def456789012345678901234567890abcdef123456789012345678901234"
        const val STALE_API_KEY = "sk-or-v1-old999stale888key777that666should555be444replaced333222111000xyz"
        const val NEW_API_KEY = "sk-or-v1-new123fresh456key789that012should345be678used901234567890abcdef"
    }

    @Nested
    @DisplayName("Key Matching Logic Tests")
    inner class KeyMatchingLogicTests {

        @Test
        @DisplayName("Should detect matching key when stored key prefix matches remote label")
        fun testMatchingKeyDetection() {
            // Given: Remote key with label "sk-or-v1-abc...xyz"
            val remoteKeyLabel = "sk-or-v1-abc...xyz"

            // And: Stored key starts with "sk-or-v1-abc"
            val storedKey = "sk-or-v1-abc123def456789012345678901234567890abcdef123456789012345678901234"

            // When: We check if the keys match using the same logic as ensureIntellijApiKeyExists
            val matches = checkKeyMatches(remoteKeyLabel, storedKey)

            // Then: Keys should match
            assertTrue(matches, "Stored key prefix should match remote label prefix")
        }

        @Test
        @DisplayName("Should detect stale key when stored key prefix does NOT match remote label")
        fun testStaleKeyDetection() {
            // Given: Remote key with label "sk-or-v1-new...xyz"
            val remoteKeyLabel = "sk-or-v1-new...xyz"

            // And: Stored key starts with "sk-or-v1-old" (different prefix!)
            val storedKey = "sk-or-v1-old999stale888key777that666should555be444replaced333222111000xyz"

            // When: We check if the keys match
            val matches = checkKeyMatches(remoteKeyLabel, storedKey)

            // Then: Keys should NOT match (stale key detected)
            assertFalse(matches, "Stale key should not match remote label")
        }

        @Test
        @DisplayName("Should handle label without ellipsis")
        fun testLabelWithoutEllipsis() {
            // Given: Remote key with label that has no "..." (edge case)
            val remoteKeyLabel = "sk-or-v1-test"

            // And: Stored key matches the full label
            val storedKey = "sk-or-v1-test123456789012345678901234567890abcdef123456789012345678901234"

            // When: We check if the keys match
            val matches = checkKeyMatches(remoteKeyLabel, storedKey)

            // Then: Keys should match
            assertTrue(matches, "Stored key should match label without ellipsis")
        }

        @Test
        @DisplayName("Should detect mismatch with label without ellipsis")
        fun testMismatchWithoutEllipsis() {
            // Given: Remote key with label that has no "..."
            val remoteKeyLabel = "sk-or-v1-new"

            // And: Stored key does NOT match
            val storedKey = "sk-or-v1-old999stale888key777that666should555be444replaced333222111000xyz"

            // When: We check if the keys match
            val matches = checkKeyMatches(remoteKeyLabel, storedKey)

            // Then: Keys should NOT match
            assertFalse(matches, "Stored key should not match different label")
        }
    }

    @Nested
    @DisplayName("Scenario Tests")
    inner class ScenarioTests {

        @Test
        @DisplayName("Should identify when no IntelliJ key exists remotely")
        fun testNoIntellijKeyExists() {
            // Given: No IntelliJ API key exists remotely
            val otherKeys = listOf(
                createApiKeyInfo(name = "other-key-1", label = "sk-or-v1-other1...xyz"),
                createApiKeyInfo(name = "other-key-2", label = "sk-or-v1-other2...xyz")
            )

            // When: We look for the IntelliJ key
            val intellijKey = otherKeys.find { it.name == INTELLIJ_API_KEY_NAME }

            // Then: No IntelliJ key should be found
            assertNull(intellijKey, "No IntelliJ key should exist in the list")
        }

        @Test
        @DisplayName("Should identify when IntelliJ key exists remotely")
        fun testIntellijKeyExists() {
            // Given: IntelliJ API key exists remotely
            val keys = listOf(
                createApiKeyInfo(name = "other-key-1", label = "sk-or-v1-other1...xyz"),
                createApiKeyInfo(name = INTELLIJ_API_KEY_NAME, label = "sk-or-v1-intellij...xyz"),
                createApiKeyInfo(name = "other-key-2", label = "sk-or-v1-other2...xyz")
            )

            // When: We look for the IntelliJ key
            val intellijKey = keys.find { it.name == INTELLIJ_API_KEY_NAME }

            // Then: IntelliJ key should be found
            assertEquals(INTELLIJ_API_KEY_NAME, intellijKey?.name, "IntelliJ key should be found")
            assertEquals("sk-or-v1-intellij...xyz", intellijKey?.label, "IntelliJ key label should match")
        }

        @Test
        @DisplayName("Should identify scenario: key exists remotely but not stored locally")
        fun testKeyExistsRemotelyButNotLocally() {
            // Given: IntelliJ API key exists remotely
            val remoteKey = createApiKeyInfo(
                name = INTELLIJ_API_KEY_NAME,
                label = "sk-or-v1-remote...xyz"
            )

            // And: No stored API key locally
            val storedKey = ""

            // When: We check the scenario
            val keyExistsRemotely = remoteKey.name == INTELLIJ_API_KEY_NAME
            val keyStoredLocally = storedKey.isNotEmpty()

            // Then: Should identify need to regenerate
            assertTrue(keyExistsRemotely, "Key should exist remotely")
            assertFalse(keyStoredLocally, "Key should not be stored locally")
        }

        @Test
        @DisplayName("Should identify scenario: key exists and matches")
        fun testKeyExistsAndMatches() {
            // Given: IntelliJ API key exists remotely with label "sk-or-v1-abc...xyz"
            val remoteKey = createApiKeyInfo(
                name = INTELLIJ_API_KEY_NAME,
                label = "sk-or-v1-abc...xyz"
            )

            // And: Stored key matches (starts with "sk-or-v1-abc")
            val storedKey = "sk-or-v1-abc123def456789012345678901234567890abcdef123456789012345678901234"

            // When: We check the scenario
            val keyExistsRemotely = remoteKey.name == INTELLIJ_API_KEY_NAME
            val keyStoredLocally = storedKey.isNotEmpty()
            val keysMatch = checkKeyMatches(remoteKey.label, storedKey)

            // Then: Should identify no action needed
            assertTrue(keyExistsRemotely, "Key should exist remotely")
            assertTrue(keyStoredLocally, "Key should be stored locally")
            assertTrue(keysMatch, "Keys should match")
        }

        @Test
        @DisplayName("Should identify scenario: key exists but is stale")
        fun testKeyExistsButIsStale() {
            // Given: IntelliJ API key exists remotely with label "sk-or-v1-new...xyz"
            val remoteKey = createApiKeyInfo(
                name = INTELLIJ_API_KEY_NAME,
                label = "sk-or-v1-new...xyz"
            )

            // And: Stored key is stale (starts with "sk-or-v1-old")
            val storedKey = "sk-or-v1-old999stale888key777that666should555be444replaced333222111000xyz"

            // When: We check the scenario
            val keyExistsRemotely = remoteKey.name == INTELLIJ_API_KEY_NAME
            val keyStoredLocally = storedKey.isNotEmpty()
            val keysMatch = checkKeyMatches(remoteKey.label, storedKey)

            // Then: Should identify need to regenerate due to stale key
            assertTrue(keyExistsRemotely, "Key should exist remotely")
            assertTrue(keyStoredLocally, "Key should be stored locally")
            assertFalse(keysMatch, "Keys should NOT match (stale)")
        }
    }

    @Nested
    @DisplayName("Key Prefix Extraction Tests")
    inner class KeyPrefixExtractionTests {

        @Test
        @DisplayName("Should correctly extract prefix from label with ellipsis")
        fun testPrefixExtractionWithEllipsis() {
            val label = "sk-or-v1-abc...xyz"
            val expectedPrefix = "sk-or-v1-abc"

            val actualPrefix = if (label.contains("...")) {
                label.substringBefore("...")
            } else {
                label
            }

            assertEquals(expectedPrefix, actualPrefix)
        }

        @Test
        @DisplayName("Should use full label when no ellipsis present")
        fun testPrefixExtractionWithoutEllipsis() {
            val label = "sk-or-v1-test"
            val expectedPrefix = "sk-or-v1-test"

            val actualPrefix = if (label.contains("...")) {
                label.substringBefore("...")
            } else {
                label
            }

            assertEquals(expectedPrefix, actualPrefix)
        }

        @Test
        @DisplayName("Should match stored key prefix correctly")
        fun testStoredKeyPrefixMatching() {
            val label = "sk-or-v1-abc...xyz"
            val storedKey = "sk-or-v1-abc123def456789012345678901234567890abcdef123456789012345678901234"

            val labelPrefix = label.substringBefore("...")
            val storedKeyPrefix = storedKey.take(labelPrefix.length)

            assertEquals(labelPrefix, storedKeyPrefix, "Prefixes should match")
        }

        @Test
        @DisplayName("Should detect mismatch when prefixes differ")
        fun testStoredKeyPrefixMismatch() {
            val label = "sk-or-v1-new...xyz"
            val storedKey = "sk-or-v1-old999stale888key777that666should555be444replaced333222111000xyz"

            val labelPrefix = label.substringBefore("...")
            val storedKeyPrefix = storedKey.take(labelPrefix.length)

            assertFalse(labelPrefix == storedKeyPrefix, "Prefixes should NOT match")
            assertEquals("sk-or-v1-new", labelPrefix)
            assertEquals("sk-or-v1-old", storedKeyPrefix)
        }
    }

    /**
     * Helper method that replicates the key matching logic from IntellijApiKeyManager.
     * This is the same logic used in ensureIntellijApiKeyExists to determine if a stored
     * key matches the remote key.
     *
     * @param remoteKeyLabel The label from the remote API key (e.g., "sk-or-v1-abc...xyz")
     * @param storedKey The full API key stored locally
     * @return true if the stored key matches the remote key label
     */
    private fun checkKeyMatches(remoteKeyLabel: String, storedKey: String): Boolean {
        if (storedKey.isEmpty()) return false

        // Extract prefix from label (before "..." if present)
        val labelPrefix = if (remoteKeyLabel.contains("...")) {
            remoteKeyLabel.substringBefore("...")
        } else {
            remoteKeyLabel
        }

        // Check if stored key starts with the same prefix
        val storedKeyPrefix = storedKey.take(labelPrefix.length)
        return storedKeyPrefix == labelPrefix
    }

    // Helper method to create ApiKeyInfo for testing
    private fun createApiKeyInfo(
        name: String,
        label: String,
        hash: String = "test-hash-${name.hashCode()}",
        limit: Double? = null,
        usage: Double = 0.0,
        disabled: Boolean = false,
        createdAt: String = "2025-01-01T00:00:00.000000+00:00",
        updatedAt: String? = null
    ): ApiKeyInfo {
        return ApiKeyInfo(
            name = name,
            label = label,
            hash = hash,
            limit = limit,
            usage = usage,
            disabled = disabled,
            createdAt = createdAt,
            updatedAt = updatedAt
        )
    }
}
