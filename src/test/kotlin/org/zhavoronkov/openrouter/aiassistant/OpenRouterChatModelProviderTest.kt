package org.zhavoronkov.openrouter.aiassistant

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@DisplayName("OpenRouter Chat Model Provider Tests")
class OpenRouterChatModelProviderTest {

    @Nested
    @DisplayName("Data Model Tests")
    inner class DataModelTests {

        @Test
        @DisplayName("ChatMessage should store role and content")
        fun testChatMessage() {
            val message = ChatMessage("user", "Hello, AI!")

            assertEquals("user", message.role)
            assertEquals("Hello, AI!", message.content)
        }

        @Test
        @DisplayName("ChatResponse success should have content and no error")
        fun testChatResponseSuccess() {
            val response = ChatResponse.success("Response content")

            assertEquals("Response content", response.content)
            assertNull(response.error)
            assertTrue(response.isSuccess())
        }

        @Test
        @DisplayName("ChatResponse error should have error and no content")
        fun testChatResponseError() {
            val response = ChatResponse.error("Something went wrong")

            assertNull(response.content)
            assertEquals("Something went wrong", response.error)
            assertFalse(response.isSuccess())
        }

        @Test
        @DisplayName("ChatResponse success with usage should include token usage")
        fun testChatResponseWithUsage() {
            val usage = ChatResponse.Usage(
                promptTokens = 10,
                completionTokens = 20,
                totalTokens = 30
            )
            val response = ChatResponse.success("Response", usage)

            assertNotNull(response.usage)
            assertEquals(10, response.usage?.promptTokens)
            assertEquals(20, response.usage?.completionTokens)
            assertEquals(30, response.usage?.totalTokens)
        }

        @Test
        @DisplayName("CompletionResponse success should have content and no error")
        fun testCompletionResponseSuccess() {
            val response = CompletionResponse.success("Completion text")

            assertEquals("Completion text", response.content)
            assertNull(response.error)
            assertTrue(response.isSuccess())
        }

        @Test
        @DisplayName("CompletionResponse error should have error and no content")
        fun testCompletionResponseError() {
            val response = CompletionResponse.error("Failed to complete")

            assertNull(response.content)
            assertEquals("Failed to complete", response.error)
            assertFalse(response.isSuccess())
        }
    }


    @Nested
    @DisplayName("Message List Tests")
    inner class MessageListTests {

        @Test
        @DisplayName("Should create list of chat messages with different roles")
        fun testMultipleMessages() {
            val messages = listOf(
                ChatMessage("system", "You are a helpful assistant"),
                ChatMessage("user", "What is 2+2?"),
                ChatMessage("assistant", "2+2 equals 4"),
                ChatMessage("user", "Thank you!")
            )

            assertEquals(4, messages.size)
            assertEquals("system", messages[0].role)
            assertEquals("user", messages[1].role)
            assertEquals("assistant", messages[2].role)
            assertEquals("user", messages[3].role)
        }

        @Test
        @DisplayName("Should handle single message conversation")
        fun testSingleMessage() {
            val messages = listOf(
                ChatMessage("user", "Hello")
            )

            assertEquals(1, messages.size)
            assertEquals("user", messages[0].role)
        }
    }

    @Nested
    @DisplayName("Response Edge Cases")
    inner class ResponseEdgeCasesTests {

        @Test
        @DisplayName("ChatResponse should handle null content gracefully")
        fun testNullContent() {
            val response = ChatResponse(null, "Error occurred")

            assertNull(response.content)
            assertFalse(response.isSuccess())
        }

        @Test
        @DisplayName("ChatResponse should handle empty content string")
        fun testEmptyContent() {
            val response = ChatResponse.success("")

            assertEquals("", response.content)
            assertTrue(response.isSuccess())
        }

        @Test
        @DisplayName("Should handle very long error messages")
        fun testLongErrorMessage() {
            val longError = "Error: " + "x".repeat(1000)
            val response = ChatResponse.error(longError)

            assertEquals(longError, response.error)
            assertFalse(response.isSuccess())
        }
    }
}
