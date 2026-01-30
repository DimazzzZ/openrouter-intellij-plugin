package org.zhavoronkov.openrouter.proxy.servlets

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonSyntaxException
import jakarta.servlet.http.HttpServletResponse
import okhttp3.Response
import org.zhavoronkov.openrouter.utils.ErrorPatterns
import org.zhavoronkov.openrouter.utils.PluginLogger
import java.io.BufferedReader
import java.io.PrintWriter
import java.util.UUID

/**
 * Handles streaming responses from OpenRouter
 *
 * OpenAI streaming chunk format (required fields):
 * - id: string (e.g., "chatcmpl-123")
 * - object: string (must be "chat.completion.chunk")
 * - created: integer (Unix timestamp)
 * - model: string (model name)
 * - choices: array (at least one choice with delta object)
 */
@Suppress(
    "MaxLineLength",
    "MagicNumber",
    "LoopWithTooManyJumpStatements",
    "UnusedParameter",
    "TooManyFunctions"
)
class StreamingResponseHandler {

    companion object {
        private const val DATA_PREFIX = "data: "
        private const val DATA_PREFIX_LENGTH = 6
        private const val DONE_MARKER = "[DONE]"
        private const val HTTP_UNAUTHORIZED = 401
        private const val HTTP_PAYMENT_REQUIRED = 402
        private const val HTTP_TOO_MANY_REQUESTS = 429
        private const val HTTP_INTERNAL_SERVER_ERROR = 500
        private const val HTTP_BAD_GATEWAY = 502
        private const val HTTP_SERVICE_UNAVAILABLE = 503

        // Required fields for OpenAI streaming chunk
        private val REQUIRED_CHUNK_FIELDS = listOf("id", "object", "created", "model", "choices")

        // Memory safeguard: limit non-data lines accumulation to prevent memory issues
        private const val MAX_NON_DATA_LINES_LENGTH = 10_000
    }

    private val gson = Gson()

    /**
     * Result of processing a stream - tracks what was sent
     */
    data class StreamResult(
        val validChunksSent: Int,
        val errorDetected: Boolean,
        val errorMessage: String? = null
    )

    fun streamResponseToClient(response: Response, writer: PrintWriter, requestId: String) {
        response.body?.use { responseBody ->
            val reader = BufferedReader(responseBody.charStream())
            processStreamLines(reader, writer, requestId)
        } ?: run {
            // No response body - send error chunk
            PluginLogger.Service.warn("[Chat-$requestId] Empty response body from OpenRouter")
            sendErrorChunk(writer, "No response received from model. The model may not support this request type.", requestId)
            sendDoneMarker(writer)
        }
    }

    @Suppress("LongMethod", "CyclomaticComplexMethod")
    private fun processStreamLines(reader: BufferedReader, writer: PrintWriter, requestId: String) {
        var validChunksSent = 0
        var errorDetected = false
        var errorMessage: String? = null
        val nonDataLines = StringBuilder()

        while (true) {
            val line = reader.readLine() ?: break

            if (line.startsWith(DATA_PREFIX)) {
                val data = line.substring(DATA_PREFIX_LENGTH)

                if (data == DONE_MARKER) {
                    break
                }

                // Validate and process the chunk
                val validationResult = validateAndProcessChunk(data, writer, requestId)
                when (validationResult) {
                    is ChunkValidationResult.Valid -> {
                        validChunksSent++
                    }
                    is ChunkValidationResult.Error -> {
                        errorDetected = true
                        errorMessage = validationResult.message
                        // Transform error into OpenAI-compatible streaming chunk
                        // AI Assistant expects chat.completion.chunk format, not raw error JSON
                        PluginLogger.Service.warn(
                            "[Chat-$requestId] Error in stream: ${validationResult.message}"
                        )
                        // Enhance generic provider errors with more helpful messages
                        val enhancedMessage = enhanceErrorMessage(validationResult.message)
                        sendErrorChunk(writer, enhancedMessage, requestId)
                    }
                    is ChunkValidationResult.Invalid -> {
                        PluginLogger.Service.warn("[Chat-$requestId] Invalid chunk format: ${validationResult.reason}")
                        // Still forward it - OpenRouter might have a different format
                        writer.println(line)
                        writer.println()
                        writer.flush()
                        validChunksSent++
                    }
                }
            } else if (line.isNotBlank()) {
                // Collect non-data lines (might be error messages or unexpected format)
                // Memory safeguard: limit accumulation to prevent memory issues
                if (nonDataLines.length < MAX_NON_DATA_LINES_LENGTH) {
                    nonDataLines.append(line).append("\n")
                }
            }
        }

        // If no valid chunks were sent and we have non-data content, it might be an error
        if (validChunksSent == 0 && !errorDetected) {
            val nonDataContent = nonDataLines.toString().trim()
            if (nonDataContent.isNotEmpty()) {
                PluginLogger.Service.warn("[Chat-$requestId] No SSE data received. Non-data content: $nonDataContent")
                val extractedError = extractErrorFromContent(nonDataContent)
                sendErrorChunk(writer, extractedError ?: "Unexpected response format from model", requestId)
            } else {
                PluginLogger.Service.warn("[Chat-$requestId] Empty stream - no data received from model")
                sendErrorChunk(
                    writer,
                    "No response received from model. The model may be unavailable or doesn't support this request.",
                    requestId
                )
            }
        }

        sendDoneMarker(writer)
        PluginLogger.Service.debug(
            "[Chat-$requestId] Streaming completed: $validChunksSent chunks sent, error=$errorDetected"
        )
    }

