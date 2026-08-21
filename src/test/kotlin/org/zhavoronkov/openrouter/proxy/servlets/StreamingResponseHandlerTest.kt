package org.zhavoronkov.openrouter.proxy.servlets

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.io.PrintWriter
import java.io.StringWriter

@DisplayName("StreamingResponseHandler Tests")
class StreamingResponseHandlerTest {

    @Test
    fun `streamResponseToClient should emit done marker`() {
        val bodyContent = """
            data: {\"id\":\"chatcmpl-1\",\"object\":\"chat.completion.chunk\",\"created\":1,\"model\":\"gpt\",\"choices\":[{\"index\":0,\"delta\":{\"role\":\"assistant\",\"content\":\"Hi\"}}]}

            data: [DONE]

        """.trimIndent()
        val responseBody: ResponseBody = bodyContent.toResponseBody("text/event-stream".toMediaType())
        val response = Response.Builder()
            .request(Request.Builder().url("http://localhost").build())
            .protocol(Protocol.HTTP_1_1)
            .code(200)
            .message("OK")
            .body(responseBody)
            .build()

        val handler = StreamingResponseHandler()
        val output = StringWriter()
        val writer = PrintWriter(output)

        handler.streamResponseToClient(response, writer, "req-1")

        assertTrue(output.toString().contains("[DONE]"))
    }

    @Test
    @DisplayName("should forward tool_call streaming chunks verbatim")
    fun testStreamingWithToolCalls() {
        // Simulate OpenRouter streaming a tool call in multiple chunks
        val chunk1 = """{"id":"chatcmpl-1","object":"chat.completion.chunk","created":1,"model":"gpt-4","choices":[{"index":0,"delta":{"role":"assistant","tool_calls":[{"index":0,"id":"call-abc","type":"function","function":{"name":"get_"}}]},"finish_reason":null}]}"""
        val chunk2 = """{"id":"chatcmpl-1","object":"chat.completion.chunk","created":1,"model":"gpt-4","choices":[{"index":0,"delta":{"tool_calls":[{"index":0,"function":{"name":"weather"}}]},"finish_reason":null}]}"""
        val chunk3 = """{"id":"chatcmpl-1","object":"chat.completion.chunk","created":1,"model":"gpt-4","choices":[{"index":0,"delta":{"tool_calls":[{"index":0,"function":{"arguments":"{\"loc\":\"NYC\"}"}}]},"finish_reason":"tool_calls"}]}"""

        val bodyContent = "data: $chunk1\n\ndata: $chunk2\n\ndata: $chunk3\n\ndata: [DONE]\n\n"
        val responseBody: ResponseBody = bodyContent.toResponseBody("text/event-stream".toMediaType())
        val response = Response.Builder()
            .request(Request.Builder().url("http://localhost").build())
            .protocol(Protocol.HTTP_1_1)
            .code(200)
            .message("OK")
            .body(responseBody)
            .build()

        val handler = StreamingResponseHandler()
        val output = StringWriter()
        val writer = PrintWriter(output)

        handler.streamResponseToClient(response, writer, "tool-req-1")

        val result = output.toString()

        // All chunks should be forwarded (preserves streaming latency)
        assertTrue(result.contains("call-abc"), "First chunk with tool_call id should be forwarded")
        assertTrue(result.contains("get_"), "Function name fragment should be forwarded")
        assertTrue(result.contains("weather"), "Function name continuation should be forwarded")
        assertTrue(result.contains("NYC"), "Function arguments should be forwarded")
        assertTrue(result.contains("tool_calls"), "finish_reason should be forwarded")
        assertTrue(result.contains("[DONE]"), "DONE marker should be emitted")
    }
}

@DisplayName("ToolCallAccumulator Tests")
class ToolCallAccumulatorTest {

    @Test
    @DisplayName("should accumulate single-chunk tool_call")
    fun testSingleChunkToolCall() {
        val accumulator = ToolCallAccumulator()
        val gson = com.google.gson.Gson()

        // Single chunk with complete tool_call
        val chunkJson = """
            {
              "tool_calls": [
                {
                  "index": 0,
                  "id": "call-abc123",
                  "type": "function",
                  "function": {
                    "name": "get_weather",
                    "arguments": "{\"location\":\"NYC\"}"
                  }
                }
              ]
            }
        """.trimIndent()

        val toolCallsArray = gson.fromJson(chunkJson, com.google.gson.JsonObject::class.java)
            .getAsJsonArray("tool_calls")

        // Process with finish_reason = "tool_calls" to emit
        val completed = accumulator.processDeltaToolCalls(toolCallsArray, "tool_calls")

        org.junit.jupiter.api.Assertions.assertEquals(1, completed.size)
        org.junit.jupiter.api.Assertions.assertEquals("call-abc123", completed[0].id)
        org.junit.jupiter.api.Assertions.assertEquals("get_weather", completed[0].function.name)
        org.junit.jupiter.api.Assertions.assertEquals("{\"location\":\"NYC\"}", completed[0].function.arguments)
    }

