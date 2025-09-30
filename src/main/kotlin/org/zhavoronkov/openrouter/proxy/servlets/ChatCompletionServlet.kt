package org.zhavoronkov.openrouter.proxy.servlets

import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.zhavoronkov.openrouter.models.ChatCompletionRequest
import org.zhavoronkov.openrouter.models.ChatCompletionResponse
import org.zhavoronkov.openrouter.proxy.models.OpenAIChatCompletionRequest
import org.zhavoronkov.openrouter.proxy.models.OpenAIChatCompletionResponse
import org.zhavoronkov.openrouter.proxy.translation.RequestTranslator
import org.zhavoronkov.openrouter.proxy.translation.ResponseTranslator
import org.zhavoronkov.openrouter.services.OpenRouterService
import org.zhavoronkov.openrouter.services.OpenRouterSettingsService
import org.zhavoronkov.openrouter.utils.PluginLogger
import jakarta.servlet.http.HttpServlet
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import java.io.BufferedReader
import java.util.concurrent.TimeUnit

/**
 * Servlet that provides OpenAI-compatible /v1/chat/completions endpoint
 * with full streaming support
 */
class ChatCompletionServlet(
    private val openRouterService: OpenRouterService
) : HttpServlet() {

    companion object {
        private const val REQUEST_TIMEOUT_SECONDS = 120L
        private const val BODY_PREVIEW_MAX_LENGTH = 5000
        private const val AUTH_HEADER_PREFIX_LENGTH = 7
        private const val NANOSECONDS_TO_MILLISECONDS = 1_000_000L
        private const val OPENROUTER_API_URL = "https://openrouter.ai/api/v1/chat/completions"

        // Request tracking for debugging duplicate requests
        @Volatile
        private var requestCounter = 0
    }

    private val gson = Gson()
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()
    private val settingsService = OpenRouterSettingsService.getInstance()

    override fun doPost(req: HttpServletRequest, resp: HttpServletResponse) {
        val requestId = (++requestCounter).toString().padStart(6, '0')
        val startNs = System.nanoTime()
        val timestamp = System.currentTimeMillis()

        PluginLogger.Service.info("═══════════════════════════════════════════════════════")
        PluginLogger.Service.info("[Chat-$requestId] NEW CHAT COMPLETION REQUEST RECEIVED")
        PluginLogger.Service.info("[Chat-$requestId] Timestamp: $timestamp")
        PluginLogger.Service.info("[Chat-$requestId] Total chat requests so far: $requestCounter")
        PluginLogger.Service.info("═══════════════════════════════════════════════════════")

        try {
            logRequestDiagnostics(req, requestId)

            // Use the API key from OpenRouter plugin settings, not from the request
            // AI Assistant should be configured with empty API key field
            val apiKey = settingsService.getApiKey()
            if (apiKey.isBlank()) {
                PluginLogger.Service.error("[Chat-$requestId] ❌ No API key configured in OpenRouter plugin settings")
                sendErrorResponse(resp, "OpenRouter API key not configured. Please configure it in Settings > Tools > OpenRouter", HttpServletResponse.SC_UNAUTHORIZED)
                return
            }

            PluginLogger.Service.info("[Chat-$requestId] 🔑 Using API key from plugin settings: ${apiKey.take(20)}... (length: ${apiKey.length})")

            val openAIRequest = parseAndValidateRequest(req, resp, requestId) ?: return

            PluginLogger.Service.info("[Chat-$requestId] 📝 Model: '${openAIRequest.model}'")

            // Check if streaming is requested
            if (openAIRequest.stream == true) {
                PluginLogger.Service.info("[Chat-$requestId] 🌊 STREAMING requested - handling SSE response")
                handleStreamingRequest(resp, openAIRequest, apiKey, requestId)
            } else {
                PluginLogger.Service.info("[Chat-$requestId] 📦 NON-STREAMING request - handling standard response")
                handleNonStreamingRequest(resp, openAIRequest, apiKey, requestId, startNs)
            }

        } catch (e: java.util.concurrent.TimeoutException) {
            PluginLogger.Service.error("[Chat-$requestId] Chat completion request timed out: ${e.message}", e)
            sendErrorResponse(resp, "Request timed out", HttpServletResponse.SC_REQUEST_TIMEOUT)
        } catch (e: java.util.concurrent.ExecutionException) {
            PluginLogger.Service.error("[Chat-$requestId] Chat completion execution failed: ${e.message}", e)
            sendErrorResponse(resp, "Execution error: ${e.message}", HttpServletResponse.SC_INTERNAL_SERVER_ERROR)
        } catch (e: java.io.IOException) {
            PluginLogger.Service.error("[Chat-$requestId] IO error during chat completion: ${e.message}", e)
            sendErrorResponse(resp, "Network error: ${e.message}", HttpServletResponse.SC_SERVICE_UNAVAILABLE)
        } catch (e: IllegalArgumentException) {
            PluginLogger.Service.error("[Chat-$requestId] Invalid argument in chat completion: ${e.message}", e)
            sendErrorResponse(resp, "Invalid request: ${e.message}", HttpServletResponse.SC_BAD_REQUEST)
        } catch (e: RuntimeException) {
            PluginLogger.Service.error("[Chat-$requestId] Runtime error during chat completion: ${e.message}", e)
            sendErrorResponse(resp, "Internal server error: ${e.message}", HttpServletResponse.SC_INTERNAL_SERVER_ERROR)
        } finally {
            PluginLogger.Service.info("[Chat-$requestId] REQUEST COMPLETE")
            PluginLogger.Service.info("═══════════════════════════════════════════════════════")
        }
    }

    /**
     * Handles streaming requests using Server-Sent Events (SSE)
     */
    private fun handleStreamingRequest(
        resp: HttpServletResponse,
        openAIRequest: OpenAIChatCompletionRequest,
        apiKey: String,
        requestId: String
    ) {
        PluginLogger.Service.info("[Chat-$requestId] Setting up SSE stream...")

        // Set SSE headers
        resp.contentType = "text/event-stream"
        resp.characterEncoding = "UTF-8"
        resp.setHeader("Cache-Control", "no-cache")
        resp.setHeader("Connection", "keep-alive")
        resp.setHeader("X-Accel-Buffering", "no") // Disable nginx buffering
        resp.status = HttpServletResponse.SC_OK

        val writer = resp.writer
        writer.flush() // Flush headers immediately

        try {
            // Convert OpenAI request to OpenRouter format
            val openRouterRequest = RequestTranslator.translateChatCompletionRequest(openAIRequest)
            val jsonBody = gson.toJson(openRouterRequest)

            PluginLogger.Service.debug("[Chat-$requestId] Streaming request body: ${jsonBody.take(500)}...")

            // Create HTTP request to OpenRouter
            val request = Request.Builder()
                .url(OPENROUTER_API_URL)
                .post(jsonBody.toRequestBody("application/json".toMediaType()))
                .addHeader("Authorization", "Bearer $apiKey")
                .addHeader("Content-Type", "application/json")
                .addHeader("HTTP-Referer", "https://github.com/openrouter-intellij-plugin")
                .addHeader("X-Title", "OpenRouter IntelliJ Plugin")
                .build()

            // Execute streaming request
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val errorBody = response.body?.string() ?: "Unknown error"
                    PluginLogger.Service.error("[Chat-$requestId] ❌ OpenRouter returned error: ${response.code}")
                    PluginLogger.Service.error("[Chat-$requestId] ❌ Error details: $errorBody")
                    PluginLogger.Service.error("[Chat-$requestId] ❌ Request URL: ${request.url}")
                    PluginLogger.Service.error("[Chat-$requestId] ❌ Request body: ${jsonBody.take(500)}")
                    PluginLogger.Service.error("[Chat-$requestId] ❌ API key length: ${apiKey.length}, prefix: ${apiKey.take(15)}...")
                    writer.write("data: ${gson.toJson(mapOf("error" to mapOf("message" to errorBody)))}\n\n")
                    writer.flush()
                    return
                }

                PluginLogger.Service.info("[Chat-$requestId] Streaming response from OpenRouter...")

                // Stream the response line by line
                response.body?.byteStream()?.bufferedReader()?.use { reader ->
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        val currentLine = line ?: continue
                        
                        // Forward SSE lines directly to client
                        if (currentLine.startsWith("data: ") || currentLine.isBlank()) {
                            writer.write(currentLine + "\n")
                            writer.flush()
                            
                            if (currentLine == "data: [DONE]") {
                                PluginLogger.Service.info("[Chat-$requestId] ✅ Stream completed successfully")
                                break
                            }
                        }
                    }
                }
            }

        } catch (e: Exception) {
            PluginLogger.Service.error("[Chat-$requestId] Streaming error: ${e.message}", e)
            writer.write("data: ${gson.toJson(mapOf("error" to mapOf("message" to e.message)))}\n\n")
            writer.flush()
        }
    }

    /**
     * Handles non-streaming requests (standard JSON response)
     */
    private fun handleNonStreamingRequest(
        resp: HttpServletResponse,
        openAIRequest: OpenAIChatCompletionRequest,
        apiKey: String,
        requestId: String,
        startNs: Long
    ) {
        val openRouterRequest = translateRequest(openAIRequest, resp, requestId) ?: return
        val openRouterResponse = executeOpenRouterRequest(openRouterRequest, apiKey, resp, requestId) ?: return
        val openAIResponse = translateResponse(openRouterResponse, openAIRequest.model, resp, requestId) ?: return

        sendSuccessResponse(resp, openAIResponse, startNs, requestId)
    }

    private fun logRequestDiagnostics(req: HttpServletRequest, requestId: String) {
        val requestURI = req.requestURI
        val servletPath = req.servletPath
        val pathInfo = req.pathInfo
        val method = req.method
        val contentType = req.contentType
        val contentLength = req.contentLength

        PluginLogger.Service.info("[Chat-$requestId] Incoming $method $requestURI (servletPath=$servletPath, pathInfo=$pathInfo)")
        PluginLogger.Service.debug("[Chat-$requestId] Content-Type=$contentType, Content-Length=$contentLength")

        val headers = req.headerNames.asSequence().associateWith { name ->
            val value = req.getHeader(name)
            if (name.equals("Authorization", ignoreCase = true)) {
                value?.let { it.take(12) + "…(redacted)" }
            } else {
                value
            }
        }
        PluginLogger.Service.debug("[Chat-$requestId] Headers: $headers")
    }

    private fun validateAndExtractApiKey(req: HttpServletRequest, resp: HttpServletResponse, requestId: String): String? {
        val authHeader = req.getHeader("Authorization")
        if (authHeader.isNullOrBlank() || !authHeader.startsWith("Bearer ")) {
            PluginLogger.Service.warn("[Chat-$requestId] Missing or invalid Authorization header")
            sendErrorResponse(resp, "Missing or invalid Authorization header", HttpServletResponse.SC_UNAUTHORIZED)
            return null
        }

        val apiKey = authHeader.substring(AUTH_HEADER_PREFIX_LENGTH).trim()
        if (apiKey.isBlank()) {
            PluginLogger.Service.warn("[Chat-$requestId] Empty API key in Authorization header")
            sendErrorResponse(resp, "Empty API key", HttpServletResponse.SC_UNAUTHORIZED)
            return null
        }

        PluginLogger.Service.info("[Chat-$requestId] 🔑 API key extracted: ${apiKey.take(20)}... (length: ${apiKey.length})")
        return apiKey
    }

    private fun parseAndValidateRequest(req: HttpServletRequest, resp: HttpServletResponse, requestId: String): OpenAIChatCompletionRequest? {
        val rawBody = req.reader.use(BufferedReader::readText)
        if (rawBody.isBlank()) {
            PluginLogger.Service.warn("[Chat-$requestId] Empty request body")
            sendErrorResponse(resp, "Empty request body", HttpServletResponse.SC_BAD_REQUEST)
            return null
        }

        val bodyPreview = if (rawBody.length > BODY_PREVIEW_MAX_LENGTH) {
            rawBody.take(BODY_PREVIEW_MAX_LENGTH) + "…(truncated)"
        } else {
            rawBody
        }
        PluginLogger.Service.debug("[Chat-$requestId] Raw request body: $bodyPreview")

        val openAIRequest = try {
            gson.fromJson<OpenAIChatCompletionRequest>(rawBody, OpenAIChatCompletionRequest::class.java)
        } catch (e: JsonSyntaxException) {
            PluginLogger.Service.error("[Chat-$requestId] Invalid JSON in request: ${e.message}", e)
            sendErrorResponse(resp, "Invalid JSON format", HttpServletResponse.SC_BAD_REQUEST)
            return null
        }

        if (openAIRequest.model.isBlank() || openAIRequest.messages.isEmpty()) {
            PluginLogger.Service.warn("[Chat-$requestId] Missing required fields: model and messages")
            sendErrorResponse(resp, "Missing required fields: model and messages", HttpServletResponse.SC_BAD_REQUEST)
            return null
        }

        PluginLogger.Service.info("[Chat-$requestId] Processing request for model: ${openAIRequest.model} with ${openAIRequest.messages.size} messages, stream=${openAIRequest.stream}")
        return openAIRequest
    }

    private fun translateRequest(openAIRequest: OpenAIChatCompletionRequest, resp: HttpServletResponse, requestId: String): ChatCompletionRequest? {
        val openRouterRequest = RequestTranslator.translateChatCompletionRequest(openAIRequest)
        val openRouterRequestJson = gson.toJson(openRouterRequest)
        PluginLogger.Service.debug("[Chat-$requestId] Translated OpenRouter request: $openRouterRequestJson")

        if (!RequestTranslator.validateTranslatedRequest(openRouterRequest)) {
            PluginLogger.Service.error("[Chat-$requestId] Request validation failed")
            sendErrorResponse(resp, "Invalid request format", HttpServletResponse.SC_BAD_REQUEST)
            return null
        }

        return openRouterRequest
    }

    private fun executeOpenRouterRequest(
        openRouterRequest: ChatCompletionRequest,
        apiKey: String,
        resp: HttpServletResponse,
        requestId: String
    ): ChatCompletionResponse? {
        PluginLogger.Service.info("[Chat-$requestId] Dispatching request to OpenRouter API…")

        // Create HTTP request directly with the provided API key
        val jsonBody = gson.toJson(openRouterRequest)
        val request = Request.Builder()
            .url(OPENROUTER_API_URL)
            .post(jsonBody.toRequestBody("application/json".toMediaType()))
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .addHeader("HTTP-Referer", "https://github.com/openrouter-intellij-plugin")
            .addHeader("X-Title", "OpenRouter IntelliJ Plugin")
            .build()

        try {
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val errorBody = response.body?.string() ?: "Unknown error"
                    PluginLogger.Service.error("[Chat-$requestId] OpenRouter returned error: ${response.code} - $errorBody")
                    sendErrorResponse(resp, "OpenRouter API error: $errorBody", response.code)
                    return null
                }

                val responseBody = response.body?.string()
                if (responseBody == null) {
                    PluginLogger.Service.error("[Chat-$requestId] OpenRouter returned null response body")
                    sendErrorResponse(resp, "Failed to get response from OpenRouter", HttpServletResponse.SC_SERVICE_UNAVAILABLE)
                    return null
                }

                val openRouterResponse = gson.fromJson(responseBody, ChatCompletionResponse::class.java)
                PluginLogger.Service.info("[Chat-$requestId] Received response from OpenRouter successfully")
                return openRouterResponse
            }
        } catch (e: Exception) {
            PluginLogger.Service.error("[Chat-$requestId] Exception calling OpenRouter: ${e.message}", e)
            sendErrorResponse(resp, "Failed to call OpenRouter: ${e.message}", HttpServletResponse.SC_SERVICE_UNAVAILABLE)
            return null
        }
    }

    private fun translateResponse(
        openRouterResponse: ChatCompletionResponse,
        originalModel: String,
        resp: HttpServletResponse,
        requestId: String
    ): OpenAIChatCompletionResponse? {
        val openAIResponse = ResponseTranslator.translateChatCompletionResponse(
            openRouterResponse,
            originalModel
        )
        val openAIResponseJson = gson.toJson(openAIResponse)
        PluginLogger.Service.debug("[Chat-$requestId] Translated OpenAI response: $openAIResponseJson")

        if (!ResponseTranslator.validateTranslatedResponse(openAIResponse)) {
            PluginLogger.Service.error("[Chat-$requestId] Response validation failed")
            sendErrorResponse(resp, "Invalid response format", HttpServletResponse.SC_INTERNAL_SERVER_ERROR)
            return null
        }

        return openAIResponse
    }

    private fun sendSuccessResponse(resp: HttpServletResponse, openAIResponse: OpenAIChatCompletionResponse, startNs: Long, requestId: String) {
        val durationMs = (System.nanoTime() - startNs) / NANOSECONDS_TO_MILLISECONDS
        PluginLogger.Service.info("[Chat-$requestId] ✅ Chat completion successful in ${durationMs}ms, returning response")

        resp.contentType = "application/json"
        resp.status = HttpServletResponse.SC_OK
        resp.writer.write(gson.toJson(openAIResponse))
    }

    private fun sendErrorResponse(resp: HttpServletResponse, message: String, statusCode: Int) {
        resp.contentType = "application/json"
        resp.status = statusCode
        val errorResponse = mapOf(
            "error" to mapOf(
                "message" to message,
                "type" to "invalid_request_error",
                "code" to statusCode
            )
        )
        resp.writer.write(gson.toJson(errorResponse))
    }
}

