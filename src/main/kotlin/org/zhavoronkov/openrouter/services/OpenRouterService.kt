package org.zhavoronkov.openrouter.services

import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.intellij.openapi.application.ApplicationManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Response
import org.zhavoronkov.openrouter.constants.OpenRouterConstants
import org.zhavoronkov.openrouter.models.ActivityResponse
import org.zhavoronkov.openrouter.models.ApiKeysListResponse
import org.zhavoronkov.openrouter.models.ApiResult
import org.zhavoronkov.openrouter.models.ChatCompletionRequest
import org.zhavoronkov.openrouter.models.ChatCompletionResponse
import org.zhavoronkov.openrouter.models.CreateApiKeyRequest
import org.zhavoronkov.openrouter.models.CreateApiKeyResponse
import org.zhavoronkov.openrouter.models.CreditsResponse
import org.zhavoronkov.openrouter.models.DeleteApiKeyResponse
import org.zhavoronkov.openrouter.models.ExchangeAuthCodeRequest
import org.zhavoronkov.openrouter.models.ExchangeAuthCodeResponse
import org.zhavoronkov.openrouter.models.GenerationResponse
import org.zhavoronkov.openrouter.models.KeyData
import org.zhavoronkov.openrouter.models.KeyInfoResponse
import org.zhavoronkov.openrouter.models.OpenRouterModelsResponse
import org.zhavoronkov.openrouter.models.OpenRouterResponse
import org.zhavoronkov.openrouter.models.ProvidersResponse
import org.zhavoronkov.openrouter.models.QuotaInfo
import org.zhavoronkov.openrouter.utils.OpenRouterRequestBuilder
import org.zhavoronkov.openrouter.utils.PluginLogger
import org.zhavoronkov.openrouter.utils.await
import org.zhavoronkov.openrouter.utils.toApiResult
import java.io.IOException
import java.util.concurrent.TimeUnit

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

