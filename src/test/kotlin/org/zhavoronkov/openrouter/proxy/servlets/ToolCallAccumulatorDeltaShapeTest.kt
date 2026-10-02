package org.zhavoronkov.openrouter.proxy.servlets

import com.google.gson.JsonArray
import com.google.gson.JsonParser
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("ToolCallAccumulator delta shapes")
class ToolCallAccumulatorDeltaShapeTest {

    private fun deltas(json: String): JsonArray = JsonParser.parseString(json).asJsonArray

    @Test
    @DisplayName("a delta that opens a tool call before its function arrives keeps its id and type")
    fun deltaWithoutFunction() {
        val accumulator = ToolCallAccumulator()

        accumulator.processDeltaToolCalls(deltas("""[{"index":0,"id":"call-1","type":"function"}]"""), null)
        val completed = accumulator.processDeltaToolCalls(
            deltas("""[{"index":0,"function":{"name":"read_file","arguments":"{}"}}]"""),
            "tool_calls"
        )

        assertEquals(1, completed.size)
        assertEquals("call-1", completed[0].id)
        assertEquals("function", completed[0].type)
        assertEquals("read_file", completed[0].function.name)
        assertEquals("{}", completed[0].function.arguments)
    }

    @Test
    @DisplayName("a tool call whose deltas never carry a function completes with an empty one")
    fun neverAFunction() {
        val accumulator = ToolCallAccumulator()

        val completed = accumulator.processDeltaToolCalls(
            deltas("""[{"index":0,"id":"call-1","type":"custom"}]"""),
            "tool_calls"
        )

        assertEquals("custom", completed.single().type)
        assertEquals("", completed.single().function.name)
        assertEquals("", completed.single().function.arguments)
    }

    @Test
    @DisplayName("a first delta whose id and type are null gets a generated id and the function type")
    fun nullIdAndType() {
        val accumulator = ToolCallAccumulator()

        val completed = accumulator.processDeltaToolCalls(
            deltas("""[{"index":0,"id":null,"type":null,"function":{"name":"read_file","arguments":"{}"}}]"""),
            "tool_calls"
        )

        assertTrue(completed.single().id.orEmpty().startsWith("tool-"), "got ${completed.single().id}")
        assertEquals("function", completed.single().type)
        assertEquals("read_file", completed.single().function.name)
    }
}
