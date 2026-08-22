package org.zhavoronkov.openrouter.proxy.servlets

import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
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
        assertEquals(0, completed.size)
    }

    @Test
    @DisplayName("T1: should emit tool_calls in index order, not lexical id order")
    fun testOrderingByIndexNotId() {
        val accumulator = ToolCallAccumulator()
        val gson = Gson()

        // Tool call at index 0 with id "call-10" (lexically > "call-2")
        val chunk1Json = """
            {
              "tool_calls": [
                {
                  "index": 0,
                  "id": "call-10",
                  "type": "function",
                  "function": { "name": "alpha", "arguments": "{}" }
                }
              ]
            }
        """.trimIndent()
        val chunk1Array = gson.fromJson(chunk1Json, JsonObject::class.java).getAsJsonArray("tool_calls")
        accumulator.processDeltaToolCalls(chunk1Array, null)

        // Tool call at index 1 with id "call-2" (lexically < "call-10")
        val chunk2Json = """
            {
              "tool_calls": [
                {
                  "index": 1,
                  "id": "call-2",
                  "type": "function",
                  "function": { "name": "beta", "arguments": "{}" }
                }
              ]
            }
        """.trimIndent()
        val chunk2Array = gson.fromJson(chunk2Json, JsonObject::class.java).getAsJsonArray("tool_calls")
        val completed = accumulator.processDeltaToolCalls(chunk2Array, "tool_calls")

        assertEquals(2, completed.size)
        // Index 0 should come first regardless of id ordering
        assertEquals("alpha", completed[0].function.name)
        assertEquals("call-10", completed[0].id)
        assertEquals("beta", completed[1].function.name)
        assertEquals("call-2", completed[1].id)
    }

    @Test
    @DisplayName("T2: should generate synthetic id when delta has no id field")
    fun testSyntheticIdFallback() {
        val accumulator = ToolCallAccumulator()
        val gson = Gson()

        // Delta with index but no id
        val chunkJson = """
            {
              "tool_calls": [
                {
                  "index": 0,
                  "function": { "name": "test_func", "arguments": "{}" }
                }
              ]
            }
        """.trimIndent()
        val toolCallsArray = gson.fromJson(chunkJson, JsonObject::class.java).getAsJsonArray("tool_calls")
        val completed = accumulator.processDeltaToolCalls(toolCallsArray, "tool_calls")

        assertEquals(1, completed.size)
        assertTrue(
            completed[0].id?.matches(Regex("^tool-[0-9a-f]{8}$")) == true,
            "Synthetic id should match ^tool-[0-9a-f]{8}\$ but was ${completed[0].id}"
        )
        assertEquals("test_func", completed[0].function.name)
    }

    @Test
    @DisplayName("T3: should default type to function when absent")
    fun testDefaultTypeFunction() {
        val accumulator = ToolCallAccumulator()
        val gson = Gson()

        // Delta without type field
        val chunkJson = """
            {
              "tool_calls": [
                {
                  "index": 0,
                  "id": "call-notype",
                  "function": { "name": "my_func", "arguments": "{}" }
                }
              ]
            }
        """.trimIndent()
        val toolCallsArray = gson.fromJson(chunkJson, JsonObject::class.java).getAsJsonArray("tool_calls")
        val completed = accumulator.processDeltaToolCalls(toolCallsArray, "tool_calls")

        assertEquals(1, completed.size)
        assertEquals("function", completed[0].type)
    }

    @Test
    @DisplayName("T4: should skip deltas with missing index field")
    fun testMissingIndexSkipped() {
        val accumulator = ToolCallAccumulator()
        val gson = Gson()

        // Delta without index field
        val chunkJson = """
            {
              "tool_calls": [
                {
                  "id": "call-noindex",
                  "function": { "name": "orphan", "arguments": "{}" }
                }
              ]
            }
        """.trimIndent()
        val toolCallsArray = gson.fromJson(chunkJson, JsonObject::class.java).getAsJsonArray("tool_calls")
        val completed = accumulator.processDeltaToolCalls(toolCallsArray, "tool_calls")

        // Should return empty because nothing was accumulated (no index = skipped)
        assertEquals(0, completed.size)
        assertFalse(accumulator.hasPending())
    }

    @Test
    @DisplayName("T5: should return empty list for empty array on finish")
    fun testEmptyArrayOnFinish() {
        val accumulator = ToolCallAccumulator()

        val emptyArray = JsonArray()
        val completed = accumulator.processDeltaToolCalls(emptyArray, "tool_calls")

        assertEquals(0, completed.size)
        assertFalse(accumulator.hasPending())
    }

    @Test
    @DisplayName("T5b: should emit accumulated calls when final chunk has null tool_calls but finish_reason=tool_calls")
    fun testEmitsOnFinishWithNullDelta() {
        // Regression for real-world OpenRouter streams: the final chunk carries
        // finish_reason="tool_calls" but delta.tool_calls is absent (only content:"").
        val accumulator = ToolCallAccumulator()
        val gson = Gson()

        // Accumulate a complete tool call across earlier chunks (no finish yet)
        val chunk1 = gson.fromJson(
            """{ "tool_calls": [ { "index": 0, "id": "call-final", "type": "function", "function": { "name": "get_weather", "arguments": "{\"city\":\"Paris\"}" } } ] }""",
            JsonObject::class.java
        ).getAsJsonArray("tool_calls")
        accumulator.processDeltaToolCalls(chunk1, null)
        assertTrue(accumulator.hasPending(), "Should have pending call before finish")

        // Final chunk: NO tool_calls in the delta, but finish_reason=tool_calls
        val completed = accumulator.processDeltaToolCalls(null, "tool_calls")

        assertEquals(1, completed.size, "Should emit accumulated call even with null delta")
        assertEquals("get_weather", completed[0].function.name)
        assertFalse(accumulator.hasPending(), "Should be cleared after emit")
    }
}

