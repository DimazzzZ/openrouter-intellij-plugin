package org.zhavoronkov.openrouter.proxy.servlets

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.zhavoronkov.openrouter.utils.PluginLogger

/**
 * Servlet that handles OpenAI engines endpoint (/v1/engines)
 * Legacy endpoint for OpenAI API compatibility
 */
class EnginesServlet : OpenAIBaseServlet() {

    companion object {
        // Unix timestamps for model creation dates
        private const val GPT4_CREATED_TIMESTAMP = 1687882411L
        private const val GPT4_TURBO_CREATED_TIMESTAMP = 1712361441L
        private const val GPT35_TURBO_CREATED_TIMESTAMP = 1677610602L
    }

    override fun doGet(req: HttpServletRequest, resp: HttpServletResponse) {
        handleRequest({
            PluginLogger.Service.debug("Engines endpoint called")

            // Validate authorization
            validateAndExtractApiKey(resp, req) ?: return@handleRequest

            // Set response headers
            resp.contentType = "application/json"
            setCORSHeaders(resp, "GET, OPTIONS")

            // Create engines response (legacy OpenAI format)
            val engines = listOf(
                mapOf(
                    "id" to "gpt-4",
                    "object" to "engine",
                    "owner" to "openai",
                    "ready" to true,
                    "created" to GPT4_CREATED_TIMESTAMP
                ),
                mapOf(
                    "id" to "gpt-4-turbo",
                    "object" to "engine",
                    "owner" to "openai",
                    "ready" to true,
                    "created" to GPT4_TURBO_CREATED_TIMESTAMP
                ),
                mapOf(
                    "id" to "gpt-3.5-turbo",
                    "object" to "engine",
                    "owner" to "openai",
                    "ready" to true,
                    "created" to GPT35_TURBO_CREATED_TIMESTAMP
                )
            )

            val enginesResponse = mapOf(
                "object" to "list",
                "data" to engines
            )

            resp.writer.write(gson.toJson(enginesResponse))
            PluginLogger.Service.debug("Engines response sent successfully")
        }, resp, "engines")
    }

    override fun doOptions(req: HttpServletRequest, resp: HttpServletResponse) {
        handleOptionsRequest(resp, "GET, OPTIONS")
    }
}
