package org.zhavoronkov.openrouter.services

import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import org.zhavoronkov.openrouter.models.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.CompletableFuture

/**
 * Service for interacting with OpenRouter API
 */
class OpenRouterService {
    
    private val logger = Logger.getInstance(OpenRouterService::class.java)
    private val gson = Gson()
    private val client = OkHttpClient()
    private val settingsService = OpenRouterSettingsService.getInstance()
    
    companion object {
        private const val BASE_URL = "https://openrouter.ai/api/v1"
        private const val CHAT_COMPLETIONS_ENDPOINT = "$BASE_URL/chat/completions"
        private const val GENERATION_ENDPOINT = "$BASE_URL/generation"
        private const val API_KEYS_ENDPOINT = "$BASE_URL/keys"

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
                                logger.warn("Failed to parse generation response", e)
                                null
                            }
                        }
                    } else {
                        logger.warn("Failed to get generation stats: ${response.code} ${response.message}")
                        null
                    }
                }
            } catch (e: IOException) {
                logger.warn("Network error getting generation stats", e)
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
                val requestBody = gson.toJson(mapOf(
                    "model" to "openai/gpt-3.5-turbo",
                    "messages" to listOf(
                        mapOf("role" to "user", "content" to "Hello")
                    ),
                    "max_tokens" to 1
                )).toRequestBody("application/json".toMediaType())
                
                val request = Request.Builder()
                    .url(CHAT_COMPLETIONS_ENDPOINT)
                    .post(requestBody)
                    .addHeader("Authorization", "Bearer ${settingsService.getApiKey()}")
                    .addHeader("Content-Type", "application/json")
                    .build()
                
                client.newCall(request).execute().use { response ->
                    response.isSuccessful
                }
            } catch (e: Exception) {
                logger.warn("Connection test failed", e)
                false
            }
        }
    }
    
    /**
     * Get API keys list with usage information
     */
    fun getApiKeysList(): CompletableFuture<ApiKeysListResponse?> {
        return CompletableFuture.supplyAsync {
            try {
                val provisioningKey = settingsService.getProvisioningKey()
                logger.info("Fetching API keys list from OpenRouter with provisioning key: ${provisioningKey.take(10)}...")
                logger.info("Making request to: $API_KEYS_ENDPOINT")

                val request = Request.Builder()
                    .url(API_KEYS_ENDPOINT)
                    .addHeader("Authorization", "Bearer $provisioningKey")
                    .addHeader("Content-Type", "application/json")
                    .build()

                client.newCall(request).execute().use { response ->
                    val responseBody = response.body?.string() ?: ""
                    logger.info("OpenRouter API keys list response code: ${response.code}")
                    logger.info("OpenRouter API keys list response: $responseBody")

                    if (response.isSuccessful) {
                        try {
                            val result = gson.fromJson(responseBody, ApiKeysListResponse::class.java)
                            logger.info("Successfully parsed ${result?.data?.size ?: 0} API keys")
                            result
                        } catch (e: JsonSyntaxException) {
                            logger.warn("Failed to parse API keys list response: $responseBody", e)
                            null
                        }
                    } else {
                        logger.warn("Failed to get API keys list: ${response.code} ${response.message} - $responseBody")
                        null
                    }
                }
            } catch (e: IOException) {
                logger.warn("Network error getting key info", e)
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
     */
    fun createApiKey(name: String, limit: Double? = null): CompletableFuture<CreateApiKeyResponse?> {
        return CompletableFuture.supplyAsync {
            try {
                val provisioningKey = settingsService.getProvisioningKey()
                val requestBody = CreateApiKeyRequest(name = name, limit = limit)
                val json = gson.toJson(requestBody)

                logger.info("Creating API key with name: $name using provisioning key: ${provisioningKey.take(10)}...")
                logger.info("Making POST request to: $API_KEYS_ENDPOINT")
                logger.info("Request body: $json")

                val request = Request.Builder()
                    .url(API_KEYS_ENDPOINT)
                    .addHeader("Authorization", "Bearer $provisioningKey")
                    .addHeader("Content-Type", "application/json")
                    .post(json.toRequestBody("application/json".toMediaType()))
                    .build()

                client.newCall(request).execute().use { response ->
                    val responseBody = response.body?.string() ?: ""
                    logger.info("Create API key response code: ${response.code}")
                    logger.info("Create API key response: $responseBody")

                    if (response.isSuccessful) {
                        try {
                            val result = gson.fromJson(responseBody, CreateApiKeyResponse::class.java)
                            logger.info("Successfully created API key: ${result?.data?.name}")
                            result
                        } catch (e: JsonSyntaxException) {
                            logger.warn("Failed to parse create API key response: $responseBody", e)
                            null
                        }
                    } else {
                        logger.warn("Failed to create API key: ${response.code} ${response.message} - $responseBody")
                        null
                    }
                }
            } catch (e: Exception) {
                logger.error("Error creating API key", e)
                null
            }
        }
    }

    /**
     * Delete an API key
     */
    fun deleteApiKey(keyName: String): CompletableFuture<DeleteApiKeyResponse?> {
        return CompletableFuture.supplyAsync {
            try {
                val request = Request.Builder()
                    .url("$API_KEYS_ENDPOINT/$keyName")
                    .addHeader("Authorization", "Bearer ${settingsService.getProvisioningKey()}")
                    .addHeader("Content-Type", "application/json")
                    .delete()
                    .build()

                logger.info("Deleting API key: $keyName")

                client.newCall(request).execute().use { response ->
                    val responseBody = response.body?.string() ?: ""
                    logger.info("Delete API key response: $responseBody")

                    if (response.isSuccessful) {
                        gson.fromJson(responseBody, DeleteApiKeyResponse::class.java)
                    } else {
                        logger.warn("Failed to delete API key: ${response.code} - $responseBody")
                        null
                    }
                }
            } catch (e: Exception) {
                logger.error("Error deleting API key", e)
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
