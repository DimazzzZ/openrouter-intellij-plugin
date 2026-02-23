package org.zhavoronkov.openrouter.toolwindow

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * Tests for ChatPanel business logic
 *
 * These tests verify:
 * - Chat operations (create, delete, update)
 * - Chat title generation from first message
 * - Token estimation calculations
 */
@DisplayName("ChatPanel Logic Tests")
class ChatPanelLogicTest {

    @Nested
    @DisplayName("Chat Session Operations")
    inner class ChatSessionOperationsTests {

        @Test
        @DisplayName("New chat should have unique ID")
        fun `New chat should have unique ID`() {
            val chat1 = createNewChatSession()
            val chat2 = createNewChatSession()

            assertNotEquals(chat1.id, chat2.id)
        }

        @Test
        @DisplayName("New chat should have default title")
        fun `New chat should have default title`() {
            val chat = createNewChatSession()

            assertEquals("New Chat", chat.title)
        }

        @Test
        @DisplayName("New chat should have empty messages")
        fun `New chat should have empty messages`() {
            val chat = createNewChatSession()

            assertTrue(chat.messages.isEmpty())
        }

        @Test
        @DisplayName("New chat should have zero tokens")
        fun `New chat should have zero tokens`() {
            val chat = createNewChatSession()

            assertEquals(0, chat.totalTokens)
        }

        @Test
        @DisplayName("Chat should be added to beginning of list")
        fun `Chat should be added to beginning of list`() {
            val chatSessions = mutableListOf<ChatPanel.ChatSession>()

            val chat1 = createNewChatSession().also { chatSessions.add(0, it) }
            val chat2 = createNewChatSession().also { chatSessions.add(0, it) }
            val chat3 = createNewChatSession().also { chatSessions.add(0, it) }

            assertEquals(chat3.id, chatSessions[0].id)
            assertEquals(chat2.id, chatSessions[1].id)
            assertEquals(chat1.id, chatSessions[2].id)
        }

        @Test
        @DisplayName("Delete should remove chat from list")
        fun `Delete should remove chat from list`() {
            val chatSessions = mutableListOf(
                ChatPanel.ChatSession("1", "Chat 1", mutableListOf(), 0, 1000L),
                ChatPanel.ChatSession("2", "Chat 2", mutableListOf(), 0, 2000L),
                ChatPanel.ChatSession("3", "Chat 3", mutableListOf(), 0, 3000L)
            )

            chatSessions.removeIf { it.id == "2" }

            assertEquals(2, chatSessions.size)
            assertEquals("1", chatSessions[0].id)
            assertEquals("3", chatSessions[1].id)
        }

        @Test
        @DisplayName("Delete non-existent chat should not affect list")
        fun `Delete non-existent chat should not affect list`() {
            val chatSessions = mutableListOf(
                ChatPanel.ChatSession("1", "Chat 1", mutableListOf(), 0, 1000L)
            )

            chatSessions.removeIf { it.id == "non-existent" }

            assertEquals(1, chatSessions.size)
        }

        private fun createNewChatSession(): ChatPanel.ChatSession {
            return ChatPanel.ChatSession(
                id = java.util.UUID.randomUUID().toString(),
                title = "New Chat",
                messages = mutableListOf(),
                totalTokens = 0,
                createdAt = System.currentTimeMillis()
            )
        }
    }

    @Nested
    @DisplayName("Chat Title Generation")
    inner class ChatTitleGenerationTests {

        @Test
        @DisplayName("First message should set chat title")
        fun `First message should set chat title`() {
            val userMessage = "Hello, how are you?"
            val title = generateChatTitle(userMessage)

            assertEquals("Hello, how are you?", title)
        }

        @Test
        @DisplayName("Long first message should be truncated to 50 chars with ellipsis")
        fun `Long first message should be truncated to 50 chars with ellipsis`() {
            val longMessage = "This is a very long message that should be truncated because it exceeds fifty characters"
            val title = generateChatTitle(longMessage)

            assertEquals(53, title.length) // 50 chars + "..."
            assertTrue(title.endsWith("..."))
            assertTrue(title.startsWith("This is a very long message that should be truncat"))
        }

        @Test
        @DisplayName("Message exactly 50 chars should not have ellipsis")
        fun `Message exactly 50 chars should not have ellipsis`() {
            val exactMessage = "A".repeat(50)
            val title = generateChatTitle(exactMessage)

            assertEquals(50, title.length)
            assertEquals(exactMessage, title)
        }

        @Test
        @DisplayName("Message shorter than 50 chars should not have ellipsis")
        fun `Message shorter than 50 chars should not have ellipsis`() {
            val shortMessage = "Short message"
            val title = generateChatTitle(shortMessage)

            assertEquals(shortMessage, title)
        }

        @Test
        @DisplayName("Empty message should result in empty title")
        fun `Empty message should result in empty title`() {
            val title = generateChatTitle("")

            assertEquals("", title)
        }

        private fun generateChatTitle(userMessage: String): String {
            return userMessage.take(50) + if (userMessage.length > 50) "..." else ""
        }
    }

