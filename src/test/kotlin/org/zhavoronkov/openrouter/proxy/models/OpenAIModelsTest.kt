package org.zhavoronkov.openrouter.proxy.models

import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@DisplayName("OpenAI Models Tests")
class OpenAIModelsTest {

    private val gson = Gson()

    @Nested
    @DisplayName("OpenAIChatCompletionRequest Tests")
    inner class ChatCompletionRequestTests {

        @Test
        @DisplayName("should create request with all fields")
        fun testFullRequest() {
            val request = OpenAIChatCompletionRequest(
                model = "gpt-4",
                messages = listOf(
                    OpenAIChatMessage(role = "user", content = JsonPrimitive("Hello"))
                ),
                temperature = 0.7,
                maxTokens = 1000,
                topP = 0.9,
                frequencyPenalty = 0.5,
                presencePenalty = 0.3,
                stop = listOf("STOP", "END"),
                stream = true,
                user = "user123"
            )

            assertEquals("gpt-4", request.model)
            assertEquals(1, request.messages.size)
            assertEquals(0.7, request.temperature)
            assertEquals(1000, request.maxTokens)
            assertEquals(0.9, request.topP)
            assertEquals(0.5, request.frequencyPenalty)
            assertEquals(0.3, request.presencePenalty)
            assertEquals(listOf("STOP", "END"), request.stop)
            assertEquals(true, request.stream)
            assertEquals("user123", request.user)
        }

        @Test
        @DisplayName("should create request with default values")
        fun testDefaultValues() {
            val request = OpenAIChatCompletionRequest(
                model = "gpt-4",
                messages = listOf(
                    OpenAIChatMessage(role = "user", content = JsonPrimitive("Hello"))
                )
            )

            assertNull(request.temperature)
            assertNull(request.maxTokens)
            assertNull(request.topP)
            assertNull(request.frequencyPenalty)
            assertNull(request.presencePenalty)
            assertNull(request.stop)
            assertEquals(false, request.stream) // Default is false
            assertNull(request.user)
        }

        @Test
        @DisplayName("should serialize to JSON correctly")
        fun testSerialization() {
            val request = OpenAIChatCompletionRequest(
                model = "gpt-4",
                messages = listOf(
                    OpenAIChatMessage(role = "user", content = JsonPrimitive("Hello"))
                ),
                maxTokens = 100
            )

            val json = gson.toJson(request)

            assertTrue(json.contains("\"model\":\"gpt-4\""))
            assertTrue(json.contains("\"max_tokens\":100"))
        }

        @Test
        @DisplayName("should deserialize from JSON correctly")
        fun testDeserialization() {
            val json = """
                {
                    "model": "gpt-4",
                    "messages": [{"role": "user", "content": "Hello"}],
                    "temperature": 0.5,
                    "max_tokens": 200
                }
            """.trimIndent()

            val request = gson.fromJson(json, OpenAIChatCompletionRequest::class.java)

            assertEquals("gpt-4", request.model)
            assertEquals(1, request.messages.size)
            assertEquals(0.5, request.temperature)
            assertEquals(200, request.maxTokens)
        }
    }

    @Nested
    @DisplayName("OpenAIChatMessage Tests")
    inner class ChatMessageTests {

        @Test
        @DisplayName("should create message with string content")
        fun testStringContent() {
            val message = OpenAIChatMessage(
                role = "user",
                content = JsonPrimitive("Hello, world!")
            )

            assertEquals("user", message.role)
            assertTrue(message.content.isJsonPrimitive)
            assertEquals("Hello, world!", message.content.asString)
            assertNull(message.name)
        }

        @Test
        @DisplayName("should create message with array content (multimodal)")
        fun testArrayContent() {
            val contentArray = JsonArray().apply {
                add(JsonObject().apply {
                    addProperty("type", "text")
                    addProperty("text", "What's in this image?")
                })
                add(JsonObject().apply {
                    addProperty("type", "image_url")
                    add("image_url", JsonObject().apply {
                        addProperty("url", "https://example.com/image.png")
                    })
                })
            }

            val message = OpenAIChatMessage(
                role = "user",
                content = contentArray
            )

            assertTrue(message.content.isJsonArray)
            assertEquals(2, message.content.asJsonArray.size())
        }

        @Test
        @DisplayName("should create message with name")
        fun testMessageWithName() {
            val message = OpenAIChatMessage(
                role = "user",
                content = JsonPrimitive("Hello"),
                name = "John"
            )

            assertEquals("John", message.name)
        }

        @Test
        @DisplayName("should support all roles")
        fun testAllRoles() {
            val roles = listOf("system", "user", "assistant")

            roles.forEach { role ->
                val message = OpenAIChatMessage(
                    role = role,
                    content = JsonPrimitive("Test")
                )
                assertEquals(role, message.role)
            }
        }
    }

