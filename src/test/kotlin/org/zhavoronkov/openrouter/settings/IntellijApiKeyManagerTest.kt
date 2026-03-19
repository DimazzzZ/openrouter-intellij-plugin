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
 *
 * These tests verify the key matching logic that determines:
 * 1. Whether a stored API key matches a remote key (by comparing prefixes)
 * 2. Whether a key needs to be regenerated (stale key detection)
 * 3. Various edge cases in key prefix extraction
 * 4. Multiple keys with same name (duplicate bug scenario)
 * 5. Duplicate cleanup logic
 */
@DisplayName("IntellijApiKeyManager Tests")
class IntellijApiKeyManagerTest {

    companion object {
        const val INTELLIJ_API_KEY_NAME = "IntelliJ IDEA Plugin"
    }

    @Nested
    @DisplayName("Key Matching Logic Tests")
    inner class KeyMatchingLogicTests {

        @Test
        @DisplayName("Should detect matching key when stored key prefix matches remote label")
        fun testMatchingKeyDetection() {
            val remoteKeyLabel = "sk-or-v1-abc...xyz"
            val storedKey = "sk-or-v1-abc123def456789012345678901234567890abcdef123456789012345678901234"

            val matches = checkKeyMatches(remoteKeyLabel, storedKey)

            assertTrue(matches, "Stored key prefix should match remote label prefix")
        }

        @Test
        @DisplayName("Should detect stale key when stored key prefix does NOT match remote label")
        fun testStaleKeyDetection() {
            val remoteKeyLabel = "sk-or-v1-new...xyz"
            val storedKey = "sk-or-v1-old999stale888key777that666should555be444replaced333222111000xyz"

            val matches = checkKeyMatches(remoteKeyLabel, storedKey)

            assertFalse(matches, "Stale key should not match remote label")
        }

        @Test
        @DisplayName("Should handle label without ellipsis")
        fun testLabelWithoutEllipsis() {
            val remoteKeyLabel = "sk-or-v1-test"
            val storedKey = "sk-or-v1-test123456789012345678901234567890abcdef123456789012345678901234"

            val matches = checkKeyMatches(remoteKeyLabel, storedKey)

            assertTrue(matches, "Stored key should match label without ellipsis")
        }

        @Test
        @DisplayName("Should detect mismatch with label without ellipsis")
        fun testMismatchWithoutEllipsis() {
            val remoteKeyLabel = "sk-or-v1-new"
            val storedKey = "sk-or-v1-old999stale888key777that666should555be444replaced333222111000xyz"

            val matches = checkKeyMatches(remoteKeyLabel, storedKey)

            assertFalse(matches, "Stored key should not match different label")
        }
    }

