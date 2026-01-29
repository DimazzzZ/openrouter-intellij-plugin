package org.zhavoronkov.openrouter.proxy.servlets

import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import jakarta.servlet.http.HttpServlet
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.zhavoronkov.openrouter.proxy.models.OpenAIChatCompletionRequest
import org.zhavoronkov.openrouter.services.OpenRouterSettingsService
import org.zhavoronkov.openrouter.utils.ModelAvailabilityNotifier
import org.zhavoronkov.openrouter.utils.OpenRouterRequestBuilder
import org.zhavoronkov.openrouter.utils.PluginLogger
import java.io.IOException
import java.io.PrintWriter
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicInteger

/**
 * Servlet that provides OpenAI-compatible /v1/chat/completions endpoint
 * with full streaming support
 */

data class StreamingErrorContext(
    val response: Response,
    val writer: PrintWriter,
    val requestId: String,
    val apiKey: String,
    val jsonBody: String,
    val request: Request
)

class ChatCompletionServlet : HttpServlet() {

    companion object {
        private const val OPENROUTER_API_URL = "https://openrouter.ai/api/v1/chat/completions"

        // Request tracking - thread-safe counter using AtomicInteger
        private val requestCounter = AtomicInteger(0)

        // HTTP Client timeouts
        private const val CONNECT_TIMEOUT_SECONDS = 30L
        private const val READ_TIMEOUT_SECONDS = 120L
        private const val WRITE_TIMEOUT_SECONDS = 30L

        // Request ID formatting
        private const val REQUEST_ID_PAD_WIDTH = 6

        // API Key validation
        private const val API_KEY_DISPLAY_LENGTH = 15

        // Streaming request constants
        private const val STREAMING_TIMEOUT_MS = 500L
        private const val STREAMING_CHUNK_DISPLAY_LENGTH = 15

        // HTTP status codes
        private const val HTTP_STATUS_NOT_FOUND = 404
        private const val HTTP_STATUS_TOO_MANY_REQUESTS = 429
        private const val HTTP_STATUS_CLIENT_ERROR_MIN = 400
        private const val HTTP_STATUS_CLIENT_ERROR_MAX = 499
        private const val HTTP_STATUS_SERVER_ERROR_MIN = 500

        // Time conversion
        private const val MILLIS_PER_SECOND = 1000
    }

