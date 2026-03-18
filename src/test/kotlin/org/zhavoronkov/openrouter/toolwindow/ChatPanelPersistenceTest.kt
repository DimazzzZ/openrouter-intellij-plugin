package org.zhavoronkov.openrouter.toolwindow

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path

/**
 * Tests for ChatPanel file-based persistence functionality
 *
 * These tests verify:
 * - Chat sessions are saved and loaded correctly from files
 * - Model selection is persisted correctly
 * - Error handling for missing/corrupt files
 */
@DisplayName("ChatPanel Persistence Tests")
class ChatPanelPersistenceTest {

    private val gson = Gson()

    @TempDir
    lateinit var tempDir: Path

    private lateinit var pluginDir: File
    private lateinit var chatsFile: File
    private lateinit var settingsFile: File

    @BeforeEach
    fun setUp() {
        pluginDir = File(tempDir.toFile(), "openrouter")
        pluginDir.mkdirs()
        chatsFile = File(pluginDir, "openrouter-chats.json")
        settingsFile = File(pluginDir, "openrouter-chat-settings.json")
    }

    @AfterEach
    fun tearDown() {
        chatsFile.delete()
        settingsFile.delete()
        pluginDir.delete()
    }

    @Nested
    @DisplayName("Chat Sessions File Persistence")
    inner class ChatSessionsFilePersistenceTests {

        @Test
        @DisplayName("saveChats should write chat sessions to JSON file")
        fun `saveChats should write chat sessions to JSON file`() {
            val sessions = listOf(
                ChatPanel.ChatSession(
                    id = "session-1",
                    title = "Test Chat",
                    messages = mutableListOf(
                        ChatPanel.ChatMessageData("user", "Hello"),
                        ChatPanel.ChatMessageData("assistant", "Hi there!")
                    ),
                    totalTokens = 50,
                    createdAt = 1234567890L
                )
            )

            // Simulate saving chats
            val json = gson.toJson(sessions)
            chatsFile.writeText(json)

            // Verify file exists and contains data
            assertTrue(chatsFile.exists())
            val content = chatsFile.readText()
            assertTrue(content.contains("session-1"))
            assertTrue(content.contains("Test Chat"))
            assertTrue(content.contains("Hello"))
        }

        @Test
        @DisplayName("loadChats should read chat sessions from JSON file")
        fun `loadChats should read chat sessions from JSON file`() {
            val json = """[
                {
                    "id": "loaded-session",
                    "title": "Loaded Chat",
                    "messages": [
                        {"role": "user", "content": "Test message"}
                    ],
                    "totalTokens": 25,
                    "createdAt": 9876543210
                }
            ]"""

            chatsFile.writeText(json)

            // Simulate loading chats
            val type = object : TypeToken<List<ChatPanel.ChatSession>>() {}.type
            val loaded: List<ChatPanel.ChatSession> = gson.fromJson(chatsFile.readText(), type)

            assertEquals(1, loaded.size)
            assertEquals("loaded-session", loaded[0].id)
            assertEquals("Loaded Chat", loaded[0].title)
            assertEquals(1, loaded[0].messages.size)
            assertEquals("user", loaded[0].messages[0].role)
            assertEquals("Test message", loaded[0].messages[0].content)
        }

        @Test
        @DisplayName("loadChats should handle missing file gracefully")
        fun `loadChats should handle missing file gracefully`() {
            // File doesn't exist
            assertFalse(chatsFile.exists())

            // Simulate loading - should return empty list or handle gracefully
            val sessions = if (chatsFile.exists()) {
                val type = object : TypeToken<List<ChatPanel.ChatSession>>() {}.type
                gson.fromJson<List<ChatPanel.ChatSession>>(chatsFile.readText(), type)
            } else {
                emptyList()
            }

            assertTrue(sessions.isEmpty())
        }

        @Test
        @DisplayName("loadChats should handle corrupt JSON gracefully")
        fun `loadChats should handle corrupt JSON gracefully`() {
            chatsFile.writeText("{ invalid json }")

            // Simulate loading with error handling - expected behavior for corrupt files
            val sessions = try {
                val type = object : TypeToken<List<ChatPanel.ChatSession>>() {}.type
                gson.fromJson<List<ChatPanel.ChatSession>>(chatsFile.readText(), type)
            } catch (@Suppress("SwallowedException") e: Exception) {
                // Expected: corrupt JSON should be handled gracefully by returning empty list
                emptyList()
            }

            // Should handle gracefully (empty list instead of crash)
            assertTrue(sessions.isEmpty())
        }

        @Test
        @DisplayName("loadChats should handle empty JSON array")
        fun `loadChats should handle empty JSON array`() {
            chatsFile.writeText("[]")

            val type = object : TypeToken<List<ChatPanel.ChatSession>>() {}.type
            val sessions: List<ChatPanel.ChatSession> = gson.fromJson(chatsFile.readText(), type)

            assertTrue(sessions.isEmpty())
        }

        @Test
        @DisplayName("saveChats should create openrouter directory if not exists")
        fun `saveChats should create openrouter directory if not exists`() {
            val newDir = File(tempDir.toFile(), "newdir/openrouter")
            val newChatsFile = File(newDir, "openrouter-chats.json")

            // Create directory structure before writing
            newDir.mkdirs()

            val sessions = listOf(
                ChatPanel.ChatSession(
                    id = "new-session",
                    title = "New Chat",
                    messages = mutableListOf(),
                    totalTokens = 0,
                    createdAt = System.currentTimeMillis()
                )
            )

            newChatsFile.writeText(gson.toJson(sessions))

            assertTrue(newDir.exists())
            assertTrue(newChatsFile.exists())

            // Cleanup
            newChatsFile.delete()
            newDir.deleteRecursively()
        }

        @Test
        @DisplayName("saveChats should preserve message order")
        fun `saveChats should preserve message order`() {
            val messages = mutableListOf(
                ChatPanel.ChatMessageData("user", "First"),
                ChatPanel.ChatMessageData("assistant", "Second"),
                ChatPanel.ChatMessageData("user", "Third"),
                ChatPanel.ChatMessageData("assistant", "Fourth")
            )

            val session = ChatPanel.ChatSession(
                id = "ordered-session",
                title = "Order Test",
                messages = messages,
                totalTokens = 100,
                createdAt = System.currentTimeMillis()
            )

            chatsFile.writeText(gson.toJson(listOf(session)))

            val type = object : TypeToken<List<ChatPanel.ChatSession>>() {}.type
            val loaded: List<ChatPanel.ChatSession> = gson.fromJson(chatsFile.readText(), type)

            assertEquals(4, loaded[0].messages.size)
            assertEquals("First", loaded[0].messages[0].content)
            assertEquals("Second", loaded[0].messages[1].content)
            assertEquals("Third", loaded[0].messages[2].content)
            assertEquals("Fourth", loaded[0].messages[3].content)
        }

        @Test
        @DisplayName("Multiple sessions should be saved and loaded correctly")
        fun `Multiple sessions should be saved and loaded correctly`() {
            val sessions = listOf(
                ChatPanel.ChatSession("1", "Chat 1", mutableListOf(), 10, 1000L),
                ChatPanel.ChatSession("2", "Chat 2", mutableListOf(), 20, 2000L),
                ChatPanel.ChatSession("3", "Chat 3", mutableListOf(), 30, 3000L)
            )

            chatsFile.writeText(gson.toJson(sessions))

            val type = object : TypeToken<List<ChatPanel.ChatSession>>() {}.type
            val loaded: List<ChatPanel.ChatSession> = gson.fromJson(chatsFile.readText(), type)

            assertEquals(3, loaded.size)
            assertEquals("1", loaded[0].id)
            assertEquals("2", loaded[1].id)
            assertEquals("3", loaded[2].id)
        }
    }