    @Nested
    @DisplayName("Token Estimation")
    inner class TokenEstimationTests {

        private val charsPerToken = 4.0

        @Test
        @DisplayName("Empty text should show 0 tokens")
        fun `Empty text should show 0 tokens`() {
            val tokens = estimateTokens("")

            assertEquals(0, tokens)
        }

        @Test
        @DisplayName("Short text should have minimum 1 token")
        fun `Short text should have minimum 1 token`() {
            val tokens = estimateTokens("Hi")

            assertEquals(1, tokens)
        }

        @Test
        @DisplayName("4 characters should be approximately 1 token")
        fun `4 characters should be approximately 1 token`() {
            val tokens = estimateTokens("test")

            assertEquals(1, tokens)
        }

        @Test
        @DisplayName("40 characters should be approximately 10 tokens")
        fun `40 characters should be approximately 10 tokens`() {
            val text = "A".repeat(40)
            val tokens = estimateTokens(text)

            assertEquals(10, tokens)
        }

        @Test
        @DisplayName("Token count should round down")
        fun `Token count should round down`() {
            val text = "A".repeat(5) // 5 chars / 4 = 1.25, should be 1
            val tokens = estimateTokens(text)

            assertEquals(1, tokens)
        }

        @Test
        @DisplayName("Large text should calculate tokens correctly")
        fun `Large text should calculate tokens correctly`() {
            val text = "A".repeat(1000)
            val tokens = estimateTokens(text)

            assertEquals(250, tokens)
        }

        private fun estimateTokens(text: String): Int {
            return if (text.isEmpty()) 0 else (text.length / charsPerToken).toInt().coerceAtLeast(1)
        }
    }

    @Nested
    @DisplayName("Message Operations")
    inner class MessageOperationsTests {

        @Test
        @DisplayName("Adding message should increase message count")
        fun `Adding message should increase message count`() {
            val messages = mutableListOf<ChatPanel.ChatMessageData>()

            messages.add(ChatPanel.ChatMessageData("user", "Hello"))

            assertEquals(1, messages.size)

            messages.add(ChatPanel.ChatMessageData("assistant", "Hi!"))

            assertEquals(2, messages.size)
        }

        @Test
        @DisplayName("Messages should preserve order")
        fun `Messages should preserve order`() {
            val messages = mutableListOf<ChatPanel.ChatMessageData>()

            messages.add(ChatPanel.ChatMessageData("user", "First"))
            messages.add(ChatPanel.ChatMessageData("assistant", "Second"))
            messages.add(ChatPanel.ChatMessageData("user", "Third"))

            assertEquals("First", messages[0].content)
            assertEquals("Second", messages[1].content)
            assertEquals("Third", messages[2].content)
        }

        @Test
        @DisplayName("User messages should have correct role")
        fun `User messages should have correct role`() {
            val message = ChatPanel.ChatMessageData("user", "Test")

            assertEquals("user", message.role)
        }

        @Test
        @DisplayName("Assistant messages should have correct role")
        fun `Assistant messages should have correct role`() {
            val message = ChatPanel.ChatMessageData("assistant", "Test")

            assertEquals("assistant", message.role)
        }

        @Test
        @DisplayName("System messages should have correct role")
        fun `System messages should have correct role`() {
            val message = ChatPanel.ChatMessageData("system", "Welcome!")

            assertEquals("system", message.role)
        }
    }

    @Nested
    @DisplayName("Token Tracking")
    inner class TokenTrackingTests {

        @Test
        @DisplayName("Total tokens should accumulate")
        fun `Total tokens should accumulate`() {
            val session = ChatPanel.ChatSession(
                id = "test",
                title = "Test",
                messages = mutableListOf(),
                totalTokens = 0,
                createdAt = System.currentTimeMillis()
            )

            // Simulate adding tokens from API response
            session.totalTokens += 100
            assertEquals(100, session.totalTokens)

            session.totalTokens += 150
            assertEquals(250, session.totalTokens)

            session.totalTokens += 50
            assertEquals(300, session.totalTokens)
        }

        @Test
        @DisplayName("Token display should show empty for zero tokens")
        fun `Token display should show empty for zero tokens`() {
            val statusText = formatTokenDisplay(0)

            assertEquals("", statusText)
        }

        @Test
        @DisplayName("Token display should show total for non-zero tokens")
        fun `Token display should show total for non-zero tokens`() {
            val statusText = formatTokenDisplay(150)

            assertEquals("Total: 150", statusText)
        }

        private fun formatTokenDisplay(tokens: Int): String {
            return if (tokens > 0) "Total: $tokens" else ""
        }
    }

    @Nested
    @DisplayName("Chat Session State")
    inner class ChatSessionStateTests {

        @Test
        @DisplayName("Chat should track created timestamp")
        fun `Chat should track created timestamp`() {
            val before = System.currentTimeMillis()
            val session = ChatPanel.ChatSession(
                id = "test",
                title = "Test",
                messages = mutableListOf(),
                totalTokens = 0,
                createdAt = System.currentTimeMillis()
            )
            val after = System.currentTimeMillis()

            assertTrue(session.createdAt >= before)
            assertTrue(session.createdAt <= after)
        }

        @Test
        @DisplayName("Chat title should be mutable")
        fun `Chat title should be mutable`() {
            val session = ChatPanel.ChatSession(
                id = "test",
                title = "New Chat",
                messages = mutableListOf(),
                totalTokens = 0,
                createdAt = System.currentTimeMillis()
            )

            assertEquals("New Chat", session.title)

            session.title = "Updated Title"

            assertEquals("Updated Title", session.title)
        }

        @Test
        @DisplayName("First message size check should work correctly")
        fun `First message size check should work correctly`() {
            val session = ChatPanel.ChatSession(
                id = "test",
                title = "New Chat",
                messages = mutableListOf(),
                totalTokens = 0,
                createdAt = System.currentTimeMillis()
            )

            // Initially no messages
            assertEquals(0, session.messages.size)

            // Add first message
            session.messages.add(ChatPanel.ChatMessageData("user", "Hello"))
            assertEquals(1, session.messages.size)

            // This is when title would be updated in actual code
            val shouldUpdateTitle = session.messages.size == 1
            assertTrue(shouldUpdateTitle)
        }
    }
}
