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
        private const val HTTP_STATUS_CLIENT_ERROR_MIN = 400
        private const val HTTP_STATUS_CLIENT_ERROR_MAX = 499
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
     */
    private fun handleStreamingErrorResponse(context: StreamingErrorContext) {
        val errorBody = context.response.body?.string() ?: "Unknown error"

        // Log as single consolidated message to avoid multiple stack traces
        val keyDisplay = context.apiKey.take(API_KEY_DISPLAY_LENGTH)
        val keyLength = context.apiKey.length
        val errorMessage = buildString {
            append("[Chat-${context.requestId}] ❌ OpenRouter API Error: ${context.response.code}\n")
            append("  Error details: $errorBody\n")
            append("  Request URL: ${context.request.url}\n")
            append("  API key prefix: $keyDisplay... (length: $keyLength)")
        }

        // Use warn() for expected API errors (404, 429, etc.) to avoid stack traces
        // Use error() only for unexpected errors (500, network failures, etc.)
        if (context.response.code in HTTP_STATUS_CLIENT_ERROR_MIN..HTTP_STATUS_CLIENT_ERROR_MAX) {
            PluginLogger.Service.warn(errorMessage)
        } else {
            PluginLogger.Service.error(errorMessage)
        }

        val bodyPreview = context.jsonBody.take(STREAMING_TIMEOUT_MS.toInt())
        PluginLogger.Service.debug("[Chat-${context.requestId}] ❌ Full request body: $bodyPreview")

        // Create user-friendly error message
        val userFriendlyMessage = createUserFriendlyErrorMessage(errorBody, context.response.code)

        // Write error event with proper SSE format (data line + blank line)
        context.writer.write("data: ${gson.toJson(mapOf("error" to mapOf("message" to userFriendlyMessage)))}\n\n")
        // Write [DONE] event to signal end of stream
        context.writer.write("data: [DONE]\n\n")
        context.writer.flush()
    }

    /**
     * Create a user-friendly error message based on the error response
     */
    private fun createUserFriendlyErrorMessage(errorBody: String, statusCode: Int): String {
        // Check if this is a "No endpoints found" error (model unavailable)
        if (statusCode == HTTP_STATUS_NOT_FOUND && errorBody.contains("No endpoints found", ignoreCase = true)) {
            // Extract model name from error message
            val modelNameRegex = """No endpoints found for ([^.]+)""".toRegex()
            val modelName = modelNameRegex.find(errorBody)?.groupValues?.get(1) ?: "the requested model"

            // Show notification to user (only once per model per hour)
            ModelAvailabilityNotifier.notifyModelUnavailable(modelName, errorBody)

            return buildString {
                append("❌ Model Unavailable: $modelName\n\n")
                append("This model is currently unavailable on OpenRouter. This can happen when:\n")
                append("• The model has been deprecated or removed\n")
                append("• All providers for this model are temporarily down\n")
                append("• The free tier for this model is unavailable\n\n")
                append("💡 Suggested alternatives:\n")
                append("• openai/gpt-4o-mini (fast, affordable)\n")
                append("• anthropic/claude-3.5-sonnet (high quality)\n")
                append("• google/gemini-pro-1.5 (large context)\n\n")
                append("📚 Check model status: https://openrouter.ai/models\n")
                append("⚙️ Update your model selection in AI Assistant settings")
            }.toString()
        }

        // For other errors, return the original error message
        return errorBody
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