@DisplayName("StreamingResponseHandler Integration Tests")
class StreamingResponseHandlerIntegrationTest {

    private fun buildSseResponse(vararg chunks: String): Response {
        val body = chunks.joinToString("") { "data: $it\n\n" } + "data:\n\n"
        val responseBody: ResponseBody = body.toResponseBody("text/event-stream".toMediaType())
        return Response.Builder()
            .request(Request.Builder().url("http://localhost").build())
            .protocol(Protocol.HTTP_1_1)
            .code(200)
            .message("OK")
            .body(responseBody)
            .build()
    }

    @Test
    @DisplayName("T6: handler should assemble complete tool_call from multi-chunk stream")
    fun testHandlerAssemblesToolCall() {
        val chunk1 = """{"id":"chatcmpl-1","object":"chat.completion.chunk","created":1,"model":"gpt-4","choices":[{"index":0,"delta":{"role":"assistant","tool_calls":[{"index":0,"id":"call-abc","type":"function","function":{"name":"get_"}}]},"finish_reason":null}]}"""
        val chunk2 = """{"id":"chatcmpl-1","object":"chat.completion.chunk","created":1,"model":"gpt-4","choices":[{"index":0,"delta":{"tool_calls":[{"index":0,"function":{"name":"weather"}}]},"finish_reason":null}]}"""
        val chunk3 = """{"id":"chatcmpl-1","object":"chat.completion.chunk","created":1,"model":"gpt-4","choices":[{"index":0,"delta":{"tool_calls":[{"index":0,"function":{"arguments":"{\"loc\":\"NYC\"}"}}]},"finish_reason":"tool_calls"}]}"""

        val response = buildSseResponse(chunk1, chunk2, chunk3)
        val handler = StreamingResponseHandler()
        val output = StringWriter()
        val writer = PrintWriter(output)

        handler.streamResponseToClient(response, writer, "t6-req")

        // After streaming, the accumulator should have emitted and cleared
        val accumulator = handler.getAccumulatorForTesting()
        assertFalse(accumulator.hasPending(), "Accumulator should be empty after finish_reason=tool_calls")

        // Verify all chunks were forwarded
        val result = output.toString()
        assertTrue(result.contains("get_"))
        assertTrue(result.contains("weather"))
        assertTrue(result.contains("NYC"))
    }

    @Test
    @DisplayName("T7: handler should not crash on malformed tool_calls (object instead of array)")
    fun testMalformedToolCallsPayload() {
        // tool_calls is a JSON object instead of array — should trigger IllegalStateException catch
        val malformedChunk = """{"id":"chatcmpl-1","object":"chat.completion.chunk","created":1,"model":"gpt-4","choices":[{"index":0,"delta":{"tool_calls":{"not":"an_array"}},"finish_reason":null}]}"""
        val normalChunk = """{"id":"chatcmpl-1","object":"chat.completion.chunk","created":1,"model":"gpt-4","choices":[{"index":0,"delta":{"content":"hello"},"finish_reason":"stop"}]}"""

        val response = buildSseResponse(malformedChunk, normalChunk)
        val handler = StreamingResponseHandler()
        val output = StringWriter()
        val writer = PrintWriter(output)

        // Should not throw — malformed chunk is caught internally
        handler.streamResponseToClient(response, writer, "t7-req")

        val result = output.toString()
        // Both chunks should still be forwarded
        assertTrue(result.contains("not"), "Malformed chunk should still be forwarded")
        assertTrue(result.contains("hello"), "Normal chunk should still be forwarded")
        assertTrue(result.contains("[DONE]"), "DONE marker should still be emitted")
    }

    @Test
    @DisplayName("T8: handleStreamingError should reset accumulator")
    fun testResetOnError() {
        // First, feed some tool_call deltas to make accumulator pending
        val chunk1 = """{"id":"chatcmpl-1","object":"chat.completion.chunk","created":1,"model":"gpt-4","choices":[{"index":0,"delta":{"tool_calls":[{"index":0,"id":"call-err","type":"function","function":{"name":"partial"}}]},"finish_reason":null}]}"""

        val response = buildSseResponse(chunk1)
        val handler = StreamingResponseHandler()
        val output = StringWriter()
        val writer = PrintWriter(output)

        handler.streamResponseToClient(response, writer, "t8-req")

        // Accumulator should have pending state (no finish_reason=tool_calls was sent)
        assertTrue(handler.getAccumulatorForTesting().hasPending())

        // Now simulate an error
        val errorOutput = StringWriter()
        val errorWriter = PrintWriter(errorOutput)
        handler.handleStreamingError(RuntimeException("test error"), errorWriter, "t8-req")

        // Accumulator should be reset
        assertFalse(handler.getAccumulatorForTesting().hasPending())
    }