    @Nested
    @DisplayName("OpenAIContentPart Tests")
    inner class ContentPartTests {

        @Test
        @DisplayName("should create text content part")
        fun testTextContentPart() {
            val part = OpenAIContentPart(
                type = "text",
                text = "Hello, world!"
            )

            assertEquals("text", part.type)
            assertEquals("Hello, world!", part.text)
            assertNull(part.imageUrl)
            assertNull(part.inputAudio)
            assertNull(part.videoUrl)
            assertNull(part.file)
        }

        @Test
        @DisplayName("should create image_url content part")
        fun testImageUrlContentPart() {
            val part = OpenAIContentPart(
                type = "image_url",
                imageUrl = OpenAIImageUrl(
                    url = "https://example.com/image.png",
                    detail = "high"
                )
            )

            assertEquals("image_url", part.type)
            assertNotNull(part.imageUrl)
            assertEquals("https://example.com/image.png", part.imageUrl?.url)
            assertEquals("high", part.imageUrl?.detail)
        }

        @Test
        @DisplayName("should create input_audio content part")
        fun testInputAudioContentPart() {
            val part = OpenAIContentPart(
                type = "input_audio",
                inputAudio = OpenAIInputAudio(
                    data = "base64encodedaudio",
                    format = "mp3"
                )
            )

            assertEquals("input_audio", part.type)
            assertNotNull(part.inputAudio)
            assertEquals("base64encodedaudio", part.inputAudio?.data)
            assertEquals("mp3", part.inputAudio?.format)
        }

        @Test
        @DisplayName("should create video_url content part")
        fun testVideoUrlContentPart() {
            val part = OpenAIContentPart(
                type = "video_url",
                videoUrl = OpenAIVideoUrl(url = "https://youtube.com/watch?v=123")
            )

            assertEquals("video_url", part.type)
            assertNotNull(part.videoUrl)
            assertEquals("https://youtube.com/watch?v=123", part.videoUrl?.url)
        }

        @Test
        @DisplayName("should create file content part")
        fun testFileContentPart() {
            val part = OpenAIContentPart(
                type = "file",
                file = OpenAIFile(
                    filename = "document.pdf",
                    fileData = "data:application/pdf;base64,..."
                )
            )

            assertEquals("file", part.type)
            assertNotNull(part.file)
            assertEquals("document.pdf", part.file?.filename)
            assertEquals("data:application/pdf;base64,...", part.file?.fileData)
        }
    }

    @Nested
    @DisplayName("OpenAIImageUrl Tests")
    inner class ImageUrlTests {

        @Test
        @DisplayName("should create with URL only")
        fun testUrlOnly() {
            val imageUrl = OpenAIImageUrl(url = "https://example.com/image.png")

            assertEquals("https://example.com/image.png", imageUrl.url)
            assertNull(imageUrl.detail)
        }

        @Test
        @DisplayName("should create with base64 data URI")
        fun testBase64DataUri() {
            val imageUrl = OpenAIImageUrl(
                url = "data:image/jpeg;base64,/9j/4AAQSkZJRg...",
                detail = "low"
            )

            assertTrue(imageUrl.url.startsWith("data:image/"))
            assertEquals("low", imageUrl.detail)
        }

        @Test
        @DisplayName("should support all detail levels")
        fun testDetailLevels() {
            listOf("auto", "low", "high").forEach { detail ->
                val imageUrl = OpenAIImageUrl(
                    url = "https://example.com/image.png",
                    detail = detail
                )
                assertEquals(detail, imageUrl.detail)
            }
        }
    }

