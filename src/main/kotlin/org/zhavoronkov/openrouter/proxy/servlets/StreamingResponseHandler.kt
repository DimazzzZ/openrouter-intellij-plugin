package org.zhavoronkov.openrouter.proxy.servlets

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonSyntaxException
import okhttp3.Response
import org.zhavoronkov.openrouter.requests.RequestTrace
import org.zhavoronkov.openrouter.utils.ErrorPatterns
import org.zhavoronkov.openrouter.utils.PluginLogger
import org.zhavoronkov.openrouter.utils.asObjectOrNull
import org.zhavoronkov.openrouter.utils.asStringOrNull
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

        // Required fields for OpenAI streaming chunk
        private val REQUIRED_CHUNK_FIELDS = listOf("id", "object", "created", "model", "choices")

        // Memory safeguard: limit non-data lines accumulation to prevent memory issues
        private const val MAX_NON_DATA_LINES_LENGTH = 10_000
    }

    private val gson = Gson()
    private val toolCallAccumulator = ToolCallAccumulator()

    /** Exposed for testing only — allows assertions on accumulator state after streaming. */
    @Suppress("unused")
    internal fun getAccumulatorForTesting(): ToolCallAccumulator = toolCallAccumulator

    /**
     * [trace], when given, is told what each chunk carries and any error in the stream, so the
     * request's Requests entry reads the same as for a whole response. It is fed from the chunks
     * already being forwarded, so it never delays the stream.
     *
     * [shownModel], when given, is the model id each chunk names to the client in place of the
     * one OpenRouter sent - a pair's id, so a Consumer's stream names what it asked for, as a
     * whole reply does. The trace still sees the model that answered.
     */
    fun streamResponseToClient(
        response: Response,
        writer: PrintWriter,
        requestId: String,
        trace: RequestTrace? = null,
        shownModel: String? = null
    ) {
        // Reset accumulator state for this stream
        toolCallAccumulator.reset()
        response.body?.use { responseBody ->
            val reader = BufferedReader(responseBody.charStream())
            processStreamLines(reader, writer, requestId, trace, shownModel)
        } ?: run {
            // No response body - send error chunk
            PluginLogger.Service.warn("[Chat-$requestId] Empty response body from OpenRouter")
            sendErrorChunk(
                writer,
                "No response received from model. The model may not support this request type.",
                trace
            )
            sendDoneMarker(writer)
        }
    }

    @Suppress("CyclomaticComplexMethod")
    private fun processStreamLines(
        reader: BufferedReader,
        writer: PrintWriter,
        requestId: String,
        trace: RequestTrace?,
        shownModel: String?
    ) {
        var validChunksSent = 0
        var errorDetected = false
        val nonDataLines = StringBuilder()

        while (true) {
            val line = reader.readLine() ?: break

            if (line.startsWith(DATA_PREFIX)) {
                val data = line.substring(DATA_PREFIX_LENGTH)

                if (data == DONE_MARKER) {
                    break
                }

                // Validate and process the chunk
                val validationResult = validateAndProcessChunk(data, writer, requestId, shownModel)
                when (validationResult) {
                    is ChunkValidationResult.Valid -> {
                        validChunksSent++
                        trace?.observe(validationResult.json)
                    }
                    is ChunkValidationResult.Error -> {
                        errorDetected = true
                        forwardStreamError(validationResult.message, writer, requestId, trace)
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

        if (validChunksSent == 0 && !errorDetected) {
            reportEmptyStream(nonDataLines.toString().trim(), writer, requestId, trace)
        }

        sendDoneMarker(writer)
        PluginLogger.Service.debug(
            "[Chat-$requestId] Streaming completed: $validChunksSent chunks sent, error=$errorDetected"
        )
    }

    /**
     * An error chunk from upstream, sent on as an OpenAI-compatible `chat.completion.chunk` - AI
     * Assistant expects that shape, not raw error JSON - with a generic provider error made more
     * helpful.
     */
    private fun forwardStreamError(message: String, writer: PrintWriter, requestId: String, trace: RequestTrace?) {
        trace?.fail(message)
        PluginLogger.Service.warn("[Chat-$requestId] Error in stream: $message")
        sendErrorChunk(writer, enhanceErrorMessage(message))
    }

    /**
     * A stream that sent no valid chunk: what it sent instead, [nonDataContent], may be an error,
     * which is passed on; with nothing at all, the client is told no response came.
     */
    private fun reportEmptyStream(
        nonDataContent: String,
        writer: PrintWriter,
        requestId: String,
        trace: RequestTrace?
    ) {
        if (nonDataContent.isNotEmpty()) {
            PluginLogger.Service.warn("[Chat-$requestId] No SSE data received. Non-data content: $nonDataContent")
            val extractedError = extractErrorFromContent(nonDataContent)
            sendErrorChunk(writer, extractedError ?: "Unexpected response format from model", trace)
        } else {
            PluginLogger.Service.warn("[Chat-$requestId] Empty stream - no data received from model")
            sendErrorChunk(
                writer,
                "No response received from model. The model may be unavailable or doesn't support this request.",
                trace
            )
        }
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
    private fun validateAndProcessChunk(
        data: String,
        writer: PrintWriter,
        requestId: String,
        shownModel: String?
    ): ChunkValidationResult {
        return try {
            val json = gson.fromJson(data, JsonObject::class.java)

            // Check if this is an error response
            if (json.has("error")) {
                // Usually {"message": ...}, but a provider may send the message as the error itself
                val error = json.get("error")
                val message = error.asStringOrNull()
                    ?: error.asObjectOrNull()?.get("message")?.asStringOrNull()
                    ?: "Unknown error from model"
                return ChunkValidationResult.Error(message)
            }

            // Validate required fields for OpenAI compatibility
            val missingFields = REQUIRED_CHUNK_FIELDS.filter { !json.has(it) }
            if (missingFields.isNotEmpty()) {
                PluginLogger.Service.debug("[Chat-$requestId] Chunk missing fields: $missingFields")
                // Don't reject - OpenRouter might use slightly different format
            }

            // Process tool_call deltas (if present) for observability and accumulation.
            // The chunk is still forwarded as-is to preserve streaming latency; the accumulator
            // is used to track state and can be inspected for verification/logging.
            processToolCallDeltas(json, requestId)

            // Write the valid chunk, naming the model the client asked for when that differs
            val written = if (shownModel != null && json.has("model")) {
                gson.toJson(json.deepCopy().apply { addProperty("model", shownModel) })
            } else {
                data
            }
            writer.println("$DATA_PREFIX$written")
            writer.println()
            writer.flush()

            ChunkValidationResult.Valid(json)
        } catch (e: JsonSyntaxException) {
            PluginLogger.Service.warn("[Chat-$requestId] Invalid JSON in chunk: ${e.message}")
            ChunkValidationResult.Invalid("Invalid JSON: ${e.message}")
        }
    }

    /**
     * Inspect a streaming chunk for delta.tool_calls and feed them to the accumulator.
     *
     * This provides observability for tool-calling agent workflows and ensures the accumulator
     * assembles complete tool_calls when finish_reason == "tool_calls". The chunk is still
     * forwarded verbatim to preserve OpenAI-compatible streaming behavior.
     */
    private fun processToolCallDeltas(json: JsonObject, requestId: String) {
        try {
            val choices = json.getAsJsonArray("choices") ?: return
            if (choices.size() == 0) return

            val choice = choices[0].asJsonObject
            val delta = choice.getAsJsonObject("delta")
            val toolCallsArray = delta?.getAsJsonArray("tool_calls")
            val finishReasonElement = choice.get("finish_reason")
            val finishReason = if (finishReasonElement != null && !finishReasonElement.isJsonNull) {
                finishReasonElement.asString
            } else {
                null
            }

            if (toolCallsArray != null || finishReason == "tool_calls") {
                val completed = toolCallAccumulator.processDeltaToolCalls(toolCallsArray, finishReason)
                if (completed.isNotEmpty()) {
                    PluginLogger.Service.debug(
                        "[Chat-$requestId] Assembled ${completed.size} complete tool_call(s) from stream"
                    )
                }
            }
        } catch (e: IllegalStateException) {
            // Unexpected JSON shape - log but don't fail the stream
            PluginLogger.Service.debug("[Chat-$requestId] Could not parse tool_calls from chunk: ${e.message}")
        } catch (e: Exception) {
            // Unexpected JSON shape (ClassCastException, etc.) - log but don't fail the stream
            PluginLogger.Service.debug("[Chat-$requestId] Could not parse tool_calls from chunk: ${e.message}")
        }
    }

    /**
     * Sends an error chunk in OpenAI-compatible streaming format
     */
    /** Sends [message] as an error chunk, and reports it to [trace] as the request's error. */
    private fun sendErrorChunk(writer: PrintWriter, message: String, trace: RequestTrace? = null) {
        trace?.fail(message)
        val errorChunk = createErrorStreamChunk(message)
        writer.println("$DATA_PREFIX$errorChunk")
        writer.println()
        writer.flush()
    }

    /**
     * Creates an OpenAI-compatible error chunk for streaming
     */
    private fun createErrorStreamChunk(message: String): String {
        val escapedMessage = message.replace("\"", "\\\"").replace("\n", "\\n")
        val chunkId = "chatcmpl-error-${UUID.randomUUID().toString().take(8)}"
        val timestamp = System.currentTimeMillis() / 1000

        return """{"id":"$chunkId","object":"chat.completion.chunk","created":$timestamp,"model":"error","choices":[{"index":0,"delta":{"role":"assistant","content":"$escapedMessage"},"finish_reason":"stop"}]}"""
    }

    /**
     * Sends the \[DONE\] marker with proper SSE format
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
            gson.fromJson(content, JsonObject::class.java)
                ?.get("error")?.asObjectOrNull()?.get("message")?.asStringOrNull()
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
        toolCallAccumulator.reset()
        val errorMessage = "Streaming error: ${e.message ?: "Unknown error"}"
        sendErrorChunk(writer, enhanceErrorMessage(errorMessage))
        sendDoneMarker(writer)
    }
}
