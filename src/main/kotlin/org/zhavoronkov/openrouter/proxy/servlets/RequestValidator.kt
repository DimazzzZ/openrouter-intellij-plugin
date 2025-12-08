package org.zhavoronkov.openrouter.proxy.servlets

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.zhavoronkov.openrouter.services.OpenRouterSettingsService
import org.zhavoronkov.openrouter.utils.PluginLogger
import java.security.MessageDigest

/**
 * Handles request validation and duplicate detection
 */
class RequestValidator(
    private val settingsService: OpenRouterSettingsService
) {

    companion object {
        private const val DUPLICATE_WINDOW_MS = 1000L
        private val recentRequests = mutableMapOf<String, Long>()
    }

    fun validateAndGetApiKey(resp: HttpServletResponse, requestId: String): String? {
        val apiKey = settingsService.apiKeyManager.getStoredApiKey()

        if (apiKey.isNullOrBlank()) {
            PluginLogger.Service.error("[$requestId] No API key configured")
            resp.status = HttpServletResponse.SC_UNAUTHORIZED
            resp.contentType = "application/json"
            val errorMessage = "API key not configured. " +
                "Please configure your OpenRouter API key in the plugin settings."
            val errorJson =
                """
                {"error": {
                    "message": "$errorMessage",
                    "type": "authentication_error",
                    "code": "api_key_missing"
                }}
                """.trimIndent().replace("\n", "")
            resp.writer.write(errorJson)
            return null
        }

        PluginLogger.Service.debug("[$requestId] API key validated successfully")
        return apiKey
    }

    fun checkForDuplicateRequest(requestBody: String, req: HttpServletRequest, requestId: String) {
        val requestHash = generateRequestHash(requestBody, req.remoteAddr)
        val now = System.currentTimeMillis()

        synchronized(recentRequests) {
            val lastRequestTime = recentRequests[requestHash]
            if (lastRequestTime != null && (now - lastRequestTime) < DUPLICATE_WINDOW_MS) {
                PluginLogger.Service.warn(
                    "[$requestId] Potential duplicate request detected (hash: $requestHash, " +
                        "time since last: ${now - lastRequestTime}ms)"
                )
            }
            recentRequests[requestHash] = now

            recentRequests.entries.removeIf { (now - it.value) > DUPLICATE_WINDOW_MS * 2 }
        }
    }

    private fun generateRequestHash(requestBody: String, remoteAddr: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest("$requestBody|$remoteAddr".toByteArray())
        return hashBytes.joinToString("") { "%02x".format(it) }
    }
}
