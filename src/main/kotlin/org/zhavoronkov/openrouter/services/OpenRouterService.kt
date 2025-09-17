package org.zhavoronkov.openrouter.services

import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.zhavoronkov.openrouter.models.ApiKeysListResponse
import org.zhavoronkov.openrouter.models.CreateApiKeyRequest
import org.zhavoronkov.openrouter.models.CreateApiKeyResponse
import org.zhavoronkov.openrouter.models.CreditsResponse
import org.zhavoronkov.openrouter.models.DeleteApiKeyResponse
import org.zhavoronkov.openrouter.models.GenerationResponse
import org.zhavoronkov.openrouter.models.KeyData
import org.zhavoronkov.openrouter.models.KeyInfoResponse
import org.zhavoronkov.openrouter.models.ProvidersResponse
import org.zhavoronkov.openrouter.models.QuotaInfo
import org.zhavoronkov.openrouter.utils.PluginLogger
import java.io.IOException
import java.util.concurrent.CompletableFuture

/**
 * Service for interacting with OpenRouter API
 *
 * AUTHENTICATION PATTERNS:
 *
 * 1. PROVISIONING KEY ENDPOINTS (Bearer <provisioning-key>):
 *    - /api/v1/keys (API key management: list, create, delete)
 *    - Used for managing API keys programmatically
 *    - Requires provisioning key from OpenRouter settings/provisioning-keys
 *
 * 2. API KEY ENDPOINTS (Bearer <api-key>):
 *    - /api/v1/chat/completions (chat completions)
 *    - /api/v1/generation (generation stats)
 *    - /api/v1/credits (credits information)
 *    - Used for actual AI model interactions and account info
 *    - Requires regular API key (typically the auto-created "IntelliJ IDEA Plugin" key)
 *
 * 3. PUBLIC ENDPOINTS (no authentication):
 *    - /api/v1/providers (list of available AI providers)
 *    - Publicly accessible information
 */
class OpenRouterService {

    private val gson = Gson()
    private val client = OkHttpClient()
    private val settingsService = OpenRouterSettingsService.getInstance()

    companion object {
        private const val BASE_URL = "https://openrouter.ai/api/v1"

        // Endpoints requiring API Key authentication (Bearer <api-key>)
        private const val CHAT_COMPLETIONS_ENDPOINT = "$BASE_URL/chat/completions"
        private const val GENERATION_ENDPOINT = "$BASE_URL/generation"
        private const val CREDITS_ENDPOINT = "$BASE_URL/credits"

        // Endpoints requiring Provisioning Key authentication (Bearer <provisioning-key>)
        private const val API_KEYS_ENDPOINT = "$BASE_URL/keys"

        // Public endpoints (no authentication required)
        private const val PROVIDERS_ENDPOINT = "$BASE_URL/providers"

        fun getInstance(): OpenRouterService {
            return ApplicationManager.getApplication().getService(OpenRouterService::class.java)
        }
    }