    @Test
    @DisplayName("T9: handler should handle finish_reason as JsonNull without crashing")
    fun testFinishReasonJsonNull() {
        // Explicit "finish_reason": null (JsonNull, not absent)
        val chunk = """{"id":"chatcmpl-1","object":"chat.completion.chunk","created":1,"model":"gpt-4","choices":[{"index":0,"delta":{"tool_calls":[{"index":0,"id":"call-null","type":"function","function":{"name":"func"}}]},"finish_reason":null}]}"""

        val response = buildSseResponse(chunk)
        val handler = StreamingResponseHandler()
        val output = StringWriter()
        val writer = PrintWriter(output)

        // Should not crash
        handler.streamResponseToClient(response, writer, "t9-req")

        // Accumulator should still be pending (null finish_reason means keep accumulating)
        assertTrue(handler.getAccumulatorForTesting().hasPending())
        assertTrue(output.toString().contains("call-null"))
    }

    @Test
    @DisplayName("T10: handler should assemble multiple tool_calls across multiple chunks")
    fun testMultiToolCallStream() {
        // Chunk 1: first tool call starts
        val chunk1 = """{"id":"chatcmpl-1","object":"chat.completion.chunk","created":1,"model":"gpt-4","choices":[{"index":0,"delta":{"role":"assistant","tool_calls":[{"index":0,"id":"call-a","type":"function","function":{"name":"get_weather","arguments":""}}]},"finish_reason":null}]}"""
        // Chunk 2: first tool call args + second tool call starts
        val chunk2 = """{"id":"chatcmpl-1","object":"chat.completion.chunk","created":1,"model":"gpt-4","choices":[{"index":0,"delta":{"tool_calls":[{"index":0,"function":{"arguments":"{\"city\":\"NYC\"}"}},{"index":1,"id":"call-b","type":"function","function":{"name":"get_time","arguments":""}}]},"finish_reason":null}]}"""
        // Chunk 3: second tool call args + finish
        val chunk3 = """{"id":"chatcmpl-1","object":"chat.completion.chunk","created":1,"model":"gpt-4","choices":[{"index":0,"delta":{"tool_calls":[{"index":1,"function":{"arguments":"{\"tz\":\"UTC\"}"}}]},"finish_reason":"tool_calls"}]}"""

        val response = buildSseResponse(chunk1, chunk2, chunk3)
        val handler = StreamingResponseHandler()
        val output = StringWriter()
        val writer = PrintWriter(output)

        handler.streamResponseToClient(response, writer, "t10-req")

        // Accumulator should be empty (emitted and cleared)
        assertFalse(handler.getAccumulatorForTesting().hasPending())

        // All content forwarded
        val result = output.toString()
        assertTrue(result.contains("get_weather"))
        assertTrue(result.contains("get_time"))
        assertTrue(result.contains("NYC"))
        assertTrue(result.contains("UTC"))
    }

    @Test
    @DisplayName("T11: handler should forward interleaved content and tool_call deltas")
    fun testInterleavedContentAndToolCalls() {
        // Chunk 1: content delta (thinking)
        val chunk1 = """{"id":"chatcmpl-1","object":"chat.completion.chunk","created":1,"model":"gpt-4","choices":[{"index":0,"delta":{"role":"assistant","content":"Let me check"},"finish_reason":null}]}"""
        // Chunk 2: tool_call delta starts
        val chunk2 = """{"id":"chatcmpl-1","object":"chat.completion.chunk","created":1,"model":"gpt-4","choices":[{"index":0,"delta":{"tool_calls":[{"index":0,"id":"call-interleave","type":"function","function":{"name":"lookup"}}]},"finish_reason":null}]}"""
        // Chunk 3: tool_call args + finish
        val chunk3 = """{"id":"chatcmpl-1","object":"chat.completion.chunk","created":1,"model":"gpt-4","choices":[{"index":0,"delta":{"tool_calls":[{"index":0,"function":{"arguments":"{\"q\":\"test\"}"}}]},"finish_reason":"tool_calls"}]}"""

        val response = buildSseResponse(chunk1, chunk2, chunk3)
        val handler = StreamingResponseHandler()
        val output = StringWriter()
        val writer = PrintWriter(output)

        handler.streamResponseToClient(response, writer, "t11-req")

        val result = output.toString()
        // Content should be forwarded
        assertTrue(result.contains("Let me check"), "Content delta should be forwarded")
        // Tool call should be forwarded
        assertTrue(result.contains("lookup"), "Tool call name should be forwarded")
        assertTrue(result.contains("test"), "Tool call args should be forwarded")
        // Accumulator should be clear
        assertFalse(handler.getAccumulatorForTesting().hasPending())
    }
}
