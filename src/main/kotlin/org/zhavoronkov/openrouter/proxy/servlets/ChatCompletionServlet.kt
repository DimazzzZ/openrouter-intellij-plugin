package org.zhavoronkov.openrouter.proxy.servlets

import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import org.zhavoronkov.openrouter.proxy.models.OpenAIChatCompletionRequest
import org.zhavoronkov.openrouter.proxy.translation.RequestTranslator
import org.zhavoronkov.openrouter.proxy.translation.ResponseTranslator
import org.zhavoronkov.openrouter.services.OpenRouterService
import org.zhavoronkov.openrouter.utils.PluginLogger
import jakarta.servlet.http.HttpServlet
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import java.io.BufferedReader
import java.util.concurrent.TimeUnit

/**
 * Servlet that provides OpenAI-compatible /v1/chat/completions endpoint
 */
class ChatCompletionServlet(
    private val openRouterService: OpenRouterService
) : HttpServlet() {

    companion object {
        private const val REQUEST_TIMEOUT_SECONDS = 120L // Longer timeout for chat completions
        private const val MAX_REQUEST_SIZE = 1024 * 1024 // 1MB max request size
    }

    private val gson = Gson()

    override fun doPost(req: HttpServletRequest, resp: HttpServletResponse) {
        val startNs = System.nanoTime()
        try {
            // Request path + method diagnostics
            val requestURI = req.requestURI
            val servletPath = req.servletPath
            val pathInfo = req.pathInfo
            val method = req.method
            val contentType = req.contentType
            val contentLength = req.contentLength

            PluginLogger.Service.info("[Chat] Incoming $method $requestURI (servletPath=$servletPath, pathInfo=$pathInfo)")
            PluginLogger.Service.debug("[Chat] Content-Type=$contentType, Content-Length=$contentLength")

            // Log headers (mask Authorization)
            val headers = req.headerNames.asSequence().associateWith { name ->
                val value = req.getHeader(name)
                if (name.equals("Authorization", ignoreCase = true)) value?.let { it.take(12) + "…(redacted)" } else value
            }
            PluginLogger.Service.debug("[Chat] Headers: $headers")

            // Validate authorization header (strict OpenAI compatibility)
            val authHeader = req.getHeader("Authorization")
            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                PluginLogger.Service.warn("[Chat] Missing or invalid Authorization header")
                sendAuthErrorResponse(resp)
                return
            }

            val apiKey = authHeader.substring(7) // Remove "Bearer " prefix
            if (apiKey.isBlank()) {
                PluginLogger.Service.warn("[Chat] Empty API key provided")
                sendAuthErrorResponse(resp)
                return
            }

            // Read request body
            val rawBody = req.reader.use(BufferedReader::readText)
            if (rawBody.isBlank()) {
                PluginLogger.Service.warn("[Chat] Empty request body")
                sendErrorResponse(resp, "Empty request body", HttpServletResponse.SC_BAD_REQUEST)
                return
            }
            val bodyPreview = if (rawBody.length > 5000) rawBody.take(5000) + "…(truncated)" else rawBody
            PluginLogger.Service.debug("[Chat] Raw request body: $bodyPreview")

            // Parse OpenAI request
            val openAIRequest = try {
                gson.fromJson<OpenAIChatCompletionRequest>(rawBody, OpenAIChatCompletionRequest::class.java)
            } catch (e: JsonSyntaxException) {
                PluginLogger.Service.error("[Chat] Invalid JSON in request: ${e.message}", e)
                sendErrorResponse(resp, "Invalid JSON format", HttpServletResponse.SC_BAD_REQUEST)
                return
            }

            // Validate request
            if (openAIRequest.model.isBlank() || openAIRequest.messages.isEmpty()) {
                PluginLogger.Service.warn("[Chat] Missing required fields: model and messages")
                sendErrorResponse(resp, "Missing required fields: model and messages", HttpServletResponse.SC_BAD_REQUEST)
                return
            }

            PluginLogger.Service.info("[Chat] Processing request for model: ${openAIRequest.model} with ${openAIRequest.messages.size} messages")

            // Translate to OpenRouter format
            val openRouterRequest = RequestTranslator.translateChatCompletionRequest(openAIRequest)
            val translatedJson = gson.toJson(openRouterRequest)
            PluginLogger.Service.debug("[Chat] Translated OpenRouter request: $translatedJson")

            // Validate translated request
            if (!RequestTranslator.validateTranslatedRequest(openRouterRequest)) {
                PluginLogger.Service.warn("[Chat] Translated request validation failed")
                sendErrorResponse(resp, "Invalid request parameters", HttpServletResponse.SC_BAD_REQUEST)
                return
            }

            // Make request to OpenRouter
            PluginLogger.Service.info("[Chat] Dispatching request to OpenRouter API…")
            val openRouterResponse = openRouterService.createChatCompletion(openRouterRequest)
                .get(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS)

            if (openRouterResponse == null) {
                PluginLogger.Service.error("[Chat] OpenRouter returned null response")
                sendErrorResponse(resp, "Failed to get response from OpenRouter", HttpServletResponse.SC_SERVICE_UNAVAILABLE)
                return
            }

            // Translate response to OpenAI format
            val openAIResponse = ResponseTranslator.translateChatCompletionResponse(openRouterResponse)
            val openAIResponseJson = gson.toJson(openAIResponse)
            PluginLogger.Service.debug("[Chat] Translated OpenAI response: $openAIResponseJson")

            // Validate translated response
            if (!ResponseTranslator.validateTranslatedResponse(openAIResponse)) {
                PluginLogger.Service.error("[Chat] Response validation failed")
                sendErrorResponse(resp, "Invalid response format", HttpServletResponse.SC_INTERNAL_SERVER_ERROR)
                return
            }

            val durationMs = (System.nanoTime() - startNs) / 1_000_000
            PluginLogger.Service.info("[Chat] Chat completion successful in ${durationMs}ms, returning response")

            resp.contentType = "application/json"
            resp.status = HttpServletResponse.SC_OK
            resp.writer.write(openAIResponseJson)

        } catch (e: java.util.concurrent.TimeoutException) {
            PluginLogger.Service.error("[Chat] Chat completion request timed out: ${e.message}", e)
            sendErrorResponse(resp, "Request timed out", HttpServletResponse.SC_REQUEST_TIMEOUT)
        } catch (e: Exception) {
            PluginLogger.Service.error("[Chat] Chat completion request failed: ${e.message}", e)
            sendErrorResponse(resp, "Internal server error: ${e.message}", HttpServletResponse.SC_INTERNAL_SERVER_ERROR)
        }
    }

    override fun doOptions(req: HttpServletRequest, resp: HttpServletResponse) {
        // Handle CORS preflight requests
        resp.setHeader("Access-Control-Allow-Origin", "*")
        resp.setHeader("Access-Control-Allow-Methods", "POST, OPTIONS")
        resp.setHeader("Access-Control-Allow-Headers", "Content-Type, Authorization")
        resp.status = HttpServletResponse.SC_OK
    }

    private fun sendErrorResponse(resp: HttpServletResponse, message: String, statusCode: Int) {
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

    private fun sendAuthErrorResponse(resp: HttpServletResponse) {
        resp.status = HttpServletResponse.SC_UNAUTHORIZED
        resp.contentType = "application/json"

        val errorResponse = ResponseTranslator.createAuthErrorResponse()
        resp.writer.write(gson.toJson(errorResponse))
    }
}
