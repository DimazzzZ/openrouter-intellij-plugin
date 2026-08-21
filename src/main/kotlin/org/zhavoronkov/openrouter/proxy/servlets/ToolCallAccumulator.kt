package org.zhavoronkov.openrouter.proxy.servlets

import org.zhavoronkov.openrouter.proxy.models.OpenAIChatToolCall
import org.zhavoronkov.openrouter.proxy.models.OpenAIChatToolCallFunction
import org.zhavoronkov.openrouter.utils.PluginLogger
import java.util.UUID

/**
 * Accumulates streaming tool_call deltas across chunks.
 * Tool calls can span multiple SSE chunks; this class reassembles them into complete tool_calls.
 *
 * OpenAI streaming format emits tool_calls as deltas:
 * - First chunk: {"delta": {"tool_calls": [{"index": 0, "id": "call-123", "type": "function", "function": {"name": "get_"}}]}}
 * - Second chunk: {"delta": {"tool_calls": [{"index": 0, "function": {"name": "weather"}}]}}
 * - Final chunk: {"delta": {"tool_calls": [{"index": 0, "function": {"arguments": "{...}"}}]}, "finish_reason": "tool_calls"}
 *
 * This accumulator collects fragments by index and emits complete tool_calls when finish_reason indicates completion.
 */
class ToolCallAccumulator {
    // Map of tool_call index → accumulated partial tool call
    private val accumulatedCalls = mutableMapOf<Int, PartialToolCall>()

    /**
     * Represents a tool call being accumulated across multiple chunks
     */
    private data class PartialToolCall(
        val id: String,
        val type: String = "function",
        var functionName: String = "",
        var functionArguments: String = ""
    )

    /**
     * Process delta.tool_calls from a streaming chunk.
     *
     * @param toolCallDeltasJson JsonArray of tool_call deltas from the chunk
     * @param finishReason finish_reason from the choice (null if streaming continues)
     * @return List of COMPLETE tool_calls ready to emit, or empty if still accumulating
     */
    fun processDeltaToolCalls(
        toolCallDeltasJson: com.google.gson.JsonArray?,
        finishReason: String?
    ): List<OpenAIChatToolCall> {
        if (toolCallDeltasJson == null || toolCallDeltasJson.size() == 0) {
            return emptyList()
        }

        // Process each delta in the array
        for (i in 0 until toolCallDeltasJson.size()) {
            val deltaObj = toolCallDeltasJson[i].asJsonObject
            val index = deltaObj.get("index")?.asInt ?: continue

            // Get or create partial tool call for this index
            val partial = accumulatedCalls.getOrPut(index) {
                val id = deltaObj.get("id")?.asString ?: "tool-${UUID.randomUUID().toString().take(8)}"
                val type = deltaObj.get("type")?.asString ?: "function"
                PartialToolCall(id = id, type = type)
            }

            // Accumulate function name and arguments
            val functionObj = deltaObj.getAsJsonObject("function")
            if (functionObj != null) {
                if (functionObj.has("name")) {
                    partial.functionName += functionObj.get("name").asString
                }
                if (functionObj.has("arguments")) {
                    partial.functionArguments += functionObj.get("arguments").asString
                }
            }
        }

        // If finish_reason indicates completion, emit all accumulated tool_calls
        if (finishReason == "tool_calls") {
            val completed = accumulatedCalls.values
                .sortedBy { it.id } // Stable ordering
                .map { partial ->
                    OpenAIChatToolCall(
                        id = partial.id,
                        type = partial.type,
                        function = OpenAIChatToolCallFunction(
                            name = partial.functionName,
                            arguments = partial.functionArguments
                        )
                    )
                }

            PluginLogger.Service.debug(
                "Tool call accumulation complete: emitting ${completed.size} tool_calls"
            )

            accumulatedCalls.clear()
            return completed
        }

        return emptyList()
    }

    /**
     * Reset the accumulator (called on stream start or error)
     */
    fun reset() {
        accumulatedCalls.clear()
    }

    /**
     * Check if there are pending accumulated tool_calls (for debugging)
     */
    fun hasPending(): Boolean = accumulatedCalls.isNotEmpty()
}
