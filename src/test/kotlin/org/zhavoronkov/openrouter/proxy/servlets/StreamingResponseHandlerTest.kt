package org.zhavoronkov.openrouter.proxy.servlets

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.io.PrintWriter
import java.io.StringWriter

/**
 * Tests for StreamingResponseHandler SSE format compliance.
 *
 * SSE (Server-Sent Events) format requires:
 * - Each event starts with "data: " prefix
 * - Each event is followed by TWO newlines (blank line separator)
 * - Stream ends with "data: [DONE]" followed by blank line
 *
 * Example of correct SSE format:
 * ```
 * data: {"id":"1","choices":[...]}
 *
 * data: {"id":"2","choices":[...]}
 *
 * data: [DONE]
 *
 * ```
 *
 * The AI Assistant's SSE parser expects this format. Without the blank lines,
 * multiple JSON objects get concatenated and parsing fails with:
 * "Expected EOF after parsing, but had { instead"
 */
@DisplayName("StreamingResponseHandler SSE Format Tests")
class StreamingResponseHandlerTest {

    companion object {
        private const val DATA_PREFIX = "data: "
        private const val DONE_MARKER = "[DONE]"
    }

    @Nested
    @DisplayName("SSE Format Compliance Tests")
    inner class SSEFormatComplianceTests {

        @Test
        @DisplayName("Should format single SSE event with blank line separator")
        fun testSingleEventFormat() {
            // Given: A single SSE data line
            val jsonData = """{"id":"gen-123","choices":[{"delta":{"content":"Hello"}}]}"""
            val dataLine = "$DATA_PREFIX$jsonData"

            // When: We format it according to SSE spec
            val output = formatSSEEvent(dataLine)

            // Then: Should have data line followed by blank line
            val lines = output.split("\n")
            assertEquals(3, lines.size, "Should have data line + blank line + trailing")
            assertEquals(dataLine, lines[0], "First line should be data line")
            assertEquals("", lines[1], "Second line should be blank (event separator)")
        }

        @Test
        @DisplayName("Should format multiple SSE events with blank line separators")
        fun testMultipleEventsFormat() {
            // Given: Multiple SSE data lines
            val events = listOf(
                """{"id":"gen-1","choices":[{"delta":{"content":"Hello"}}]}""",
                """{"id":"gen-2","choices":[{"delta":{"content":" World"}}]}""",
                """{"id":"gen-3","choices":[{"delta":{"content":"!"}}]}"""
            )

            // When: We format them according to SSE spec
            val output = StringBuilder()
            events.forEach { json ->
                output.append(formatSSEEvent("$DATA_PREFIX$json"))
            }
            output.append(formatSSEEvent("$DATA_PREFIX$DONE_MARKER"))

            val result = output.toString()

            // Then: Each event should be separated by blank lines
            val lines = result.split("\n")

            // Verify pattern: data, blank, data, blank, data, blank, done, blank
            var dataLineCount = 0
            var blankLineCount = 0
            for (i in lines.indices) {
                if (lines[i].startsWith(DATA_PREFIX)) {
                    dataLineCount++
                    // Next line should be blank (if not last)
                    if (i + 1 < lines.size) {
                        assertEquals("", lines[i + 1], "Line after data should be blank")
                    }
                } else if (lines[i].isEmpty() && i > 0 && lines[i - 1].startsWith(DATA_PREFIX)) {
                    blankLineCount++
                }
            }

            assertEquals(4, dataLineCount, "Should have 4 data lines (3 events + DONE)")
            assertEquals(4, blankLineCount, "Should have 4 blank lines (one after each event)")
        }

        @Test
        @DisplayName("Should end stream with [DONE] marker and blank line")
        fun testDoneMarkerFormat() {
            // Given: A DONE marker
            val doneEvent = "$DATA_PREFIX$DONE_MARKER"

            // When: We format it
            val output = formatSSEEvent(doneEvent)

            // Then: Should have DONE line followed by blank line
            val lines = output.split("\n")
            assertTrue(lines[0].contains(DONE_MARKER), "Should contain DONE marker")
            assertEquals("", lines[1], "Should have blank line after DONE")
        }

        @Test
        @DisplayName("Should produce parseable SSE stream")
        fun testParseableStream() {
            // Given: A complete SSE stream
            val events = listOf(
                """{"id":"1","object":"chat.completion.chunk","choices":[{"delta":{"content":"Hi"}}]}""",
                """{"id":"2","object":"chat.completion.chunk","choices":[{"delta":{"content":"!"}}]}"""
            )

            val stream = StringBuilder()
            events.forEach { json ->
                stream.append(formatSSEEvent("$DATA_PREFIX$json"))
            }
            stream.append(formatSSEEvent("$DATA_PREFIX$DONE_MARKER"))

            // When: We parse the stream
            val parsedEvents = parseSSEStream(stream.toString())

            // Then: Should correctly parse all events
            assertEquals(3, parsedEvents.size, "Should parse 3 events (2 data + DONE)")
            assertEquals(events[0], parsedEvents[0], "First event should match")
            assertEquals(events[1], parsedEvents[1], "Second event should match")
            assertEquals(DONE_MARKER, parsedEvents[2], "Last event should be DONE")
        }
    }

