package org.zhavoronkov.openrouter.toolwindow

import com.google.gson.Gson
import com.google.gson.JsonParser
import com.google.gson.reflect.TypeToken
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.zhavoronkov.openrouter.toolwindow.chat.ReplySummary

/**
 * Tests for ChatPanel data classes serialization and deserialization
 */
@DisplayName("ChatPanel Data Model Tests")
class ChatPanelDataModelTest {

    private val gson = Gson()

    @Nested
    @DisplayName("ChatMessageData Tests")
    inner class ChatMessageDataTests {

        private val summary = ReplySummary(
            requestedModel = "openrouter/auto",
            respondingModel = "anthropic/claude-sonnet-4.5",
            provider = "Google Vertex",
            cost = 0.00042,
            finishReason = "length"
        )

        private fun reload(message: ChatPanel.ChatMessageData): ChatPanel.ChatMessageData =
            gson.fromJson(gson.toJson(message), ChatPanel.ChatMessageData::class.java)

        @Test
        @DisplayName("ChatMessageData should serialize to JSON correctly")
        fun `ChatMessageData should serialize to JSON correctly`() {
            val message = ChatPanel.ChatMessageData(role = "user", content = "Hello, world!")

            val json = gson.toJson(message)

            assertTrue(json.contains("\"role\":\"user\""))
            assertTrue(json.contains("\"content\":\"Hello, world!\""))
        }

        @Test
        @DisplayName("a reply is saved with its model, provider, cost and finish reason as separate fields")
        fun `a reply is saved with its model provider cost and finish reason as separate fields`() {
            val json = JsonParser.parseString(gson.toJson(ChatPanel.ChatMessageData.reply("Hi there!", summary)))

            val expected = """
                {
                  "role": "assistant",
                  "content": "Hi there!",
                  "summary": {
                    "requestedModel": "openrouter/auto",
                    "respondingModel": "anthropic/claude-sonnet-4.5",
                    "provider": "Google Vertex",
                    "cost": 0.00042,
                    "finishReason": "length",
                    "webSearches": 0
                  }
                }
            """
            assertEquals(
                JsonParser.parseString(expected),
                json,
                "a reply must store the facts rather than footer wording, and must not write the footnote field"
            )
        }

        @Test
        @DisplayName("a reopened reply shows the footer the live reply showed, warning included")
        fun `a reopened reply shows the footer the live reply showed warning included`() {
            val reloaded = reload(ChatPanel.ChatMessageData.reply("Hi there!", summary))

            assertEquals(summary, reloaded.summary)
            assertEquals(summary.facts, reloaded.footerFacts)
            assertEquals(summary.warning, reloaded.footerWarning)
            assertNotNull(reloaded.footerWarning, "a truncated reply must still warn after reopening")
        }

        @Test
        @DisplayName("a reply that web search still says so after reopening")
        fun `a reply that web search still says so after reopening`() {
            val reloaded = reload(ChatPanel.ChatMessageData.reply("Hi there!", summary.copy(webSearches = 2)))

            assertEquals(2, reloaded.summary?.webSearches)
            assertTrue(reloaded.footerFacts!!.endsWith("2 web searches"), "got '${reloaded.footerFacts}'")
        }

        @Test
        @DisplayName("a reply saved before Web Search existed loads as one that did not search")
        fun `a reply saved before Web Search existed loads as one that did not search`() {
            val json = """{"role":"assistant","content":"Hi","summary":{"requestedModel":"openai/gpt-5.2"}}"""

            val message = gson.fromJson(json, ChatPanel.ChatMessageData::class.java)

            assertEquals(0, message.summary?.webSearches)
            assertEquals("openai/gpt-5.2", message.footerFacts)
        }

        @Test
        @DisplayName("a reply whose provider and cost were absent still leaves them out after reopening")
        fun `a reply whose provider and cost were absent still leaves them out after reopening`() {
            val bare = ReplySummary(requestedModel = "openai/gpt-5.2", finishReason = "stop")

            val reloaded = reload(ChatPanel.ChatMessageData.reply("Hi there!", bare))

            assertEquals("openai/gpt-5.2", reloaded.footerFacts)
            assertNull(reloaded.footerWarning)
        }

        @Test
        @DisplayName("a reply saved with only a rendered footnote still loads and still shows it")
        fun `a reply saved with only a rendered footnote still loads and still shows it`() {
            val json = """{"role":"assistant","content":"Hi there!","footnote":"deepseek/deepseek-v4-flash-0731"}"""

            val message = gson.fromJson(json, ChatPanel.ChatMessageData::class.java)

            assertNull(message.summary, "a footnote-only message carries no structured facts")
            assertEquals(
                "deepseek/deepseek-v4-flash-0731",
                message.footerFacts,
                "reopening a chat must still show the footnote it was saved with"
            )
            assertNull(message.footerWarning)
        }

        @Test
        @DisplayName("ChatMessageData should load chats saved before the footnote existed")
        fun `ChatMessageData should load chats saved before the footnote existed`() {
            val json = """{"role":"assistant","content":"Hi there!"}"""

            val message = gson.fromJson(json, ChatPanel.ChatMessageData::class.java)

            assertNull(message.footerFacts, "an older chat has no footnote to show, and must still load")
            assertNull(message.footerWarning)
        }

        @Test
        @DisplayName("ChatMessageData should deserialize from JSON correctly")
        fun `ChatMessageData should deserialize from JSON correctly`() {
            val json = """{"role":"assistant","content":"Hi there!"}"""

            val message = gson.fromJson(json, ChatPanel.ChatMessageData::class.java)

            assertEquals("assistant", message.role)
            assertEquals("Hi there!", message.content)
        }

        @Test
        @DisplayName("ChatMessageData should handle special characters in content")
        fun `ChatMessageData should handle special characters in content`() {
            val content = "Hello <world> & \"quotes\" 'apostrophe'"
            val message = ChatPanel.ChatMessageData(role = "user", content = content)

            val json = gson.toJson(message)
            val restored = gson.fromJson(json, ChatPanel.ChatMessageData::class.java)

            assertEquals(content, restored.content)
        }

        @Test
        @DisplayName("ChatMessageData should handle multiline content")
        fun `ChatMessageData should handle multiline content`() {
            val content = "Line 1\nLine 2\nLine 3"
            val message = ChatPanel.ChatMessageData(role = "user", content = content)

            val json = gson.toJson(message)
            val restored = gson.fromJson(json, ChatPanel.ChatMessageData::class.java)

            assertEquals(content, restored.content)
        }

        @Test
        @DisplayName("ChatMessageData should handle empty content")
        fun `ChatMessageData should handle empty content`() {
            val message = ChatPanel.ChatMessageData(role = "system", content = "")

            val json = gson.toJson(message)
            val restored = gson.fromJson(json, ChatPanel.ChatMessageData::class.java)

            assertEquals("", restored.content)
        }
    }