    @Nested
    @DisplayName("OpenAIChatCompletionResponse Tests")
    inner class ChatCompletionResponseTests {

        @Test
        @DisplayName("should create response with all fields")
        fun testFullResponse() {
            val response = OpenAIChatCompletionResponse(
                id = "chatcmpl-123",
                created = 1677652288,
                model = "gpt-4",
                choices = listOf(
                    OpenAIChatChoice(
                        index = 0,
                        message = OpenAIChatMessage(
                            role = "assistant",
                            content = JsonPrimitive("Hello!")
                        ),
                        finishReason = "stop"
                    )
                ),
                usage = OpenAIUsage(
                    promptTokens = 10,
                    completionTokens = 5,
                    totalTokens = 15
                )
            )

            assertEquals("chatcmpl-123", response.id)
            assertEquals("chat.completion", response.`object`)
            assertEquals(1677652288, response.created)
            assertEquals("gpt-4", response.model)
            assertEquals(1, response.choices.size)
            assertNotNull(response.usage)
            assertEquals(15, response.usage?.totalTokens)
        }

        @Test
        @DisplayName("should use default object value")
        fun testDefaultObjectValue() {
            val response = OpenAIChatCompletionResponse(
                id = "chatcmpl-123",
                created = 1677652288,
                model = "gpt-4",
                choices = emptyList()
            )

            assertEquals("chat.completion", response.`object`)
        }

        @Test
        @DisplayName("should allow null usage")
        fun testNullUsage() {
            val response = OpenAIChatCompletionResponse(
                id = "chatcmpl-123",
                created = 1677652288,
                model = "gpt-4",
                choices = emptyList(),
                usage = null
            )

            assertNull(response.usage)
        }
    }

    @Nested
    @DisplayName("OpenAIChatChoice Tests")
    inner class ChatChoiceTests {

        @Test
        @DisplayName("should create choice with all fields")
        fun testFullChoice() {
            val choice = OpenAIChatChoice(
                index = 0,
                message = OpenAIChatMessage(
                    role = "assistant",
                    content = JsonPrimitive("Hello!")
                ),
                finishReason = "stop"
            )

            assertEquals(0, choice.index)
            assertEquals("assistant", choice.message.role)
            assertEquals("stop", choice.finishReason)
        }

        @Test
        @DisplayName("should support different finish reasons")
        fun testFinishReasons() {
            listOf("stop", "length", "content_filter", "tool_calls", null).forEach { reason ->
                val choice = OpenAIChatChoice(
                    index = 0,
                    message = OpenAIChatMessage(
                        role = "assistant",
                        content = JsonPrimitive("Hello!")
                    ),
                    finishReason = reason
                )
                assertEquals(reason, choice.finishReason)
            }
        }
    }

    @Nested
    @DisplayName("OpenAIUsage Tests")
    inner class UsageTests {

        @Test
        @DisplayName("should create usage with token counts")
        fun testTokenCounts() {
            val usage = OpenAIUsage(
                promptTokens = 100,
                completionTokens = 50,
                totalTokens = 150
            )

            assertEquals(100, usage.promptTokens)
            assertEquals(50, usage.completionTokens)
            assertEquals(150, usage.totalTokens)
        }

        @Test
        @DisplayName("should serialize with snake_case")
        fun testSerialization() {
            val usage = OpenAIUsage(
                promptTokens = 10,
                completionTokens = 5,
                totalTokens = 15
            )

            val json = gson.toJson(usage)

            assertTrue(json.contains("\"prompt_tokens\":10"))
            assertTrue(json.contains("\"completion_tokens\":5"))
            assertTrue(json.contains("\"total_tokens\":15"))
        }
    }

    @Nested
    @DisplayName("OpenAIModelsResponse Tests")
    inner class ModelsResponseTests {

        @Test
        @DisplayName("should create models response")
        fun testModelsResponse() {
            val response = OpenAIModelsResponse(
                data = listOf(
                    OpenAIModel(
                        id = "gpt-4",
                        created = 1687882411,
                        ownedBy = "openai"
                    )
                )
            )

            assertEquals("list", response.`object`)
            assertEquals(1, response.data.size)
            assertEquals("gpt-4", response.data.first().id)
        }

        @Test
        @DisplayName("should use default object value")
        fun testDefaultObjectValue() {
            val response = OpenAIModelsResponse(data = emptyList())

            assertEquals("list", response.`object`)
        }
    }

