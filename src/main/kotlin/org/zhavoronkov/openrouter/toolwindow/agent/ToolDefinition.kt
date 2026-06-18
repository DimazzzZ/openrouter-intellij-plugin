package org.zhavoronkov.openrouter.toolwindow.agent

import com.google.gson.JsonObject
import org.zhavoronkov.openrouter.models.ChatTool
import org.zhavoronkov.openrouter.models.ChatToolFunction

data class ToolDefinition(
    val name: String,
    val description: String,
    val parameters: JsonObject,
    val execute: suspend (JsonObject, String) -> String
)

object AgentTools {

    fun getAllTools(): List<ToolDefinition> = listOf(
        readFileTool(),
        writeFileTool(),
        listFilesTool(),
        searchCodeTool(),
        runCommandTool()
    )

    fun toChatTools(tools: List<ToolDefinition>): List<ChatTool> {
        return tools.map { tool ->
            ChatTool(
                function = ChatToolFunction(
                    name = tool.name,
                    description = tool.description,
                    parameters = tool.parameters
                )
            )
        }
    }

    private fun readFileTool(): ToolDefinition {
        val params = JsonObject().apply {
            addProperty("type", "object")
            val props = JsonObject()
            val pathProp = JsonObject()
            pathProp.addProperty("type", "string")
            pathProp.addProperty("description", "Absolute path to the file to read")
            props.add("path", pathProp)
            add("properties", props)
            val required = com.google.gson.JsonArray()
            required.add("path")
            add("required", required)
        }
        return ToolDefinition(
            name = "read_file",
            description = "Read the contents of a file at the given absolute path.",
            parameters = params,
            execute = ::executeReadFile
        )
    }

    private fun writeFileTool(): ToolDefinition {
        val params = JsonObject().apply {
            addProperty("type", "object")
            val props = JsonObject()
            val pathProp = JsonObject()
            pathProp.addProperty("type", "string")
            pathProp.addProperty("description", "Absolute path to the file to write")
            props.add("path", pathProp)
            val contentProp = JsonObject()
            contentProp.addProperty("type", "string")
            contentProp.addProperty("description", "Content to write to the file")
            props.add("content", contentProp)
            add("properties", props)
            val required = com.google.gson.JsonArray()
            required.add("path")
            required.add("content")
            add("required", required)
        }
        return ToolDefinition(
            name = "write_file",
            description = "Write content to a file. Creates if not exists, overwrites if it does.",
            parameters = params,
            execute = ::executeWriteFile
        )
    }

    private fun listFilesTool(): ToolDefinition {
        val params = JsonObject().apply {
            addProperty("type", "object")
            val props = JsonObject()
            val pathProp = JsonObject()
            pathProp.addProperty("type", "string")
            pathProp.addProperty("description", "Absolute path to the directory to list")
            props.add("path", pathProp)
            add("properties", props)
            val required = com.google.gson.JsonArray()
            required.add("path")
            add("required", required)
        }
        return ToolDefinition(
            name = "list_files",
            description = "List files and directories at the given absolute path.",
            parameters = params,
            execute = ::executeListFiles
        )
    }

    private fun searchCodeTool(): ToolDefinition {
        val params = JsonObject().apply {
            addProperty("type", "object")
            val props = JsonObject()
            val pathProp = JsonObject()
            pathProp.addProperty("type", "string")
            pathProp.addProperty("description", "Absolute path to the directory to search in")
            props.add("path", pathProp)
            val queryProp = JsonObject()
            queryProp.addProperty("type", "string")
            queryProp.addProperty("description", "Text or regex pattern to search for")
            props.add("query", queryProp)
            val extProp = JsonObject()
            extProp.addProperty("type", "string")
            extProp.addProperty("description", "Optional file extension filter (e.g. '.kt', '.java')")
            props.add("extension", extProp)
            add("properties", props)
            val required = com.google.gson.JsonArray()
            required.add("path")
            required.add("query")
            add("required", required)
        }
        return ToolDefinition(
            name = "search_code",
            description = "Search for a text pattern in files under the given directory.",
            parameters = params,
            execute = ::executeSearchCode
        )
    }

    private fun runCommandTool(): ToolDefinition {
        val params = JsonObject().apply {
            addProperty("type", "object")
            val props = JsonObject()
            val cmdProp = JsonObject()
            cmdProp.addProperty("type", "string")
            cmdProp.addProperty("description", "The shell command to execute")
            props.add("command", cmdProp)
            val dirProp = JsonObject()
            dirProp.addProperty("type", "string")
            dirProp.addProperty("description", "Optional working directory for the command")
            props.add("working_directory", dirProp)
            add("properties", props)
            val required = com.google.gson.JsonArray()
            required.add("command")
            add("required", required)
        }
        return ToolDefinition(
            name = "run_command",
            description = "Execute a shell command and return its stdout and stderr output.",
            parameters = params,
            execute = ::executeRunCommand
        )
    }
}
