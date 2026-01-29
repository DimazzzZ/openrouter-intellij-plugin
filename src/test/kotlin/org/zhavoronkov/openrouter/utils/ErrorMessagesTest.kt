package org.zhavoronkov.openrouter.utils

import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

/**
 * Tests for ErrorMessages utility object
 */
@DisplayName("ErrorMessages Tests")
class ErrorMessagesTest {

    @Test
    @DisplayName("Should return correct API key required message")
    fun testApiKeyRequired() {
        val message = ErrorMessages.apiKeyRequired()
        assertEquals("[OpenRouter] API key is required", message)
    }

    @Test
    @DisplayName("Should return correct API key invalid message")
    fun testApiKeyInvalid() {
        val message = ErrorMessages.apiKeyInvalid()
        assertEquals("[OpenRouter] Invalid API key", message)
    }

    @Test
    @DisplayName("Should return correct provisioning key required message")
    fun testProvisioningKeyRequired() {
        val message = ErrorMessages.provisioningKeyRequired()
        assertEquals("[OpenRouter] Provisioning key is required", message)
    }

    @Test
    @DisplayName("Should return correct provisioning key invalid message")
    fun testProvisioningKeyInvalid() {
        val message = ErrorMessages.provisioningKeyInvalid()
        assertEquals("[OpenRouter] Invalid provisioning key", message)
    }

    @Test
    @DisplayName("Should return authentication failed message with reason")
    fun testAuthenticationFailedWithReason() {
        val message = ErrorMessages.authenticationFailed("token expired")
        assertEquals("[OpenRouter] Authentication failed: token expired", message)
    }

    @Test
    @DisplayName("Should return authentication failed message without reason")
    fun testAuthenticationFailedWithoutReason() {
        val message = ErrorMessages.authenticationFailed()
        assertEquals("[OpenRouter] Authentication failed", message)
    }

    @Test
    @DisplayName("Should return network error message with context")
    fun testNetworkErrorWithContext() {
        val message = ErrorMessages.networkError("fetching models")
        assertEquals("[OpenRouter] Network error during fetching models", message)
    }

    @Test
    @DisplayName("Should return network error message without context")
    fun testNetworkErrorWithoutContext() {
        val message = ErrorMessages.networkError()
        assertEquals("[OpenRouter] Network error", message)
    }

    @Test
    @DisplayName("Should return connection timeout message with context")
    fun testConnectionTimeoutWithContext() {
        val message = ErrorMessages.connectionTimeout("API call")
        assertEquals("[OpenRouter] Connection timeout during API call", message)
    }

    @Test
    @DisplayName("Should return connection timeout message without context")
    fun testConnectionTimeoutWithoutContext() {
        val message = ErrorMessages.connectionTimeout()
        assertEquals("[OpenRouter] Connection timeout", message)
    }

    @Test
    @DisplayName("Should return request failed message with correct format")
    fun testRequestFailed() {
        val message = ErrorMessages.requestFailed("/api/v1/models", 404)
        assertEquals("[OpenRouter] Request to /api/v1/models failed with status 404", message)
    }

    @Test
    @DisplayName("Should return not configured message")
    fun testNotConfigured() {
        val message = ErrorMessages.notConfigured()
        assertEquals("[OpenRouter] Plugin not configured", message)
    }

    @Test
    @DisplayName("Should return invalid configuration message")
    fun testInvalidConfiguration() {
        val message = ErrorMessages.invalidConfiguration("missing API key")
        assertEquals("[OpenRouter] Invalid configuration: missing API key", message)
    }

    @Test
    @DisplayName("Should return PKCE auth timeout message")
    fun testPkceAuthTimeout() {
        val message = ErrorMessages.pkceAuthTimeout()
        assertEquals("[PKCE] Authentication timed out. Please try again.", message)
    }

    @Test
    @DisplayName("Should return PKCE server error message")
    fun testPkceServerError() {
        val message = ErrorMessages.pkceServerError("connection refused")
        assertEquals("[PKCE] Server error: connection refused", message)
    }