    @Nested
    @DisplayName("OpenAIModel Tests")
    inner class ModelTests {

        @Test
        @DisplayName("should create model with all fields")
        fun testFullModel() {
            val model = OpenAIModel(
                id = "gpt-4",
                created = 1687882411,
                ownedBy = "openai",
                permission = listOf(
                    OpenAIPermission(
                        id = "perm-123",
                        created = 1687882411
                    )
                ),
                root = "gpt-4",
                parent = null
            )

            assertEquals("gpt-4", model.id)
            assertEquals("model", model.`object`)
            assertEquals(1687882411, model.created)
            assertEquals("openai", model.ownedBy)
            assertEquals(1, model.permission.size)
            assertEquals("gpt-4", model.root)
            assertNull(model.parent)
        }

        @Test
        @DisplayName("should use default values")
        fun testDefaultValues() {
            val model = OpenAIModel(
                id = "gpt-4",
                created = 1687882411,
                ownedBy = "openai"
            )

            assertEquals("model", model.`object`)
            assertTrue(model.permission.isEmpty())
            assertNull(model.root)
            assertNull(model.parent)
        }
    }

    @Nested
    @DisplayName("OpenAIPermission Tests")
    inner class PermissionTests {

        @Test
        @DisplayName("should create permission with all fields")
        fun testFullPermission() {
            val permission = OpenAIPermission(
                id = "perm-123",
                created = 1687882411,
                allowCreateEngine = true,
                allowSampling = true,
                allowLogprobs = true,
                allowSearchIndices = true,
                allowView = true,
                allowFineTuning = true,
                organization = "org-123",
                group = "group-456",
                isBlocking = true
            )

            assertEquals("perm-123", permission.id)
            assertEquals("model_permission", permission.`object`)
            assertEquals(1687882411, permission.created)
            assertTrue(permission.allowCreateEngine)
            assertTrue(permission.allowSampling)
            assertTrue(permission.allowLogprobs)
            assertTrue(permission.allowSearchIndices)
            assertTrue(permission.allowView)
            assertTrue(permission.allowFineTuning)
            assertEquals("org-123", permission.organization)
            assertEquals("group-456", permission.group)
            assertTrue(permission.isBlocking)
        }

        @Test
        @DisplayName("should use default values")
        fun testDefaultValues() {
            val permission = OpenAIPermission(
                id = "perm-123",
                created = 1687882411
            )

            assertEquals("model_permission", permission.`object`)
            assertFalse(permission.allowCreateEngine)
            assertTrue(permission.allowSampling)
            assertTrue(permission.allowLogprobs)
            assertFalse(permission.allowSearchIndices)
            assertTrue(permission.allowView)
            assertFalse(permission.allowFineTuning)
            assertEquals("*", permission.organization)
            assertNull(permission.group)
            assertFalse(permission.isBlocking)
        }
    }

    @Nested
    @DisplayName("OpenAIErrorResponse Tests")
    inner class ErrorResponseTests {

        @Test
        @DisplayName("should create error response")
        fun testErrorResponse() {
            val response = OpenAIErrorResponse(
                error = OpenAIError(
                    message = "Invalid API key",
                    type = "invalid_request_error",
                    code = "invalid_api_key"
                )
            )

            assertEquals("Invalid API key", response.error.message)
            assertEquals("invalid_request_error", response.error.type)
            assertEquals("invalid_api_key", response.error.code)
        }

        @Test
        @DisplayName("should allow null optional fields in error")
        fun testErrorWithNulls() {
            val error = OpenAIError(
                message = "Something went wrong",
                type = "server_error",
                param = null,
                code = null
            )

            assertNull(error.param)
            assertNull(error.code)
        }

        @Test
        @DisplayName("should include param in error")
        fun testErrorWithParam() {
            val error = OpenAIError(
                message = "Invalid parameter",
                type = "invalid_request_error",
                param = "temperature"
            )

            assertEquals("temperature", error.param)
        }
    }

