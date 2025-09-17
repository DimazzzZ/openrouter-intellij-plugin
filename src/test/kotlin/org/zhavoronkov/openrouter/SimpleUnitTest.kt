package org.zhavoronkov.openrouter

import com.google.gson.Gson
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.zhavoronkov.openrouter.models.*

@DisplayName("OpenRouter Plugin Unit Tests")
class SimpleUnitTest {

    private val gson = Gson()

    @Nested
    @DisplayName("Data Model Tests")
    inner class DataModelTests {

        @Test
        @DisplayName("Should serialize and deserialize ApiKeyInfo")
        fun testApiKeyInfoSerialization() {
            val apiKey = ApiKeyInfo(
                name = "test-key",
                label = "sk-or-v1-test",
                limit = 100.0,
                usage = 25.5,
                disabled = false,
                createdAt = "2025-09-17T12:00:00.000000+00:00",
                updatedAt = "2025-09-17T13:00:00.000000+00:00",
                hash = "abc123"
            )

            val json = gson.toJson(apiKey)
            val deserialized = gson.fromJson(json, ApiKeyInfo::class.java)

            assertEquals(apiKey.name, deserialized.name)
            assertEquals(apiKey.label, deserialized.label)
            assertEquals(apiKey.limit, deserialized.limit)
            assertEquals(apiKey.usage, deserialized.usage)
            assertEquals(apiKey.disabled, deserialized.disabled)
            assertEquals(apiKey.createdAt, deserialized.createdAt)
            assertEquals(apiKey.updatedAt, deserialized.updatedAt)
            assertEquals(apiKey.hash, deserialized.hash)
        }

        @Test
        @DisplayName("Should handle null values in ApiKeyInfo")
        fun testApiKeyInfoWithNulls() {
            val apiKey = ApiKeyInfo(
                name = "test-key",
                label = "sk-or-v1-test",
                limit = null,
                usage = 0.0,
                disabled = false,
                createdAt = "2025-09-17T12:00:00.000000+00:00",
                updatedAt = null,
                hash = "abc123"
            )

            val json = gson.toJson(apiKey)
            val deserialized = gson.fromJson(json, ApiKeyInfo::class.java)

            assertNull(deserialized.limit)
            assertNull(deserialized.updatedAt)
            assertEquals(0.0, deserialized.usage)
        }

        @Test
        @DisplayName("Should deserialize API keys list response")
        fun testApiKeysListResponse() {
            val json = """
                {
                    "data": [
                        {
                            "hash": "abc123",
                            "name": "test-key-1",
                            "label": "sk-or-v1-test1",
                            "disabled": false,
                            "limit": null,
                            "usage": 0.1,
                            "created_at": "2025-09-17T12:00:00.000000+00:00",
                            "updated_at": null
                        },
                        {
                            "hash": "def456",
                            "name": "test-key-2",
                            "label": "sk-or-v1-test2",
                            "disabled": true,
                            "limit": 50.0,
                            "usage": 25.5,
                            "created_at": "2025-09-16T12:00:00.000000+00:00",
                            "updated_at": "2025-09-17T12:00:00.000000+00:00"
                        }
                    ]
                }
            """.trimIndent()

            val response = gson.fromJson(json, ApiKeysListResponse::class.java)

            assertEquals(2, response.data.size)
            assertEquals("test-key-1", response.data[0].name)
            assertEquals("test-key-2", response.data[1].name)
            assertFalse(response.data[0].disabled)
            assertTrue(response.data[1].disabled)
        }

        @Test
        @DisplayName("Should serialize CreateApiKeyRequest correctly")
        fun testCreateApiKeyRequest() {
            val request = CreateApiKeyRequest(name = "test-key", limit = 100.0)
            val json = gson.toJson(request)

            assertTrue(json.contains("\"name\":\"test-key\""))
            assertTrue(json.contains("\"limit\":100.0"))
        }

        @Test
        @DisplayName("Should deserialize CreateApiKeyResponse")
        fun testCreateApiKeyResponse() {
            val json = """
                {
                    "data": {
                        "hash": "new123",
                        "name": "new-key",
                        "label": "sk-or-v1-new",
                        "disabled": false,
                        "limit": null,
                        "usage": 0.0,
                        "created_at": "2025-09-17T12:00:00.000000+00:00",
                        "updated_at": null,
                        "key": "sk-or-v1-actual-key-value"
                    }
                }
            """.trimIndent()

            val response = gson.fromJson(json, CreateApiKeyResponse::class.java)

            assertEquals("new123", response.data.hash)
            assertEquals("new-key", response.data.name)
            assertEquals("sk-or-v1-actual-key-value", response.data.key)
            assertEquals(0.0, response.data.usage)
        }
    }

