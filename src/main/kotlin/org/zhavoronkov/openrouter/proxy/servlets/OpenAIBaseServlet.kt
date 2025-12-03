package org.zhavoronkov.openrouter.proxy.servlets

import com.google.gson.Gson
import jakarta.servlet.http.HttpServlet
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.zhavoronkov.openrouter.utils.PluginLogger
import org.zhavoronkov.openrouter.proxy.translation.ResponseTranslator

/**
 * Base servlet class that provides common OpenAI API compatibility functionality
 * including CORS handling, authorization validation, and error responses.
 */
abstract class OpenAIBaseServlet : HttpServlet() {

    protected val gson = Gson()

    protected val AUTH_HEADER_PREFIX_LENGTH = 7

    /**
     * Validates the Authorization header and extracts the API key
     * Returns null if validation fails (error response is handled internally)
     */
    protected fun validateAndExtractApiKey(resp: HttpServletResponse, req: HttpServletRequest): String? {
        val authHeader = req.getHeader("Authorization")
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            PluginLogger.Service.debug("Missing or invalid Authorization header")
            sendAuthErrorResponse(resp)
            return null
        }

        val apiKey = authHeader.substring(AUTH_HEADER_PREFIX_LENGTH).trim() // Remove "Bearer " prefix
        if (apiKey.isBlank()) {
            PluginLogger.Service.debug("Empty API key provided")
            sendAuthErrorResponse(resp)
            return null
        }

        PluginLogger.Service.debug("Valid Authorization header provided")
        return apiKey
    }

    /**
     * Sets standard CORS headers for OpenAI API compatibility
     */
    protected fun setCORSHeaders(resp: HttpServletResponse, allowedMethods: String = "GET, OPTIONS") {
        resp.setHeader("Access-Control-Allow-Origin", "*")
        resp.setHeader("Access-Control-Allow-Methods", allowedMethods)
        resp.setHeader("Access-Control-Allow-Headers", "Content-Type, Authorization")
    }

    /**
     * Handles CORS preflight OPTIONS requests
     */
    fun handleOptionsRequest(resp: HttpServletResponse, allowedMethods: String = "GET, OPTIONS") {
        setCORSHeaders(resp, allowedMethods)
        resp.status = HttpServletResponse.SC_OK
    }

    /**
     * Sends a standardized authentication error response
     */
    protected fun sendAuthErrorResponse(resp: HttpServletResponse) {
        resp.status = HttpServletResponse.SC_UNAUTHORIZED
        resp.contentType = "application/json"

        val errorResponse = ResponseTranslator.createAuthErrorResponse()
        resp.writer.write(gson.toJson(errorResponse))
    }

    /**
     * Sends a standardized error response with mapping for common HTTP status codes
     */
    protected fun sendErrorResponse(resp: HttpServletResponse, message: String, statusCode: Int) {
        resp.status = statusCode
        resp.contentType = "application/json"

        val errorResponse = ResponseTranslator.createErrorResponse(
            message = message,
            type = when (statusCode) {
                HttpServletResponse.SC_BAD_REQUEST -> "invalid_request_error"
                HttpServletResponse.SC_REQUEST_TIMEOUT -> "timeout_error"
                HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE -> "request_too_large"
                HttpServletResponse.SC_SERVICE_UNAVAILABLE -> "service_unavailable"
                else -> "internal_error"
            }
        )

        resp.writer.write(gson.toJson(errorResponse))
    }

    /**
     * Wraps request handling in try-catch for consistent error handling
     */
    protected fun handleRequest(
        requestHandler: () -> Unit,
        resp: HttpServletResponse,
        errorContext: String
    ) {
        try {
            requestHandler()
        } catch (e: Exception) {
            PluginLogger.Service.error("Error in $errorContext endpoint", e)
            sendErrorResponse(resp, "Internal server error", HttpServletResponse.SC_INTERNAL_SERVER_ERROR)
        }
    }
}
