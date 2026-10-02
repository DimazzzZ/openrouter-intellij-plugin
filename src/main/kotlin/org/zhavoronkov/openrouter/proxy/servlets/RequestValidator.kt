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
    private val settingsService: OpenRouterSettingsService,
    private val clock: () -> Long = System::currentTimeMillis
) {

    companion object {
        private const val DUPLICATE_WINDOW_MS = 1000L
        private val recentRequests = mutableMapOf<String, Long>()
    }

    fun validateAndGetApiKey(resp: HttpServletResponse, requestId: String): String? {
        val apiKey = settingsService.apiKeyManager.getStoredApiKey()

        if (apiKey.isNullOrBlank()) {
            PluginLogger.Service.error("[Chat-$requestId] No API key configured")
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

        PluginLogger.Service.debug("[Chat-$requestId] API key validated successfully")
        return apiKey
    }

    /**
     * Logs a request the same sender sent with the same body moments ago, and returns whether it was
     * one. The request is let through either way: the warning is for diagnosing a tool that retries.
     */
    fun checkForDuplicateRequest(requestBody: String, req: HttpServletRequest, requestId: String): Boolean {
        val requestHash = generateRequestHash(requestBody, req.remoteAddr)
        val now = clock()

        return synchronized(recentRequests) {
            val lastRequestTime = recentRequests[requestHash]
            val duplicate = lastRequestTime != null && (now - lastRequestTime) < DUPLICATE_WINDOW_MS
            if (duplicate) {
                PluginLogger.Service.warn(
                    "[$requestId] Potential duplicate request detected (hash: $requestHash, " +
                        "time since last: ${now - lastRequestTime}ms)"
                )
            }
            recentRequests[requestHash] = now

            recentRequests.entries.removeIf { (now - it.value) > DUPLICATE_WINDOW_MS * 2 }
            duplicate
        }
    }

    private fun generateRequestHash(requestBody: String, remoteAddr: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest("$requestBody|$remoteAddr".toByteArray())
        return hashBytes.joinToString("") { "%02x".format(it) }
    }

    /**
     * Clear the recent requests cache
     * Called during plugin unload to prevent memory leaks
     */
    fun clearRecentRequests() {
        synchronized(recentRequests) {
            recentRequests.clear()
        }
        PluginLogger.Service.debug("Cleared RequestValidator recent requests cache")
    }
}
