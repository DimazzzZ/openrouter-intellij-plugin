package org.zhavoronkov.openrouter.utils

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * Centralized request builder for OpenRouter API calls
 * Eliminates code duplication and ensures consistent headers across all requests
 */
object OpenRouterRequestBuilder {

    // OpenRouter-specific headers that should be included in all requests
    private const val HTTP_REFERER = "https://github.com/DimazzzZ/openrouter-intellij-plugin"
    private const val X_TITLE = "OpenRouter IntelliJ Plugin"
    private const val CONTENT_TYPE_JSON = "application/json"

    /**
     * OAuth app name used in the OAuth authorization flow.
     *
     * This is passed as the 'name' parameter in the OAuth authorization URL:
     * https://openrouter.ai/auth?callback_url=...&name=<OAUTH_APP_NAME>
     *
     * Note: OpenRouter may ignore this parameter for localhost callbacks and use a default name
     * (e.g., "OAuth: $CONST Terminal") for security reasons. The generated API key will still
     * work correctly regardless of the displayed name.
     */
    const val OAUTH_APP_NAME = "OpenRouter IntelliJ Plugin"

    /**
     * Authentication types for different OpenRouter endpoints
     */
    enum class AuthType {
        /** No authentication required (public endpoints like /models) */
        NONE,

        /** API key authentication (Bearer <api-key>) for chat completions, credits, etc. */
        API_KEY,

        /** Provisioning key authentication (Bearer <provisioning-key>) for API key management */
        PROVISIONING_KEY
    }

    /**
     * HTTP methods supported by the request builder
     */
    enum class HttpMethod {
        GET, POST, DELETE
    }

    /**
     * Build a GET request with standard OpenRouter headers
     */
    fun buildGetRequest(
        url: String,
        authType: AuthType = AuthType.NONE,
        authToken: String? = null
    ): Request {
        return buildRequest(
            url = url,
            method = HttpMethod.GET,
            authType = authType,
            authToken = authToken
        )
    }

    /**
     * Build a POST request with JSON body and standard OpenRouter headers
     */
    fun buildPostRequest(
        url: String,
        jsonBody: String,
        authType: AuthType = AuthType.API_KEY,
        authToken: String? = null
    ): Request {
        return buildRequest(
            url = url,
            method = HttpMethod.POST,
            authType = authType,
            authToken = authToken,
            requestBody = jsonBody.toRequestBody(CONTENT_TYPE_JSON.toMediaType())
        )
    }

    /**
     * Build a DELETE request with standard OpenRouter headers
     */
    fun buildDeleteRequest(
        url: String,
        authType: AuthType = AuthType.PROVISIONING_KEY,
        authToken: String? = null
    ): Request {
        return buildRequest(
            url = url,
            method = HttpMethod.DELETE,
            authType = authType,
            authToken = authToken
        )
    }

    /**
     * Core request builder that handles all HTTP methods and authentication types
     */
    private fun buildRequest(
        url: String,
        method: HttpMethod,
        authType: AuthType = AuthType.NONE,
        authToken: String? = null,
        requestBody: RequestBody? = null
    ): Request {
        val builder = Request.Builder()
            .url(url)
            .header("Content-Type", CONTENT_TYPE_JSON)
            .header("HTTP-Referer", HTTP_REFERER)
            .header("Referer", HTTP_REFERER) // Add standard Referer just in case
            .header("X-Title", X_TITLE)

        // Add authentication header if required
        if (authType != AuthType.NONE && !authToken.isNullOrBlank()) {
            val authHeaderValue = "Bearer ${authToken.trim()}"
            builder.header("Authorization", authHeaderValue)

            // Mask the token for logging using centralized utility
            val maskedToken = KeyValidator.maskApiKey(authToken)
            PluginLogger.Service.debug("[OpenRouter] Request: $method $url, Auth: Bearer $maskedToken")
        } else {
            PluginLogger.Service.debug("[OpenRouter] Request: $method $url, Auth: NONE")
        }

        // Set HTTP method and body
        when (method) {
            HttpMethod.GET -> {
                // GET requests don't have a body
            }
            HttpMethod.POST -> {
                require(requestBody != null) { "POST requests require a request body" }
                builder.post(requestBody)
            }
            HttpMethod.DELETE -> {
                builder.delete()
            }
        }

        return builder.build()
    }

    /**
     * Convenience method to create JSON request body
     */
    fun createJsonBody(jsonString: String): RequestBody {
        return jsonString.toRequestBody(CONTENT_TYPE_JSON.toMediaType())
    }

    /**
     * Get the standard OpenRouter headers as a map (for AI Assistant integration)
     */
    fun getStandardHeaders(authToken: String? = null): Map<String, String> {
        val headers = mutableMapOf<String, String>()
        headers["Content-Type"] = CONTENT_TYPE_JSON
        headers["HTTP-Referer"] = HTTP_REFERER
        headers["X-Title"] = X_TITLE

        if (authToken != null) {
            headers["Authorization"] = "Bearer $authToken"
        }

        return headers
    }

    /**
     * Update configuration values (for future extensibility)
     */
    object Config {
        fun getHttpReferer(): String = HTTP_REFERER
        fun getXTitle(): String = X_TITLE
        fun getContentType(): String = CONTENT_TYPE_JSON
    }
}
