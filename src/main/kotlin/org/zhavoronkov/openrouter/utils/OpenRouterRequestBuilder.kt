package org.zhavoronkov.openrouter.utils

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
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
        authType: AuthType,
        authToken: String? = null
    ): Request = baseRequest(url, HttpMethod.GET, authType, authToken).build()

    /**
     * Build a POST request with JSON body and standard OpenRouter headers
     */
    fun buildPostRequest(
        url: String,
        jsonBody: String,
        authType: AuthType,
        authToken: String? = null
    ): Request = baseRequest(url, HttpMethod.POST, authType, authToken)
        .post(jsonBody.toRequestBody(CONTENT_TYPE_JSON.toMediaType()))
        .build()

    /**
     * Build a DELETE request with standard OpenRouter headers
     */
    fun buildDeleteRequest(
        url: String,
        authType: AuthType,
        authToken: String?
    ): Request = baseRequest(url, HttpMethod.DELETE, authType, authToken).delete().build()

    /**
     * The headers every request carries, and its authentication; the caller sets the method and
     * body. [method] only names the request in the log.
     */
    private fun baseRequest(
        url: String,
        method: HttpMethod,
        authType: AuthType,
        authToken: String?
    ): Request.Builder {
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
            PluginLogger.Service.debug("Request: $method $url, Auth: Bearer $maskedToken")
        } else {
            PluginLogger.Service.debug("Request: $method $url, Auth: NONE")
        }

        return builder
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