    /**
     * Get usage statistics for a specific generation
     */
    fun getGenerationStats(generationId: String): CompletableFuture<GenerationResponse?> {
        return CompletableFuture.supplyAsync {
            try {
                val request = Request.Builder()
                    .url("$GENERATION_ENDPOINT?id=$generationId")
                    .addHeader("Authorization", "Bearer ${settingsService.getApiKey()}")
                    .addHeader("Content-Type", "application/json")
                    .build()

                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        response.body?.string()?.let { responseBody ->
                            try {
                                gson.fromJson(responseBody, GenerationResponse::class.java)
                            } catch (e: JsonSyntaxException) {
                                PluginLogger.Service.warn("Failed to parse generation response", e)
                                null
                            }
                        }
                    } else {
                        PluginLogger.Service.warn(
                            "Failed to get generation stats: ${response.code} ${response.message}"
                        )
                        null
                    }
                }
            } catch (e: IOException) {
                PluginLogger.Service.warn("Network error getting generation stats", e)
                null
            }
        }
    }

    /**
     * Test API connection with a simple request
     */
    fun testConnection(): CompletableFuture<Boolean> {
        return CompletableFuture.supplyAsync {
            try {
                val requestBody = gson.toJson(
                    mapOf(
                        "model" to "openai/gpt-3.5-turbo",
                        "messages" to listOf(
                            mapOf("role" to "user", "content" to "Hello")
                        ),
                        "max_tokens" to 1
                    )
                ).toRequestBody("application/json".toMediaType())

                val request = Request.Builder()
                    .url(CHAT_COMPLETIONS_ENDPOINT)
                    .post(requestBody)
                    .addHeader("Authorization", "Bearer ${settingsService.getApiKey()}")
                    .addHeader("Content-Type", "application/json")
                    .build()

                client.newCall(request).execute().use { response ->
                    response.isSuccessful
                }
            } catch (e: IOException) {
                PluginLogger.Service.warn("Connection test failed", e)
                false
            } catch (e: JsonSyntaxException) {
                PluginLogger.Service.warn("Connection test failed - invalid JSON response", e)
                false
            }
        }
    }

    /**
     * Get API keys list with usage information
     * NOTE: This endpoint requires Provisioning Key authentication
     */
    fun getApiKeysList(): CompletableFuture<ApiKeysListResponse?> {
        return getApiKeysList(settingsService.getProvisioningKey())
    }

    /**
     * Get API keys list with usage information using a specific provisioning key
     * NOTE: This endpoint requires Provisioning Key authentication
     */
    fun getApiKeysList(provisioningKey: String): CompletableFuture<ApiKeysListResponse?> {
        return CompletableFuture.supplyAsync {
            try {
                PluginLogger.Service.debug(
                    "Fetching API keys list from OpenRouter with provisioning key: ${provisioningKey.take(10)}..."
                )
                PluginLogger.Service.debug("Making request to: $API_KEYS_ENDPOINT")

                val request = Request.Builder()
                    .url(API_KEYS_ENDPOINT)
                    .addHeader("Authorization", "Bearer $provisioningKey")
                    .addHeader("Content-Type", "application/json")
                    .build()

                client.newCall(request).execute().use { response ->
                    val responseBody = response.body?.string() ?: ""
                    PluginLogger.Service.debug("OpenRouter API keys list response code: ${response.code}")
                    PluginLogger.Service.debug("OpenRouter API keys list response: $responseBody")

                    if (response.isSuccessful) {
                        try {
                            PluginLogger.Service.debug("Attempting to parse JSON response...")
                            val result = gson.fromJson(responseBody, ApiKeysListResponse::class.java)
                            PluginLogger.Service.info("Successfully parsed ${result?.data?.size ?: 0} API keys")
                            PluginLogger.Service.debug("Parsed API keys: ${result?.data?.map { it.name } ?: emptyList()}")
                            result
                        } catch (e: JsonSyntaxException) {
                            PluginLogger.Service.warn("Failed to parse API keys list response: $responseBody", e)
                            PluginLogger.Service.debug("JSON parsing error details: ${e.message}")
                            null
                        } catch (e: Exception) {
                            PluginLogger.Service.warn("Unexpected error parsing API keys list response", e)
                            null
                        }
                    } else {
                        PluginLogger.Service.warn(
                            "Failed to get API keys list: ${response.code} ${response.message} - $responseBody"
                        )
                        null
                    }
                }
            } catch (e: IOException) {
                PluginLogger.Service.warn("Network error getting API keys list", e)
                null
            }
        }
    }

    /**
     * Get current quota information based on API keys list
     */
    fun getQuotaInfo(): CompletableFuture<QuotaInfo?> {
        return getApiKeysList().thenApply { apiKeysResponse ->
            apiKeysResponse?.let { response ->
                // Sum up limits from all enabled keys (usage not available from this endpoint)
                val enabledKeys = response.data.filter { !it.disabled }
                val totalUsed = 0.0 // Usage not available from keys list endpoint
                val totalLimit = enabledKeys.mapNotNull { it.limit }.sum()
                val remaining = if (totalLimit > 0) totalLimit - totalUsed else Double.MAX_VALUE

                QuotaInfo(
                    remaining = remaining,
                    total = totalLimit,
                    used = totalUsed,
                    resetDate = null // OpenRouter doesn't provide reset date in this endpoint
                )
            }
        }
    }

    /**
     * Get key info for backward compatibility - returns summary of all keys
     */
    fun getKeyInfo(): CompletableFuture<KeyInfoResponse?> {
        return getApiKeysList().thenApply { apiKeysResponse ->
            apiKeysResponse?.let { response ->
                val enabledKeys = response.data.filter { !it.disabled }
                val totalUsed = 0.0 // Usage not available from keys list endpoint
                val totalLimit = enabledKeys.mapNotNull { it.limit }.sum()
                val hasLimit = totalLimit > 0

                // Create a summary KeyInfoResponse
                KeyInfoResponse(
                    data = KeyData(
                        label = "All Keys Summary",
                        usage = totalUsed,
                        limit = if (hasLimit) totalLimit else null,
                        isFreeTier = false // Assume paid if using provisioning keys
                    )
                )
            }
        }
    }

    /**
     * Create a new API key
     * NOTE: This endpoint requires Provisioning Key authentication
     */
    fun createApiKey(name: String, limit: Double? = null): CompletableFuture<CreateApiKeyResponse?> {
        return CompletableFuture.supplyAsync {
            try {
                // API key creation requires provisioning key
                val provisioningKey = settingsService.getProvisioningKey()
                val requestBody = CreateApiKeyRequest(name = name, limit = limit)
                val json = gson.toJson(requestBody)

                PluginLogger.Service.debug("Creating API key with name: $name using provisioning key: ${provisioningKey.take(10)}...")
                PluginLogger.Service.debug("Making POST request to: $API_KEYS_ENDPOINT")
                PluginLogger.Service.debug("Request body: $json")

                val request = Request.Builder()
                    .url(API_KEYS_ENDPOINT)
                    .addHeader("Authorization", "Bearer $provisioningKey")
                    .addHeader("Content-Type", "application/json")
                    .post(json.toRequestBody("application/json".toMediaType()))
                    .build()

                client.newCall(request).execute().use { response ->
                    val responseBody = response.body?.string() ?: ""
                    PluginLogger.Service.debug("Create API key response code: ${response.code}")
                    PluginLogger.Service.debug("Create API key response: $responseBody")

                    if (response.isSuccessful) {
                        try {
                            val result = gson.fromJson(responseBody, CreateApiKeyResponse::class.java)
                            PluginLogger.Service.info("Successfully created API key: ${result?.data?.name}")
                            result
                        } catch (e: JsonSyntaxException) {
                            PluginLogger.Service.warn("Failed to parse create API key response: $responseBody", e)
                            null
                        }
                    } else {
                        PluginLogger.Service.warn("Failed to create API key: ${response.code} ${response.message} - $responseBody")
                        null
                    }
                }
            } catch (e: IOException) {
                PluginLogger.Service.error("Error creating API key - network issue", e)
                null
            } catch (e: JsonSyntaxException) {
                PluginLogger.Service.error("Error creating API key - invalid JSON response", e)
                null
            }
        }
    }

    /**
     * Delete an API key
     * NOTE: This endpoint requires Provisioning Key authentication
     */
    fun deleteApiKey(keyName: String): CompletableFuture<DeleteApiKeyResponse?> {
        return CompletableFuture.supplyAsync {
            try {
                // API key deletion requires provisioning key
                val request = Request.Builder()
                    .url("$API_KEYS_ENDPOINT/$keyName")
                    .addHeader("Authorization", "Bearer ${settingsService.getProvisioningKey()}")
                    .addHeader("Content-Type", "application/json")
                    .delete()
                    .build()

                PluginLogger.Service.debug("Deleting API key: $keyName")

                client.newCall(request).execute().use { response ->
                    val responseBody = response.body?.string() ?: ""
                    PluginLogger.Service.debug("Delete API key response: $responseBody")

                    if (response.isSuccessful) {
                        gson.fromJson(responseBody, DeleteApiKeyResponse::class.java)
                    } else {
                        PluginLogger.Service.warn("Failed to delete API key: ${response.code} - $responseBody")
                        null
                    }
                }
            } catch (e: IOException) {
                PluginLogger.Service.error("Error deleting API key - network issue", e)
                null
            } catch (e: JsonSyntaxException) {
                PluginLogger.Service.error("Error deleting API key - invalid JSON response", e)
                null
            }
        }
    }

    /**
     * Get credits information from OpenRouter
     * NOTE: This endpoint requires API Key authentication, not Provisioning Key
     */
    fun getCredits(): CompletableFuture<CreditsResponse?> {
        return CompletableFuture.supplyAsync {
            try {
                // Credits endpoint requires API key, not provisioning key
                val apiKey = settingsService.getStoredApiKey()
                if (apiKey.isNullOrBlank()) {
                    PluginLogger.Service.warn("No API key available for credits endpoint")
                    return@supplyAsync null
                }

                PluginLogger.Service.debug("Fetching credits from OpenRouter with API key: ${apiKey.take(10)}...")
                PluginLogger.Service.debug("Making request to: $CREDITS_ENDPOINT")

                val request = Request.Builder()
                    .url(CREDITS_ENDPOINT)
                    .addHeader("Authorization", "Bearer $apiKey")
                    .addHeader("Content-Type", "application/json")
                    .build()

                val response = client.newCall(request).execute()
                val responseBody = response.body?.string() ?: ""
                PluginLogger.Service.debug("Credits response: ${response.code} - $responseBody")

                if (response.isSuccessful) {
                    val creditsResponse = gson.fromJson(responseBody, CreditsResponse::class.java)
                    PluginLogger.Service.info("Successfully parsed credits response: ${creditsResponse.data}")
                    creditsResponse
                } else {
                    PluginLogger.Service.warn("Failed to fetch credits: ${response.code} - $responseBody")
                    null
                }
            } catch (e: IOException) {
                PluginLogger.Service.error("Error fetching credits - network issue", e)
                null
            } catch (e: JsonSyntaxException) {
                PluginLogger.Service.error("Error fetching credits - invalid JSON response", e)
                null
            }
        }
    }

    /**
     * Get list of available providers from OpenRouter
     * NOTE: This is a public endpoint that requires no authentication
     */
    fun getProviders(): CompletableFuture<ProvidersResponse?> {
        return CompletableFuture.supplyAsync {
            try {
                PluginLogger.Service.debug("Fetching providers list from OpenRouter (public endpoint)")
                PluginLogger.Service.debug("Making request to: $PROVIDERS_ENDPOINT")

                // Providers endpoint is public - no authentication required
                val request = Request.Builder()
                    .url(PROVIDERS_ENDPOINT)
                    .addHeader("Content-Type", "application/json")
                    .build()

                val response = client.newCall(request).execute()
                val responseBody = response.body?.string() ?: ""
                PluginLogger.Service.debug("Providers response: ${response.code} - ${responseBody.take(200)}...")

                if (response.isSuccessful) {
                    val providersResponse = gson.fromJson(responseBody, ProvidersResponse::class.java)
                    PluginLogger.Service.info("Successfully parsed providers response: ${providersResponse.data.size} providers")
                    providersResponse
                } else {
                    PluginLogger.Service.warn("Failed to fetch providers: ${response.code} - $responseBody")
                    null
                }
            } catch (e: IOException) {
                PluginLogger.Service.error("Error fetching providers - network issue", e)
                null
            } catch (e: JsonSyntaxException) {
                PluginLogger.Service.error("Error fetching providers - invalid JSON response", e)
                null
            }
        }
    }

    /**
     * Check if the service is properly configured
     */
    fun isConfigured(): Boolean {
        return settingsService.isConfigured()
    }
}
