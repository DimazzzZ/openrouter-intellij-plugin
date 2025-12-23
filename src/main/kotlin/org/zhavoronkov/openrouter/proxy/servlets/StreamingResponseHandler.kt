package org.zhavoronkov.openrouter.proxy.servlets

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonSyntaxException
import jakarta.servlet.http.HttpServletResponse
import okhttp3.Response
import org.zhavoronkov.openrouter.utils.PluginLogger
import java.io.BufferedReader
import java.io.PrintWriter

/**
 * Handles streaming responses from OpenRouter
 */
class StreamingResponseHandler {

    companion object {
        private const val DATA_PREFIX = "data: "
        private const val DATA_PREFIX_LENGTH = 6
        private const val HTTP_UNAUTHORIZED = 401
        private const val HTTP_PAYMENT_REQUIRED = 402
        private const val HTTP_TOO_MANY_REQUESTS = 429
        private const val HTTP_INTERNAL_SERVER_ERROR = 500
        private const val HTTP_BAD_GATEWAY = 502
        private const val HTTP_SERVICE_UNAVAILABLE = 503
    }

    private val gson = Gson()

    fun streamResponseToClient(response: Response, writer: PrintWriter, requestId: String) {
        response.body?.use { responseBody ->
            val reader = BufferedReader(responseBody.charStream())
            processStreamLines(reader, writer, requestId)
        }
    }

    private fun processStreamLines(reader: BufferedReader, writer: PrintWriter, requestId: String) {
        var shouldContinue = true
        while (shouldContinue) {
            val line = reader.readLine() ?: break
            if (processStreamLine(line, writer)) {
                shouldContinue = false
            }
        }
        writer.println("data: [DONE]")
        writer.println()
        writer.flush()
        PluginLogger.Service.debug("[$requestId] Streaming completed successfully")
    }

    private fun processStreamLine(line: String, writer: PrintWriter): Boolean {
        if (line.startsWith(DATA_PREFIX)) {
            val data = line.substring(DATA_PREFIX_LENGTH)
            if (data == "[DONE]") {
                return true
            }
            // SSE format requires: "data: <content>\n\n" (blank line after each event)
            // println() adds one newline, then we add another for the blank line
            writer.println(line)
            writer.println() // Required blank line to separate SSE events
            writer.flush()
        }
        return false
    }

    fun handleStreamingError(e: Exception, writer: PrintWriter, requestId: String) {
        PluginLogger.Service.error("[$requestId] Error during streaming", e)
        val errorJson = """{"error": {"message": "Streaming error: ${e.message}", "type": "stream_error"}}"""
        writer.println("data: $errorJson")
        writer.println() // Blank line to separate SSE events
        writer.println("data: [DONE]")
        writer.println() // Blank line after final event
        writer.flush()
    }

    data class StreamingErrorContext(
        val response: Response,
        val resp: HttpServletResponse,
        val requestId: String
    )

    fun handleStreamingErrorResponse(context: StreamingErrorContext) {
        val errorBody = context.response.body?.string() ?: "Unknown error"
        PluginLogger.Service.error(
            "[${context.requestId}] OpenRouter streaming request failed: " +
                "status=${context.response.code}, body=$errorBody"
        )

        context.resp.status = context.response.code
        context.resp.contentType = "text/event-stream"
        context.resp.setHeader("Cache-Control", "no-cache")
        context.resp.setHeader("Connection", "keep-alive")

        val writer = context.resp.writer
        val userFriendlyMessage = createUserFriendlyErrorMessage(errorBody, context.response.code)
        val errorJson =
            """
            {"error": {
                "message": "$userFriendlyMessage",
                "type": "api_error",
                "code": "${context.response.code}"
            }}
            """.trimIndent().replace("\n", "")

        writer.println("data: $errorJson")
        writer.println() // Blank line to separate SSE events
        writer.println("data: [DONE]")
        writer.println() // Blank line after final event
        writer.flush()
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
