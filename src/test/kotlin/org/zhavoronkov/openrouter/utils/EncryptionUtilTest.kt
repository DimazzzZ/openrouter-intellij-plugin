package org.zhavoronkov.openrouter.utils

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * Comprehensive tests for EncryptionUtil
 * Tests encryption, decryption, and edge cases
 */
@DisplayName("EncryptionUtil Tests")
class EncryptionUtilTest {

    companion object {
        private const val SAMPLE_API_KEY = "sk-or-v1-1234567890abcdef1234567890abcdef"
        private const val SAMPLE_PROVISIONING_KEY = "sk-or-pk-1234567890abcdef1234567890abcdef"
        private const val SHORT_TEXT = "test"
        private const val EMPTY_STRING = ""
        private const val SPECIAL_CHARS = "!@#\$%^&*()_+-=[]{}|;:',.<>?/~`"
        private const val UNICODE_TEXT = "Hello 世界 🌍"
        private const val LONG_TEXT_SIZE = 200
    }

    @Nested
    @DisplayName("Encryption Tests")
    inner class EncryptionTests {

        @Test
        @DisplayName("Should encrypt API key successfully")
        fun testEncryptApiKey() {
            val encrypted = EncryptionUtil.encrypt(SAMPLE_API_KEY)

            assertNotEquals(SAMPLE_API_KEY, encrypted, "Encrypted text should differ from plain text")
            assertTrue(encrypted.isNotBlank(), "Encrypted text should not be blank")
        }

        @Test
        @DisplayName("Should encrypt provisioning key successfully")
        fun testEncryptProvisioningKey() {
            val encrypted = EncryptionUtil.encrypt(SAMPLE_PROVISIONING_KEY)

            assertNotEquals(SAMPLE_PROVISIONING_KEY, encrypted, "Encrypted text should differ from plain text")
            assertTrue(encrypted.isNotBlank(), "Encrypted text should not be blank")
        }

        @Test
        @DisplayName("Should encrypt short text")
        fun testEncryptShortText() {
            val encrypted = EncryptionUtil.encrypt(SHORT_TEXT)

            assertNotEquals(SHORT_TEXT, encrypted, "Encrypted text should differ from plain text")
            assertTrue(encrypted.isNotBlank(), "Encrypted text should not be blank")
        }

        @Test
        @DisplayName("Should encrypt long text")
        fun testEncryptLongText() {
            val longText = "a".repeat(LONG_TEXT_SIZE)
            val encrypted = EncryptionUtil.encrypt(longText)

            assertNotEquals(longText, encrypted, "Encrypted text should differ from plain text")
            assertTrue(encrypted.isNotBlank(), "Encrypted text should not be blank")
        }

        @Test
        @DisplayName("Should handle empty string encryption")
        fun testEncryptEmptyString() {
            val encrypted = EncryptionUtil.encrypt(EMPTY_STRING)

            assertEquals(EMPTY_STRING, encrypted, "Empty string should remain empty")
        }

        @Test
        @DisplayName("Should encrypt special characters")
        fun testEncryptSpecialCharacters() {
            val encrypted = EncryptionUtil.encrypt(SPECIAL_CHARS)

            assertNotEquals(SPECIAL_CHARS, encrypted, "Encrypted text should differ from plain text")
            assertTrue(encrypted.isNotBlank(), "Encrypted text should not be blank")
        }

        @Test
        @DisplayName("Should encrypt Unicode text")
        fun testEncryptUnicodeText() {
            val encrypted = EncryptionUtil.encrypt(UNICODE_TEXT)

            assertNotEquals(UNICODE_TEXT, encrypted, "Encrypted text should differ from plain text")
            assertTrue(encrypted.isNotBlank(), "Encrypted text should not be blank")
        }

        @Test
        @DisplayName("Should produce same encrypted values for same input (deterministic)")
        fun testEncryptionDeterministic() {
            val encrypted1 = EncryptionUtil.encrypt(SAMPLE_API_KEY)
            val encrypted2 = EncryptionUtil.encrypt(SAMPLE_API_KEY)

            assertEquals(encrypted1, encrypted2, "Current implementation is deterministic")
        }
    }

