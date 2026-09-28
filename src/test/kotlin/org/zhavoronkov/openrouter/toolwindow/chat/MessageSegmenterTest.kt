package org.zhavoronkov.openrouter.toolwindow.chat

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.zhavoronkov.openrouter.utils.MarkdownRenderer

@DisplayName("MessageSegmenter")
class MessageSegmenterTest {

    @Test
    @DisplayName("plain prose is one segment")
    fun `plain prose is one segment`() {
        val segments = MessageSegmenter.split("Just a sentence.")

        assertEquals(1, segments.size)
        assertTrue(segments[0] is MessageSegment.Prose)
        assertEquals("Just a sentence.", (segments[0] as MessageSegment.Prose).markdown)
    }

    @Test
    @DisplayName("a fenced block becomes a code segment carrying its language")
    fun `a fenced block becomes a code segment`() {
        val segments = MessageSegmenter.split("```kotlin\nval x = 1\n```")

        assertEquals(1, segments.size)
        val code = segments[0] as MessageSegment.Code
        assertEquals("kotlin", code.language)
        assertEquals("val x = 1", code.code)
    }

    @Test
    @DisplayName("prose around a fence produces three segments in order")
    fun `prose around a fence produces three segments in order`() {
        val segments = MessageSegmenter.split("Before.\n\n```\nrun()\n```\n\nAfter.")

        assertEquals(3, segments.size)
        assertEquals("Before.", (segments[0] as MessageSegment.Prose).markdown)
        assertEquals("run()", (segments[1] as MessageSegment.Code).code)
        assertEquals("After.", (segments[2] as MessageSegment.Prose).markdown)
    }

    @Test
    @DisplayName("a fence without an info string has a null language")
    fun `a fence without an info string has a null language`() {
        val segments = MessageSegmenter.split("```\nplain\n```")

        assertNull((segments[0] as MessageSegment.Code).language)
    }

    @Test
    @DisplayName("two fences in a row stay two segments")
    fun `two fences in a row stay two segments`() {
        val segments = MessageSegmenter.split("```\na\n```\n\n```\nb\n```")

        assertEquals(2, segments.size)
        assertEquals("a", (segments[0] as MessageSegment.Code).code)
        assertEquals("b", (segments[1] as MessageSegment.Code).code)
    }

    @Test
    @DisplayName("blank input produces no segments")
    fun `blank input produces no segments`() {
        assertTrue(MessageSegmenter.split("   ").isEmpty())
    }

    @Test
    @DisplayName("inline code is prose, not a code segment")
    fun `inline code is prose not a code segment`() {
        val segments = MessageSegmenter.split("Call `run()` first.")

        assertEquals(1, segments.size)
        assertTrue(segments[0] is MessageSegment.Prose)
    }

    @Test
    @DisplayName("list containing fence becomes isolated prose segment; surrounded paragraphs stay separate")
    fun `list with nested fence is isolated`() {
        val markdown = """First paragraph.

1. Install it:

   ```bash
   npm install foo
   ```

2. Run it.

Last paragraph."""

        val segments = MessageSegmenter.split(markdown)

        assertEquals(3, segments.size)
        assertEquals("First paragraph.", (segments[0] as MessageSegment.Prose).markdown)

        val listSegment = (segments[1] as MessageSegment.Prose).markdown
        assertTrue(listSegment.contains("npm install foo"), "isolated segment must contain fenced code body")
        assertTrue(listSegment.contains("1."), "isolated segment must contain list marker '1.'")
        assertTrue(listSegment.contains("2."), "isolated segment must contain list marker '2.'")

        assertEquals("Last paragraph.", (segments[2] as MessageSegment.Prose).markdown)
    }

    @Test
    @DisplayName("blockquote containing fence becomes isolated prose segment; surrounded paragraphs stay separate")
    fun `blockquote with nested fence is isolated`() {
        val markdown = """Before the quote.

> ```python
> code inside quote
> ```

After the quote."""

        val segments = MessageSegmenter.split(markdown)

        assertEquals(3, segments.size)
        assertEquals("Before the quote.", (segments[0] as MessageSegment.Prose).markdown)

        val quoteSegment = (segments[1] as MessageSegment.Prose).markdown
        assertTrue(quoteSegment.contains("code inside quote"), "isolated segment must contain fenced code body")
        assertTrue(quoteSegment.contains(">"), "isolated segment must contain quote marker")

        assertEquals("After the quote.", (segments[2] as MessageSegment.Prose).markdown)
    }

    @Test
    @DisplayName("ordinary prose without nested fences remains one segment")
    fun `prose without nested fences does not fragment`() {
        val markdown = """This is **bold** and [a link](http://example.com).

Regular paragraph here.

- item 1
- item 2

Final paragraph."""

        val segments = MessageSegmenter.split(markdown)

        assertEquals(1, segments.size)
        assertTrue(segments[0] is MessageSegment.Prose)
    }

    /**
     * The defect this pins: blocks were rejoined with nothing between them, and a table needs a
     * blank line before it or the whole reply parses as one paragraph. A model asked for a table
     * answered with rows of literal pipes.
     */
    @Test
    @DisplayName("a table between two paragraphs survives segmentation")
    fun `a table between two paragraphs survives segmentation`() {
        val original = """Here are the main features:

| Feature | Description |
|---------|-------------|
| Chat | Talk to any model |

Would you like more detail?"""

        val segments = MessageSegmenter.split(original)
        assertEquals(1, segments.size)
        val html = MarkdownRenderer.renderToHtml((segments[0] as MessageSegment.Prose).markdown)

        assertTrue(html.contains("<table"), "Expected a real table, got: $html")
        assertEquals(MarkdownRenderer.renderToHtml(original), html)
    }

    /**
     * The same loss seen from the other side: without the blank line the closing paragraph becomes
     * a lazy continuation of the list's last item and is drawn indented inside it.
     */
    @Test
    @DisplayName("a paragraph after a list stays out of the list")
    fun `a paragraph after a list stays out of the list`() {
        val original = """So this plugin probably lets you:

- Chat with different models
- Compare outputs

I can't browse the page right now."""

        val segments = MessageSegmenter.split(original)
        assertEquals(1, segments.size)
        val html = MarkdownRenderer.renderToHtml((segments[0] as MessageSegment.Prose).markdown)

        assertFalse(
            html.substringAfter("</ul>").isBlank(),
            "The closing paragraph was swallowed by the list: $html"
        )
        assertEquals(MarkdownRenderer.renderToHtml(original), html)
    }

    @Test
    @DisplayName("prose with emphasis link and list re-renders to identical HTML after segmentation")
    fun `prose markdown re-renders identically after segmentation`() {
        val original = """This is **bold** and [a link](http://example.com).

- item 1
- item 2"""

        val segments = MessageSegmenter.split(original)
        assertEquals(1, segments.size)
        val extractedProse = (segments[0] as MessageSegment.Prose).markdown

        val directHtml = MarkdownRenderer.renderToHtml(original)
        val rerenderHtml = MarkdownRenderer.renderToHtml(extractedProse)

        assertEquals(directHtml, rerenderHtml)
    }
}
