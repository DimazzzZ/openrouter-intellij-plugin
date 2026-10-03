package org.zhavoronkov.openrouter.proxy.servlets

import com.google.gson.JsonParser
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.io.PrintWriter
import java.io.StringWriter

/**
 * A pair's stream names the pair the client asked for, as its whole reply does: each chunk that
 * carries a `model` is rewritten to it, and a chunk without one is forwarded as it came.
 */
@DisplayName("StreamingResponseHandler, naming the model the client asked for")
class StreamingResponseHandlerShownModelTest {

    private fun stream(vararg chunks: String, shownModel: String?): List<String> {
        val body = chunks.joinToString("") { "data: $it\n\n" } + "data: [DONE]\n\n"
        val response = Response.Builder()
            .request(Request.Builder().url("http://localhost").build())
            .protocol(Protocol.HTTP_1_1)
            .code(200)
            .message("OK")
            .body(body.toResponseBody("text/event-stream".toMediaType()))
            .build()
        val output = StringWriter()

        StreamingResponseHandler().streamResponseToClient(
            response,
            PrintWriter(output),
            "req-1",
            shownModel = shownModel
        )

        return output.toString().lines().filter { it.startsWith("data: {") }.map { it.removePrefix("data: ") }
    }

    private val chunk = """{"id":"1","object":"chat.completion.chunk","model":"openai/gpt-4o-mini","choices":[]}"""

    @Test
    @DisplayName("a chunk that names a model names the pair instead")
    fun renamed() {
        val written = stream(chunk, shownModel = "openai/gpt-4o-mini@preset/research").single()

        val model = JsonParser.parseString(written).asJsonObject["model"].asString
        assertEquals("openai/gpt-4o-mini@preset/research", model)
    }

    @Test
    @DisplayName("a chunk without a model, or a stream that is not a pair's, is forwarded as it came")
    fun unchanged() {
        val bare = """{"id":"1","object":"chat.completion.chunk","choices":[]}"""

        assertEquals(listOf(bare), stream(bare, shownModel = "openai/gpt-4o-mini@preset/research"))
        assertEquals(listOf(chunk), stream(chunk, shownModel = null))
    }
}
