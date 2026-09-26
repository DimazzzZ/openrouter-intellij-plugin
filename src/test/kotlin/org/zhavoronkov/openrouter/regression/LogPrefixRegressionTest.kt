package org.zhavoronkov.openrouter.regression

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.io.File

/**
 * Regression tests for the doubled log prefix.
 *
 * Every `PluginLogger` level already prepends `[OpenRouter]` (and `[OpenRouter][DEBUG]` for
 * debug), so a caller that writes the prefix into its own message produces
 * `[OpenRouter] [OpenRouter] ...` in idea.log. That is not only noise: it breaks the grep
 * recipes in DEBUGGING.md, which anchor on a single prefix.
 *
 * These tests scan the source rather than capture log output, because the platform `Logger`
 * is not injectable here - the same reason [ModalDialogRegressionTest] scans source for its
 * dispatcher invariant.
 */
@DisplayName("Regression: log prefix is written once")
class LogPrefixRegressionTest {

    private companion object {
        val MAIN_SOURCES = File("src/main/kotlin")

        /** `PluginLogger` itself is where the single, legitimate prefix comes from. */
        const val LOGGER_FILE = "PluginLogger.kt"

        /** e.g. `PluginLogger.Service.warn("[OpenRouter] ...` */
        val INLINE_PREFIX = Regex("""PluginLogger\.\w+\.\w+\(\s*"\[OpenRouter]""")

        /** e.g. `private const val TAG = "[OpenRouter]"`, later interpolated into a log call. */
        val PREFIX_CONSTANT = Regex("""const\s+val\s+\w+\s*=\s*"\[OpenRouter]"""")
    }

    private fun kotlinSources(): List<File> =
        MAIN_SOURCES.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()

    @Test
    @DisplayName("no log call writes the [OpenRouter] prefix into its own message")
    fun testNoInlinePrefixInLogCalls() {
        assertTrue(MAIN_SOURCES.isDirectory, "src/main/kotlin should exist")

        val offenders = kotlinSources().flatMap { file ->
            file.readLines().withIndex()
                .filter { (_, line) -> INLINE_PREFIX.containsMatchIn(line) }
                .map { (index, line) -> "${file.path}:${index + 1}: ${line.trim()}" }
        }

        assertTrue(
            offenders.isEmpty(),
            "PluginLogger already prepends [OpenRouter]; these calls add a second one:\n" +
                offenders.joinToString("\n")
        )
    }

    @Test
    @DisplayName("no class keeps its own [OpenRouter] tag constant")
    fun testNoPrefixConstants() {
        val offenders = kotlinSources()
            .filterNot { it.name == LOGGER_FILE || it.name == "ErrorMessages.kt" }
            .flatMap { file ->
                file.readLines().withIndex()
                    .filter { (_, line) -> PREFIX_CONSTANT.containsMatchIn(line) }
                    .map { (index, line) -> "${file.path}:${index + 1}: ${line.trim()}" }
            }

        assertTrue(
            offenders.isEmpty(),
            "A tag constant equal to the logger's own prefix doubles it at every call site. " +
                "ErrorMessages is exempt: its prefix belongs to the user-facing string, not to a log line.\n" +
                offenders.joinToString("\n")
        )
    }
}
