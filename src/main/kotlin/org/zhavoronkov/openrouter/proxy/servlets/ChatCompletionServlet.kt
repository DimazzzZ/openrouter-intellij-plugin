package org.zhavoronkov.openrouter.proxy.servlets

import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import org.zhavoronkov.openrouter.models.ChatCompletionRequest
import org.zhavoronkov.openrouter.models.ChatCompletionResponse
import org.zhavoronkov.openrouter.proxy.models.OpenAIChatCompletionRequest
import org.zhavoronkov.openrouter.proxy.models.OpenAIChatCompletionResponse
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
        private const val REQUEST_TIMEOUT_SECONDS = 120L
        private const val BODY_PREVIEW_MAX_LENGTH = 5000
        private const val AUTH_HEADER_PREFIX_LENGTH = 7
        private const val NANOSECONDS_TO_MILLISECONDS = 1_000_000L
    }

    private val gson = Gson()

    override fun doPost(req: HttpServletRequest, resp: HttpServletResponse) {
        val startNs = System.nanoTime()
        try {
            logRequestDiagnostics(req)

            val apiKey = validateAndExtractApiKey(req, resp) ?: return
            val openAIRequest = parseAndValidateRequest(req, resp) ?: return
            val openRouterRequest = translateRequest(openAIRequest, resp) ?: return
            val openRouterResponse = executeOpenRouterRequest(openRouterRequest, resp) ?: return
            val openAIResponse = translateResponse(openRouterResponse, openAIRequest.model, resp) ?: return

            sendSuccessResponse(resp, openAIResponse, startNs)

        } catch (e: java.util.concurrent.TimeoutException) {
            PluginLogger.Service.error("[Chat] Chat completion request timed out: ${e.message}", e)
            sendErrorResponse(resp, "Request timed out", HttpServletResponse.SC_REQUEST_TIMEOUT)
        } catch (e: java.util.concurrent.ExecutionException) {
            PluginLogger.Service.error("[Chat] Chat completion execution failed: ${e.message}", e)
            sendErrorResponse(resp, "Execution error: ${e.message}", HttpServletResponse.SC_INTERNAL_SERVER_ERROR)
        } catch (e: java.io.IOException) {
            PluginLogger.Service.error("[Chat] IO error during chat completion: ${e.message}", e)
            sendErrorResponse(resp, "Network error: ${e.message}", HttpServletResponse.SC_SERVICE_UNAVAILABLE)
        } catch (e: IllegalArgumentException) {
            PluginLogger.Service.error("[Chat] Invalid argument in chat completion: ${e.message}", e)
            sendErrorResponse(resp, "Invalid request: ${e.message}", HttpServletResponse.SC_BAD_REQUEST)
        } catch (e: RuntimeException) {
            PluginLogger.Service.error("[Chat] Runtime error during chat completion: ${e.message}", e)
            sendErrorResponse(resp, "Internal server error: ${e.message}", HttpServletResponse.SC_INTERNAL_SERVER_ERROR)
        }
    }

    private fun logRequestDiagnostics(req: HttpServletRequest) {
        val requestURI = req.requestURI
        val servletPath = req.servletPath
        val pathInfo = req.pathInfo
        val method = req.method
        val contentType = req.contentType
        val contentLength = req.contentLength

        PluginLogger.Service.info("[Chat] Incoming $method $requestURI (servletPath=$servletPath, pathInfo=$pathInfo)")
        PluginLogger.Service.debug("[Chat] Content-Type=$contentType, Content-Length=$contentLength")

        val headers = req.headerNames.asSequence().associateWith { name ->
            val value = req.getHeader(name)
            if (name.equals("Authorization", ignoreCase = true)) {
                value?.let { it.take(12) + "…(redacted)" }
            } else {
                value
            }
        }
        PluginLogger.Service.debug("[Chat] Headers: $headers")
    }

    private fun validateAndExtractApiKey(req: HttpServletRequest, resp: HttpServletResponse): String? {
        val authHeader = req.getHeader("Authorization")
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            PluginLogger.Service.warn("[Chat] Missing or invalid Authorization header")
            sendAuthErrorResponse(resp)
            return null
        }

        val apiKey = authHeader.substring(AUTH_HEADER_PREFIX_LENGTH)
        if (apiKey.isBlank()) {
            PluginLogger.Service.warn("[Chat] Empty API key provided")
            sendAuthErrorResponse(resp)
            return null
        }

        return apiKey
    }

    private fun parseAndValidateRequest(req: HttpServletRequest, resp: HttpServletResponse): OpenAIChatCompletionRequest? {
        val rawBody = req.reader.use(BufferedReader::readText)
        if (rawBody.isBlank()) {
            PluginLogger.Service.warn("[Chat] Empty request body")
            sendErrorResponse(resp, "Empty request body", HttpServletResponse.SC_BAD_REQUEST)
            return null
        }

        val bodyPreview = if (rawBody.length > BODY_PREVIEW_MAX_LENGTH) {
            rawBody.take(BODY_PREVIEW_MAX_LENGTH) + "…(truncated)"
        } else {
            rawBody
        }
        PluginLogger.Service.debug("[Chat] Raw request body: $bodyPreview")

        val openAIRequest = try {
            gson.fromJson<OpenAIChatCompletionRequest>(rawBody, OpenAIChatCompletionRequest::class.java)
        } catch (e: JsonSyntaxException) {
            PluginLogger.Service.error("[Chat] Invalid JSON in request: ${e.message}", e)
            sendErrorResponse(resp, "Invalid JSON format", HttpServletResponse.SC_BAD_REQUEST)
            return null
        }

        if (openAIRequest.model.isBlank() || openAIRequest.messages.isEmpty()) {
            PluginLogger.Service.warn("[Chat] Missing required fields: model and messages")
            sendErrorResponse(resp, "Missing required fields: model and messages", HttpServletResponse.SC_BAD_REQUEST)
            return null
        }

        PluginLogger.Service.info("[Chat] Processing request for model: ${openAIRequest.model} with ${openAIRequest.messages.size} messages")
        return openAIRequest
    }

    private fun translateRequest(openAIRequest: OpenAIChatCompletionRequest, resp: HttpServletResponse): ChatCompletionRequest? {
        val openRouterRequest = RequestTranslator.translateChatCompletionRequest(openAIRequest)
        val translatedJson = gson.toJson(openRouterRequest)
        PluginLogger.Service.debug("[Chat] Translated OpenRouter request: $translatedJson")

        if (!RequestTranslator.validateTranslatedRequest(openRouterRequest)) {
            PluginLogger.Service.warn("[Chat] Translated request validation failed")
            sendErrorResponse(resp, "Invalid request parameters", HttpServletResponse.SC_BAD_REQUEST)
            return null
        }

        return openRouterRequest
    }

    private fun executeOpenRouterRequest(openRouterRequest: ChatCompletionRequest, resp: HttpServletResponse): ChatCompletionResponse? {
        PluginLogger.Service.info("[Chat] Dispatching request to OpenRouter API…")
        val openRouterResponse = openRouterService.createChatCompletion(openRouterRequest)
            .get(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS)

        if (openRouterResponse == null) {
            PluginLogger.Service.error("[Chat] OpenRouter returned null response")
            sendErrorResponse(resp, "Failed to get response from OpenRouter", HttpServletResponse.SC_SERVICE_UNAVAILABLE)
            return null
        }

        return openRouterResponse
    }

    private fun translateResponse(
        openRouterResponse: ChatCompletionResponse,
        originalModel: String,
        resp: HttpServletResponse
    ): OpenAIChatCompletionResponse? {
        val openAIResponse = ResponseTranslator.translateChatCompletionResponse(
            openRouterResponse,
            originalModel
        )
        val openAIResponseJson = gson.toJson(openAIResponse)
        PluginLogger.Service.debug("[Chat] Translated OpenAI response: $openAIResponseJson")

        if (!ResponseTranslator.validateTranslatedResponse(openAIResponse)) {
            PluginLogger.Service.error("[Chat] Response validation failed")
            sendErrorResponse(resp, "Invalid response format", HttpServletResponse.SC_INTERNAL_SERVER_ERROR)
            return null
        }

        return openAIResponse
    }

    private fun sendSuccessResponse(resp: HttpServletResponse, openAIResponse: OpenAIChatCompletionResponse, startNs: Long) {
        val durationMs = (System.nanoTime() - startNs) / NANOSECONDS_TO_MILLISECONDS
        PluginLogger.Service.info("[Chat] Chat completion successful in ${durationMs}ms, returning response")

        resp.contentType = "application/json"
        resp.status = HttpServletResponse.SC_OK
        resp.writer.write(gson.toJson(openAIResponse))
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
