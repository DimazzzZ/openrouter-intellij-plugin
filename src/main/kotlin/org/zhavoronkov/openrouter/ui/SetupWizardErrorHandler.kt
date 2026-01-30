package org.zhavoronkov.openrouter.ui

import org.zhavoronkov.openrouter.models.ApiResult
import org.zhavoronkov.openrouter.utils.PluginLogger

/**
 * Centralized error handling utility for SetupWizardDialog
 * Standardizes error handling and provides user-friendly error messages
 */
object SetupWizardErrorHandler {
    private const val TAG = "[OpenRouter]"

    /**
     * Handle API validation errors and convert to user-friendly messages
     */
    fun handleValidationError(result: ApiResult.Error): String {
        return when {
            result.message.contains("Missing Authentication header", ignoreCase = true) ||
                result.message.contains("Invalid key format", ignoreCase = true) ->
                "Invalid key format or type"

            result.message.contains("Invalid provisioningkey", ignoreCase = true) ->
                "Invalid provisioning key"

            result.message.contains("No cookie auth", ignoreCase = true) ||
                result.message.contains("Authentication failed", ignoreCase = true) ->
                "Invalid API key"

            result.message.contains("Network error", ignoreCase = true) ||
                result.message.contains("Unable to reach", ignoreCase = true) ||
                result.message.contains("Connection refused", ignoreCase = true) ->
                "Network error: Unable to connect to OpenRouter"

            else -> {
                PluginLogger.Service.warn("$TAG Unknown validation error: ${result.message}")
                "Invalid key: ${result.message}"
            }
        }
    }

    /**
     * Handle network errors gracefully
     */
    fun handleNetworkError(e: Exception, context: String): String {
        return when (e) {
            is java.net.UnknownHostException -> "Unable to reach OpenRouter (offline or DNS issue)"
            is java.net.SocketTimeoutException -> "Request timed out - OpenRouter may be slow or unreachable"
            is java.net.ConnectException -> "Connection refused - OpenRouter may be down"
            else -> {
                SetupWizardLogger.error("Network error in $context", e)
                "Network error: ${e.message ?: "Unknown network error"}"
            }
        }
    }

    /**
     * Handle PKCE flow errors
     */
    fun handlePkceError(e: Exception, context: String): String {
        return when (e) {
            is java.net.BindException -> {
                val port = SetupWizardConfig.PKCE_PORT
                "Port $port is in use. OpenRouter requires port $port for authentication."
            }
            else -> {
                SetupWizardLogger.error("PKCE error in $context", e)
                "Authentication failed: ${e.message ?: "Unknown error"}"
            }
        }
    }

    /**
     * Handle model loading errors
     */
    fun handleModelLoadingError(e: Exception): String {
        SetupWizardLogger.error("Model loading failed", e)
        return "Failed to load models. Please check your connection and try again."
    }

    /**
     * Create a standardized error result for validation failures
     */
    fun createValidationError(originalError: ApiResult.Error): ApiResult.Error {
        val friendlyMessage = handleValidationError(originalError)
        return ApiResult.Error(
            message = friendlyMessage,
            statusCode = originalError.statusCode,
            throwable = originalError.throwable
        )
    }
}
