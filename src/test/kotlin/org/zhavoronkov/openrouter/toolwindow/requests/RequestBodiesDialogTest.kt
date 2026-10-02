package org.zhavoronkov.openrouter.toolwindow.requests

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.zhavoronkov.openrouter.requests.RequestBodies

@DisplayName("RequestBodiesDialog")
class RequestBodiesDialogTest {

    @Test
    @DisplayName("lists only the bodies a request has, in the order they happened, JSON indented")
    fun tabs() {
        val tabs = RequestBodiesDialog.tabs(
            RequestBodies(sent = """{"model":"m"}""", reply = """{"a":1}""" + "\n" + """{"b":2}""")
        )

        assertEquals(listOf("Sent to OpenRouter", "Reply"), tabs.map { it.first })
        assertEquals("{\n  \"model\": \"m\"\n}", tabs[0].second)
        assertEquals("{\n  \"a\": 1\n}\n\n{\n  \"b\": 2\n}", tabs[1].second)
    }

    @Test
    @DisplayName("text that is not one JSON object or array - a cut body, a failure message - is shown as it is")
    fun plainText() {
        assertEquals("""{"model":"m""", RequestBodiesDialog.indented("""{"model":"m"""))
        assertEquals("Network error: timeout", RequestBodiesDialog.indented("Network error: timeout"))
        assertEquals("42", RequestBodiesDialog.indented("42"))
    }

    @Test
    @DisplayName("a request with every body lists all four, the failure as it was reported")
    fun everyBody() {
        val tabs = RequestBodiesDialog.tabs(
            RequestBodies(received = "[1,2]", sent = "{}", reply = "plain", failure = """{"error":"boom"}""")
        )

        assertEquals(listOf("Received", "Sent to OpenRouter", "Reply", "Failure"), tabs.map { it.first })
        assertEquals("[\n  1,\n  2\n]", tabs[0].second)
        assertEquals("plain", tabs[2].second)
        assertEquals("""{"error":"boom"}""", tabs[3].second)
        assertEquals(emptyList<Pair<String, String>>(), RequestBodiesDialog.tabs(RequestBodies()))
    }

    @Test
    @DisplayName("empty text and a JSON null are shown as they are")
    fun emptyText() {
        assertEquals("", RequestBodiesDialog.indented(""))
        assertEquals("null", RequestBodiesDialog.indented("null"))
    }
}
