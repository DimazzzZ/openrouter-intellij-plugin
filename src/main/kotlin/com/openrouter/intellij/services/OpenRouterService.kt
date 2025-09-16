package com.openrouter.intellij.services

import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.openrouter.intellij.models.*
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
        private const val KEY_INFO_ENDPOINT = "$BASE_URL/key"

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
     * Get API key information including usage and limits
     */
    fun getKeyInfo(): CompletableFuture<KeyInfoResponse?> {
        return CompletableFuture.supplyAsync {
            try {
                val request = Request.Builder()
                    .url(KEY_INFO_ENDPOINT)
                    .addHeader("Authorization", "Bearer ${settingsService.getApiKey()}")
                    .addHeader("Content-Type", "application/json")
                    .build()

                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        response.body?.string()?.let { responseBody ->
                            try {
                                logger.info("OpenRouter API response: $responseBody")
                                val keyInfo = gson.fromJson(responseBody, KeyInfoResponse::class.java)
                                logger.info("Parsed key info - usage: ${keyInfo.data.usage}, limit: ${keyInfo.data.limit}, isFreeTier: ${keyInfo.data.isFreeTier}")
                                keyInfo
                            } catch (e: JsonSyntaxException) {
                                logger.warn("Failed to parse key info response: $responseBody", e)
                                null
                            }
                        }
                    } else {
                        logger.warn("Failed to get key info: ${response.code} ${response.message}")
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
     * Get current quota information based on key info
     */
    fun getQuotaInfo(): CompletableFuture<QuotaInfo?> {
        return getKeyInfo().thenApply { keyInfo ->
            keyInfo?.let {
                val used = it.data.usage
                val total = it.data.limit ?: Double.MAX_VALUE
                val remaining = if (it.data.limit != null) it.data.limit - used else Double.MAX_VALUE

                QuotaInfo(
                    remaining = remaining,
                    total = total,
                    used = used,
                    resetDate = null // OpenRouter doesn't provide reset date in this endpoint
                )
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
