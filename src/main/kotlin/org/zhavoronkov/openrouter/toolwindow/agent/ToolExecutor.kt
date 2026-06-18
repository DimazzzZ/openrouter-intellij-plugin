package org.zhavoronkov.openrouter.toolwindow.agent

import com.google.gson.JsonObject
import java.io.File

private const val MAX_FILE_SIZE_BYTES = 100_000
private const val MAX_LINES = 500
private const val MAX_COMMAND_OUTPUT_CHARS = 10_000
private const val COMMAND_TIMEOUT_MS = 30_000L
private const val MAX_SEARCH_FILES = 100

internal suspend fun executeReadFile(args: JsonObject, projectBasePath: String): String {
    val path = args.get("path")?.asString ?: return "Error: missing 'path' parameter"
    val file = resolveFile(path, projectBasePath)
    if (!file.exists()) return "Error: file does not exist: $path"
    if (!file.isFile) return "Error: not a file: $path"
    if (file.length() > MAX_FILE_SIZE_BYTES) {
        return "Error: file too large (${file.length()} bytes, max $MAX_FILE_SIZE_BYTES)"
    }
    return try {
        val content = file.readText()
        val lines = content.lines()
        if (lines.size > MAX_LINES) {
            lines.take(MAX_LINES).joinToString("\n") + "\n... (truncated, ${lines.size} total lines)"
        } else {
            content
        }
    } catch (e: Exception) {
        "Error reading file: ${e.message}"
    }
}

internal suspend fun executeWriteFile(args: JsonObject, projectBasePath: String): String {
    val path = args.get("path")?.asString ?: return "Error: missing 'path' parameter"
    val content = args.get("content")?.asString ?: return "Error: missing 'content' parameter"
    val file = resolveFile(path, projectBasePath)
    return try {
        file.parentFile?.mkdirs()
        file.writeText(content)
        "File written successfully: ${file.absolutePath} (${content.length} chars)"
    } catch (e: Exception) {
        "Error writing file: ${e.message}"
    }
}

internal suspend fun executeListFiles(args: JsonObject, projectBasePath: String): String {
    val path = args.get("path")?.asString ?: return "Error: missing 'path' parameter"
    val dir = resolveFile(path, projectBasePath)
    if (!dir.exists()) return "Error: directory does not exist: $path"
    if (!dir.isDirectory) return "Error: not a directory: $path"
    return try {
        val entries = dir.listFiles()?.sortedWith(compareBy<File> { !it.isDirectory }.thenBy { it.name }) ?: emptyList()
        if (entries.isEmpty()) {
            "(empty directory)"
        } else {
            entries.joinToString("\n") { entry ->
                val type = if (entry.isDirectory) "[dir]" else "[file]"
                val size = if (entry.isFile) " (${entry.length()} bytes)" else ""
                "$type ${entry.name}$size"
            }
        }
    } catch (e: Exception) {
        "Error listing files: ${e.message}"
    }
}

internal suspend fun executeSearchCode(args: JsonObject, projectBasePath: String): String {
    val path = args.get("path")?.asString ?: return "Error: missing 'path' parameter"
    val query = args.get("query")?.asString ?: return "Error: missing 'query' parameter"
    val extension = args.get("extension")?.asString
    val dir = resolveFile(path, projectBasePath)
    if (!dir.exists()) return "Error: directory does not exist: $path"
    if (!dir.isDirectory) return "Error: not a directory: $path"

    return try {
        val results = mutableListOf<String>()
        dir.walkTopDown()
            .filter { it.isFile }
            .filter { extension == null || it.extension == extension.removePrefix(".") }
            .filter { it.length() < MAX_FILE_SIZE_BYTES }
            .take(MAX_SEARCH_FILES)
            .forEach { file ->
                file.readLines().forEachIndexed { index, line ->
                    if (line.contains(query, ignoreCase = true)) {
                        val relativePath = file.absolutePath.removePrefix(projectBasePath).removePrefix("/")
                        results.add("$relativePath:${index + 1}: ${line.trim()}")
                    }
                }
                if (results.size >= MAX_LINES) return@forEach
            }
        if (results.isEmpty()) "No matches found for '$query'" else results.take(MAX_LINES).joinToString("\n")
    } catch (e: Exception) {
        "Error searching: ${e.message}"
    }
}

internal suspend fun executeRunCommand(args: JsonObject, projectBasePath: String): String {
    val command = args.get("command")?.asString ?: return "Error: missing 'command' parameter"
    val workingDir = args.get("working_directory")?.asString?.let { resolveFile(it, projectBasePath) }
        ?: File(projectBasePath)

    return try {
        val process = ProcessBuilder("sh", "-c", command)
            .directory(workingDir)
            .redirectErrorStream(true)
            .start()

        val exited = process.waitFor(COMMAND_TIMEOUT_MS, java.util.concurrent.TimeUnit.MILLISECONDS)
        if (!exited) {
            process.destroyForcibly()
            return "Error: command timed out after ${COMMAND_TIMEOUT_MS}ms"
        }

        val output = process.inputStream.bufferedReader().readText()
        val truncated = if (output.length > MAX_COMMAND_OUTPUT_CHARS) {
            output.take(MAX_COMMAND_OUTPUT_CHARS) + "\n... (truncated)"
        } else {
            output
        }
        val exitCode = process.exitValue()
        if (exitCode == 0) truncated else "Exit code: $exitCode\n$truncated"
    } catch (e: Exception) {
        "Error executing command: ${e.message}"
    }
}

private fun resolveFile(path: String, projectBasePath: String): File {
    val file = File(path)
    return if (file.isAbsolute) file else File(projectBasePath, path)
}