    @Nested
    @DisplayName("Decryption Tests")
    inner class DecryptionTests {

        @Test
        @DisplayName("Should decrypt API key successfully")
        fun testDecryptApiKey() {
            val encrypted = EncryptionUtil.encrypt(SAMPLE_API_KEY)
            val decrypted = EncryptionUtil.decrypt(encrypted)

            assertEquals(SAMPLE_API_KEY, decrypted, "Decrypted text should match original")
        }

        @Test
        @DisplayName("Should decrypt provisioning key successfully")
        fun testDecryptProvisioningKey() {
            val encrypted = EncryptionUtil.encrypt(SAMPLE_PROVISIONING_KEY)
            val decrypted = EncryptionUtil.decrypt(encrypted)

            assertEquals(SAMPLE_PROVISIONING_KEY, decrypted, "Decrypted text should match original")
        }

        @Test
        @DisplayName("Should decrypt short text")
        fun testDecryptShortText() {
            val encrypted = EncryptionUtil.encrypt(SHORT_TEXT)
            val decrypted = EncryptionUtil.decrypt(encrypted)

            assertEquals(SHORT_TEXT, decrypted, "Decrypted text should match original")
        }

        @Test
        @DisplayName("Should decrypt long text")
        fun testDecryptLongText() {
            val longText = "a".repeat(LONG_TEXT_SIZE)
            val encrypted = EncryptionUtil.encrypt(longText)
            val decrypted = EncryptionUtil.decrypt(encrypted)

            assertEquals(longText, decrypted, "Decrypted text should match original")
        }

        @Test
        @DisplayName("Should handle empty string decryption")
        fun testDecryptEmptyString() {
            val decrypted = EncryptionUtil.decrypt(EMPTY_STRING)

            assertEquals(EMPTY_STRING, decrypted, "Empty string should remain empty")
        }

        @Test
        @DisplayName("Should decrypt special characters")
        fun testDecryptSpecialCharacters() {
            val encrypted = EncryptionUtil.encrypt(SPECIAL_CHARS)
            val decrypted = EncryptionUtil.decrypt(encrypted)

            assertEquals(SPECIAL_CHARS, decrypted, "Decrypted text should match original")
        }

        @Test
        @DisplayName("Should decrypt Unicode text")
        fun testDecryptUnicodeText() {
            val encrypted = EncryptionUtil.encrypt(UNICODE_TEXT)
            val decrypted = EncryptionUtil.decrypt(encrypted)

            assertEquals(UNICODE_TEXT, decrypted, "Decrypted text should match original")
        }

        @Test
        @DisplayName("Should handle invalid encrypted text gracefully")
        fun testDecryptInvalidText() {
            val invalidEncrypted = "not-valid-base64-!@#\$%"
            val decrypted = EncryptionUtil.decrypt(invalidEncrypted)

            assertEquals(invalidEncrypted, decrypted, "Invalid encrypted text should be returned as-is")
        }

        @Test
        @DisplayName("Should handle plain text as encrypted input")
        fun testDecryptPlainText() {
            val plainText = "plain-text-not-encrypted"
            val decrypted = EncryptionUtil.decrypt(plainText)

            assertEquals(plainText, decrypted, "Plain text should be returned as-is")
        }
    }

    @Nested
    @DisplayName("Encryption Detection Tests")
    inner class EncryptionDetectionTests {

        @Test
        @DisplayName("Should detect encrypted text")
        fun testIsEncryptedForEncryptedText() {
            val encrypted = EncryptionUtil.encrypt(SAMPLE_API_KEY)

            assertTrue(EncryptionUtil.isEncrypted(encrypted), "Should detect encrypted text")
        }

        @Test
        @DisplayName("Should not detect plain API key as encrypted")
        fun testIsEncryptedForPlainApiKey() {
            assertFalse(EncryptionUtil.isEncrypted(SAMPLE_API_KEY), "Plain API key should not be detected as encrypted")
        }

        @Test
        @DisplayName("Should not detect short plain text as encrypted")
        fun testIsEncryptedForShortPlainText() {
            assertFalse(EncryptionUtil.isEncrypted(SHORT_TEXT), "Short plain text should not be detected as encrypted")
        }

        @Test
        @DisplayName("Should not detect empty string as encrypted")
        fun testIsEncryptedForEmptyString() {
            assertFalse(EncryptionUtil.isEncrypted(EMPTY_STRING), "Empty string should not be detected as encrypted")
        }

        @Test
        @DisplayName("Should not detect special characters as encrypted")
        fun testIsEncryptedForSpecialCharacters() {
            assertFalse(
                EncryptionUtil.isEncrypted(SPECIAL_CHARS),
                "Special characters should not be detected as encrypted"
            )
        }
    }

    @Nested
    @DisplayName("Round-Trip Tests")
    inner class RoundTripTests {

        @Test
        @DisplayName("Should maintain data integrity through encrypt-decrypt cycle")
        fun testRoundTripIntegrity() {
            val testCases = listOf(
                SAMPLE_API_KEY,
                SAMPLE_PROVISIONING_KEY,
                SHORT_TEXT,
                "a".repeat(LONG_TEXT_SIZE),
                SPECIAL_CHARS,
                UNICODE_TEXT
            )

            testCases.forEach { original ->
                val encrypted = EncryptionUtil.encrypt(original)
                val decrypted = EncryptionUtil.decrypt(encrypted)

                assertEquals(original, decrypted, "Round-trip should preserve original text")
            }
        }

        @Test
        @DisplayName("Should handle multiple encrypt-decrypt cycles")
        fun testMultipleRoundTrips() {
            var text = SAMPLE_API_KEY

            repeat(5) {
                val encrypted = EncryptionUtil.encrypt(text)
                val decrypted = EncryptionUtil.decrypt(encrypted)

                assertEquals(SAMPLE_API_KEY, decrypted, "Multiple round-trips should preserve original text")
                text = decrypted
            }
        }
    }

    @Nested
    @DisplayName("Edge Cases")
    inner class EdgeCaseTests {

        @Test
        @DisplayName("Should handle blank string")
        fun testBlankString() {
            val blank = "   "
            val encrypted = EncryptionUtil.encrypt(blank)

            assertEquals(blank, encrypted, "Blank string should remain unchanged")
        }

        @Test
        @DisplayName("Should handle newline characters")
        fun testNewlineCharacters() {
            val textWithNewlines = "line1\nline2\nline3"
            val encrypted = EncryptionUtil.encrypt(textWithNewlines)
            val decrypted = EncryptionUtil.decrypt(encrypted)

            assertEquals(textWithNewlines, decrypted, "Newlines should be preserved")
        }

        @Test
        @DisplayName("Should handle tab characters")
        fun testTabCharacters() {
            val textWithTabs = "col1\tcol2\tcol3"
            val encrypted = EncryptionUtil.encrypt(textWithTabs)
            val decrypted = EncryptionUtil.decrypt(encrypted)

            assertEquals(textWithTabs, decrypted, "Tabs should be preserved")
        }
    }
}
