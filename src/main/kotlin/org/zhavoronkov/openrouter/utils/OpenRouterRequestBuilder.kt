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
            .addHeader("Content-Type", CONTENT_TYPE_JSON)
            .addHeader("HTTP-Referer", HTTP_REFERER)
            .addHeader("X-Title", X_TITLE)
        
        // Add authentication header if required
        if (authType != AuthType.NONE && authToken != null) {
            builder.addHeader("Authorization", "Bearer $authToken")
        }
        
        // Set HTTP method and body
        when (method) {
            HttpMethod.GET -> {
                // GET requests don't have a body
            }
            HttpMethod.POST -> {
                if (requestBody != null) {
                    builder.post(requestBody)
                } else {
                    throw IllegalArgumentException("POST requests require a request body")
                }
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