    /**
     * Validation result for a streaming chunk
     */
    sealed class ChunkValidationResult {
        data class Valid(val json: JsonObject) : ChunkValidationResult()
        data class Invalid(val reason: String) : ChunkValidationResult()
        data class Error(val message: String) : ChunkValidationResult()
    }

    /**
     * Validates a chunk and writes it to the client if valid
     */
    private fun validateAndProcessChunk(data: String, writer: PrintWriter, requestId: String): ChunkValidationResult {
        return try {
            val json = gson.fromJson(data, JsonObject::class.java)

            // Check if this is an error response
            if (json.has("error")) {
                val errorObj = json.getAsJsonObject("error")
                val message = errorObj?.get("message")?.asString ?: "Unknown error from model"
                return ChunkValidationResult.Error(message)
            }

            // Validate required fields for OpenAI compatibility
            val missingFields = REQUIRED_CHUNK_FIELDS.filter { !json.has(it) }
            if (missingFields.isNotEmpty()) {
                PluginLogger.Service.debug("[Chat-$requestId] Chunk missing fields: $missingFields")
                // Don't reject - OpenRouter might use slightly different format
            }

            // Write the valid chunk
            writer.println("$DATA_PREFIX$data")
            writer.println()
            writer.flush()

            ChunkValidationResult.Valid(json)
        } catch (e: JsonSyntaxException) {
            PluginLogger.Service.warn("[Chat-$requestId] Invalid JSON in chunk: ${e.message}")
            ChunkValidationResult.Invalid("Invalid JSON: ${e.message}")
        }
    }

    /**
     * Sends an error chunk in OpenAI-compatible streaming format
     */
    private fun sendErrorChunk(writer: PrintWriter, message: String, requestId: String) {
        val errorChunk = createErrorStreamChunk(message, requestId)
        writer.println("$DATA_PREFIX$errorChunk")
        writer.println()
        writer.flush()
    }

    /**
     * Creates an OpenAI-compatible error chunk for streaming
     */
    private fun createErrorStreamChunk(message: String, requestId: String): String {
        val escapedMessage = message.replace("\"", "\\\"").replace("\n", "\\n")
        val chunkId = "chatcmpl-error-${UUID.randomUUID().toString().take(8)}"
        val timestamp = System.currentTimeMillis() / 1000

        return """{"id":"$chunkId","object":"chat.completion.chunk","created":$timestamp,"model":"error","choices":[{"index":0,"delta":{"role":"assistant","content":"$escapedMessage"},"finish_reason":"stop"}]}"""
    }

    /**
     * Sends the [DONE] marker with proper SSE format
     */
    private fun sendDoneMarker(writer: PrintWriter) {
        writer.println("$DATA_PREFIX$DONE_MARKER")
        writer.println()
        writer.flush()
    }

    /**
     * Extracts error message from non-SSE content (e.g., HTML error page, plain text error)
     */
    private fun extractErrorFromContent(content: String): String? {
        // Try to parse as JSON error
        return try {
            val json = gson.fromJson(content, JsonObject::class.java)
            json.getAsJsonObject("error")?.get("message")?.asString
        } catch (e: JsonSyntaxException) {
            PluginLogger.Service.debug("Content is not JSON, checking for error patterns: ${e.message}")
            // Not JSON - check for common error patterns using ErrorPatterns
            when {
                content.contains(
                    ErrorPatterns.RATE_LIMIT,
                    ignoreCase = true
                ) -> "Rate limit exceeded. Please try again later."
                content.contains(
                    ErrorPatterns.UNAUTHORIZED,
                    ignoreCase = true
                ) -> "Authentication failed. Please check your API key."
                content.contains(ErrorPatterns.NOT_FOUND, ignoreCase = true) -> "Model or endpoint not found."
                content.contains(ErrorPatterns.UNAVAILABLE, ignoreCase = true) -> "Service temporarily unavailable."
                content.contains(ErrorPatterns.TIMEOUT, ignoreCase = true) -> "Request timed out."
                content.length < 200 -> content // Short content might be an error message
                else -> null
            }
        }
    }