    @Test
    @DisplayName("Should return PKCE code exchange failed message with reason")
    fun testPkceCodeExchangeFailedWithReason() {
        val message = ErrorMessages.pkceCodeExchangeFailed("invalid code")
        assertEquals("[PKCE] Code exchange failed: invalid code", message)
    }

    @Test
    @DisplayName("Should return PKCE code exchange failed message without reason")
    fun testPkceCodeExchangeFailedWithoutReason() {
        val message = ErrorMessages.pkceCodeExchangeFailed()
        assertEquals("[PKCE] Code exchange failed", message)
    }

    @Test
    @DisplayName("Should return proxy start failed message")
    fun testProxyStartFailed() {
        val message = ErrorMessages.proxyStartFailed("port already in use")
        assertEquals("[Proxy] Failed to start: port already in use", message)
    }

    @Test
    @DisplayName("Should return proxy port in use message")
    fun testProxyPortInUse() {
        val message = ErrorMessages.proxyPortInUse(8080)
        assertEquals("[Proxy] Port 8080 is already in use", message)
    }

    @Test
    @DisplayName("Should return proxy not running message")
    fun testProxyNotRunning() {
        val message = ErrorMessages.proxyNotRunning()
        assertEquals("[Proxy] Server is not running", message)
    }

    @Test
    @DisplayName("Should return failed to load data message")
    fun testFailedToLoadData() {
        val message = ErrorMessages.failedToLoadData("models")
        assertEquals("[OpenRouter] Failed to load models", message)
    }

    @Test
    @DisplayName("Should return failed to parse response message")
    fun testFailedToParseResponse() {
        val message = ErrorMessages.failedToParseResponse("credits")
        assertEquals("[OpenRouter] Failed to parse credits response", message)
    }

    @Test
    @DisplayName("Should return settings save failed message with reason")
    fun testSettingsSaveFailedWithReason() {
        val message = ErrorMessages.settingsSaveFailed("disk full")
        assertEquals("[Settings] Failed to save settings: disk full", message)
    }

    @Test
    @DisplayName("Should return settings save failed message without reason")
    fun testSettingsSaveFailedWithoutReason() {
        val message = ErrorMessages.settingsSaveFailed()
        assertEquals("[Settings] Failed to save settings", message)
    }

    @Test
    @DisplayName("Should return settings load failed message with reason")
    fun testSettingsLoadFailedWithReason() {
        val message = ErrorMessages.settingsLoadFailed("file not found")
        assertEquals("[Settings] Failed to load settings: file not found", message)
    }

    @Test
    @DisplayName("Should return settings load failed message without reason")
    fun testSettingsLoadFailedWithoutReason() {
        val message = ErrorMessages.settingsLoadFailed()
        assertEquals("[Settings] Failed to load settings", message)
    }

    @Test
    @DisplayName("Should return validation failed message")
    fun testValidationFailed() {
        val message = ErrorMessages.validationFailed("API key", "too short")
        assertEquals("[OpenRouter] Validation failed for API key: too short", message)
    }

    @Test
    @DisplayName("Should return unexpected error message with context and throwable")
    fun testUnexpectedErrorWithContextAndThrowable() {
        val exception = RuntimeException("Something went wrong")
        val message = ErrorMessages.unexpectedError("processing request", exception)
        assertEquals("[OpenRouter] Unexpected error during processing request: Something went wrong", message)
    }

    @Test
    @DisplayName("Should return unexpected error message with context only")
    fun testUnexpectedErrorWithContextOnly() {
        val message = ErrorMessages.unexpectedError("processing request")
        assertEquals("[OpenRouter] Unexpected error during processing request", message)
    }

    @Test
    @DisplayName("Should return unexpected error message without context")
    fun testUnexpectedErrorWithoutContext() {
        val message = ErrorMessages.unexpectedError()
        assertEquals("[OpenRouter] Unexpected error", message)
    }

    @Test
    @DisplayName("Should format message with details")
    fun testFormatWithDetails() {
        val message = ErrorMessages.format("Base message", "additional details")
        assertEquals("Base message: additional details", message)
    }

    @Test
    @DisplayName("Should format message without details")
    fun testFormatWithoutDetails() {
        val message = ErrorMessages.format("Base message")
        assertEquals("Base message", message)
    }
}