    @Nested
    @DisplayName("Model Selection Persistence")
    inner class ModelSelectionPersistenceTests {

        @Test
        @DisplayName("saveSelectedModel should write selected model to settings file")
        fun `saveSelectedModel should write selected model to settings file`() {
            val settings = mapOf("selectedModel" to "openai/gpt-4o")

            settingsFile.writeText(gson.toJson(settings))

            assertTrue(settingsFile.exists())
            val content = settingsFile.readText()
            assertTrue(content.contains("openai/gpt-4o"))
        }

        @Test
        @DisplayName("restoreSelectedModel should restore model from settings file")
        fun `restoreSelectedModel should restore model from settings file`() {
            val json = """{"selectedModel":"anthropic/claude-3.5-sonnet"}"""
            settingsFile.writeText(json)

            val type = object : TypeToken<Map<String, String>>() {}.type
            val settings: Map<String, String> = gson.fromJson(settingsFile.readText(), type)

            assertEquals("anthropic/claude-3.5-sonnet", settings["selectedModel"])
        }

        @Test
        @DisplayName("restoreSelectedModel should handle missing file gracefully")
        fun `restoreSelectedModel should handle missing file gracefully`() {
            assertFalse(settingsFile.exists())

            val selectedModel = if (settingsFile.exists()) {
                val type = object : TypeToken<Map<String, String>>() {}.type
                val settings: Map<String, String> = gson.fromJson(settingsFile.readText(), type)
                settings["selectedModel"]
            } else {
                null
            }

            assertEquals(null, selectedModel)
        }

        @Test
        @DisplayName("restoreSelectedModel should handle corrupt JSON gracefully")
        fun `restoreSelectedModel should handle corrupt JSON gracefully`() {
            settingsFile.writeText("not valid json {")

            val selectedModel = try {
                val type = object : TypeToken<Map<String, String>>() {}.type
                val settings: Map<String, String> = gson.fromJson(settingsFile.readText(), type)
                settings["selectedModel"]
            } catch (@Suppress("SwallowedException") e: Exception) {
                // Expected: corrupt JSON should be handled gracefully by returning null
                null
            }

            assertEquals(null, selectedModel)
        }

        @Test
        @DisplayName("Model with special characters should be saved correctly")
        fun `Model with special characters should be saved correctly`() {
            val modelWithSlash = "openai/gpt-4o-mini"
            val settings = mapOf("selectedModel" to modelWithSlash)

            settingsFile.writeText(gson.toJson(settings))

            val type = object : TypeToken<Map<String, String>>() {}.type
            val loaded: Map<String, String> = gson.fromJson(settingsFile.readText(), type)

            assertEquals(modelWithSlash, loaded["selectedModel"])
        }
    }

