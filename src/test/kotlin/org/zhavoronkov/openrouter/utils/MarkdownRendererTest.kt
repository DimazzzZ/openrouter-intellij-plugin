package org.zhavoronkov.openrouter.utils

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * Tests for MarkdownRenderer utility
 */
@DisplayName("MarkdownRenderer Tests")
class MarkdownRendererTest {

    @Nested
    @DisplayName("Markdown to HTML Rendering")
    inner class MarkdownToHtmlTests {

        @Test
        @DisplayName("Should render bold text")
        fun `Should render bold text`() {
            val html = MarkdownRenderer.renderToHtml("This is **bold** text")
            assertTrue(
                html.contains("<strong>bold</strong>") || html.contains("<b>bold</b>"),
                "Should contain bold tags"
            )
        }

        @Test
        @DisplayName("Should render italic text")
        fun `Should render italic text`() {
            val html = MarkdownRenderer.renderToHtml("This is *italic* text")
            assertTrue(html.contains("<em>italic</em>") || html.contains("<i>italic</i>"), "Should contain italic tags")
        }

        @Test
        @DisplayName("Should render inline code")
        fun `Should render inline code`() {
            val html = MarkdownRenderer.renderToHtml("This is `code` text")
            assertTrue(html.contains("<code>"), "Should contain code tags")
        }

        @Test
        @DisplayName("Should render fenced code blocks")
        fun `Should render fenced code blocks`() {
            val markdown = """
                Here is some code:
                ```python
                def hello():
                    print("Hello")
                ```
            """.trimIndent()
            val html = MarkdownRenderer.renderToHtml(markdown)
            assertTrue(html.contains("<pre"), "Should contain pre tags for code blocks")
            assertTrue(html.contains("def hello()"), "Should contain code content")
        }

        @Test
        @DisplayName("Should render links")
        fun `Should render links`() {
            val html = MarkdownRenderer.renderToHtml("Check [this link](https://example.com)")
            assertTrue(html.contains("<a href="), "Should contain anchor tags")
            assertTrue(html.contains("https://example.com"), "Should contain href")
        }

        @Test
        @DisplayName("Should render strikethrough")
        fun `Should render strikethrough`() {
            val html = MarkdownRenderer.renderToHtml("This is ~~deleted~~ text")
            assertTrue(
                html.contains("<del>") || html.contains("<s>") || html.contains("<strike>"),
                "Should contain strikethrough tags"
            )
        }

        @Test
        @DisplayName("Should render tables")
        fun `Should render tables`() {
            val markdown = """
                | Header 1 | Header 2 |
                |----------|----------|
                | Cell 1   | Cell 2   |
            """.trimIndent()
            val html = MarkdownRenderer.renderToHtml(markdown)
            assertTrue(html.contains("<table>"), "Should contain table tags")
            assertTrue(html.contains("<th>"), "Should contain th tags")
            assertTrue(html.contains("<td>"), "Should contain td tags")
        }

        @Test
        @DisplayName("Should render task lists")
        fun `Should render task lists`() {
            val markdown = """
                - [ ] Unchecked task
                - [x] Checked task
            """.trimIndent()
            val html = MarkdownRenderer.renderToHtml(markdown)
            assertTrue(html.contains("checkbox"), "Should contain checkbox input")
        }

        @Test
        @DisplayName("Should render headings")
        fun `Should render headings`() {
            val html = MarkdownRenderer.renderToHtml("# Heading 1\n## Heading 2\n### Heading 3")
            assertTrue(html.contains("<h1>"), "Should contain h1 tag")
            assertTrue(html.contains("<h2>"), "Should contain h2 tag")
            assertTrue(html.contains("<h3>"), "Should contain h3 tag")
        }

        @Test
        @DisplayName("Should render blockquotes")
        fun `Should render blockquotes`() {
            val html = MarkdownRenderer.renderToHtml("> This is a quote")
            assertTrue(html.contains("<blockquote>"), "Should contain blockquote tags")
        }

        @Test
        @DisplayName("Should handle empty string")
        fun `Should handle empty string`() {
            val html = MarkdownRenderer.renderToHtml("")
            assertEquals("", html, "Empty input should return empty output")
        }

        @Test
        @DisplayName("Should handle plain text without markdown")
        fun `Should handle plain text without markdown`() {
            val html = MarkdownRenderer.renderToHtml("Just plain text here")
            assertTrue(html.contains("Just plain text here"), "Should contain the plain text")
        }

        @Test
        @DisplayName("Should unwrap single paragraph for inline display")
        fun `Should unwrap single paragraph for inline display`() {
            val html = MarkdownRenderer.renderToHtml("Hello world")
            // Single paragraph should be unwrapped so it renders inline next to "Assistant:"
            assertFalse(html.startsWith("<p>"), "Should not start with <p> tag")
            assertFalse(html.endsWith("</p>"), "Should not end with </p> tag")
            assertTrue(html.contains("Hello world"), "Should contain the text")
        }

        @Test
        @DisplayName("Should normalize margins for multi-paragraph content")
        fun `Should normalize margins for multi-paragraph content`() {
            val html = MarkdownRenderer.renderToHtml("First paragraph.\n\nSecond paragraph.")
            assertTrue(html.contains("<p"), "Should contain paragraph tags, got: $html")
            // Check for margin normalization on paragraphs
            assertTrue(html.contains("margin-top: 0"), "Should have margin-top: 0 on paragraphs, got: $html")
        }

        @Test
        @DisplayName("Should render unordered lists")
        fun `Should render unordered lists`() {
            val html = MarkdownRenderer.renderToHtml("- Item 1\n- Item 2\n- Item 3")
            assertTrue(html.contains("<ul>"), "Should contain ul tags")
            assertTrue(html.contains("<li>"), "Should contain li tags")
        }

        @Test
        @DisplayName("Should render ordered lists")
        fun `Should render ordered lists`() {
            val html = MarkdownRenderer.renderToHtml("1. First\n2. Second\n3. Third")
            assertTrue(html.contains("<ol>"), "Should contain ol tags")
            assertTrue(html.contains("<li>"), "Should contain li tags")
        }

        @Test
        @DisplayName("Should render horizontal rules")
        fun `Should render horizontal rules`() {
            val html = MarkdownRenderer.renderToHtml("Text\n\n---\n\nMore text")
            assertTrue(html.contains("<hr"), "Should contain hr tag")
        }
    }

