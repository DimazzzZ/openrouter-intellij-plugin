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
    @DisplayName("Token Estimation Tests")
    inner class TokenEstimationTests {

        private val provider = OpenRouterChatModelProvider()

        @Test
        @DisplayName("Should estimate token count for short text")
        fun testEstimateTokensShort() {
            val text = "Hello"
            val tokens = provider.estimateTokenCount(text)

            assertTrue(tokens >= 1)
        }

        @Test
        @DisplayName("Should estimate token count for medium text")
        fun testEstimateTokensMedium() {
            val text = "This is a longer message that should be multiple tokens"
            val tokens = provider.estimateTokenCount(text)

            val expectedTokens = text.length / 4
            assertTrue(tokens >= expectedTokens - 2 && tokens <= expectedTokens + 2)
        }

        @Test
        @DisplayName("Should estimate at least 1 token for any non-empty text")
        fun testEstimateTokensMinimum() {
            val text = "Hi"
            val tokens = provider.estimateTokenCount(text)

            assertTrue(tokens >= 1)
        }

        @Test
        @DisplayName("Should handle empty string with minimum 1 token")
        fun testEstimateTokensEmpty() {
            val text = ""
            val tokens = provider.estimateTokenCount(text)

            assertEquals(1, tokens)
        }
    }

    @Nested
    @DisplayName("Streaming Support Tests")
    inner class StreamingSupportTests {

        private val provider = OpenRouterChatModelProvider()

        @Test
        @DisplayName("Should support streaming for all models")
        fun testStreamingSupport() {
            assertTrue(provider.supportsStreaming("openai/gpt-4"))
            assertTrue(provider.supportsStreaming("anthropic/claude-3-opus"))
            assertTrue(provider.supportsStreaming("meta-llama/llama-3-70b"))
            assertTrue(provider.supportsStreaming("any-model-id"))
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