    @Nested
    @DisplayName("Scenario Tests")
    inner class ScenarioTests {

        @Test
        @DisplayName("Should identify when no IntelliJ key exists remotely")
        fun testNoIntellijKeyExists() {
            val otherKeys = listOf(
                createApiKeyInfo(name = "other-key-1", label = "sk-or-v1-other1...xyz"),
                createApiKeyInfo(name = "other-key-2", label = "sk-or-v1-other2...xyz")
            )

            val intellijKey = otherKeys.find { it.name == INTELLIJ_API_KEY_NAME }

            assertNull(intellijKey, "No IntelliJ key should exist in the list")
        }

        @Test
        @DisplayName("Should identify when IntelliJ key exists remotely")
        fun testIntellijKeyExists() {
            val keys = listOf(
                createApiKeyInfo(name = "other-key-1", label = "sk-or-v1-other1...xyz"),
                createApiKeyInfo(name = INTELLIJ_API_KEY_NAME, label = "sk-or-v1-intellij...xyz"),
                createApiKeyInfo(name = "other-key-2", label = "sk-or-v1-other2...xyz")
            )

            val intellijKey = keys.find { it.name == INTELLIJ_API_KEY_NAME }

            assertEquals(INTELLIJ_API_KEY_NAME, intellijKey?.name, "IntelliJ key should be found")
            assertEquals("sk-or-v1-intellij...xyz", intellijKey?.label, "IntelliJ key label should match")
        }

        @Test
        @DisplayName("Should identify scenario: key exists remotely but not stored locally")
        fun testKeyExistsRemotelyButNotLocally() {
            val remoteKey = createApiKeyInfo(name = INTELLIJ_API_KEY_NAME, label = "sk-or-v1-remote...xyz")
            val storedKey = ""

            val keyExistsRemotely = remoteKey.name == INTELLIJ_API_KEY_NAME
            val keyStoredLocally = storedKey.isNotEmpty()

            assertTrue(keyExistsRemotely, "Key should exist remotely")
            assertFalse(keyStoredLocally, "Key should not be stored locally")
        }

        @Test
        @DisplayName("Should identify scenario: key exists and matches")
        fun testKeyExistsAndMatches() {
            val remoteKey = createApiKeyInfo(name = INTELLIJ_API_KEY_NAME, label = "sk-or-v1-abc...xyz")
            val storedKey = "sk-or-v1-abc123def456789012345678901234567890abcdef123456789012345678901234"

            val keyExistsRemotely = remoteKey.name == INTELLIJ_API_KEY_NAME
            val keyStoredLocally = storedKey.isNotEmpty()
            val keysMatch = checkKeyMatches(remoteKey.label, storedKey)

            assertTrue(keyExistsRemotely, "Key should exist remotely")
            assertTrue(keyStoredLocally, "Key should be stored locally")
            assertTrue(keysMatch, "Keys should match")
        }

        @Test
        @DisplayName("Should identify scenario: key exists but is stale")
        fun testKeyExistsButIsStale() {
            val remoteKey = createApiKeyInfo(name = INTELLIJ_API_KEY_NAME, label = "sk-or-v1-new...xyz")
            val storedKey = "sk-or-v1-old999stale888key777that666should555be444replaced333222111000xyz"

            val keyExistsRemotely = remoteKey.name == INTELLIJ_API_KEY_NAME
            val keyStoredLocally = storedKey.isNotEmpty()
            val keysMatch = checkKeyMatches(remoteKey.label, storedKey)

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

    @Nested
    @DisplayName("Multiple Keys With Same Name Tests")
    inner class MultipleKeysTests {

        @Test
        @DisplayName("Should find matching key among multiple keys with same name")
        fun testFindMatchingKeyAmongDuplicates() {
            val keys = listOf(
                createApiKeyInfo(name = INTELLIJ_API_KEY_NAME, label = "sk-or-v1-first...111", hash = "hash1"),
                createApiKeyInfo(name = INTELLIJ_API_KEY_NAME, label = "sk-or-v1-second...222", hash = "hash2"),
                createApiKeyInfo(name = INTELLIJ_API_KEY_NAME, label = "sk-or-v1-third...333", hash = "hash3"),
                createApiKeyInfo(name = INTELLIJ_API_KEY_NAME, label = "sk-or-v1-match...444", hash = "hash4"),
                createApiKeyInfo(name = INTELLIJ_API_KEY_NAME, label = "sk-or-v1-fifth...555", hash = "hash5")
            )

            val storedKey = "sk-or-v1-match123456789012345678901234567890abcdef123456789012345678"

            val intellijKeys = keys.filter { it.name == INTELLIJ_API_KEY_NAME }
            val matchingKey = findMatchingKey(intellijKeys, storedKey)

            assertEquals(5, intellijKeys.size, "Should find all 5 duplicate keys")
            assertEquals("hash4", matchingKey?.hash, "Should find the matching key (4th one)")
            assertEquals("sk-or-v1-match...444", matchingKey?.label, "Matching key label should be correct")
        }

        @Test
        @DisplayName("Should return null when no key matches among duplicates")
        fun testNoMatchingKeyAmongDuplicates() {
            val keys = listOf(
                createApiKeyInfo(name = INTELLIJ_API_KEY_NAME, label = "sk-or-v1-first...111", hash = "hash1"),
                createApiKeyInfo(name = INTELLIJ_API_KEY_NAME, label = "sk-or-v1-second...222", hash = "hash2"),
                createApiKeyInfo(name = INTELLIJ_API_KEY_NAME, label = "sk-or-v1-third...333", hash = "hash3")
            )

            val storedKey = "sk-or-v1-nomatch9999999999999999999999999999999999999999999999999999"

            val intellijKeys = keys.filter { it.name == INTELLIJ_API_KEY_NAME }
            val matchingKey = findMatchingKey(intellijKeys, storedKey)

            assertEquals(3, intellijKeys.size, "Should find all 3 duplicate keys")
            assertNull(matchingKey, "No key should match the stored key")
        }

        @Test
        @DisplayName("Should find first matching key when multiple could match")
        fun testFirstMatchingKeySelected() {
            val keys = listOf(
                createApiKeyInfo(name = INTELLIJ_API_KEY_NAME, label = "sk-or-v1-abc...111", hash = "hash1"),
                createApiKeyInfo(name = INTELLIJ_API_KEY_NAME, label = "sk-or-v1-abc...222", hash = "hash2"),
                createApiKeyInfo(name = INTELLIJ_API_KEY_NAME, label = "sk-or-v1-abc...333", hash = "hash3")
            )

            val storedKey = "sk-or-v1-abc123456789012345678901234567890abcdef123456789012345678"

            val matchingKey = findMatchingKey(keys, storedKey)

            assertEquals("hash1", matchingKey?.hash, "Should return first matching key")
        }

        @Test
        @DisplayName("Should handle empty stored key with multiple remote keys")
        fun testEmptyStoredKeyWithDuplicates() {
            val keys = listOf(
                createApiKeyInfo(name = INTELLIJ_API_KEY_NAME, label = "sk-or-v1-first...111", hash = "hash1"),
                createApiKeyInfo(name = INTELLIJ_API_KEY_NAME, label = "sk-or-v1-second...222", hash = "hash2")
            )

            val storedKey = ""

            val matchingKey = findMatchingKey(keys, storedKey)

            assertNull(matchingKey, "Empty stored key should not match any remote key")
        }

        @Test
        @DisplayName("Should handle single key correctly (no duplicates)")
        fun testSingleKeyNoDuplicates() {
            val keys = listOf(
                createApiKeyInfo(name = INTELLIJ_API_KEY_NAME, label = "sk-or-v1-only...999", hash = "hash1")
            )

            val storedKey = "sk-or-v1-only123456789012345678901234567890abcdef123456789012345678"

            val matchingKey = findMatchingKey(keys, storedKey)

            assertEquals("hash1", matchingKey?.hash, "Should find the only key")
        }
    }

    @Nested
    @DisplayName("Duplicate Cleanup Logic Tests")
    inner class DuplicateCleanupTests {

        @Test
        @DisplayName("Should identify all duplicate keys to delete")
        fun testIdentifyDuplicatesToDelete() {
            val keys = listOf(
                createApiKeyInfo(name = INTELLIJ_API_KEY_NAME, label = "sk-or-v1-dup1...111", hash = "hash1"),
                createApiKeyInfo(name = INTELLIJ_API_KEY_NAME, label = "sk-or-v1-dup2...222", hash = "hash2"),
                createApiKeyInfo(name = INTELLIJ_API_KEY_NAME, label = "sk-or-v1-keep...333", hash = "hash3"),
                createApiKeyInfo(name = INTELLIJ_API_KEY_NAME, label = "sk-or-v1-dup4...444", hash = "hash4"),
                createApiKeyInfo(name = INTELLIJ_API_KEY_NAME, label = "sk-or-v1-dup5...555", hash = "hash5")
            )

            val storedKey = "sk-or-v1-keep123456789012345678901234567890abcdef123456789012345678"
            val matchingKey = findMatchingKey(keys, storedKey)

            val keysToDelete = keys.filter { it.hash != matchingKey?.hash }

            assertEquals(4, keysToDelete.size, "Should identify 4 duplicate keys to delete")
            assertTrue(keysToDelete.all { it.hash != "hash3" }, "Matching key should not be in delete list")
            assertTrue(keysToDelete.any { it.hash == "hash1" }, "hash1 should be in delete list")
            assertTrue(keysToDelete.any { it.hash == "hash2" }, "hash2 should be in delete list")
            assertTrue(keysToDelete.any { it.hash == "hash4" }, "hash4 should be in delete list")
            assertTrue(keysToDelete.any { it.hash == "hash5" }, "hash5 should be in delete list")
        }

        @Test
        @DisplayName("Should identify all keys to delete when none match")
        fun testAllKeysToDeleteWhenNoMatch() {
            val keys = listOf(
                createApiKeyInfo(name = INTELLIJ_API_KEY_NAME, label = "sk-or-v1-old1...111", hash = "hash1"),
                createApiKeyInfo(name = INTELLIJ_API_KEY_NAME, label = "sk-or-v1-old2...222", hash = "hash2"),
                createApiKeyInfo(name = INTELLIJ_API_KEY_NAME, label = "sk-or-v1-old3...333", hash = "hash3")
            )

            val storedKey = "sk-or-v1-different123456789012345678901234567890abcdef1234567890123"
            val matchingKey = findMatchingKey(keys, storedKey)

            val keysToDelete = if (matchingKey == null) keys else keys.filter { it.hash != matchingKey.hash }

            assertEquals(3, keysToDelete.size, "All 3 keys should be deleted when none match")
        }

        @Test
        @DisplayName("Should not delete any keys when single key matches")
        fun testNoDeleteWhenSingleKeyMatches() {
            val keys = listOf(
                createApiKeyInfo(name = INTELLIJ_API_KEY_NAME, label = "sk-or-v1-single...111", hash = "hash1")
            )

            val storedKey = "sk-or-v1-single123456789012345678901234567890abcdef123456789012345678"
            val matchingKey = findMatchingKey(keys, storedKey)

            val keysToDelete = keys.filter { it.hash != matchingKey?.hash }

            assertEquals(0, keysToDelete.size, "No keys should be deleted when single key matches")
        }

        @Test
        @DisplayName("Should count duplicates correctly for 12 keys scenario")
        fun testDuplicateCount() {
            val keys = (1..12).map { i ->
                createApiKeyInfo(
                    name = INTELLIJ_API_KEY_NAME,
                    label = "sk-or-v1-dup$i...${i.toString().padStart(3, '0')}",
                    hash = "hash$i"
                )
            }

            val storedKey = "sk-or-v1-dup7123456789012345678901234567890abcdef1234567890123456789"
            val matchingKey = findMatchingKey(keys, storedKey)

            val duplicateCount = keys.size - 1

            assertEquals(11, duplicateCount, "Should count 11 duplicates (12 total - 1 matching)")
            assertEquals("hash7", matchingKey?.hash, "Should find hash7 as matching")
        }
    }

    @Nested
    @DisplayName("Filter vs Find Behavior Tests")
    inner class FilterVsFindTests {

        @Test
        @DisplayName("filter() should return all matching keys, not just first")
        fun testFilterReturnsAll() {
            val keys = listOf(
                createApiKeyInfo(name = "other-key", label = "sk-or-v1-other...000", hash = "hash0"),
                createApiKeyInfo(name = INTELLIJ_API_KEY_NAME, label = "sk-or-v1-ij1...111", hash = "hash1"),
                createApiKeyInfo(name = "another-key", label = "sk-or-v1-another...aaa", hash = "hashA"),
                createApiKeyInfo(name = INTELLIJ_API_KEY_NAME, label = "sk-or-v1-ij2...222", hash = "hash2"),
                createApiKeyInfo(name = INTELLIJ_API_KEY_NAME, label = "sk-or-v1-ij3...333", hash = "hash3")
            )

            val intellijKeys = keys.filter { it.name == INTELLIJ_API_KEY_NAME }

            assertEquals(3, intellijKeys.size, "filter() should return all 3 IntelliJ keys")
            assertTrue(intellijKeys.all { it.name == INTELLIJ_API_KEY_NAME }, "All should be IntelliJ keys")
        }

        @Test
        @DisplayName("find() would only return first key (showing why filter is needed)")
        fun testFindReturnsOnlyFirst() {
            val keys = listOf(
                createApiKeyInfo(name = INTELLIJ_API_KEY_NAME, label = "sk-or-v1-first...111", hash = "hash1"),
                createApiKeyInfo(name = INTELLIJ_API_KEY_NAME, label = "sk-or-v1-second...222", hash = "hash2"),
                createApiKeyInfo(name = INTELLIJ_API_KEY_NAME, label = "sk-or-v1-third...333", hash = "hash3")
            )

            val firstKey = keys.find { it.name == INTELLIJ_API_KEY_NAME }

            assertEquals("hash1", firstKey?.hash, "find() only returns first key")
        }

        @Test
        @DisplayName("Stored key might match 3rd key but find() would check 1st key only - bug scenario")
        fun testBugScenarioWithFind() {
            val keys = listOf(
                createApiKeyInfo(name = INTELLIJ_API_KEY_NAME, label = "sk-or-v1-first...111", hash = "hash1"),
                createApiKeyInfo(name = INTELLIJ_API_KEY_NAME, label = "sk-or-v1-second...222", hash = "hash2"),
                createApiKeyInfo(name = INTELLIJ_API_KEY_NAME, label = "sk-or-v1-match...333", hash = "hash3")
            )

            val storedKey = "sk-or-v1-match123456789012345678901234567890abcdef123456789012345678"

            // OLD buggy approach using find()
            val firstKey = keys.find { it.name == INTELLIJ_API_KEY_NAME }
            val oldBuggyMatch = checkKeyMatches(firstKey!!.label, storedKey)

            assertFalse(oldBuggyMatch, "OLD find() approach would not find the match in 3rd key")

            // NEW correct approach using filter() + findMatchingKey
            val allKeys = keys.filter { it.name == INTELLIJ_API_KEY_NAME }
            val newCorrectMatch = findMatchingKey(allKeys, storedKey)

            assertEquals("hash3", newCorrectMatch?.hash, "NEW filter() approach finds the correct match")
        }
    }

    @Nested
    @DisplayName("Edge Cases Tests")
    inner class EdgeCasesTests {

        @Test
        @DisplayName("Should handle empty keys list")
        fun testEmptyKeysList() {
            val keys = emptyList<ApiKeyInfo>()
            val storedKey = "sk-or-v1-any123456789012345678901234567890abcdef123456789012345678"

            val intellijKeys = keys.filter { it.name == INTELLIJ_API_KEY_NAME }
            val matchingKey = findMatchingKey(intellijKeys, storedKey)

            assertEquals(0, intellijKeys.size, "Should be empty")
            assertNull(matchingKey, "No match in empty list")
        }

        @Test
        @DisplayName("Should handle keys with very similar prefixes - first match wins")
        fun testSimilarPrefixes() {
            // Given: Keys with overlapping prefixes
            val keys = listOf(
                createApiKeyInfo(name = INTELLIJ_API_KEY_NAME, label = "sk-or-v1-abc...111", hash = "hash1"),
                createApiKeyInfo(name = INTELLIJ_API_KEY_NAME, label = "sk-or-v1-abcd...222", hash = "hash2"),
                createApiKeyInfo(name = INTELLIJ_API_KEY_NAME, label = "sk-or-v1-abcde...333", hash = "hash3")
            )

            // And: Stored key starts with "sk-or-v1-abcd"
            val storedKey = "sk-or-v1-abcd123456789012345678901234567890abcdef12345678901234567"

            // When: We find matching key
            val matchingKey = findMatchingKey(keys, storedKey)

            // Then: The first key "sk-or-v1-abc..." matches because "abc" is a prefix of "abcd..."
            // This is the expected behavior - shorter prefix matches first
            assertEquals("hash1", matchingKey?.hash, "First matching prefix wins (abc matches abcd)")
        }

        @Test
        @DisplayName("Should match exact prefix when order is different")
        fun testExactPrefixMatchWithDifferentOrder() {
            // Given: Keys with overlapping prefixes in different order (longer first)
            val keys = listOf(
                createApiKeyInfo(name = INTELLIJ_API_KEY_NAME, label = "sk-or-v1-abcde...333", hash = "hash3"),
                createApiKeyInfo(name = INTELLIJ_API_KEY_NAME, label = "sk-or-v1-abcd...222", hash = "hash2"),
                createApiKeyInfo(name = INTELLIJ_API_KEY_NAME, label = "sk-or-v1-abc...111", hash = "hash1")
            )

            // And: Stored key starts with "sk-or-v1-abcde"
            val storedKey = "sk-or-v1-abcde23456789012345678901234567890abcdef12345678901234567"

            // When: We find matching key
            val matchingKey = findMatchingKey(keys, storedKey)

            // Then: Should match hash3 (exact match with abcde)
            assertEquals("hash3", matchingKey?.hash, "Should match the first key with matching prefix")
        }

        @Test
        @DisplayName("Should handle key labels with different suffix lengths")
        fun testDifferentSuffixLengths() {
            val keys = listOf(
                createApiKeyInfo(name = INTELLIJ_API_KEY_NAME, label = "sk-or-v1-short...x", hash = "hash1"),
                createApiKeyInfo(name = INTELLIJ_API_KEY_NAME, label = "sk-or-v1-match...abcdefghij", hash = "hash2")
            )

            val storedKey = "sk-or-v1-match123456789012345678901234567890abcdef12345678901234567"

            val matchingKey = findMatchingKey(keys, storedKey)

            assertEquals("hash2", matchingKey?.hash, "Should match regardless of suffix length")
        }

        @Test
        @DisplayName("Should handle mixed key names correctly")
        fun testMixedKeyNames() {
            val keys = listOf(
                createApiKeyInfo(name = "Cline", label = "sk-or-v1-cline...111", hash = "hash-cline"),
                createApiKeyInfo(name = INTELLIJ_API_KEY_NAME, label = "sk-or-v1-ij1...222", hash = "hash-ij1"),
                createApiKeyInfo(name = "Goose", label = "sk-or-v1-goose...333", hash = "hash-goose"),
                createApiKeyInfo(name = INTELLIJ_API_KEY_NAME, label = "sk-or-v1-ij2...444", hash = "hash-ij2"),
                createApiKeyInfo(name = "Serper", label = "sk-or-v1-serper...555", hash = "hash-serper")
            )

            val intellijKeys = keys.filter { it.name == INTELLIJ_API_KEY_NAME }

            assertEquals(2, intellijKeys.size, "Should filter only IntelliJ keys")
            assertTrue(intellijKeys.all { it.name == INTELLIJ_API_KEY_NAME })
            assertTrue(intellijKeys.any { it.hash == "hash-ij1" })
            assertTrue(intellijKeys.any { it.hash == "hash-ij2" })
        }
    }

    // ==================== HELPER METHODS ====================

    /**
     * Helper method that replicates the key matching logic from IntellijApiKeyManager.
     * This is the same logic used in ensureIntellijApiKeyExists to determine if a stored
     * key matches the remote key.
     */
    private fun checkKeyMatches(remoteKeyLabel: String, storedKey: String): Boolean {
        if (storedKey.isEmpty()) return false

        val labelPrefix = if (remoteKeyLabel.contains("...")) {
            remoteKeyLabel.substringBefore("...")
        } else {
            remoteKeyLabel
        }

        val storedKeyPrefix = storedKey.take(labelPrefix.length)
        return storedKeyPrefix == labelPrefix
    }

    /**
     * Helper method that replicates the findMatchingKey logic from IntellijApiKeyManager.
     * This finds a key from the list that matches the stored API key prefix.
     */
    @Suppress("ReturnCount")
    private fun findMatchingKey(keys: List<ApiKeyInfo>, storedApiKey: String): ApiKeyInfo? {
        if (storedApiKey.isEmpty()) return null

        for (key in keys) {
            val keyLabel = key.label
            val labelPrefix = if (keyLabel.contains("...")) {
                keyLabel.substringBefore("...")
            } else {
                keyLabel
            }
            val storedKeyPrefix = storedApiKey.take(labelPrefix.length)
            if (storedKeyPrefix == labelPrefix) {
                return key
            }
        }
        return null
    }

    /**
     * Helper method to create ApiKeyInfo for testing.
     */
    @Suppress("LongParameterList")
    private fun createApiKeyInfo(
        name: String,
        label: String,
        hash: String = "test-hash-${name.hashCode()}",
        limit: Double? = null,
        usage: Double = 0.0,
        disabled: Boolean = false
    ): ApiKeyInfo {
        return ApiKeyInfo(
            name = name,
            label = label,
            hash = hash,
            limit = limit,
            usage = usage,
            disabled = disabled,
            createdAt = "2025-01-01T00:00:00.000000+00:00",
            updatedAt = null
        )
    }
}