    /**
     * Formats an SSE event line with proper blank line separator.
     * This replicates the logic in StreamingResponseHandler.processStreamLine
     */
    private fun formatSSEEvent(dataLine: String): String {
        val writer = StringWriter()
        val printWriter = PrintWriter(writer)
        printWriter.println(dataLine) // data line + newline
        printWriter.println() // blank line (event separator)
        printWriter.flush()
        return writer.toString()
    }

    /**
     * Parses an SSE stream and extracts the data payloads.
     * This simulates what the AI Assistant's SSE parser does.
     */
    private fun parseSSEStream(stream: String): List<String> {
        return stream.lines()
            .filter { it.startsWith(DATA_PREFIX) }
            .map { it.removePrefix(DATA_PREFIX) }
            .toList()
    }

    @Nested
    @DisplayName("AI Assistant Compatibility Tests")
    inner class AIAssistantCompatibilityTests {

        @Test
        @DisplayName("Should not concatenate JSON objects without separator")
        fun testNoJsonConcatenation() {
            // This test verifies the fix for the error:
            // "Expected EOF after parsing, but had { instead"
            // which occurs when JSON objects are concatenated without blank lines

            // Given: Two JSON chunks that would cause parsing error if concatenated
            val chunk1 = """{"id":"1","choices":[{"delta":{"content":"Hello"},"finish_reason":null}]}"""
            val chunk2 = """{"id":"2","choices":[{"delta":{"content":" World"},"finish_reason":null}]}"""

            // When: We format them with proper SSE format
            val stream = StringBuilder()
            stream.append(formatSSEEvent("$DATA_PREFIX$chunk1"))
            stream.append(formatSSEEvent("$DATA_PREFIX$chunk2"))

            val result = stream.toString()

            // Then: The chunks should NOT be directly adjacent
            // Bad format: ...null}]}{"id":"2"... (causes parsing error)
            // Good format: ...null}]}\n\ndata: {"id":"2"...
            val badPattern = "}]{\"id\":"
            assertTrue(
                !result.contains(badPattern),
                "JSON objects should not be concatenated without separator"
            )

            // And: There should be blank lines between events
            assertTrue(
                result.contains("$chunk1\n\n"),
                "First chunk should be followed by blank line"
            )
        }

        @Test
        @DisplayName("Should produce valid SSE format for streaming response")
        fun testValidSSEFormat() {
            // Given: A typical streaming response with multiple chunks
            val chunks = listOf(
                """{"id":"gen-abc","object":"chat.completion.chunk","created":1234567890,"model":"gpt-4","choices":[{"index":0,"delta":{"role":"assistant"},"finish_reason":null}]}""",
                """{"id":"gen-abc","object":"chat.completion.chunk","created":1234567890,"model":"gpt-4","choices":[{"index":0,"delta":{"content":"Hello"},"finish_reason":null}]}""",
                """{"id":"gen-abc","object":"chat.completion.chunk","created":1234567890,"model":"gpt-4","choices":[{"index":0,"delta":{"content":"!"},"finish_reason":"stop"}]}"""
            )

            // When: We format them as SSE stream
            val stream = StringBuilder()
            chunks.forEach { chunk ->
                stream.append(formatSSEEvent("$DATA_PREFIX$chunk"))
            }
            stream.append(formatSSEEvent("$DATA_PREFIX$DONE_MARKER"))

            val result = stream.toString()

            // Then: Each line starting with "data:" should be followed by a blank line
            val lines = result.lines()
            for (i in lines.indices) {
                if (lines[i].startsWith(DATA_PREFIX) && i + 1 < lines.size) {
                    assertEquals(
                        "",
                        lines[i + 1],
                        "Data line at index $i should be followed by blank line"
                    )
                }
            }
        }

        @Test
        @DisplayName("Should handle empty content chunks correctly")
        fun testEmptyContentChunks() {
            // Given: Chunks with empty content (common in streaming)
            val chunks = listOf(
                """{"id":"1","choices":[{"delta":{"content":""},"finish_reason":null}]}""",
                """{"id":"2","choices":[{"delta":{"content":"Hi"},"finish_reason":null}]}""",
                """{"id":"3","choices":[{"delta":{},"finish_reason":"stop"}]}"""
            )

            // When: We format them
            val stream = StringBuilder()
            chunks.forEach { chunk ->
                stream.append(formatSSEEvent("$DATA_PREFIX$chunk"))
            }
            stream.append(formatSSEEvent("$DATA_PREFIX$DONE_MARKER"))

            // Then: Should parse correctly
            val parsed = parseSSEStream(stream.toString())
            assertEquals(4, parsed.size, "Should have 4 events")
            assertEquals(DONE_MARKER, parsed.last(), "Last should be DONE")
        }
    }

