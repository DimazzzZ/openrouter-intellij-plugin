package org.zhavoronkov.openrouter.ui

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.zhavoronkov.openrouter.models.ApiResult
import java.net.BindException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

@DisplayName("SetupWizardErrorHandler Tests")
class SetupWizardErrorHandlerTest {

    @Nested
    @DisplayName("handleValidationError")
    inner class HandleValidationErrorTests {

        @Test
        fun `handles Missing Authentication header error`() {
            val error = ApiResult.Error("Missing Authentication header", 401)
            val result = SetupWizardErrorHandler.handleValidationError(error)
            assertEquals("Invalid key format or type", result)
        }

        @Test
        fun `handles Invalid key format error`() {
            val error = ApiResult.Error("Invalid key format", 400)
            val result = SetupWizardErrorHandler.handleValidationError(error)
            assertEquals("Invalid key format or type", result)
        }

        @Test
        fun `handles Invalid provisioningkey error`() {
            val error = ApiResult.Error("Invalid provisioningkey provided", 401)
            val result = SetupWizardErrorHandler.handleValidationError(error)
            assertEquals("Invalid provisioning key", result)
        }

        @Test
        fun `handles No cookie auth error`() {
            val error = ApiResult.Error("No cookie auth found", 401)
            val result = SetupWizardErrorHandler.handleValidationError(error)
            assertEquals("Invalid API key", result)
        }

        @Test
        fun `handles Authentication failed error`() {
            val error = ApiResult.Error("Authentication failed", 401)
            val result = SetupWizardErrorHandler.handleValidationError(error)
            assertEquals("Invalid API key", result)
        }

        @Test
        fun `handles Network error`() {
            val error = ApiResult.Error("Network error occurred", 0)
            val result = SetupWizardErrorHandler.handleValidationError(error)
            assertEquals("Network error: Unable to connect to OpenRouter", result)
        }

        @Test
        fun `handles Unable to reach error`() {
            val error = ApiResult.Error("Unable to reach server", 0)
            val result = SetupWizardErrorHandler.handleValidationError(error)
            assertEquals("Network error: Unable to connect to OpenRouter", result)
        }

        @Test
        fun `handles Connection refused error`() {
            val error = ApiResult.Error("Connection refused", 0)
            val result = SetupWizardErrorHandler.handleValidationError(error)
            assertEquals("Network error: Unable to connect to OpenRouter", result)
        }

        @Test
        fun `handles unknown error with original message`() {
            val error = ApiResult.Error("Some unknown error", 500)
            val result = SetupWizardErrorHandler.handleValidationError(error)
            assertTrue(result.contains("Some unknown error"))
        }
    }

    @Nested
    @DisplayName("handleNetworkError")
    inner class HandleNetworkErrorTests {

        @Test
        fun `handles UnknownHostException`() {
            val error = UnknownHostException("openrouter.ai")
            val result = SetupWizardErrorHandler.handleNetworkError(error, "test")
            assertTrue(result.contains("offline") || result.contains("DNS"))
        }

        @Test
        fun `handles SocketTimeoutException`() {
            val error = SocketTimeoutException("Read timed out")
            val result = SetupWizardErrorHandler.handleNetworkError(error, "test")
            assertTrue(result.contains("timed out"))
        }

        @Test
        fun `handles ConnectException`() {
            val error = ConnectException("Connection refused")
            val result = SetupWizardErrorHandler.handleNetworkError(error, "test")
            assertTrue(result.contains("refused") || result.contains("down"))
        }

        @Test
        fun `handles generic exception`() {
            val error = RuntimeException("Some network error")
            val result = SetupWizardErrorHandler.handleNetworkError(error, "test")
            assertTrue(result.contains("Network error"))
        }

        @Test
        fun `handles exception with null message`() {
            val error = RuntimeException()
            val result = SetupWizardErrorHandler.handleNetworkError(error, "test")
            assertNotNull(result)
        }
    }

    @Nested
    @DisplayName("handlePkceError")
    inner class HandlePkceErrorTests {

        @Test
        fun `handles BindException`() {
            val error = BindException("Address already in use")
            val result = SetupWizardErrorHandler.handlePkceError(error, "test")
            assertTrue(result.contains("Port"))
            assertTrue(result.contains("in use"))
        }

        @Test
        fun `handles generic exception`() {
            val error = RuntimeException("PKCE flow failed")
            val result = SetupWizardErrorHandler.handlePkceError(error, "test")
            assertTrue(result.contains("Authentication failed"))
        }

        @Test
        fun `handles exception with null message`() {
            val error = RuntimeException()
            val result = SetupWizardErrorHandler.handlePkceError(error, "test")
            assertTrue(result.contains("Authentication failed"))
        }
    }

    @Nested
    @DisplayName("handleModelLoadingError")
    inner class HandleModelLoadingErrorTests {

        @Test
        fun `returns user-friendly message`() {
            val error = RuntimeException("API error")
            val result = SetupWizardErrorHandler.handleModelLoadingError(error)
            assertTrue(result.contains("Failed to load models"))
        }

        @Test
        fun `handles different exception types`() {
            val error = java.io.IOException("Network issue")
            val result = SetupWizardErrorHandler.handleModelLoadingError(error)
            assertNotNull(result)
        }
    }

    @Nested
    @DisplayName("createValidationError")
    inner class CreateValidationErrorTests {

        @Test
        fun `creates error with friendly message`() {
            val originalError = ApiResult.Error("Missing Authentication header", 401)
            val result = SetupWizardErrorHandler.createValidationError(originalError)
            
            assertEquals("Invalid key format or type", result.message)
            assertEquals(401, result.statusCode)
        }

        @Test
        fun `preserves status code from original error`() {
            val originalError = ApiResult.Error("Network error", 503)
            val result = SetupWizardErrorHandler.createValidationError(originalError)
            
            assertEquals(503, result.statusCode)
        }

        @Test
        fun `preserves throwable from original error`() {
            val throwable = RuntimeException("Original cause")
            val originalError = ApiResult.Error("Error", 500, throwable)
            val result = SetupWizardErrorHandler.createValidationError(originalError)
            
            assertEquals(throwable, result.throwable)
        }
    }
}