    @Nested
    @DisplayName("HTML Document Wrapping")
    inner class HtmlDocumentTests {

        @Test
        @DisplayName("Should wrap HTML in minimal document")
        fun `Should wrap HTML in minimal document`() {
            val bodyHtml = "<p>Hello World</p>"
            val document = MarkdownRenderer.wrapInHtmlDocument(bodyHtml, isUser = false)
            assertTrue(document.contains("<html>"), "Should contain html tags")
            assertTrue(document.contains("<body"), "Should contain body tags")
            assertTrue(document.contains("<p>Hello World</p>"), "Should contain original body content")
        }

        @Test
        @DisplayName("Should apply font styling when provided")
        fun `Should apply font styling when provided`() {
            val document = MarkdownRenderer.wrapInHtmlDocument(
                "<p>Test</p>",
                fontFamily = "JetBrains Mono",
                fontSizePx = 13,
                colorHex = "#000000"
            )
            assertTrue(document.contains("margin: 0; padding: 0;"), "Should contain margin/padding reset")
            assertTrue(document.contains("font-family: JetBrains Mono"), "Should contain font-family")
            assertTrue(document.contains("font-size: 13px"), "Should contain font-size")
            assertTrue(document.contains("color: #000000"), "Should contain color")
        }

        @Test
        @DisplayName("Should not include style block when no font args")
        fun `Should not include style block when no font args`() {
            val document = MarkdownRenderer.wrapInHtmlDocument("<p>Test</p>")
            assertFalse(document.contains("<style>"), "Should not contain style block")
            assertFalse(document.contains("<head>"), "Should not contain head block")
        }

        @Test
        @DisplayName("Should handle empty body with font styling")
        fun `Should handle empty body with font styling`() {
            val document = MarkdownRenderer.wrapInHtmlDocument(
                "",
                fontFamily = "Arial",
                fontSizePx = 14
            )
            assertTrue(document.contains("<html>"), "Should contain html")
            assertTrue(document.contains("font-family: Arial"), "Should contain font-family")
        }
    }

    @Nested
    @DisplayName("HTML Document with Role Prefix")
    inner class HtmlDocumentWithRolePrefixTests {

        @Test
        @DisplayName("Should wrap HTML with role prefix")
        fun `Should wrap HTML with role prefix`() {
            val document = MarkdownRenderer.wrapInHtmlDocumentWithRolePrefix(
                bodyHtml = "Hello world",
                rolePrefix = "Assistant:",
                roleColorHex = "#9B9BD2",
                fontFamily = "JetBrains Mono",
                fontSizePx = 13,
                contentColorHex = "#000000",
            )
            assertTrue(document.contains("Assistant:"), "Should contain role prefix")
            assertTrue(document.contains("color: #9B9BD2"), "Should contain role color")
            assertTrue(document.contains("Hello world"), "Should contain body content")
        }

        @Test
        @DisplayName("Should apply font styling")
        fun `Should apply font styling`() {
            val document = MarkdownRenderer.wrapInHtmlDocumentWithRolePrefix(
                bodyHtml = "Test",
                rolePrefix = "You:",
                roleColorHex = "#6B9BD2",
                fontFamily = "Arial",
                fontSizePx = 14,
                contentColorHex = "#333333"
            )
            assertTrue(document.contains("font-family: Arial"), "Should contain font-family")
            assertTrue(document.contains("font-size: 14px"), "Should contain font-size")
        }
    }
}