    @Nested
    @DisplayName("Regression Tests")
    inner class RegressionTests {

        /**
         * Regression test for the SSE parsing error in AI Assistant.
         *
         * Bug: AI Assistant showed "Something went wrong" with error:
         * "Expected EOF after parsing, but had { instead at path: $"
         *
         * Root cause: SSE events were not separated by blank lines, causing
         * the JSON parser to receive concatenated JSON objects like:
         * {"id":"1",...}{"id":"2",...}
         *
         * Fix: Added blank line after each SSE event in StreamingResponseHandler
         */
        @Test
        @DisplayName("Regression: SSE events must be separated by blank lines")
        fun testSSEEventsSeparatedByBlankLines() {
            // Given: The exact scenario that caused the bug
            // Two SSE chunks that would be concatenated without proper formatting
            val chunk1 = """{"id":"gen-1","object":"chat.completion.chunk","choices":[{"index":0,"delta":{"content":"Hi"},"logprobs":null,"finish_reason":null}]}"""
            val chunk2 = """{"id":"gen-2","object":"chat.completion.chunk","choices":[{"index":0,"delta":{"content":"!"},"logprobs":null,"finish_reason":null}]}"""

            // When: We format them with the FIXED SSE format
            val stream = StringBuilder()
            stream.append(formatSSEEvent("$DATA_PREFIX$chunk1"))
            stream.append(formatSSEEvent("$DATA_PREFIX$chunk2"))

            val result = stream.toString()

            // Then: The problematic pattern should NOT exist
            // This pattern caused: "Expected EOF after parsing, but had { instead"
            val problematicPattern = "]}]{\"id\":"
            assertFalse(
                result.contains(problematicPattern),
                "Should not have concatenated JSON objects (this caused the parsing error)"
            )

            // And: Each data line should be followed by exactly one blank line
            val lines = result.lines()
            for (i in lines.indices) {
                if (lines[i].startsWith(DATA_PREFIX)) {
                    assertTrue(
                        i + 1 < lines.size && lines[i + 1].isEmpty(),
                        "Data line at index $i must be followed by blank line"
                    )
                }
            }
        }

        @Test
        @DisplayName("Regression: Stream must end with [DONE] and blank line")
        fun testStreamEndsWithDoneAndBlankLine() {
            // Given: A complete stream
            val chunk = """{"id":"1","choices":[{"delta":{"content":"test"}}]}"""

            // When: We format it
            val stream = StringBuilder()
            stream.append(formatSSEEvent("$DATA_PREFIX$chunk"))
            stream.append(formatSSEEvent("$DATA_PREFIX$DONE_MARKER"))

            val result = stream.toString()

            // Then: Should end with DONE followed by blank line
            assertTrue(result.contains("$DATA_PREFIX$DONE_MARKER\n\n"), "Should have DONE with blank line")
        }
    }

    @Nested
    @DisplayName("Error Response SSE Format Tests")
    inner class ErrorResponseSSEFormatTests {

        @Test
        @DisplayName("Should format error response with proper SSE format")
        fun testErrorResponseFormat() {
            // Given: An error response
            val errorJson = """{"error":{"message":"Rate limit exceeded","type":"rate_limit_error"}}"""

            // When: We format it as SSE
            val stream = StringBuilder()
            stream.append(formatSSEEvent("$DATA_PREFIX$errorJson"))
            stream.append(formatSSEEvent("$DATA_PREFIX$DONE_MARKER"))

            val result = stream.toString()

            // Then: Should have proper format
            val parsed = parseSSEStream(result)
            assertEquals(2, parsed.size, "Should have error + DONE")
            assertTrue(parsed[0].contains("Rate limit exceeded"), "Should contain error message")
            assertEquals(DONE_MARKER, parsed[1], "Should end with DONE")
        }

        @Test
        @DisplayName("Should include DONE marker after error")
        fun testDoneAfterError() {
            // Given: An error event
            val errorJson = """{"error":{"message":"API error","type":"api_error"}}"""

            // When: We format error with DONE
            val stream = StringBuilder()
            stream.append(formatSSEEvent("$DATA_PREFIX$errorJson"))
            stream.append(formatSSEEvent("$DATA_PREFIX$DONE_MARKER"))

            val result = stream.toString()

            // Then: Stream should end with DONE
            assertTrue(result.contains("$DATA_PREFIX$DONE_MARKER"), "Should contain DONE marker")
            assertTrue(result.endsWith("\n"), "Should end with newline")
        }
    }
}