    /**
     * Enhances generic error messages with more helpful context
     */
    private fun enhanceErrorMessage(message: String): String {
        return when {
            // Generic provider error - make it more helpful
            message.equals("Provider returned error", ignoreCase = true) ||
                message.contains(ErrorPatterns.PROVIDER_ERROR, ignoreCase = true) &&
                message.contains(ErrorPatterns.ERROR, ignoreCase = true) ->
                "⚠️ The model provider encountered an error.\n\n" +
                    "This is usually a temporary issue. Try:\n" +
                    "• Waiting a moment and trying again\n" +
                    "• Switching to a different model\n" +
                    "• Using a non-free model if available"

            // Rate limiting
            message.contains(ErrorPatterns.RATE_LIMIT, ignoreCase = true) ||
                message.contains(ErrorPatterns.TOO_MANY_REQUESTS, ignoreCase = true) ->
                "⚠️ Rate limit exceeded.\n\n" +
                    "Please wait a moment before trying again.\n" +
                    "Free models have lower rate limits."

            // Timeout
            message.contains(ErrorPatterns.TIMEOUT, ignoreCase = true) ->
                "⚠️ Request timed out.\n\n" +
                    "The model took too long to respond. Try:\n" +
                    "• Sending a shorter message\n" +
                    "• Using a faster model"

            // Model unavailable
            message.contains(ErrorPatterns.UNAVAILABLE, ignoreCase = true) ||
                message.contains(ErrorPatterns.NO_ENDPOINTS_FOUND, ignoreCase = true) ->
                "⚠️ Model temporarily unavailable.\n\n" +
                    "Please try a different model or wait a moment."

            // Default - return original message
            else -> message
        }
    }

    /**
     * Handles exceptions during streaming by sending an OpenAI-compatible error chunk
     */
    fun handleStreamingError(e: Exception, writer: PrintWriter, requestId: String) {
        PluginLogger.Service.error("[Chat-$requestId] Error during streaming", e)
        val errorMessage = "Streaming error: ${e.message ?: "Unknown error"}"
        sendErrorChunk(writer, enhanceErrorMessage(errorMessage), requestId)
        sendDoneMarker(writer)
    }

    data class StreamingErrorContext(
        val response: Response,
        val resp: HttpServletResponse,
        val requestId: String
    )

    /**
     * Handles error responses from OpenRouter during streaming
     * Sends an OpenAI-compatible error chunk so AI Assistant can display the error
     */
    fun handleStreamingErrorResponse(context: StreamingErrorContext) {
        val errorBody = context.response.body?.string() ?: "Unknown error"
        PluginLogger.Service.error(
            "[Chat-${context.requestId}] OpenRouter streaming request failed: " +
                "status=${context.response.code}, body=$errorBody"
        )

        context.resp.status = context.response.code
        context.resp.contentType = "text/event-stream"
        context.resp.setHeader("Cache-Control", "no-cache")
        context.resp.setHeader("Connection", "keep-alive")

        val writer = context.resp.writer
        val userFriendlyMessage = createUserFriendlyErrorMessage(errorBody, context.response.code)

        // Send as OpenAI-compatible streaming chunk so AI Assistant can display it
        sendErrorChunk(writer, userFriendlyMessage, context.requestId)
        sendDoneMarker(writer)
    }

    private fun createUserFriendlyErrorMessage(errorBody: String, statusCode: Int): String {
        return try {
            val errorJson = gson.fromJson(errorBody, JsonObject::class.java)
            val errorObj = errorJson.getAsJsonObject("error")
            val message = errorObj?.get("message")?.asString ?: errorBody

            when (statusCode) {
                HTTP_UNAUTHORIZED -> "Authentication failed: $message"
                HTTP_PAYMENT_REQUIRED -> "Insufficient credits: $message"
                HTTP_TOO_MANY_REQUESTS -> "Rate limit exceeded: $message"
                HTTP_INTERNAL_SERVER_ERROR,
                HTTP_BAD_GATEWAY,
                HTTP_SERVICE_UNAVAILABLE -> "OpenRouter service error: $message"
                else -> message
            }
        } catch (e: JsonSyntaxException) {
            PluginLogger.Service.warn("Failed to parse error response", e)
            when (statusCode) {
                HTTP_UNAUTHORIZED -> "Authentication failed. Please check your API key."
                HTTP_PAYMENT_REQUIRED -> "Insufficient credits. Please add credits to your OpenRouter account."
                HTTP_TOO_MANY_REQUESTS -> "Rate limit exceeded. Please try again later."
                HTTP_INTERNAL_SERVER_ERROR,
                HTTP_BAD_GATEWAY,
                HTTP_SERVICE_UNAVAILABLE -> "OpenRouter service is temporarily unavailable. Please try again later."
                else -> "Request failed with status $statusCode"
            }
        }
    }
}