open class OpenRouterService(
    private val gson: Gson = Gson(),
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(OpenRouterConstants.CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(OpenRouterConstants.READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .writeTimeout(OpenRouterConstants.WRITE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .build(),
    private val settingsService: OpenRouterSettingsService = OpenRouterSettingsService.getInstance(),
    private val baseUrlOverride: String? = null
) {

    companion object {
        fun getInstance(): OpenRouterService {
            return ApplicationManager.getApplication().getService(OpenRouterService::class.java)
        }
    }

    // Method to get base URL for testing purposes
    protected open fun getBaseUrl(): String = baseUrlOverride ?: OpenRouterConstants.BASE_URL

    // Dynamic endpoint getters that use getBaseUrl()
    private fun getChatCompletionsEndpoint() = "${getBaseUrl()}/chat/completions"
    private fun getGenerationEndpoint() = "${getBaseUrl()}/generation"
    private fun getCreditsEndpoint() = "${getBaseUrl()}/credits"
    private fun getApiKeysEndpoint() = "${getBaseUrl()}/keys"
    private fun getKeyEndpoint() = "${getBaseUrl()}/key"
    private fun getActivityEndpoint() = "${getBaseUrl()}/activity"
    private fun getAuthKeyEndpoint() = "${getBaseUrl()}/auth/key"
    private fun getAuthKeysEndpoint() = "${getBaseUrl()}/auth/keys"
    private fun getProvidersEndpoint() = "${getBaseUrl()}/providers"
    private fun getModelsEndpoint() = "${getBaseUrl()}/models"

    /**
     * Handle network errors gracefully without alarming users
     * Network issues (offline, DNS problems, etc.) are common and expected
     */
    private fun handleNetworkError(e: IOException, context: String) {
        val errorMsg = when (e) {
            is java.net.UnknownHostException -> "Unable to reach OpenRouter (offline or DNS issue)"
            is java.net.SocketTimeoutException -> "Request timed out - OpenRouter may be slow or unreachable"
            is java.net.ConnectException -> "Connection refused - OpenRouter may be down"
            else -> "Network error: ${e.message}"
        }
        PluginLogger.Service.warn("$context: $errorMsg")
        PluginLogger.Service.debug("$context - network issue details", e)
    }

    /**
     * Get usage statistics for a specific generation
     */
    suspend fun getGenerationStats(generationId: String): ApiResult<GenerationResponse> =
        withContext(Dispatchers.IO) {
            try {
                val request = OpenRouterRequestBuilder.buildGetRequest(
                    url = "${getGenerationEndpoint()}?id=$generationId",
                    authType = OpenRouterRequestBuilder.AuthType.API_KEY,
                    authToken = settingsService.getApiKey()
                )

                val response = client.newCall(request).await()
                response.toApiResult(gson)
            } catch (e: IOException) {
                handleNetworkError(e, "Error getting generation stats")
                ApiResult.Error(message = e.message ?: "Network error", throwable = e)
            }
        }

    /**
     * Create a chat completion using OpenRouter API
     */

    suspend fun createChatCompletion(request: ChatCompletionRequest): ApiResult<ChatCompletionResponse> =
        withContext(Dispatchers.IO) {
            val startNs = System.nanoTime()
            try {
                val apiKey = settingsService.apiKeyManager.getStoredApiKey()
                if (apiKey.isNullOrBlank()) {
                    PluginLogger.Service.error("[OR] No API key configured for chat completion")
                    return@withContext ApiResult.Error("No API key configured")
                }

                val jsonBody = gson.toJson(request)
                logOutgoingRequest(apiKey, jsonBody)

                val httpRequest = OpenRouterRequestBuilder.buildPostRequest(
                    url = getChatCompletionsEndpoint(),
                    jsonBody = jsonBody,
                    authType = OpenRouterRequestBuilder.AuthType.API_KEY,
                    authToken = apiKey
                )

                val response = client.newCall(httpRequest).await()
                val responseBody = response.body?.string().orEmpty()
                val durationMs = (System.nanoTime() - startNs) / OpenRouterConstants.NANOSECONDS_TO_MILLISECONDS
                logIncomingResponse(response, responseBody, durationMs)

                handleChatCompletionResponse(response, responseBody)
            } catch (e: IOException) {
                PluginLogger.Service.error("[OR] Chat completion network error: ${e.message}", e)
                ApiResult.Error(message = e.message ?: "Network error", throwable = e)
            } catch (e: Exception) {
                PluginLogger.Service.error("[OR] Chat completion unexpected error: ${e.message}", e)
                ApiResult.Error(message = e.message ?: "Unexpected error", throwable = e)
            }
        }

    private fun logOutgoingRequest(apiKey: String, jsonBody: String) {
        PluginLogger.Service.info("[OR] POST ${getChatCompletionsEndpoint()}")
        val keyPreview = apiKey.take(OpenRouterConstants.STRING_TRUNCATE_LENGTH)
        PluginLogger.Service.debug(
            "[OR] Headers: Authorization=Bearer $keyPreview…(redacted), Content-Type=application/json"
        )
        val bodyPreview = jsonBody.take(OpenRouterConstants.RESPONSE_PREVIEW_LENGTH)
        val isTruncated = jsonBody.length > OpenRouterConstants.RESPONSE_PREVIEW_LENGTH
        PluginLogger.Service.debug(
            "[OR] Outgoing JSON: $bodyPreview${if (isTruncated) "…(truncated)" else ""}"
        )
    }

    private fun logIncomingResponse(response: Response, responseBody: String, durationMs: Long) {
        val contentType = response.header("Content-Type").orEmpty()
        PluginLogger.Service.info(
            "[OR] Response ${response.code} from OpenRouter in ${durationMs}ms (Content-Type=$contentType)"
        )
        PluginLogger.Service.debug(
            "[OR] Response body: ${responseBody.take(
                OpenRouterConstants.RESPONSE_PREVIEW_LENGTH_SMALL
            )}${if (responseBody.length > OpenRouterConstants.RESPONSE_PREVIEW_LENGTH_SMALL) "…(truncated)" else ""}"
        )
    }

    private fun handleChatCompletionResponse(
        response: Response,
        responseBody: String
    ): ApiResult<ChatCompletionResponse> {
        return if (response.isSuccessful) {
            try {
                val trimmed = responseBody.trimStart()
                val result = gson.fromJson(trimmed, ChatCompletionResponse::class.java)
                PluginLogger.Service.debug("[OR] Parsed ChatCompletionResponse successfully")
                ApiResult.Success(result, response.code)
            } catch (e: JsonSyntaxException) {
                PluginLogger.Service.error("[OR] Failed to parse chat completion response: $responseBody", e)
                ApiResult.Error("Failed to parse response", statusCode = response.code, throwable = e)
            }
        } else {
            PluginLogger.Service.error(
                "[OR] Chat completion failed: ${response.code} ${response.message} - $responseBody"
            )
            ApiResult.Error(
                message = responseBody.ifBlank { response.message ?: "Chat completion failed" },
                statusCode = response.code
            )
        }
    }

    /**
     * Test API connection with a simple request
     */
    suspend fun testConnection(): ApiResult<Boolean> =
        testApiKey(settingsService.getApiKey())

    /**
     * Test a specific API key with a simple request
     */
    suspend fun testApiKey(apiKey: String): ApiResult<Boolean> =
        withContext(Dispatchers.IO) {
            try {
                if (apiKey.isBlank()) {
                    return@withContext ApiResult.Error("API key is required")
                }

                val request = OpenRouterRequestBuilder.buildGetRequest(
                    url = getKeyEndpoint(),
                    authType = OpenRouterRequestBuilder.AuthType.API_KEY,
                    authToken = apiKey
                )

                val response = client.newCall(request).await()
                if (response.isSuccessful) {
                    ApiResult.Success(true, response.code)
                } else {
                    val body = response.body?.string() ?: ""
                    PluginLogger.Service.warn("API key validation failed: ${response.code} $body")

                    val errorMessage = try {
                        val errorObj = gson.fromJson(body, OpenRouterResponse::class.java)
                        errorObj?.error?.message ?: "Invalid API key"
                    } catch (_: Exception) {
                        "Invalid API key (HTTP ${response.code})"
                    }

                    ApiResult.Error(
                        message = errorMessage,
                        statusCode = response.code
                    )
                }
            } catch (e: IOException) {
                handleNetworkError(e, "API key validation failed")
                ApiResult.Error(message = e.message ?: "Network error", throwable = e)
            }
        }

    /**
     * Get API keys list with usage information
     * NOTE: This endpoint requires Provisioning Key authentication
     */
    suspend fun getApiKeysList(): ApiResult<ApiKeysListResponse> =
        getApiKeysList(settingsService.getProvisioningKey())

    /**
     * Get API keys list with usage information using a specific provisioning key
     * NOTE: This endpoint requires Provisioning Key authentication
     */
    suspend fun getApiKeysList(provisioningKey: String): ApiResult<ApiKeysListResponse> =
        withContext(Dispatchers.IO) {
            try {
                if (provisioningKey.isBlank()) {
                    PluginLogger.Service.warn("Provisioning key is blank - cannot fetch API keys list")
                    return@withContext ApiResult.Error("Provisioning key is required")
                }

                val keyPreview = provisioningKey.take(OpenRouterConstants.STRING_TRUNCATE_LENGTH)
                PluginLogger.Service.debug(
                    "Fetching API keys list from OpenRouter with provisioning key: $keyPreview..."
                )
                PluginLogger.Service.debug("Making request to: ${getApiKeysEndpoint()}")

                val request = OpenRouterRequestBuilder.buildGetRequest(
                    url = getApiKeysEndpoint(),
                    authType = OpenRouterRequestBuilder.AuthType.PROVISIONING_KEY,
                    authToken = provisioningKey
                )

                val response = client.newCall(request).await()
                response.toApiResult(gson)
            } catch (e: IOException) {
                handleNetworkError(e, "Error getting API keys list")
                ApiResult.Error(message = e.message ?: "Network error", throwable = e)
            }
        }

    /**
     * Get current quota information based on API keys list
     */
    suspend fun getQuotaInfo(): ApiResult<QuotaInfo> {
        val provisioningKey = settingsService.getProvisioningKey()
        if (provisioningKey.isBlank()) {
            PluginLogger.Service.warn("No provisioning key available for quota info")
            return ApiResult.Error("No provisioning key configured")
        }

        return runCatching { getApiKeysList(provisioningKey) }
            .fold(
                onSuccess = { apiKeysResult ->
                    when (apiKeysResult) {
                        is ApiResult.Success -> {
                            val response = apiKeysResult.data
                            // Sum up usage and limits from all enabled keys
                            val enabledKeys = response.data.filter { !it.disabled }
                            val totalUsed = enabledKeys.sumOf { it.usage }
                            val totalLimit = enabledKeys.mapNotNull { it.limit }.sum()
                            val remaining = if (totalLimit > 0) totalLimit - totalUsed else Double.MAX_VALUE

                            val quotaInfo = QuotaInfo(
                                remaining = remaining,
                                total = totalLimit,
                                used = totalUsed,
                                resetDate = null // OpenRouter doesn't provide reset date in this endpoint
                            )
                            ApiResult.Success(quotaInfo, apiKeysResult.statusCode)
                        }
                        is ApiResult.Error -> apiKeysResult.copy()
                    }
                },
                onFailure = { e ->
                    ApiResult.Error(message = "Failed to get quota info", throwable = e)
                }
            )
    }

    /**
     * Get key info for backward compatibility - returns summary of all keys
     */
    suspend fun getKeyInfo(): ApiResult<KeyInfoResponse> {
        val result = getApiKeysList(settingsService.getProvisioningKey())
        return when (result) {
            is ApiResult.Success -> {
                val apiKeysResponse = result.data
                val enabledKeys = apiKeysResponse.data.filter { !it.disabled }
                val totalUsed = enabledKeys.sumOf { it.usage }
                val totalLimit = enabledKeys.mapNotNull { it.limit }.sum()
                val hasLimit = totalLimit > 0

                // Create a summary KeyInfoResponse
                ApiResult.Success(
                    KeyInfoResponse(
                        data = KeyData(
                            label = "All Keys Summary",
                            usage = totalUsed,
                            limit = if (hasLimit) totalLimit else null,
                            isFreeTier = false // Assume paid if using provisioning keys
                        )
                    ),
                    result.statusCode
                )
            }
            is ApiResult.Error -> result.copy()
        }
    }

    /**
     * Create a new API key
     * NOTE: This endpoint requires Provisioning Key authentication
     */
    suspend fun createApiKey(name: String, limit: Double? = null): ApiResult<CreateApiKeyResponse> =
        withContext(Dispatchers.IO) {
            try {
                // API key creation requires provisioning key
                val provisioningKey = settingsService.getProvisioningKey()
                val requestBody = CreateApiKeyRequest(name = name, limit = limit)
                val json = gson.toJson(requestBody)

                val keyPreview = provisioningKey.take(OpenRouterConstants.STRING_TRUNCATE_LENGTH)
                PluginLogger.Service.debug(
                    "Creating API key with name: $name using provisioning key: $keyPreview..."
                )
                PluginLogger.Service.debug("Making POST request to: ${getApiKeysEndpoint()}")
                PluginLogger.Service.debug("Request body: $json")

                val request = OpenRouterRequestBuilder.buildPostRequest(
                    url = getApiKeysEndpoint(),
                    jsonBody = json,
                    authType = OpenRouterRequestBuilder.AuthType.PROVISIONING_KEY,
                    authToken = provisioningKey
                )

                val response = client.newCall(request).await()
                val responseBody = response.body?.string().orEmpty()
                PluginLogger.Service.debug("Create API key response code: ${response.code}")
                PluginLogger.Service.debug("Create API key response: $responseBody")

                if (response.isSuccessful) {
                    try {
                        val result = gson.fromJson(responseBody, CreateApiKeyResponse::class.java)
                        PluginLogger.Service.info("Successfully created API key: ${result.data.name}")
                        ApiResult.Success(result, response.code)
                    } catch (e: JsonSyntaxException) {
                        PluginLogger.Service.warn("Failed to parse create API key response: $responseBody", e)
                        ApiResult.Error("Failed to parse response", statusCode = response.code, throwable = e)
                    }
                } else {
                    PluginLogger.Service.warn(
                        "Failed to create API key: ${response.code} ${response.message} - $responseBody"
                    )
                    ApiResult.Error(
                        message = responseBody.ifBlank { "Failed to create API key" },
                        statusCode = response.code
                    )
                }
            } catch (e: IOException) {
                handleNetworkError(e, "Error creating API key")
                ApiResult.Error(message = e.message ?: "Network error", throwable = e)
            }
        }

    /**
     * Delete an API key by hash
     * NOTE: This endpoint requires Provisioning Key authentication
     */
    suspend fun deleteApiKey(keyHash: String): ApiResult<DeleteApiKeyResponse> =
        withContext(Dispatchers.IO) {
            try {
                // API key deletion requires provisioning key and uses hash in URL
                val request = OpenRouterRequestBuilder.buildDeleteRequest(
                    url = "${getApiKeysEndpoint()}/$keyHash",
                    authType = OpenRouterRequestBuilder.AuthType.PROVISIONING_KEY,
                    authToken = settingsService.getProvisioningKey()
                )

                PluginLogger.Service.debug("Deleting API key with hash: $keyHash")

                val response = client.newCall(request).await()
                val responseBody = response.body?.string().orEmpty()
                PluginLogger.Service.debug("Delete API key response: $responseBody")

                if (response.isSuccessful) {
                    try {
                        val result = gson.fromJson(responseBody, DeleteApiKeyResponse::class.java)
                        ApiResult.Success(result, response.code)
                    } catch (e: JsonSyntaxException) {
                        PluginLogger.Service.warn("Failed to parse delete API key response: $responseBody", e)
                        ApiResult.Error("Failed to parse response", statusCode = response.code, throwable = e)
                    }
                } else {
                    PluginLogger.Service.warn("Failed to delete API key: ${response.code} - $responseBody")
                    ApiResult.Error(
                        message = responseBody.ifBlank { "Failed to delete API key" },
                        statusCode = response.code
                    )
                }
            } catch (e: IOException) {
                handleNetworkError(e, "Error deleting API key")
                ApiResult.Error(message = e.message ?: "Network error", throwable = e)
            }
        }

    /**
     * Get credits information from OpenRouter
     * NOTE: This endpoint requires Provisioning Key authentication, not API Key
     */
    suspend fun getCredits(): ApiResult<CreditsResponse> =
        withContext(Dispatchers.IO) {
            try {
                // Credits endpoint requires provisioning key, not API key
                val provisioningKey = settingsService.getProvisioningKey()
                if (provisioningKey.isBlank()) {
                    PluginLogger.Service.warn("No provisioning key available for credits endpoint")
                    return@withContext ApiResult.Error("No provisioning key configured")
                }

                val keyPreview = provisioningKey.take(OpenRouterConstants.STRING_TRUNCATE_LENGTH)
                PluginLogger.Service.debug(
                    "Fetching credits from OpenRouter with provisioning key: $keyPreview..."
                )
                PluginLogger.Service.debug("Making request to: ${getCreditsEndpoint()}")

                val request = OpenRouterRequestBuilder.buildGetRequest(
                    url = getCreditsEndpoint(),
                    authType = OpenRouterRequestBuilder.AuthType.PROVISIONING_KEY,
                    authToken = provisioningKey
                )

                val response = client.newCall(request).await()
                val responseBody = response.body?.string().orEmpty()
                PluginLogger.Service.debug("Credits response: ${response.code} - $responseBody")

                if (response.isSuccessful) {
                    try {
                        val creditsResponse = gson.fromJson(responseBody, CreditsResponse::class.java)
                        PluginLogger.Service.info("Successfully parsed credits response: ${creditsResponse.data}")
                        ApiResult.Success(creditsResponse, response.code)
                    } catch (e: JsonSyntaxException) {
                        PluginLogger.Service.error("Error fetching credits - invalid JSON response", e)
                        ApiResult.Error("Failed to parse response", statusCode = response.code, throwable = e)
                    }
                } else {
                    PluginLogger.Service.warn("Failed to fetch credits: ${response.code} - $responseBody")
                    ApiResult.Error(
                        message = responseBody.ifBlank { "Failed to fetch credits" },
                        statusCode = response.code
                    )
                }
            } catch (e: IOException) {
                handleNetworkError(e, "Error fetching credits")
                ApiResult.Error(message = e.message ?: "Network error", throwable = e)
            }
        }

    /**
     * Get activity analytics from OpenRouter
     * NOTE: This endpoint requires Provisioning Key authentication
     */
    suspend fun getActivity(): ApiResult<ActivityResponse> =
        withContext(Dispatchers.IO) {
            try {
                val provisioningKey = settingsService.getProvisioningKey()
                if (provisioningKey.isBlank()) {
                    PluginLogger.Service.warn("No provisioning key available for activity endpoint")
                    return@withContext ApiResult.Error("No provisioning key configured")
                }

                val keyPreview = provisioningKey.take(OpenRouterConstants.STRING_TRUNCATE_LENGTH)
                PluginLogger.Service.debug(
                    "Fetching activity from OpenRouter with provisioning key: $keyPreview..."
                )
                PluginLogger.Service.debug("Making request to: ${getActivityEndpoint()}")

                val request = OpenRouterRequestBuilder.buildGetRequest(
                    url = getActivityEndpoint(),
                    authType = OpenRouterRequestBuilder.AuthType.PROVISIONING_KEY,
                    authToken = provisioningKey
                )

                val response = client.newCall(request).await()
                val responseBody = response.body?.string().orEmpty()
                PluginLogger.Service.debug("Activity response: ${response.code} - $responseBody")

                if (response.isSuccessful) {
                    try {
                        val activityResponse = gson.fromJson(responseBody, ActivityResponse::class.java)
                        PluginLogger.Service.info(
                            "Successfully parsed activity response with ${activityResponse.data.size} entries"
                        )
                        ApiResult.Success(activityResponse, response.code)
                    } catch (e: JsonSyntaxException) {
                        PluginLogger.Service.error("Error fetching activity - invalid JSON response", e)
                        ApiResult.Error("Failed to parse response", statusCode = response.code, throwable = e)
                    }
                } else {
                    PluginLogger.Service.warn("Failed to fetch activity: ${response.code} - $responseBody")
                    ApiResult.Error(
                        message = responseBody.ifBlank { "Failed to fetch activity" },
                        statusCode = response.code
                    )
                }
            } catch (e: IOException) {
                handleNetworkError(e, "Error fetching activity")
                ApiResult.Error(message = e.message ?: "Network error", throwable = e)
            }
        }

    /**
     * Get list of available models from OpenRouter
     * NOTE: This is a public endpoint that requires no authentication
     */
    suspend fun getModels(): ApiResult<OpenRouterModelsResponse> =
        fetchPublicEndpoint(
            getModelsEndpoint(),
            "models",
            OpenRouterConstants.RESPONSE_PREVIEW_LENGTH,
            "Error fetching models"
        ) { responseBody ->
            gson.fromJson(responseBody, OpenRouterModelsResponse::class.java)
        }

    /**
     * Get list of available providers from OpenRouter
     * NOTE: This is a public endpoint that requires no authentication
     */
    suspend fun getProviders(): ApiResult<ProvidersResponse> =
        fetchPublicEndpoint(
            getProvidersEndpoint(),
            "providers",
            OpenRouterConstants.RESPONSE_PREVIEW_LENGTH_SMALL,
            "Error fetching providers"
        ) { responseBody ->
            gson.fromJson(responseBody, ProvidersResponse::class.java)
        }

    /**
     * Generic method to fetch data from public endpoints
     */
    private suspend inline fun <reified T> fetchPublicEndpoint(
        url: String,
        name: String,
        previewLength: Int,
        errorContext: String,
        crossinline parseResponse: (String) -> T
    ): ApiResult<T> =
        withContext(Dispatchers.IO) {
            try {
                PluginLogger.Service.debug("Fetching $name list from OpenRouter (public endpoint)")
                PluginLogger.Service.debug("Making request to: $url")

                val request = OpenRouterRequestBuilder.buildGetRequest(
                    url = url,
                    authType = OpenRouterRequestBuilder.AuthType.NONE
                )

                val response = client.newCall(request).await()
                val responseBody = response.body?.string() ?: ""
                val responsePreview = responseBody.take(previewLength)
                PluginLogger.Service.debug("$name response: ${response.code} - $responsePreview...")

                if (response.isSuccessful) {
                    try {
                        val result = parseResponse(responseBody)
                        PluginLogger.Service.info("Successfully parsed $name response")
                        ApiResult.Success(result, response.code)
                    } catch (e: JsonSyntaxException) {
                        PluginLogger.Service.error("Error fetching $name - invalid JSON response", e)
                        ApiResult.Error("Failed to parse $name response", statusCode = response.code, throwable = e)
                    }
                } else {
                    PluginLogger.Service.warn("Failed to fetch $name: ${response.code} - $responseBody")
                    ApiResult.Error(
                        message = responseBody.ifBlank { "Failed to fetch $name" },
                        statusCode = response.code
                    )
                }
            } catch (e: IOException) {
                handleNetworkError(e, errorContext)
                ApiResult.Error(message = e.message ?: "Network error", throwable = e)
            }
        }

    /**
     * Exchange auth code for API key
     */
    suspend fun exchangeAuthCode(code: String, codeVerifier: String): ApiResult<ExchangeAuthCodeResponse> =
        withContext(Dispatchers.IO) {
            try {
                val requestBody = ExchangeAuthCodeRequest(
                    code = code,
                    codeVerifier = codeVerifier
                )
                val json = gson.toJson(requestBody)

                PluginLogger.Service.info("PKCE: Starting auth code exchange")
                PluginLogger.Service.debug("PKCE: Request body: $json")

                val endpoint = getAuthKeysEndpoint()
                PluginLogger.Service.info("PKCE: Endpoint: $endpoint")

                val request = OpenRouterRequestBuilder.buildPostRequest(
                    url = endpoint,
                    jsonBody = json,
                    authType = OpenRouterRequestBuilder.AuthType.NONE
                )

                PluginLogger.Service.info("PKCE: Sending request...")
                val response = client.newCall(request).await()
                val responseBody = response.body?.string().orEmpty()
                PluginLogger.Service.info("PKCE: Response code: ${response.code}")
                PluginLogger.Service.info("PKCE: Response body: $responseBody")

                if (response.isSuccessful) {
                    try {
                        val result = gson.fromJson(responseBody, ExchangeAuthCodeResponse::class.java)
                        PluginLogger.Service.info("PKCE: Successfully parsed response, key length: ${result.key.length}")
                        ApiResult.Success(result, response.code)
                    } catch (e: JsonSyntaxException) {
                        PluginLogger.Service.error("PKCE: Failed to parse response", e)
                        ApiResult.Error("Failed to parse response: ${e.message}", statusCode = response.code, throwable = e)
                    }
                } else {
                    val errorMessage = responseBody.ifBlank { "Failed to exchange auth code (HTTP ${response.code})" }
                    PluginLogger.Service.error("PKCE: Exchange failed: $errorMessage")
                    ApiResult.Error(
                        message = errorMessage,
                        statusCode = response.code
                    )
                }
            } catch (e: IOException) {
                PluginLogger.Service.error("PKCE: Network error during exchange", e)
                handleNetworkError(e, "Error exchanging auth code")
                ApiResult.Error(message = e.message ?: "Network error", throwable = e)
            } catch (e: Exception) {
                PluginLogger.Service.error("PKCE: Unexpected error during exchange", e)
                ApiResult.Error(message = e.message ?: "Unexpected error", throwable = e)
            }
        }

    /**
     * Check if the service is properly configured
     */
    fun isConfigured(): Boolean {
        return settingsService.isConfigured()
    }
}
