package org.zhavoronkov.openrouter.toolwindow.agent

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.zhavoronkov.openrouter.models.ChatCompletionRequest
import org.zhavoronkov.openrouter.models.ChatMessage
import org.zhavoronkov.openrouter.services.OpenRouterService

private const val MAX_TOOL_ROUNDS = 10
private const val MAX_ARG_DISPLAY_LENGTH = 80

data class ToolCallRoundResult(
    val finalText: String?,
    val toolCallsMade: Int,
    val error: String? = null
)

class ToolCallHandler(
    private val openRouterService: OpenRouterService,
    private val tools: List<ToolDefinition> = AgentTools.getAllTools()
) {
    private val gson = Gson()
    private val toolMap = tools.associateBy { it.name }

    suspend fun runWithTools(
        request: ChatCompletionRequest,
        projectBasePath: String,
        onToolStart: suspend (String, String) -> Unit,
        onToolEnd: suspend (String, String) -> Unit
    ): ToolCallRoundResult {
        val chatTools = AgentTools.toChatTools(tools)
        var currentRequest = request.copy(tools = chatTools)
        var toolCallCount = 0
        var result: ToolCallRoundResult? = null

        @Suppress("LoopWithTooManyJumpStatements")
        for (unused in 0 until MAX_TOOL_ROUNDS) {
            val apiResult = openRouterService.createChatCompletion(currentRequest)
            val response = when (apiResult) {
                is org.zhavoronkov.openrouter.models.ApiResult.Success -> apiResult.data
                is org.zhavoronkov.openrouter.models.ApiResult.Error -> {
                    result = ToolCallRoundResult(null, toolCallCount, "API error: ${apiResult.message}")
                    break
                }
            }

            val choice = response.choices?.firstOrNull()
            val message = choice?.message
            if (choice == null || message == null) {
                result = ToolCallRoundResult(null, toolCallCount, "No choices in response")
                break
            }

            val toolCalls = message.toolCalls
            if (toolCalls.isNullOrEmpty()) {
                val content = message.content
                val text = when {
                    content == null -> null
                    content.isJsonPrimitive -> content.asString
                    content.isJsonArray -> content.asJsonArray.joinToString("") { element ->
                        if (element.isJsonObject && element.asJsonObject.has("text")) {
                            element.asJsonObject.get("text").asString
                        } else if (element.isJsonPrimitive) {
                            element.asString
                        } else ""
                    }
                    else -> content.toString()
                }
                result = ToolCallRoundResult(text, toolCallCount)
                break
            }

            val messages = currentRequest.messages.toMutableList()
            messages.add(
                ChatMessage(
                    role = "assistant",
                    content = com.google.gson.JsonPrimitive(""),
                    toolCalls = toolCalls
                )
            )

            for (toolCall in toolCalls) {
                val toolName = toolCall.function.name
                val toolArgs = try {
                    JsonParser.parseString(toolCall.function.arguments).asJsonObject
                } catch (e: Exception) {
                    JsonObject()
                }

                onToolStart(toolName, formatToolArgs(toolArgs))

                val toolResult = try {
                    val handler = toolMap[toolName]
                    if (handler != null) {
                        handler.execute(toolArgs, projectBasePath)
                    } else {
                        "Error: unknown tool '$toolName'"
                    }
                } catch (e: Exception) {
                    "Error executing tool '$toolName': ${e.message}"
                }

                onToolEnd(toolName, toolResult)
                toolCallCount++

                messages.add(
                    ChatMessage(
                        role = "tool",
                        content = com.google.gson.JsonPrimitive(toolResult),
                        toolCallId = toolCall.id
                    )
                )
            }

            currentRequest = currentRequest.copy(messages = messages)
        }

        return result ?: ToolCallRoundResult(
            "Reached maximum tool call rounds ($MAX_TOOL_ROUNDS)",
            toolCallCount
        )
    }

    private fun formatToolArgs(args: JsonObject): String {
        return args.entrySet().joinToString(", ") { (key, value) ->
            val str = value.toString()
            val displayValue = if (str.length > MAX_ARG_DISPLAY_LENGTH) {
                str.take(MAX_ARG_DISPLAY_LENGTH) + "..."
            } else {
                str
            }
            "$key=$displayValue"
        }
    }
}