    private val gson = Gson()
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .writeTimeout(WRITE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .build()
    private val settingsService = OpenRouterSettingsService.getInstance()
    private val requestValidator = RequestValidator(settingsService)
    private val streamingHandler = StreamingResponseHandler()
    private val nonStreamingHandler = NonStreamingResponseHandler(httpClient, gson)

    override fun doPost(req: HttpServletRequest, resp: HttpServletResponse) {
        val requestNumber = requestCounter.incrementAndGet()
        val requestId = requestNumber.toString().padStart(REQUEST_ID_PAD_WIDTH, '0')
        val startNs = System.nanoTime()

        logRequestBoundary(requestId, isStart = true, requestNumber = requestNumber)

        try {
            processRequest(req, resp, requestId, startNs)
        } catch (e: TimeoutException) {
            handleException(e, resp, requestId)
        } catch (e: ExecutionException) {
            handleException(e, resp, requestId)
        } catch (e: IOException) {
            handleException(e, resp, requestId)
        } catch (e: IllegalArgumentException) {
            handleException(e, resp, requestId)
        } catch (e: IllegalStateException) {
            handleException(e, resp, requestId)
        } catch (e: JsonSyntaxException) {
            handleException(e, resp, requestId)
        } catch (e: RuntimeException) {
            handleException(e, resp, requestId)
        } finally {
            val durationMs = (System.nanoTime() - startNs) / 1_000_000
            logRequestBoundary(requestId, isStart = false, durationMs = durationMs)
        }
    }

    /**
     * Log request start/completion with optional duration
     */
    private fun logRequestBoundary(
        requestId: String,
        isStart: Boolean,
        requestNumber: Int = 0,
        durationMs: Long = 0
    ) {
        PluginLogger.Service.info("═══════════════════════════════════════════════════════")
        if (isStart) {
            val timestamp = System.currentTimeMillis()
            PluginLogger.Service.info("[Chat-$requestId] NEW CHAT COMPLETION REQUEST RECEIVED")
            PluginLogger.Service.info("[Chat-$requestId] Timestamp: $timestamp")
            PluginLogger.Service.info("[Chat-$requestId] Total chat requests so far: $requestNumber")
        } else {
            PluginLogger.Service.info("[Chat-$requestId] REQUEST COMPLETE (${durationMs}ms)")
        }
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
        requestValidator.checkForDuplicateRequest(requestBody, req, requestId)
    }

    /**
     * Validate and get API key from settings
     */
    private fun validateAndGetApiKey(resp: HttpServletResponse, requestId: String): String? {
        return requestValidator.validateAndGetApiKey(resp, requestId)
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
    private fun handleException(
        e: Exception,
        resp: HttpServletResponse,
        requestId: String
    ) {
        when (e) {
            is java.util.concurrent.TimeoutException -> {
                val msg = "[Chat-$requestId] Chat completion request timed out: ${e.message}"
                PluginLogger.Service.error(msg, e)
                sendErrorResponse(resp, "Request timed out", HttpServletResponse.SC_REQUEST_TIMEOUT)
            }
            is java.util.concurrent.ExecutionException -> {
                val msg = "[Chat-$requestId] Chat completion execution failed: ${e.message}"
                PluginLogger.Service.error(msg, e)
                val errMsg = "Execution error: ${e.message}"
                sendErrorResponse(resp, errMsg, HttpServletResponse.SC_INTERNAL_SERVER_ERROR)
            }
            is java.io.IOException -> {
                val msg = "[Chat-$requestId] IO error during chat completion: ${e.message}"
                PluginLogger.Service.error(msg, e)
                val errMsg = "Network error: ${e.message}"
                sendErrorResponse(resp, errMsg, HttpServletResponse.SC_SERVICE_UNAVAILABLE)
            }
            is IllegalArgumentException -> {
                val msg = "[Chat-$requestId] Invalid argument in chat completion: ${e.message}"
                PluginLogger.Service.error(msg, e)
                val errMsg = "Invalid request: ${e.message}"
                sendErrorResponse(resp, errMsg, HttpServletResponse.SC_BAD_REQUEST)
            }
            is RuntimeException -> {
                val msg = "[Chat-$requestId] Runtime error during chat completion: ${e.message}"
                PluginLogger.Service.error(msg, e)
                val errMsg = "Internal server error: ${e.message}"
                sendErrorResponse(resp, errMsg, HttpServletResponse.SC_INTERNAL_SERVER_ERROR)
            }
            else -> {
                val msg = "[Chat-$requestId] Unexpected error: ${e.message}"
                PluginLogger.Service.error(msg, e)
                sendErrorResponse(resp, "Internal server error", HttpServletResponse.SC_INTERNAL_SERVER_ERROR)
            }
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

        // Set up Server-Sent Events headers
        resp.contentType = "text/event-stream"
        resp.characterEncoding = "UTF-8"
        resp.setHeader("Cache-Control", "no-cache")
        resp.setHeader("Connection", "keep-alive")
        resp.setHeader("X-Accel-Buffering", "no") // Disable nginx buffering
        resp.status = HttpServletResponse.SC_OK

        val writer = resp.writer
        writer.flush() // Flush headers immediately

        try {
            val jsonBody = prepareRequest(openAIRequest, requestId, isStreaming = true)
            val request = buildOpenRouterRequest(jsonBody, apiKey)
            executeStreamingRequest(request, writer, requestId, apiKey, jsonBody)
        } catch (e: IOException) {
            handleStreamingError(e, writer, requestId)
        } catch (e: JsonSyntaxException) {
            handleStreamingError(e, writer, requestId)
        } catch (e: IllegalStateException) {
            handleStreamingError(e, writer, requestId)
        } catch (e: IllegalArgumentException) {
            handleStreamingError(e, writer, requestId)
        }
    }

    /**
     * Prepare the request for pure passthrough
     */
    private fun prepareRequest(
        openAIRequest: OpenAIChatCompletionRequest,
        requestId: String,
        isStreaming: Boolean
    ): String {
        val jsonBody = gson.toJson(openAIRequest)
        val bodyPreview = jsonBody.take(STREAMING_TIMEOUT_MS.toInt())
        val mode = if (isStreaming) "Streaming" else "Non-streaming"
        PluginLogger.Service.debug("[Chat-$requestId] $mode request body (passthrough): $bodyPreview...")
        return jsonBody
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
                val errorContext = StreamingErrorContext(response, writer, requestId, apiKey, jsonBody, request)
                handleStreamingErrorResponse(errorContext)
                return
            }

            PluginLogger.Service.info("[Chat-$requestId] Streaming response from OpenRouter...")
            streamResponseToClient(response, writer, requestId)
        }
    }

    /**
     * Handle error response from OpenRouter
     * Sends error as OpenAI-compatible streaming chunk so AI Assistant can display it properly
     */
    private fun handleStreamingErrorResponse(context: StreamingErrorContext) {
        val errorBody = context.response.body?.string() ?: "Unknown error"

        // Log each piece of information separately to avoid IntelliJ logger truncation
        val keyDisplay = context.apiKey.take(API_KEY_DISPLAY_LENGTH)
        val keyLength = context.apiKey.length
        val statusCode = context.response.code
        val requestId = context.requestId

        // Use warn() for expected API errors (404, 429, etc.) to avoid stack traces
        // Use error() only for unexpected errors (500, network failures, etc.)
        val isClientError = statusCode in HTTP_STATUS_CLIENT_ERROR_MIN..HTTP_STATUS_CLIENT_ERROR_MAX
        if (isClientError) {
            PluginLogger.Service.warn("[Chat-$requestId] ❌ OpenRouter API Error: $statusCode")
        } else {
            PluginLogger.Service.warn("[Chat-$requestId] ❌ OpenRouter API Error: $statusCode (server error)")
        }

        // Log error body separately so it's always visible for debugging
        PluginLogger.Service.warn("[Chat-$requestId] ❌ Error response body: $errorBody")
        PluginLogger.Service.debug("[Chat-$requestId] Request URL: ${context.request.url}")
        PluginLogger.Service.debug("[Chat-$requestId] API key prefix: $keyDisplay... (length: $keyLength)")

        val bodyPreview = context.jsonBody.take(STREAMING_TIMEOUT_MS.toInt())
        PluginLogger.Service.debug("[Chat-$requestId] ❌ Full request body: $bodyPreview")

        // Create user-friendly error message
        val userFriendlyMessage = createUserFriendlyErrorMessage(errorBody, context.response.code)

        // Send error as OpenAI-compatible streaming chunk
        // This ensures AI Assistant can parse and display the error properly
        sendOpenAICompatibleErrorChunk(context.writer, userFriendlyMessage, context.requestId)
    }

    /**
     * Sends an error message as an OpenAI-compatible streaming chunk
     * This format is required for AI Assistant to properly parse and display errors
     */
    private fun sendOpenAICompatibleErrorChunk(writer: PrintWriter, message: String, requestId: String) {
        val escapedMessage = message.replace("\"", "\\\"").replace("\n", "\\n")
        val chunkId = "chatcmpl-error-$requestId"
        val timestamp = System.currentTimeMillis() / MILLIS_PER_SECOND

        // Create a valid OpenAI streaming chunk with the error message in the content
        val errorChunk = buildString {
            append("{\"id\":\"$chunkId\",")
            append("\"object\":\"chat.completion.chunk\",")
            append("\"created\":$timestamp,")
            append("\"model\":\"error\",")
            append("\"choices\":[{")
            append("\"index\":0,")
            append("\"delta\":{\"role\":\"assistant\",\"content\":\"$escapedMessage\"},")
            append("\"finish_reason\":\"stop\"")
            append("}]}")
        }

        writer.println("data: $errorChunk")
        writer.println()
        writer.println("data: [DONE]")
        writer.println()
        writer.flush()
    }

    /**
     * Create a user-friendly error message based on the error response
     * Handles all multimodal content type errors (image, audio, video, PDF/file)
     */
    @Suppress("ReturnCount")
    private fun createUserFriendlyErrorMessage(errorBody: String, statusCode: Int): String {
        // First, try to extract the message from JSON error body
        val extractedMessage = extractErrorMessageFromJson(errorBody)

        // Check for "No endpoints found" pattern - can occur with 404 or 500 status codes
        // OpenRouter may return different status codes for model availability issues
        if (errorBody.contains("No endpoints found", ignoreCase = true)) {
            return handleNoEndpointsFoundError(errorBody)
        }

        // Check for multimodal content type errors (typically 404)
        if (statusCode == HTTP_STATUS_NOT_FOUND) {
            val multimodalError = handleMultimodalContentTypeError(errorBody)
            if (multimodalError != null) {
                return multimodalError
            }
        }

        // Check if this is a rate limit error (429)
        if (statusCode == HTTP_STATUS_TOO_MANY_REQUESTS) {
            return createRateLimitMessage(extractedMessage)
        }

        // Check for server errors (500+)
        if (statusCode >= HTTP_STATUS_SERVER_ERROR_MIN) {
            return createServerErrorMessage(extractedMessage, statusCode)
        }

        // For other errors, return the extracted message or a generic error
        return extractedMessage ?: "Request failed (HTTP $statusCode). Please try again."
    }

    /**
     * Handle "No endpoints found" errors - model unavailable or doesn't support requested features
     */
    private fun handleNoEndpointsFoundError(errorBody: String): String {
        // Check for specific capability errors first
        if (errorBody.contains("support image input", ignoreCase = true)) {
            ModelAvailabilityNotifier.notifyModelUnavailable("the requested model", errorBody)
            return createImageInputNotSupportedMessage()
        }
        if (errorBody.contains("support audio input", ignoreCase = true) ||
            errorBody.contains("audio not supported", ignoreCase = true)
        ) {
            ModelAvailabilityNotifier.notifyModelUnavailable("the requested model", errorBody)
            return createAudioInputNotSupportedMessage()
        }
        if (errorBody.contains("support video input", ignoreCase = true) ||
            errorBody.contains("video not supported", ignoreCase = true)
        ) {
            ModelAvailabilityNotifier.notifyModelUnavailable("the requested model", errorBody)
            return createVideoInputNotSupportedMessage()
        }
        if (errorBody.contains("support pdf", ignoreCase = true) ||
            errorBody.contains("support file", ignoreCase = true) ||
            errorBody.contains("pdf not supported", ignoreCase = true) ||
            errorBody.contains("file not supported", ignoreCase = true)
        ) {
            ModelAvailabilityNotifier.notifyModelUnavailable("the requested model", errorBody)
            return createFileInputNotSupportedMessage()
        }

        // Generic "No endpoints found for <model>" error
        val modelNameRegex = """No endpoints found for ([^.]+)""".toRegex()
        val modelName = modelNameRegex.find(errorBody)?.groupValues?.get(1) ?: "the requested model"
        ModelAvailabilityNotifier.notifyModelUnavailable(modelName, errorBody)
        return createModelUnavailableMessage(modelName)
    }

    /**
     * Handle multimodal content type errors (image, audio, video, PDF/file not supported)
     * Returns null if no multimodal error pattern is detected
     */
    private fun handleMultimodalContentTypeError(errorBody: String): String? {
        if (errorBody.contains("support image input", ignoreCase = true)) {
            ModelAvailabilityNotifier.notifyModelUnavailable("the requested model", errorBody)
            return createImageInputNotSupportedMessage()
        }
        if (errorBody.contains("support audio input", ignoreCase = true) ||
            errorBody.contains("audio not supported", ignoreCase = true)
        ) {
            ModelAvailabilityNotifier.notifyModelUnavailable("the requested model", errorBody)
            return createAudioInputNotSupportedMessage()
        }
        if (errorBody.contains("support video input", ignoreCase = true) ||
            errorBody.contains("video not supported", ignoreCase = true)
        ) {
            ModelAvailabilityNotifier.notifyModelUnavailable("the requested model", errorBody)
            return createVideoInputNotSupportedMessage()
        }
        if (errorBody.contains("support pdf", ignoreCase = true) ||
            errorBody.contains("support file", ignoreCase = true) ||
            errorBody.contains("pdf not supported", ignoreCase = true) ||
            errorBody.contains("file not supported", ignoreCase = true)
        ) {
            ModelAvailabilityNotifier.notifyModelUnavailable("the requested model", errorBody)
            return createFileInputNotSupportedMessage()
        }
        return null
    }

    /**
     * Create a user-friendly message for server errors (HTTP 500+)
     */
    private fun createServerErrorMessage(extractedMessage: String?, statusCode: Int): String = buildString {
        append("OpenRouter server error (HTTP $statusCode).\n\n")
        if (extractedMessage != null) {
            append("Details: $extractedMessage\n\n")
        }
        append("This is usually a temporary issue. Please try again in a moment.\n")
        append("If the problem persists, check OpenRouter status: https://status.openrouter.ai")
    }

    private fun createImageInputNotSupportedMessage(): String = buildString {
        append("This model doesn't support image input.\n\n")
        append("Try a vision-capable model like:\n")
        append("- openai/gpt-4o or openai/gpt-4o-mini\n")
        append("- anthropic/claude-3.5-sonnet\n")
        append("- google/gemini-pro-1.5\n\n")
        append("Check model capabilities: https://openrouter.ai/models")
    }

    private fun createAudioInputNotSupportedMessage(): String = buildString {
        append("This model doesn't support audio input.\n\n")
        append("Try an audio-capable model like:\n")
        append("- openai/gpt-4o-audio-preview\n")
        append("- google/gemini-2.0-flash-exp\n")
        append("- google/gemini-pro-1.5\n\n")
        append("Check model capabilities: https://openrouter.ai/models")
    }

    private fun createVideoInputNotSupportedMessage(): String = buildString {
        append("This model doesn't support video input.\n\n")
        append("Try a video-capable model like:\n")
        append("- google/gemini-2.0-flash-exp\n")
        append("- google/gemini-pro-1.5\n")
        append("- openai/gpt-4o (for video frames)\n\n")
        append("Check model capabilities: https://openrouter.ai/models")
    }

    private fun createFileInputNotSupportedMessage(): String = buildString {
        append("This model doesn't support PDF/file input.\n\n")
        append("Try a document-capable model like:\n")
        append("- google/gemini-pro-1.5\n")
        append("- anthropic/claude-3.5-sonnet\n")
        append("- openai/gpt-4o\n\n")
        append("Check model capabilities: https://openrouter.ai/models")
    }

    private fun createModelUnavailableMessage(modelName: String): String = buildString {
        append("Model Unavailable: $modelName\n\n")
        append("This model is currently unavailable. Try:\n")
        append("- openai/gpt-4o-mini (fast, affordable)\n")
        append("- anthropic/claude-3.5-sonnet (high quality)\n")
        append("- google/gemini-pro-1.5 (large context)\n\n")
        append("Check model status: https://openrouter.ai/models")
    }

    private fun createRateLimitMessage(extractedMessage: String?): String = buildString {
        append("Rate limit exceeded. Please wait a moment and try again.\n\n")
        if (extractedMessage != null && extractedMessage.contains("free", ignoreCase = true)) {
            append("Tip: Free tier models have lower rate limits. ")
            append("Consider using a paid model for higher limits.")
        }
    }

    /**
     * Extracts the error message from a JSON error body.
     * OpenRouter returns errors in format: {"error":{"message":"...","code":...}}
     */
    @Suppress("SwallowedException")
    private fun extractErrorMessageFromJson(errorBody: String): String? {
        return try {
            val json = gson.fromJson(errorBody, com.google.gson.JsonObject::class.java)
            json?.getAsJsonObject("error")?.get("message")?.asString
        } catch (e: JsonSyntaxException) {
            // Not valid JSON - expected for non-JSON error bodies, no need to log
            null
        } catch (e: IllegalStateException) {
            // JSON doesn't have expected structure - expected for different error formats
            null
        }
    }

    /**
     * Stream the response from OpenRouter to the client
     */
    private fun streamResponseToClient(response: Response, writer: PrintWriter, requestId: String) {
        streamingHandler.streamResponseToClient(response, writer, requestId)
    }

    private fun handleStreamingError(e: Exception, writer: PrintWriter, requestId: String) {
        streamingHandler.handleStreamingError(e, writer, requestId)
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
        val requestBody = prepareRequest(openAIRequest, requestId, isStreaming = false)
        nonStreamingHandler.handleNonStreamingRequest(
            resp = resp,
            requestBody = requestBody,
            apiKey = apiKey,
            originalModel = openAIRequest.model,
            requestId = requestId,
            startNs = startNs
        )
    }

    private fun logRequestDiagnostics(req: HttpServletRequest, requestId: String) {
        val requestURI = req.requestURI
        val servletPath = req.servletPath
        val pathInfo = req.pathInfo
        val method = req.method
        val contentType = req.contentType
        val contentLength = req.contentLength

        PluginLogger.Service.info(
            "[Chat-$requestId] Incoming $method $requestURI (servletPath=$servletPath, pathInfo=$pathInfo)"
        )
        PluginLogger.Service.debug("[Chat-$requestId] Content-Type=$contentType, Content-Length=$contentLength")

        val headers = req.headerNames.asSequence().associateWith { name ->
            val value = req.getHeader(name)
            if (name.equals("Authorization", ignoreCase = true)) {
                value?.let { it.take(STREAMING_CHUNK_DISPLAY_LENGTH) + "…(redacted)" }
            } else {
                value
            }
        }
        PluginLogger.Service.debug("[Chat-$requestId] Headers: $headers")
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
     * Parse request body from string instead of reading from request again
     */
    private fun parseRequestBody(
        requestBody: String,
        resp: HttpServletResponse,
        requestId: String
    ): OpenAIChatCompletionRequest? {
        return try {
            val openAIRequest = gson.fromJson(requestBody, OpenAIChatCompletionRequest::class.java)

            if (openAIRequest.messages.isEmpty()) {
                PluginLogger.Service.error(
                    "[Chat-$requestId] Request validation failed: messages cannot be null or empty"
                )
                sendErrorResponse(resp, "Messages cannot be null or empty", HttpServletResponse.SC_BAD_REQUEST)
                return null
            }

            val msgCount = openAIRequest.messages.size
            val streamStr = openAIRequest.stream
            val model = openAIRequest.model
            PluginLogger.Service.info(
                "[Chat-$requestId] Processing request for model: $model with $msgCount messages, stream=$streamStr"
            )
            openAIRequest
        } catch (e: JsonSyntaxException) {
            PluginLogger.Service.error("[Chat-$requestId] Failed to parse request JSON: ${e.message}", e)
            sendErrorResponse(resp, "Invalid JSON format", HttpServletResponse.SC_BAD_REQUEST)
            null
        }
    }
}