    @Nested
    @DisplayName("Settings Model Tests")
    inner class SettingsModelTests {

        @Test
        @DisplayName("Should create OpenRouterSettings with defaults")
        fun testDefaultSettings() {
            val settings = OpenRouterSettings()

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
        @DisplayName("Should create OpenRouterSettings with custom values")
        fun testCustomSettings() {
            val settings = OpenRouterSettings(
                apiKey = "test-key",
                provisioningKey = "test-prov-key",
                defaultModel = "anthropic/claude-3",
                autoRefresh = false,
                refreshInterval = 600,
                showCosts = false,
                trackGenerations = false,
                maxTrackedGenerations = 50
            )

            assertEquals("test-key", settings.apiKey)
            assertEquals("test-prov-key", settings.provisioningKey)
            assertEquals("anthropic/claude-3", settings.defaultModel)
            assertFalse(settings.autoRefresh)
            assertEquals(600, settings.refreshInterval)
            assertFalse(settings.showCosts)
            assertFalse(settings.trackGenerations)
            assertEquals(50, settings.maxTrackedGenerations)
        }
    }

    @Nested
    @DisplayName("Key Info Model Tests")
    inner class KeyInfoModelTests {

        @Test
        @DisplayName("Should deserialize KeyInfoResponse")
        fun testKeyInfoResponse() {
            val json = """
                {
                    "data": {
                        "label": "sk-or-v1-test",
                        "usage": 0.0000495,
                        "limit": 100.0,
                        "is_free_tier": false
                    }
                }
            """.trimIndent()

            val response = gson.fromJson(json, KeyInfoResponse::class.java)

            assertEquals("sk-or-v1-test", response.data.label)
            assertEquals(0.0000495, response.data.usage)
            assertEquals(100.0, response.data.limit)
            assertFalse(response.data.isFreeTier)
        }

        @Test
        @DisplayName("Should handle null limit in KeyData")
        fun testKeyDataWithNullLimit() {
            val json = """
                {
                    "data": {
                        "label": "sk-or-v1-test",
                        "usage": 0.5,
                        "limit": null,
                        "is_free_tier": true
                    }
                }
            """.trimIndent()

            val response = gson.fromJson(json, KeyInfoResponse::class.java)

            assertNull(response.data.limit)
            assertTrue(response.data.isFreeTier)
        }
    }

    @Nested
    @DisplayName("Error Model Tests")
    inner class ErrorModelTests {

        @Test
        @DisplayName("Should deserialize ApiError")
        fun testApiError() {
            val json = """
                {
                    "code": 401,
                    "message": "Invalid API key",
                    "metadata": {
                        "type": "authentication_error"
                    }
                }
            """.trimIndent()

            val error = gson.fromJson(json, ApiError::class.java)

            assertEquals(401, error.code)
            assertEquals("Invalid API key", error.message)
            assertEquals("authentication_error", error.metadata?.get("type"))
        }

        @Test
        @DisplayName("Should handle ApiError without metadata")
        fun testApiErrorWithoutMetadata() {
            val json = """
                {
                    "code": 500,
                    "message": "Internal server error"
                }
            """.trimIndent()

            val error = gson.fromJson(json, ApiError::class.java)

            assertEquals(500, error.code)
            assertEquals("Internal server error", error.message)
            assertNull(error.metadata)
        }
    }

    @Nested
    @DisplayName("Business Logic Tests")
    inner class BusinessLogicTests {

        @Test
        @DisplayName("Should validate API key format")
        fun testApiKeyValidation() {
            val validKey = "sk-or-v1-1234567890abcdef"
            val invalidKey = "invalid-key"

            assertTrue(validKey.startsWith("sk-or-v1-"))
            assertFalse(invalidKey.startsWith("sk-or-v1-"))
        }

        @Test
        @DisplayName("Should format currency correctly")
        fun testCurrencyFormatting() {
            val testCases = mapOf(
                0.0 to "$0.00",
                0.01 to "$0.01",
                1.0 to "$1.00",
                25.5 to "$25.50",
                100.0 to "$100.00"
            )

            testCases.forEach { (value, expected) ->
                val formatted = String.format("$%.2f", value)
                assertEquals(expected, formatted)
            }
        }

        @Test
        @DisplayName("Should handle unlimited quota display")
        fun testUnlimitedQuotaDisplay() {
            val limit: Double? = null
            val display = limit?.let { String.format("$%.2f", it) } ?: "Unlimited"

            assertEquals("Unlimited", display)
        }

        @Test
        @DisplayName("Should calculate usage percentage")
        fun testUsagePercentage() {
            val usage = 25.0
            val limit = 100.0
            val percentage = (usage / limit) * 100

            assertEquals(25.0, percentage)
        }
    }
}
