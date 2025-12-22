package org.zhavoronkov.openrouter.utils

/**
 * Centralized error messages for the OpenRouter plugin
 * Provides consistent error message formatting across the codebase
 */
object ErrorMessages {

    // ========== Prefix Constants ==========
    private const val PREFIX_OPENROUTER = "[OpenRouter]"
    private const val PREFIX_PKCE = "[PKCE]"
    private const val PREFIX_PROXY = "[Proxy]"
    private const val PREFIX_SETTINGS = "[Settings]"

    // ========== Authentication Errors ==========

    fun apiKeyRequired(): String = "$PREFIX_OPENROUTER API key is required"

    fun apiKeyInvalid(): String = "$PREFIX_OPENROUTER Invalid API key"

    fun provisioningKeyRequired(): String = "$PREFIX_OPENROUTER Provisioning key is required"

    fun provisioningKeyInvalid(): String = "$PREFIX_OPENROUTER Invalid provisioning key"

    fun authenticationFailed(reason: String? = null): String {
        return if (reason != null) {
            "$PREFIX_OPENROUTER Authentication failed: $reason"
        } else {
            "$PREFIX_OPENROUTER Authentication failed"
        }
    }

    // ========== Network Errors ==========

    fun networkError(context: String? = null): String {
        return if (context != null) {
            "$PREFIX_OPENROUTER Network error during $context"
        } else {
            "$PREFIX_OPENROUTER Network error"
        }
    }

    fun connectionTimeout(context: String? = null): String {
        return if (context != null) {
            "$PREFIX_OPENROUTER Connection timeout during $context"
        } else {
            "$PREFIX_OPENROUTER Connection timeout"
        }
    }

    fun requestFailed(endpoint: String, statusCode: Int): String {
        return "$PREFIX_OPENROUTER Request to $endpoint failed with status $statusCode"
    }

    // ========== Configuration Errors ==========

    fun notConfigured(): String = "$PREFIX_OPENROUTER Plugin not configured"

    fun invalidConfiguration(reason: String): String {
        return "$PREFIX_OPENROUTER Invalid configuration: $reason"
    }

    // ========== PKCE Errors ==========

    fun pkceAuthTimeout(): String = "$PREFIX_PKCE Authentication timed out. Please try again."

    fun pkceServerError(reason: String): String = "$PREFIX_PKCE Server error: $reason"

    fun pkceCodeExchangeFailed(reason: String? = null): String {
        return if (reason != null) {
            "$PREFIX_PKCE Code exchange failed: $reason"
        } else {
            "$PREFIX_PKCE Code exchange failed"
        }
    }

    // ========== Proxy Errors ==========

    fun proxyStartFailed(reason: String): String = "$PREFIX_PROXY Failed to start: $reason"

    fun proxyPortInUse(port: Int): String = "$PREFIX_PROXY Port $port is already in use"

    fun proxyNotRunning(): String = "$PREFIX_PROXY Server is not running"

    // ========== Data Loading Errors ==========

    fun failedToLoadData(dataType: String): String {
        return "$PREFIX_OPENROUTER Failed to load $dataType"
    }

    fun failedToParseResponse(dataType: String): String {
        return "$PREFIX_OPENROUTER Failed to parse $dataType response"
    }

    // ========== Settings Errors ==========

    fun settingsSaveFailed(reason: String? = null): String {
        return if (reason != null) {
            "$PREFIX_SETTINGS Failed to save settings: $reason"
        } else {
            "$PREFIX_SETTINGS Failed to save settings"
        }
    }

    fun settingsLoadFailed(reason: String? = null): String {
        return if (reason != null) {
            "$PREFIX_SETTINGS Failed to load settings: $reason"
        } else {
            "$PREFIX_SETTINGS Failed to load settings"
        }
    }

    // ========== Validation Errors ==========

    fun validationFailed(field: String, reason: String): String {
        return "$PREFIX_OPENROUTER Validation failed for $field: $reason"
    }

    // ========== Generic Errors ==========

    fun unexpectedError(context: String? = null, throwable: Throwable? = null): String {
        val base = if (context != null) {
            "$PREFIX_OPENROUTER Unexpected error during $context"
        } else {
            "$PREFIX_OPENROUTER Unexpected error"
        }

        return if (throwable != null) {
            "$base: ${throwable.message}"
        } else {
            base
        }
    }

    /**
     * Formats an error message with optional details
     */
    fun format(message: String, details: String? = null): String {
        return if (details != null) {
            "$message: $details"
        } else {
            message
        }
    }
}
