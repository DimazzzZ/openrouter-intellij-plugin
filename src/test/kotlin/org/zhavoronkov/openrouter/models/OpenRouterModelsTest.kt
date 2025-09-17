package org.zhavoronkov.openrouter.models

import com.google.gson.Gson
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested

@DisplayName("OpenRouter Models Tests")
class OpenRouterModelsTest {

    private val gson = Gson()

    @Nested
    @DisplayName("API Key Models")
    inner class ApiKeyModelsTest {

        @Test
        @DisplayName("Should deserialize ApiKeyInfo from JSON")
        fun testApiKeyInfoDeserialization() {
            val json = """
                {
                    "hash": "abc123",
                    "name": "test-key",
                    "label": "sk-or-v1-test",
                    "disabled": false,
                    "limit": 100.0,
                    "usage": 0.5,
                    "created_at": "2025-09-17T12:00:00.000000+00:00",
                    "updated_at": "2025-09-17T13:00:00.000000+00:00"
                }
            """.trimIndent()

            val apiKeyInfo = gson.fromJson(json, ApiKeyInfo::class.java)

            assertEquals("abc123", apiKeyInfo.hash)
            assertEquals("test-key", apiKeyInfo.name)
            assertEquals("sk-or-v1-test", apiKeyInfo.label)
            assertFalse(apiKeyInfo.disabled)
            assertEquals(100.0, apiKeyInfo.limit)
            assertEquals(0.5, apiKeyInfo.usage)
            assertEquals("2025-09-17T12:00:00.000000+00:00", apiKeyInfo.createdAt)
            assertEquals("2025-09-17T13:00:00.000000+00:00", apiKeyInfo.updatedAt)
        }

        @Test
        @DisplayName("Should handle null values in ApiKeyInfo")
        fun testApiKeyInfoWithNulls() {
            val json = """
                {
                    "hash": "abc123",
                    "name": "test-key",
                    "label": "sk-or-v1-test",
                    "disabled": false,
                    "limit": null,
                    "usage": 0.0,
                    "created_at": "2025-09-17T12:00:00.000000+00:00",
                    "updated_at": null
                }
            """.trimIndent()

            val apiKeyInfo = gson.fromJson(json, ApiKeyInfo::class.java)

            assertNull(apiKeyInfo.limit)
            assertNull(apiKeyInfo.updatedAt)
            assertEquals(0.0, apiKeyInfo.usage)
        }

        @Test
        @DisplayName("Should deserialize ApiKeysListResponse")
        fun testApiKeysListResponseDeserialization() {
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
        @DisplayName("Should serialize CreateApiKeyRequest")
        fun testCreateApiKeyRequestSerialization() {
            val request = CreateApiKeyRequest(name = "test-key", limit = 100.0)
            val json = gson.toJson(request)
            
            assertTrue(json.contains("\"name\":\"test-key\""))
            assertTrue(json.contains("\"limit\":100.0"))
        }

        @Test
        @DisplayName("Should serialize CreateApiKeyRequest with null limit")
        fun testCreateApiKeyRequestWithNullLimit() {
            val request = CreateApiKeyRequest(name = "test-key", limit = null)
            val json = gson.toJson(request)
            
            assertTrue(json.contains("\"name\":\"test-key\""))
            // Gson should include null values by default
        }

        @Test
        @DisplayName("Should deserialize CreateApiKeyResponse")
        fun testCreateApiKeyResponseDeserialization() {
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
                        "updated_at": null
                    },
                    "key": "sk-or-v1-actual-key-value"
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
    @DisplayName("Key Info Models")
    inner class KeyInfoModelsTest {

        @Test
        @DisplayName("Should deserialize KeyInfoResponse")
        fun testKeyInfoResponseDeserialization() {
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
    @DisplayName("Settings Models")
    inner class SettingsModelsTest {

        @Test
        @DisplayName("Should create OpenRouterSettings with defaults")
        fun testOpenRouterSettingsDefaults() {
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
        fun testOpenRouterSettingsCustomValues() {
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
    @DisplayName("Error Models")
    inner class ErrorModelsTest {

        @Test
        @DisplayName("Should deserialize ApiError")
        fun testApiErrorDeserialization() {
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
}
