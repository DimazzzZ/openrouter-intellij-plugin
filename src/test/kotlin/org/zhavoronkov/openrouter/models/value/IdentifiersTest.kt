package org.zhavoronkov.openrouter.models.value

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@DisplayName("Value Class Identifiers Tests")
class IdentifiersTest {

    @Nested
    @DisplayName("ApiKey")
    inner class ApiKeyTests {

        @Test
        fun `ApiKey wraps string value`() {
            val key = ApiKey("sk-or-v1-test-key")
            assertEquals("sk-or-v1-test-key", key.value)
        }

        @Test
        fun `ApiKey equality works correctly`() {
            val key1 = ApiKey("sk-or-v1-test-key")
            val key2 = ApiKey("sk-or-v1-test-key")
            val key3 = ApiKey("sk-or-v1-different-key")

            assertEquals(key1, key2)
            assertNotEquals(key1, key3)
        }

        @Test
        fun `ApiKey hashCode is consistent with value`() {
            val key1 = ApiKey("sk-or-v1-test-key")
            val key2 = ApiKey("sk-or-v1-test-key")

            assertEquals(key1.hashCode(), key2.hashCode())
        }

        @Test
        fun `ApiKey toString contains value`() {
            val key = ApiKey("sk-or-v1-test-key")
            // Value classes may wrap the string in their toString representation
            assertTrue(key.toString().contains("sk-or-v1-test-key"))
        }

        @Test
        fun `ApiKey can hold empty string`() {
            val key = ApiKey("")
            assertEquals("", key.value)
        }
    }

    @Nested
    @DisplayName("ProvisioningKey")
    inner class ProvisioningKeyTests {

        @Test
        fun `ProvisioningKey wraps string value`() {
            val key = ProvisioningKey("sk-or-v1-provisioning-key")
            assertEquals("sk-or-v1-provisioning-key", key.value)
        }

        @Test
        fun `ProvisioningKey equality works correctly`() {
            val key1 = ProvisioningKey("sk-or-v1-prov-key")
            val key2 = ProvisioningKey("sk-or-v1-prov-key")
            val key3 = ProvisioningKey("sk-or-v1-different-prov-key")

            assertEquals(key1, key2)
            assertNotEquals(key1, key3)
        }

        @Test
        fun `ProvisioningKey hashCode is consistent with value`() {
            val key1 = ProvisioningKey("sk-or-v1-prov-key")
            val key2 = ProvisioningKey("sk-or-v1-prov-key")

            assertEquals(key1.hashCode(), key2.hashCode())
        }

        @Test
        fun `ProvisioningKey toString contains value`() {
            val key = ProvisioningKey("sk-or-v1-prov-key")
            // Value classes may wrap the string in their toString representation
            assertTrue(key.toString().contains("sk-or-v1-prov-key"))
        }
    }

    @Nested
    @DisplayName("GenerationId")
    inner class GenerationIdTests {

        @Test
        fun `GenerationId wraps string value`() {
            val id = GenerationId("gen-12345-abcde")
            assertEquals("gen-12345-abcde", id.value)
        }

        @Test
        fun `GenerationId equality works correctly`() {
            val id1 = GenerationId("gen-12345")
            val id2 = GenerationId("gen-12345")
            val id3 = GenerationId("gen-67890")

            assertEquals(id1, id2)
            assertNotEquals(id1, id3)
        }

        @Test
        fun `GenerationId hashCode is consistent with value`() {
            val id1 = GenerationId("gen-12345")
            val id2 = GenerationId("gen-12345")

            assertEquals(id1.hashCode(), id2.hashCode())
        }

        @Test
        fun `GenerationId toString contains value`() {
            val id = GenerationId("gen-12345")
            // Value classes may wrap the string in their toString representation
            assertTrue(id.toString().contains("gen-12345"))
        }

        @Test
        fun `GenerationId can hold UUID format`() {
            val id = GenerationId("550e8400-e29b-41d4-a716-446655440000")
            assertEquals("550e8400-e29b-41d4-a716-446655440000", id.value)
        }
    }

    @Nested
    @DisplayName("Cross-type comparison")
    inner class CrossTypeTests {

        @Test
        fun `different value classes with same string are not equal`() {
            val apiKey = ApiKey("sk-or-v1-test")
            val provKey = ProvisioningKey("sk-or-v1-test")

            // Value classes of different types should not be equal even with same underlying value
            // Note: This test verifies type safety
            assertNotEquals(apiKey as Any, provKey as Any)
        }
    }
}
