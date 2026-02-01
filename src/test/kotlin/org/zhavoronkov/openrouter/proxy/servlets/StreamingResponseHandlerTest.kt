package org.zhavoronkov.openrouter.proxy.servlets

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.io.PrintWriter
import java.io.StringWriter

@DisplayName("StreamingResponseHandler Tests")
class StreamingResponseHandlerTest {

    @Test
    fun `streamResponseToClient should emit done marker`() {
        val bodyContent = """
            data: {\"id\":\"chatcmpl-1\",\"object\":\"chat.completion.chunk\",\"created\":1,\"model\":\"gpt\",\"choices\":[{\"index\":0,\"delta\":{\"role\":\"assistant\",\"content\":\"Hi\"}}]}
            
            data: [DONE]
            
        """.trimIndent()
        val responseBody: ResponseBody = bodyContent.toResponseBody("text/event-stream".toMediaType())
        val response = Response.Builder()
            .request(Request.Builder().url("http://localhost").build())
            .protocol(Protocol.HTTP_1_1)
            .code(200)
            .message("OK")
            .body(responseBody)
            .build()

        val handler = StreamingResponseHandler()
        val output = StringWriter()
        val writer = PrintWriter(output)

        handler.streamResponseToClient(response, writer, "req-1")

        assertTrue(output.toString().contains("[DONE]"))
    }
}
