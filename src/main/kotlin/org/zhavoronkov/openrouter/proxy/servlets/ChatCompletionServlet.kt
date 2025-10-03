package org.zhavoronkov.openrouter.proxy.servlets

import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.zhavoronkov.openrouter.models.ChatCompletionRequest
import org.zhavoronkov.openrouter.models.ChatCompletionResponse
import org.zhavoronkov.openrouter.proxy.models.OpenAIChatCompletionRequest
import org.zhavoronkov.openrouter.proxy.models.OpenAIChatCompletionResponse
import org.zhavoronkov.openrouter.proxy.translation.RequestTranslator
import org.zhavoronkov.openrouter.proxy.translation.ResponseTranslator
import org.zhavoronkov.openrouter.services.OpenRouterService
import org.zhavoronkov.openrouter.services.OpenRouterSettingsService
import org.zhavoronkov.openrouter.utils.OpenRouterRequestBuilder
import org.zhavoronkov.openrouter.utils.PluginLogger
import jakarta.servlet.http.HttpServlet
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import java.io.BufferedReader
import java.io.PrintWriter
import java.util.concurrent.TimeUnit
import java.security.MessageDigest

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

        // Request deduplication tracking
        private val recentRequests = mutableMapOf<String, Long>()
        private const val DUPLICATE_WINDOW_MS = 5000L // 5 second window
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

        logRequestStart(requestId)

        try {
            processRequest(req, resp, requestId, startNs)
        } catch (e: java.util.concurrent.TimeoutException) {
            handleTimeoutException(e, resp, requestId)
        } catch (e: java.util.concurrent.ExecutionException) {
            handleExecutionException(e, resp, requestId)
        } catch (e: java.io.IOException) {
            handleIOException(e, resp, requestId)
        } catch (e: IllegalArgumentException) {
            handleIllegalArgumentException(e, resp, requestId)
        } catch (e: RuntimeException) {
            handleRuntimeException(e, resp, requestId)
        } finally {
            logRequestComplete(requestId)
        }
    }

    /**
     * Log request start
     */
    private fun logRequestStart(requestId: String) {
        val timestamp = System.currentTimeMillis()
        PluginLogger.Service.info("═══════════════════════════════════════════════════════")
        PluginLogger.Service.info("[Chat-$requestId] NEW CHAT COMPLETION REQUEST RECEIVED")
        PluginLogger.Service.info("[Chat-$requestId] Timestamp: $timestamp")
        PluginLogger.Service.info("[Chat-$requestId] Total chat requests so far: $requestCounter")
        PluginLogger.Service.info("═══════════════════════════════════════════════════════")
    }

    /**
     * Log request complete
     */
    private fun logRequestComplete(requestId: String) {
        PluginLogger.Service.info("[Chat-$requestId] REQUEST COMPLETE")
        PluginLogger.Service.info("═══════════════════════════════════════════════════════")
    }

    /**
     * Process the chat completion request
     */
    private fun processRequest(
        req: HttpServletRequest,
        resp: HttpServletResponse,
        requestId: String,
        startNs: Long
    ) {
        logRequestDiagnostics(req, requestId)

        val requestBody = req.reader.readText()
        checkForDuplicateRequest(requestBody, req, requestId)

        val apiKey = validateAndGetApiKey(resp, requestId) ?: return
        val openAIRequest = parseRequestBody(requestBody, resp, requestId) ?: return

        PluginLogger.Service.info("[Chat-$requestId] 📝 Model: '${openAIRequest.model}'")

        routeRequest(resp, openAIRequest, apiKey, requestId, startNs)
    }

    /**
     * Check for duplicate requests
     */
    private fun checkForDuplicateRequest(requestBody: String, req: HttpServletRequest, requestId: String) {
        val requestHash = generateRequestHash(requestBody, req.remoteAddr)
        val currentTime = System.currentTimeMillis()

        synchronized(recentRequests) {
            recentRequests.entries.removeIf { currentTime - it.value > DUPLICATE_WINDOW_MS }

            if (recentRequests.containsKey(requestHash)) {
                logDuplicateRequest(requestId, requestHash, req, currentTime)
            } else {
                recentRequests[requestHash] = currentTime
            }
        }
    }

    /**
     * Log duplicate request warning
     */
    private fun logDuplicateRequest(
        requestId: String,
        requestHash: String,
        req: HttpServletRequest,
        currentTime: Long
    ) {
        val timeSinceFirst = currentTime - recentRequests[requestHash]!!
        PluginLogger.Service.warn("[Chat-$requestId] 🚨 DUPLICATE REQUEST DETECTED!")
        PluginLogger.Service.warn("[Chat-$requestId] 🚨 Time since first request: ${timeSinceFirst}ms")
        PluginLogger.Service.warn("[Chat-$requestId] 🚨 Request hash: $requestHash")
        PluginLogger.Service.warn("[Chat-$requestId] 🚨 Remote address: ${req.remoteAddr}")
        PluginLogger.Service.warn("[Chat-$requestId] 🚨 User-Agent: ${req.getHeader("User-Agent")}")
    }

    /**
     * Validate and get API key from settings
     */
    private fun validateAndGetApiKey(resp: HttpServletResponse, requestId: String): String? {
        val apiKey = settingsService.getApiKey()

        if (apiKey.isBlank()) {
            PluginLogger.Service.error("[Chat-$requestId] ❌ No API key configured in OpenRouter plugin settings")
            sendErrorResponse(
                resp,
                "OpenRouter API key not configured. Please configure it in Settings > Tools > OpenRouter",
                HttpServletResponse.SC_UNAUTHORIZED
            )
            return null
        }

        PluginLogger.Service.info("[Chat-$requestId] 🔑 Using API key from plugin settings: ${apiKey.take(20)}... (length: ${apiKey.length})")
        PluginLogger.Service.info("[Chat-$requestId] 🔑 API key prefix: ${apiKey.take(15)}...")

        return apiKey
    }

    /**
     * Route request to streaming or non-streaming handler
     */
    private fun routeRequest(
        resp: HttpServletResponse,
        openAIRequest: OpenAIChatCompletionRequest,
        apiKey: String,
        requestId: String,
        startNs: Long
    ) {
        if (openAIRequest.stream == true) {
            PluginLogger.Service.info("[Chat-$requestId] 🌊 STREAMING requested - handling SSE response")
            handleStreamingRequest(resp, openAIRequest, apiKey, requestId)
        } else {
            PluginLogger.Service.info("[Chat-$requestId] 📦 NON-STREAMING request - handling standard response")
            handleNonStreamingRequest(resp, openAIRequest, apiKey, requestId, startNs)
        }
    }

    /**
     * Handle timeout exception
     */
    private fun handleTimeoutException(e: java.util.concurrent.TimeoutException, resp: HttpServletResponse, requestId: String) {
        PluginLogger.Service.error("[Chat-$requestId] Chat completion request timed out: ${e.message}", e)
        sendErrorResponse(resp, "Request timed out", HttpServletResponse.SC_REQUEST_TIMEOUT)
    }

    /**
     * Handle execution exception
     */
    private fun handleExecutionException(e: java.util.concurrent.ExecutionException, resp: HttpServletResponse, requestId: String) {
        PluginLogger.Service.error("[Chat-$requestId] Chat completion execution failed: ${e.message}", e)
        sendErrorResponse(resp, "Execution error: ${e.message}", HttpServletResponse.SC_INTERNAL_SERVER_ERROR)
    }

    /**
     * Handle IO exception
     */
    private fun handleIOException(e: java.io.IOException, resp: HttpServletResponse, requestId: String) {
        PluginLogger.Service.error("[Chat-$requestId] IO error during chat completion: ${e.message}", e)
        sendErrorResponse(resp, "Network error: ${e.message}", HttpServletResponse.SC_SERVICE_UNAVAILABLE)
    }

    /**
     * Handle illegal argument exception
     */
    private fun handleIllegalArgumentException(e: IllegalArgumentException, resp: HttpServletResponse, requestId: String) {
        PluginLogger.Service.error("[Chat-$requestId] Invalid argument in chat completion: ${e.message}", e)
        sendErrorResponse(resp, "Invalid request: ${e.message}", HttpServletResponse.SC_BAD_REQUEST)
    }

    /**
     * Handle runtime exception
     */
    private fun handleRuntimeException(e: RuntimeException, resp: HttpServletResponse, requestId: String) {
        PluginLogger.Service.error("[Chat-$requestId] Runtime error during chat completion: ${e.message}", e)
        sendErrorResponse(resp, "Internal server error: ${e.message}", HttpServletResponse.SC_INTERNAL_SERVER_ERROR)
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
        setupSSEHeaders(resp)

        val writer = resp.writer
        writer.flush() // Flush headers immediately

        try {
            val (_, jsonBody) = prepareStreamingRequest(openAIRequest, requestId)
            val request = buildOpenRouterRequest(jsonBody, apiKey)
            executeStreamingRequest(request, writer, requestId, apiKey, jsonBody)
        } catch (e: Exception) {
            handleStreamingError(e, writer, requestId)
        }
    }

    /**
     * Set up Server-Sent Events headers
     */
    private fun setupSSEHeaders(resp: HttpServletResponse) {
        resp.contentType = "text/event-stream"
        resp.characterEncoding = "UTF-8"
        resp.setHeader("Cache-Control", "no-cache")
        resp.setHeader("Connection", "keep-alive")
        resp.setHeader("X-Accel-Buffering", "no") // Disable nginx buffering
        resp.status = HttpServletResponse.SC_OK
    }

    /**
     * Prepare the streaming request by translating to OpenRouter format
     */
    private fun prepareStreamingRequest(
        openAIRequest: OpenAIChatCompletionRequest,
        requestId: String
    ): Pair<ChatCompletionRequest, String> {
        val openRouterRequest = RequestTranslator.translateChatCompletionRequest(openAIRequest)
        val jsonBody = gson.toJson(openRouterRequest)
        PluginLogger.Service.debug("[Chat-$requestId] Streaming request body: ${jsonBody.take(500)}...")
        return Pair(openRouterRequest, jsonBody)
    }

    /**
     * Build the HTTP request to OpenRouter
     */
    private fun buildOpenRouterRequest(jsonBody: String, apiKey: String): Request {
        return OpenRouterRequestBuilder.buildPostRequest(
            url = OPENROUTER_API_URL,
            jsonBody = jsonBody,
            authType = OpenRouterRequestBuilder.AuthType.API_KEY,
            authToken = apiKey
        )
    }

    /**
     * Execute the streaming request and handle the response
     */
    private fun executeStreamingRequest(
        request: Request,
        writer: PrintWriter,
        requestId: String,
        apiKey: String,
        jsonBody: String
    ) {
        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                handleStreamingErrorResponse(response, writer, requestId, apiKey, jsonBody, request)
                return
            }

            PluginLogger.Service.info("[Chat-$requestId] Streaming response from OpenRouter...")
            streamResponseToClient(response, writer, requestId)
        }
    }

    /**
     * Handle error response from OpenRouter
     */
    private fun handleStreamingErrorResponse(
        response: Response,
        writer: PrintWriter,
        requestId: String,
        apiKey: String,
        jsonBody: String,
        request: Request
    ) {
        val errorBody = response.body?.string() ?: "Unknown error"
        PluginLogger.Service.error("[Chat-$requestId] ❌ OpenRouter API Error: ${response.code}")
        PluginLogger.Service.error("[Chat-$requestId] ❌ Error details: $errorBody")
        PluginLogger.Service.error("[Chat-$requestId] ❌ Request URL: ${request.url}")
        PluginLogger.Service.error("[Chat-$requestId] ❌ API key prefix: ${apiKey.take(15)}... (length: ${apiKey.length})")
        PluginLogger.Service.debug("[Chat-$requestId] ❌ Full request body: ${jsonBody.take(500)}")

        writer.write("data: ${gson.toJson(mapOf("error" to mapOf("message" to errorBody)))}\n\n")
        writer.flush()
    }

    /**
     * Stream the response from OpenRouter to the client
     */
    private fun streamResponseToClient(response: Response, writer: PrintWriter, requestId: String) {
        val reader = response.body?.byteStream()?.bufferedReader() ?: return

        reader.use {
            processStreamLines(it, writer, requestId)
        }
    }

    /**
     * Process stream lines from the reader
     */
    private fun processStreamLines(reader: BufferedReader, writer: PrintWriter, requestId: String) {
        var line: String?
        while (reader.readLine().also { line = it } != null) {
            val currentLine = line ?: continue

            if (shouldForwardLine(currentLine)) {
                forwardLineToClient(currentLine, writer)

                if (isStreamComplete(currentLine)) {
                    PluginLogger.Service.info("[Chat-$requestId] ✅ Stream completed successfully")
                    break
                }
            }
        }
    }

    /**
     * Check if line should be forwarded to client
     */
    private fun shouldForwardLine(line: String): Boolean {
        return line.startsWith("data: ") || line.isBlank()
    }

    /**
     * Forward a line to the client
     */
    private fun forwardLineToClient(line: String, writer: PrintWriter) {
        writer.write(line + "\n")
        writer.flush()
    }

    /**
     * Check if stream is complete
     */
    private fun isStreamComplete(line: String): Boolean {
        return line == "data: [DONE]"
    }

    /**
     * Handle streaming errors
     */
    private fun handleStreamingError(e: Exception, writer: PrintWriter, requestId: String) {
        PluginLogger.Service.error("[Chat-$requestId] Streaming error: ${e.message}", e)
        writer.write("data: ${gson.toJson(mapOf("error" to mapOf("message" to e.message)))}\n\n")
        writer.flush()
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
        val request = OpenRouterRequestBuilder.buildPostRequest(
            url = OPENROUTER_API_URL,
            jsonBody = jsonBody,
            authType = OpenRouterRequestBuilder.AuthType.API_KEY,
            authToken = apiKey
        )

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

    /**
     * Generate a hash for request deduplication based on content and source
     */
    private fun generateRequestHash(requestBody: String, remoteAddr: String): String {
        val content = "$requestBody|$remoteAddr"
        val digest = MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(content.toByteArray())
        return hashBytes.joinToString("") { "%02x".format(it) }.take(16) // First 16 chars of SHA-256
    }

    /**
     * Parse request body from string instead of reading from request again
     */
    private fun parseRequestBody(requestBody: String, resp: HttpServletResponse, requestId: String): OpenAIChatCompletionRequest? {
        return try {
            val openAIRequest = gson.fromJson(requestBody, OpenAIChatCompletionRequest::class.java)

            if (openAIRequest.messages.isNullOrEmpty()) {
                PluginLogger.Service.error("[Chat-$requestId] Request validation failed: messages cannot be null or empty")
                sendErrorResponse(resp, "Messages cannot be null or empty", HttpServletResponse.SC_BAD_REQUEST)
                return null
            }

            PluginLogger.Service.info("[Chat-$requestId] Processing request for model: ${openAIRequest.model} with ${openAIRequest.messages.size} messages, stream=${openAIRequest.stream}")
            openAIRequest
        } catch (e: JsonSyntaxException) {
            PluginLogger.Service.error("[Chat-$requestId] Failed to parse request JSON: ${e.message}", e)
            sendErrorResponse(resp, "Invalid JSON format", HttpServletResponse.SC_BAD_REQUEST)
            null
        }
    }
}