    @Test
    @DisplayName("should accumulate multi-chunk tool_call fragments")
    fun testMultiChunkToolCall() {
        val accumulator = ToolCallAccumulator()
        val gson = com.google.gson.Gson()

        // Chunk 1: tool_call id and start of function name
        val chunk1Json = """
            {
              "tool_calls": [
                {
                  "index": 0,
                  "id": "call-xyz789",
                  "type": "function",
                  "function": {
                    "name": "get_"
                  }
                }
              ]
            }
        """.trimIndent()

        val chunk1Array = gson.fromJson(chunk1Json, com.google.gson.JsonObject::class.java)
            .getAsJsonArray("tool_calls")
        accumulator.processDeltaToolCalls(chunk1Array, null)

        // Chunk 2: continue function name
        val chunk2Json = """
            {
              "tool_calls": [
                {
                  "index": 0,
                  "function": {
                    "name": "weather"
                  }
                }
              ]
            }
        """.trimIndent()

        val chunk2Array = gson.fromJson(chunk2Json, com.google.gson.JsonObject::class.java)
            .getAsJsonArray("tool_calls")
        accumulator.processDeltaToolCalls(chunk2Array, null)

        // Chunk 3: function arguments and finish_reason
        val chunk3Json = """
            {
              "tool_calls": [
                {
                  "index": 0,
                  "function": {
                    "arguments": "{\"location\":\"NYC\",\"unit\":\"F\"}"
                  }
                }
              ]
            }
        """.trimIndent()

        val chunk3Array = gson.fromJson(chunk3Json, com.google.gson.JsonObject::class.java)
            .getAsJsonArray("tool_calls")
        val completed = accumulator.processDeltaToolCalls(chunk3Array, "tool_calls")

        org.junit.jupiter.api.Assertions.assertEquals(1, completed.size)
        org.junit.jupiter.api.Assertions.assertEquals("call-xyz789", completed[0].id)
        org.junit.jupiter.api.Assertions.assertEquals("get_weather", completed[0].function.name)
        org.junit.jupiter.api.Assertions.assertTrue(
            completed[0].function.arguments.contains("NYC")
        )
    }

    @Test
    @DisplayName("should accumulate multiple tool_calls in same stream")
    fun testMultipleToolCalls() {
        val accumulator = ToolCallAccumulator()
        val gson = com.google.gson.Gson()

        // First tool_call
        val chunk1Json = """
            {
              "tool_calls": [
                {
                  "index": 0,
                  "id": "call-1",
                  "type": "function",
                  "function": {
                    "name": "get_weather",
                    "arguments": "{\"location\":\"NYC\"}"
                  }
                }
              ]
            }
        """.trimIndent()

        val chunk1Array = gson.fromJson(chunk1Json, com.google.gson.JsonObject::class.java)
            .getAsJsonArray("tool_calls")
        accumulator.processDeltaToolCalls(chunk1Array, null)

        // Second tool_call
        val chunk2Json = """
            {
              "tool_calls": [
                {
                  "index": 1,
                  "id": "call-2",
                  "type": "function",
                  "function": {
                    "name": "get_time",
                    "arguments": "{\"timezone\":\"UTC\"}"
                  }
                }
              ]
            }
        """.trimIndent()

        val chunk2Array = gson.fromJson(chunk2Json, com.google.gson.JsonObject::class.java)
            .getAsJsonArray("tool_calls")
        val completed = accumulator.processDeltaToolCalls(chunk2Array, "tool_calls")

        org.junit.jupiter.api.Assertions.assertEquals(2, completed.size)
        org.junit.jupiter.api.Assertions.assertEquals("call-1", completed[0].id)
        org.junit.jupiter.api.Assertions.assertEquals("call-2", completed[1].id)
    }

    @Test
    @DisplayName("should not emit tool_calls until finish_reason is tool_calls")
    fun testNoEmitWithoutFinishReason() {
        val accumulator = ToolCallAccumulator()
        val gson = com.google.gson.Gson()

        val chunkJson = """
            {
              "tool_calls": [
                {
                  "index": 0,
                  "id": "call-1",
                  "type": "function",
                  "function": {
                    "name": "func",
                    "arguments": "{}"
                  }
                }
              ]
            }
        """.trimIndent()

        val toolCallsArray = gson.fromJson(chunkJson, com.google.gson.JsonObject::class.java)
            .getAsJsonArray("tool_calls")

        // Process without finish_reason
        val completed = accumulator.processDeltaToolCalls(toolCallsArray, null)
        org.junit.jupiter.api.Assertions.assertEquals(0, completed.size)

        // Process with finish_reason = "stop" (not "tool_calls")
        val completed2 = accumulator.processDeltaToolCalls(null, "stop")
        org.junit.jupiter.api.Assertions.assertEquals(0, completed2.size)
    }

    @Test
    @DisplayName("should reset accumulator state")
    fun testReset() {
        val accumulator = ToolCallAccumulator()
        val gson = com.google.gson.Gson()

        val chunkJson = """
            {
              "tool_calls": [
                {
                  "index": 0,
                  "id": "call-1",
                  "type": "function",
                  "function": {
                    "name": "func",
                    "arguments": "{}"
                  }
                }
              ]
            }
        """.trimIndent()

        val toolCallsArray = gson.fromJson(chunkJson, com.google.gson.JsonObject::class.java)
            .getAsJsonArray("tool_calls")

        accumulator.processDeltaToolCalls(toolCallsArray, null)
        org.junit.jupiter.api.Assertions.assertTrue(accumulator.hasPending())

        accumulator.reset()
        org.junit.jupiter.api.Assertions.assertFalse(accumulator.hasPending())
    }

    @Test
    @DisplayName("should handle null tool_calls gracefully")
    fun testNullToolCalls() {
        val accumulator = ToolCallAccumulator()

        val completed = accumulator.processDeltaToolCalls(null, "tool_calls")
        org.junit.jupiter.api.Assertions.assertEquals(0, completed.size)
    }
}