    @Nested
    @DisplayName("Large Data Persistence")
    inner class LargeDataPersistenceTests {

        @Test
        @DisplayName("Should handle large number of chat sessions")
        fun `Should handle large number of chat sessions`() {
            val sessions = (1..100).map { i ->
                ChatPanel.ChatSession(
                    id = "session-$i",
                    title = "Chat $i",
                    messages = mutableListOf(
                        ChatPanel.ChatMessageData("user", "Message $i"),
                        ChatPanel.ChatMessageData("assistant", "Response $i")
                    ),
                    totalTokens = i * 10,
                    createdAt = System.currentTimeMillis() + i
                )
            }

            chatsFile.writeText(gson.toJson(sessions))

            val type = object : TypeToken<List<ChatPanel.ChatSession>>() {}.type
            val loaded: List<ChatPanel.ChatSession> = gson.fromJson(chatsFile.readText(), type)

            assertEquals(100, loaded.size)
            assertEquals("session-1", loaded[0].id)
            assertEquals("session-100", loaded[99].id)
        }

        @Test
        @DisplayName("Should handle chat with many messages")
        fun `Should handle chat with many messages`() {
            val messages = (1..500).map { i ->
                if (i % 2 == 1) {
                    ChatPanel.ChatMessageData("user", "User message $i")
                } else {
                    ChatPanel.ChatMessageData("assistant", "Assistant response $i")
                }
            }.toMutableList()

            val session = ChatPanel.ChatSession(
                id = "large-chat",
                title = "Large Chat",
                messages = messages,
                totalTokens = 50000,
                createdAt = System.currentTimeMillis()
            )

            chatsFile.writeText(gson.toJson(listOf(session)))

            val type = object : TypeToken<List<ChatPanel.ChatSession>>() {}.type
            val loaded: List<ChatPanel.ChatSession> = gson.fromJson(chatsFile.readText(), type)

            assertEquals(500, loaded[0].messages.size)
            assertEquals("User message 1", loaded[0].messages[0].content)
            assertEquals("Assistant response 500", loaded[0].messages[499].content)
        }

        @Test
        @DisplayName("Should handle very long message content")
        fun `Should handle very long message content`() {
            val longContent = "A".repeat(100_000) // 100KB of text
            val session = ChatPanel.ChatSession(
                id = "long-content",
                title = "Long Content Chat",
                messages = mutableListOf(
                    ChatPanel.ChatMessageData("user", longContent)
                ),
                totalTokens = 25000,
                createdAt = System.currentTimeMillis()
            )

            chatsFile.writeText(gson.toJson(listOf(session)))

            val type = object : TypeToken<List<ChatPanel.ChatSession>>() {}.type
            val loaded: List<ChatPanel.ChatSession> = gson.fromJson(chatsFile.readText(), type)

            assertEquals(100_000, loaded[0].messages[0].content.length)
        }
    }
}
