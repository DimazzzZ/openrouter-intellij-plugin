package org.zhavoronkov.openrouter.proxy.servlets

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.zhavoronkov.openrouter.services.OpenRouterSettingsService
import org.zhavoronkov.openrouter.services.settings.ApiKeySettingsManager
import java.io.PrintWriter
import java.io.StringWriter

@DisplayName("RequestValidator Tests")
class RequestValidatorTest {

    private fun validatorWithKey(apiKey: String?): RequestValidator {
        val settingsService = mock(OpenRouterSettingsService::class.java)
        val apiKeyManager = mock(ApiKeySettingsManager::class.java)
        `when`(apiKeyManager.getStoredApiKey()).thenReturn(apiKey)
        `when`(settingsService.apiKeyManager).thenReturn(apiKeyManager)
        return RequestValidator(settingsService)
    }

    @Test
    fun `validateAndGetApiKey should return null and write 401 when missing`() {
        val validator = validatorWithKey(null)
        val resp = mock(HttpServletResponse::class.java)
        val writer = StringWriter()
        `when`(resp.writer).thenReturn(PrintWriter(writer))

        val result = validator.validateAndGetApiKey(resp, "req-1")

        assertNull(result)
        verify(resp).status = HttpServletResponse.SC_UNAUTHORIZED
        assertTrue(writer.toString().contains("api_key_missing"))
        assertTrue(writer.toString().contains("authentication_error"))
    }

    @Test
    fun `validateAndGetApiKey should return null when blank`() {
        val validator = validatorWithKey("   ")
        val resp = mock(HttpServletResponse::class.java)
        val writer = StringWriter()
        `when`(resp.writer).thenReturn(PrintWriter(writer))

        val result = validator.validateAndGetApiKey(resp, "req-blank")

        assertNull(result)
        verify(resp).status = HttpServletResponse.SC_UNAUTHORIZED
    }

    @Test
    fun `validateAndGetApiKey should return key when configured`() {
        val validator = validatorWithKey("sk-or-test")
        val resp = mock(HttpServletResponse::class.java)

        val result = validator.validateAndGetApiKey(resp, "req-2")

        assertEquals("sk-or-test", result)
    }

    @Test
    fun `checkForDuplicateRequest should record and detect duplicate within window`() {
        val validator = validatorWithKey("sk-or-test")
        val req = mock(HttpServletRequest::class.java)
        `when`(req.remoteAddr).thenReturn("127.0.0.1")
        validator.clearRecentRequests()

        // First request records the hash; second within window hits the duplicate branch.
        validator.checkForDuplicateRequest("{\"body\":1}", req, "req-3")
        validator.checkForDuplicateRequest("{\"body\":1}", req, "req-3")
    }

    @Test
    fun `checkForDuplicateRequest should handle distinct requests`() {
        val validator = validatorWithKey("sk-or-test")
        val req = mock(HttpServletRequest::class.java)
        `when`(req.remoteAddr).thenReturn("10.0.0.1")
        validator.clearRecentRequests()

        validator.checkForDuplicateRequest("{\"a\":1}", req, "req-4")
        validator.checkForDuplicateRequest("{\"b\":2}", req, "req-4")
    }

    @Test
    fun `clearRecentRequests should empty the cache`() {
        val validator = validatorWithKey("sk-or-test")
        val req = mock(HttpServletRequest::class.java)
        `when`(req.remoteAddr).thenReturn("192.168.1.1")
        validator.checkForDuplicateRequest("{\"x\":1}", req, "req-5")

        validator.clearRecentRequests()
    }
}
