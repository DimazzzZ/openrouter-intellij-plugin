package org.zhavoronkov.openrouter.utils

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.zhavoronkov.openrouter.models.AuthScope

@DisplayName("KeyValidator Tests")
class KeyValidatorTest {

    @Nested
    @DisplayName("validateKeyFormat")
    inner class ValidateKeyFormatTests {

        @Test
        fun `valid key returns Valid`() {
            val result = KeyValidator.validateKeyFormat("sk-or-v1-abcdefghijklmnopqrstuvwxyz")
            assertTrue(result is KeyValidator.ValidationResult.Valid)
        }

        @Test
        fun `blank key returns Invalid`() {
            val result = KeyValidator.validateKeyFormat("")
            assertTrue(result is KeyValidator.ValidationResult.Invalid)
            assertEquals("Key cannot be empty", (result as KeyValidator.ValidationResult.Invalid).message)
        }

        @Test
        fun `whitespace only key returns Invalid`() {
            val result = KeyValidator.validateKeyFormat("   ")
            assertTrue(result is KeyValidator.ValidationResult.Invalid)
        }

        @Test
        fun `short key returns Invalid`() {
            val result = KeyValidator.validateKeyFormat("short")
            assertTrue(result is KeyValidator.ValidationResult.Invalid)
            assertTrue((result as KeyValidator.ValidationResult.Invalid).message.contains("too short"))
        }

        @Test
        fun `key without proper prefix returns Warning`() {
            val result = KeyValidator.validateKeyFormat("invalid-prefix-but-long-enough-key")
            assertTrue(result is KeyValidator.ValidationResult.Warning)
            assertTrue((result as KeyValidator.ValidationResult.Warning).message.contains("sk-or-v1-"))
        }
    }

    @Nested
    @DisplayName("validateApiKey")
    inner class ValidateApiKeyTests {

        @Test
        fun `valid API key returns Valid`() {
            val result = KeyValidator.validateApiKey("sk-or-v1-valid-api-key-12345")
            assertTrue(result is KeyValidator.ValidationResult.Valid)
        }

        @Test
        fun `empty API key returns Invalid`() {
            val result = KeyValidator.validateApiKey("")
            assertTrue(result is KeyValidator.ValidationResult.Invalid)
        }
    }

    @Nested
    @DisplayName("validateProvisioningKey")
    inner class ValidateProvisioningKeyTests {

        @Test
        fun `valid provisioning key returns Valid`() {
            val result = KeyValidator.validateProvisioningKey("sk-or-v1-valid-prov-key-12345")
            assertTrue(result is KeyValidator.ValidationResult.Valid)
        }

        @Test
        fun `empty provisioning key returns Invalid`() {
            val result = KeyValidator.validateProvisioningKey("")
            assertTrue(result is KeyValidator.ValidationResult.Invalid)
        }
    }

    @Nested
    @DisplayName("validateKey with AuthScope")
    inner class ValidateKeyWithScopeTests {

        @Test
        fun `valid key with REGULAR scope returns Valid`() {
            val result = KeyValidator.validateKey("sk-or-v1-valid-key-12345", AuthScope.REGULAR)
            assertTrue(result is KeyValidator.ValidationResult.Valid)
        }

        @Test
        fun `valid key with EXTENDED scope returns Valid`() {
            val result = KeyValidator.validateKey("sk-or-v1-valid-key-12345", AuthScope.EXTENDED)
            assertTrue(result is KeyValidator.ValidationResult.Valid)
        }

        @Test
        fun `empty key with any scope returns Invalid`() {
            val result = KeyValidator.validateKey("", AuthScope.REGULAR)
            assertTrue(result is KeyValidator.ValidationResult.Invalid)
        }
    }

    @Nested
    @DisplayName("looksLikeOpenRouterKey")
    inner class LooksLikeOpenRouterKeyTests {

        @Test
        fun `key with correct prefix and length returns true`() {
            assertTrue(KeyValidator.looksLikeOpenRouterKey("sk-or-v1-valid-key-12345"))
        }

        @Test
        fun `key without prefix returns false`() {
            assertFalse(KeyValidator.looksLikeOpenRouterKey("invalid-prefix-key"))
        }

        @Test
        fun `short key returns false`() {
            assertFalse(KeyValidator.looksLikeOpenRouterKey("sk-or"))
        }

        @Test
        fun `empty key returns false`() {
            assertFalse(KeyValidator.looksLikeOpenRouterKey(""))
        }

        @Test
        fun `key with prefix but too short returns false`() {
            assertFalse(KeyValidator.looksLikeOpenRouterKey("sk-or-v1"))
        }
    }

    @Nested
    @DisplayName("getValidationMessage")
    inner class GetValidationMessageTests {

        @Test
        fun `Valid result returns null`() {
            assertNull(KeyValidator.getValidationMessage(KeyValidator.ValidationResult.Valid))
        }

        @Test
        fun `Invalid result returns message`() {
            val result = KeyValidator.ValidationResult.Invalid("Error message")
            assertEquals("Error message", KeyValidator.getValidationMessage(result))
        }

        @Test
        fun `Warning result returns message`() {
            val result = KeyValidator.ValidationResult.Warning("Warning message")
            assertEquals("Warning message", KeyValidator.getValidationMessage(result))
        }
    }

    @Nested
    @DisplayName("isError")
    inner class IsErrorTests {

        @Test
        fun `Valid is not an error`() {
            assertFalse(KeyValidator.isError(KeyValidator.ValidationResult.Valid))
        }

        @Test
        fun `Invalid is an error`() {
            assertTrue(KeyValidator.isError(KeyValidator.ValidationResult.Invalid("Error")))
        }

        @Test
        fun `Warning is not an error`() {
            assertFalse(KeyValidator.isError(KeyValidator.ValidationResult.Warning("Warning")))
        }
    }

    @Nested
    @DisplayName("maskApiKey")
    inner class MaskApiKeyTests {

        @Test
        fun `long key is masked with ellipsis`() {
            val masked = KeyValidator.maskApiKey("sk-or-v1-abcdefghijklmnopqrstuvwxyz")
            assertEquals("sk-or-v1-abcdef...", masked)
        }

        @Test
        fun `short key is fully masked`() {
            val masked = KeyValidator.maskApiKey("short")
            assertEquals("****", masked)
        }

        @Test
        fun `empty key is fully masked`() {
            val masked = KeyValidator.maskApiKey("")
            assertEquals("****", masked)
        }

        @Test
        fun `key at exact display length is masked`() {
            // Key with exactly 15 characters should show "****"
            val masked = KeyValidator.maskApiKey("123456789012345")
            assertEquals("****", masked)
        }

        @Test
        fun `key slightly longer than display length shows partial`() {
            // Key with 16 characters should show first 15 + "..."
            val masked = KeyValidator.maskApiKey("1234567890123456")
            assertEquals("123456789012345...", masked)
        }
    }

    @Nested
    @DisplayName("ValidationResult sealed class")
    inner class ValidationResultTests {

        @Test
        fun `Valid is accessible`() {
            val valid = KeyValidator.ValidationResult.Valid
            assertNotNull(valid)
        }

        @Test
        fun `Invalid contains message`() {
            val invalid = KeyValidator.ValidationResult.Invalid("Test error")
            assertEquals("Test error", invalid.message)
        }

        @Test
        fun `Warning contains message`() {
            val warning = KeyValidator.ValidationResult.Warning("Test warning")
            assertEquals("Test warning", warning.message)
        }
    }
}