    @Nested
    @DisplayName("ChatSession Tests")
    inner class ChatSessionTests {

        @Test
        @DisplayName("ChatSession should serialize to JSON correctly")
        fun `ChatSession should serialize to JSON correctly`() {
            val session = ChatPanel.ChatSession(
                id = "test-id-123",
                title = "Test Chat",
                messages = mutableListOf(
                    ChatPanel.ChatMessageData("user", "Hello"),
                    ChatPanel.ChatMessageData("assistant", "Hi!")
                ),
                totalTokens = 100,
                createdAt = 1234567890L
            )

            val json = gson.toJson(session)

            assertTrue(json.contains("\"id\":\"test-id-123\""))
            assertTrue(json.contains("\"title\":\"Test Chat\""))
            assertTrue(json.contains("\"totalTokens\":100"))
            assertTrue(json.contains("\"createdAt\":1234567890"))
        }

        @Test
        @DisplayName("ChatSession should deserialize from JSON correctly")
        fun `ChatSession should deserialize from JSON correctly`() {
            val json = """{
                "id": "session-456",
                "title": "My Chat",
                "messages": [
                    {"role": "user", "content": "Question?"},
                    {"role": "assistant", "content": "Answer!"}
                ],
                "totalTokens": 50,
                "createdAt": 9876543210
            }"""

            val session = gson.fromJson(json, ChatPanel.ChatSession::class.java)

            assertEquals("session-456", session.id)
            assertEquals("My Chat", session.title)
            assertEquals(2, session.messages.size)
            assertEquals("user", session.messages[0].role)
            assertEquals("Question?", session.messages[0].content)
            assertEquals("assistant", session.messages[1].role)
            assertEquals("Answer!", session.messages[1].content)
            assertEquals(50, session.totalTokens)
            assertEquals(9876543210L, session.createdAt)
        }

        @Test
        @DisplayName("ChatSession should handle empty messages list")
        fun `ChatSession should handle empty messages list`() {
            val session = ChatPanel.ChatSession(
                id = "empty-session",
                title = "New Chat",
                messages = mutableListOf(),
                totalTokens = 0,
                createdAt = System.currentTimeMillis()
            )

            val json = gson.toJson(session)
            val restored = gson.fromJson(json, ChatPanel.ChatSession::class.java)

            assertEquals(0, restored.messages.size)
            assertEquals(0, restored.totalTokens)
        }

        @Test
        @DisplayName("ChatSession list should serialize and deserialize correctly")
        fun `ChatSession list should serialize and deserialize correctly`() {
            val sessions = listOf(
                ChatPanel.ChatSession(
                    id = "1",
                    title = "Chat 1",
                    messages = mutableListOf(ChatPanel.ChatMessageData("user", "Hi")),
                    totalTokens = 10,
                    createdAt = 1000L
                ),
                ChatPanel.ChatSession(
                    id = "2",
                    title = "Chat 2",
                    messages = mutableListOf(),
                    totalTokens = 0,
                    createdAt = 2000L
                )
            )

            val json = gson.toJson(sessions)
            val type = object : TypeToken<List<ChatPanel.ChatSession>>() {}.type
            val restored: List<ChatPanel.ChatSession> = gson.fromJson(json, type)

            assertEquals(2, restored.size)
            assertEquals("1", restored[0].id)
            assertEquals("Chat 1", restored[0].title)
            assertEquals("2", restored[1].id)
            assertEquals("Chat 2", restored[1].title)
        }

        @Test
        @DisplayName("ChatSession should handle long title")
        fun `ChatSession should handle long title`() {
            val longTitle = "A".repeat(200)
            val session = ChatPanel.ChatSession(
                id = "long-title",
                title = longTitle,
                messages = mutableListOf(),
                totalTokens = 0,
                createdAt = System.currentTimeMillis()
            )

            val json = gson.toJson(session)
            val restored = gson.fromJson(json, ChatPanel.ChatSession::class.java)

            assertEquals(longTitle, restored.title)
        }
    }

    @Nested
    @DisplayName("Settings Serialization Tests")
    inner class SettingsSerializationTests {

        @Test
        @DisplayName("Settings map should serialize correctly")
        fun `Settings map should serialize correctly`() {
            val settings = mapOf("selectedModel" to "openai/gpt-4o")

            val json = gson.toJson(settings)

            assertTrue(json.contains("selectedModel"))
            assertTrue(json.contains("openai/gpt-4o"))
        }

        @Test
        @DisplayName("Settings map should deserialize correctly")
        fun `Settings map should deserialize correctly`() {
            val json = """{"selectedModel":"anthropic/claude-3.5-sonnet"}"""

            val type = object : TypeToken<Map<String, String>>() {}.type
            val settings: Map<String, String> = gson.fromJson(json, type)

            assertEquals("anthropic/claude-3.5-sonnet", settings["selectedModel"])
        }

        @Test
        @DisplayName("Settings should handle missing keys gracefully")
        fun `Settings should handle missing keys gracefully`() {
            val json = """{}"""

            val type = object : TypeToken<Map<String, String>>() {}.type
            val settings: Map<String, String> = gson.fromJson(json, type)

            assertEquals(null, settings["selectedModel"])
        }
    }
}
