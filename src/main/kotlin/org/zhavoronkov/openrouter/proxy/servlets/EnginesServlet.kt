package org.zhavoronkov.openrouter.proxy.servlets

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.zhavoronkov.openrouter.utils.PluginLogger

/**
 * Servlet that handles OpenAI engines endpoint (/v1/engines)
 * Legacy endpoint for OpenAI API compatibility
 */
class EnginesServlet : OpenAIBaseServlet() {

    override fun doGet(req: HttpServletRequest, resp: HttpServletResponse) {
        handleRequest({
            PluginLogger.Service.debug("Engines endpoint called")

            // Validate authorization and extract API key
            val apiKey = validateAndExtractApiKey(resp, req) ?: return@handleRequest

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
                    "created" to 1687882411
                ),
                mapOf(
                    "id" to "gpt-4-turbo",
                    "object" to "engine",
                    "owner" to "openai",
                    "ready" to true,
                    "created" to 1712361441
                ),
                mapOf(
                    "id" to "gpt-3.5-turbo",
                    "object" to "engine",
                    "owner" to "openai",
                    "ready" to true,
                    "created" to 1677610602
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