    @Nested
    @DisplayName("OpenAIStreamResponse Tests")
    inner class StreamResponseTests {

        @Test
        @DisplayName("should create stream response")
        fun testStreamResponse() {
            val response = OpenAIStreamResponse(
                id = "chatcmpl-123",
                created = 1677652288,
                model = "gpt-4",
                choices = listOf(
                    OpenAIStreamChoice(
                        index = 0,
                        delta = OpenAIChatMessage(
                            role = "assistant",
                            content = JsonPrimitive("Hel")
                        ),
                        finishReason = null
                    )
                )
            )

            assertEquals("chatcmpl-123", response.id)
            assertEquals("chat.completion.chunk", response.`object`)
            assertEquals(1677652288, response.created)
            assertEquals("gpt-4", response.model)
            assertEquals(1, response.choices.size)
        }

        @Test
        @DisplayName("should use default object value")
        fun testDefaultObjectValue() {
            val response = OpenAIStreamResponse(
                id = "chatcmpl-123",
                created = 1677652288,
                model = "gpt-4",
                choices = emptyList()
            )

            assertEquals("chat.completion.chunk", response.`object`)
        }
    }

    @Nested
    @DisplayName("OpenAIStreamChoice Tests")
    inner class StreamChoiceTests {

        @Test
        @DisplayName("should create stream choice with delta")
        fun testStreamChoice() {
            val choice = OpenAIStreamChoice(
                index = 0,
                delta = OpenAIChatMessage(
                    role = "assistant",
                    content = JsonPrimitive("Hello")
                ),
                finishReason = null
            )

            assertEquals(0, choice.index)
            assertEquals("assistant", choice.delta.role)
            assertNull(choice.finishReason)
        }

        @Test
        @DisplayName("should handle final chunk with finish reason")
        fun testFinalChunk() {
            val choice = OpenAIStreamChoice(
                index = 0,
                delta = OpenAIChatMessage(
                    role = "assistant",
                    content = JsonPrimitive("")
                ),
                finishReason = "stop"
            )

            assertEquals("stop", choice.finishReason)
        }
    }

    @Nested
    @DisplayName("OpenAIInputAudio Tests")
    inner class InputAudioTests {

        @Test
        @DisplayName("should create input audio")
        fun testInputAudio() {
            val audio = OpenAIInputAudio(
                data = "SGVsbG8gV29ybGQ=",
                format = "wav"
            )

            assertEquals("SGVsbG8gV29ybGQ=", audio.data)
            assertEquals("wav", audio.format)
        }

        @Test
        @DisplayName("should support different formats")
        fun testDifferentFormats() {
            listOf("wav", "mp3", "ogg", "flac").forEach { format ->
                val audio = OpenAIInputAudio(
                    data = "base64data",
                    format = format
                )
                assertEquals(format, audio.format)
            }
        }
    }

    @Nested
    @DisplayName("OpenAIVideoUrl Tests")
    inner class VideoUrlTests {

        @Test
        @DisplayName("should create video URL")
        fun testVideoUrl() {
            val video = OpenAIVideoUrl(url = "https://youtube.com/watch?v=abc123")

            assertEquals("https://youtube.com/watch?v=abc123", video.url)
        }

        @Test
        @DisplayName("should support base64 data URI")
        fun testBase64DataUri() {
            val video = OpenAIVideoUrl(url = "data:video/mp4;base64,AAAAHGZ0eXA...")

            assertTrue(video.url.startsWith("data:video/"))
        }
    }

    @Nested
    @DisplayName("OpenAIFile Tests")
    inner class FileTests {

        @Test
        @DisplayName("should create file with URL")
        fun testFileWithUrl() {
            val file = OpenAIFile(
                filename = "document.pdf",
                fileData = "https://example.com/document.pdf"
            )

            assertEquals("document.pdf", file.filename)
            assertEquals("https://example.com/document.pdf", file.fileData)
        }

        @Test
        @DisplayName("should create file with base64 data")
        fun testFileWithBase64() {
            val file = OpenAIFile(
                filename = "report.pdf",
                fileData = "data:application/pdf;base64,JVBERi0xLjQ..."
            )

            assertEquals("report.pdf", file.filename)
            assertTrue(file.fileData.startsWith("data:application/pdf;"))
        }

        @Test
        @DisplayName("should serialize with snake_case")
        fun testSerialization() {
            val file = OpenAIFile(
                filename = "test.pdf",
                fileData = "data:application/pdf;base64,test"
            )

            val json = gson.toJson(file)

            assertTrue(json.contains("\"file_data\""))
        }
    }
}
