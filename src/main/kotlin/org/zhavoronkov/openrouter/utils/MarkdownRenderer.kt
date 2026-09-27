package org.zhavoronkov.openrouter.utils

import com.vladsch.flexmark.ext.autolink.AutolinkExtension
import com.vladsch.flexmark.ext.gfm.strikethrough.StrikethroughExtension
import com.vladsch.flexmark.ext.gfm.tasklist.TaskListExtension
import com.vladsch.flexmark.ext.tables.TablesExtension
import com.vladsch.flexmark.html.HtmlRenderer
import com.vladsch.flexmark.parser.Parser
import com.vladsch.flexmark.util.ast.Document
import com.vladsch.flexmark.util.data.MutableDataSet

/**
 * Renders Markdown text to HTML for display in Swing components.
 * Supports: headings, bold, italic, strikethrough, code (inline + fenced blocks),
 * links, lists, blockquotes, tables, and task lists.
 */
object MarkdownRenderer {
    private val options: MutableDataSet by lazy {
        MutableDataSet().apply {
            set(
                Parser.EXTENSIONS,
                listOf(
                    TablesExtension.create(),
                    StrikethroughExtension.create(),
                    AutolinkExtension.create(),
                    TaskListExtension.create(),
                )
            )
            set(HtmlRenderer.SOFT_BREAK, "<br/>\n")
        }
    }

    private val parser: Parser by lazy { Parser.builder(options).build() }
    private val renderer: HtmlRenderer by lazy { HtmlRenderer.builder(options).build() }

    /**
     * Parse Markdown to a flexmark AST using the same configuration as
     * [renderToHtml], so the segmenter and the renderer never disagree about
     * what counts as a fenced code block.
     */
    fun parse(markdown: String): Document = parser.parse(markdown)

    /**
     * Render Markdown text to HTML string.
     *
     * @param markdown Raw Markdown input (from LLM)
     * @return HTML string suitable for JEditorPane display
     */
    fun renderToHtml(markdown: String): String {
        if (markdown.isBlank()) return ""
        val document: Document = parser.parse(markdown)
        val html = renderer.render(document)
        return normalizeForInlineDisplay(html)
    }

    /**
     * Normalize rendered HTML so that short assistant messages
     * render inline next to the "Assistant:" label without a line break.
     *
     * - Single <p>...</p> blocks are unwrapped to inline content
     * - Multi-paragraph content keeps structure but gets zero top margin on first <p>
     */
    private fun normalizeForInlineDisplay(html: String): String {
        if (html.isBlank()) return html

        // Check if the entire content is EXACTLY one <p> block (no other tags except inline ones inside).
        //
        // Two details this regex depends on, both of which cost a silent bug when dropped:
        //  - flexmark ends its output with a newline, and `matchEntire` requires the WHOLE string to
        //    match, so the input has to be trimmed first. Without the trim nothing ever matched and
        //    every short message fell through to the multi-paragraph branch.
        //  - the group stops at the FIRST `</p>`, otherwise a greedy `.*` under DOT_MATCHES_ALL
        //    happily spans two paragraphs and splices them into one.
        val trimmed = html.trim()
        val singleParagraphRegex = Regex("""^<p>((?:(?!</p>).)*)</p>$""", RegexOption.DOT_MATCHES_ALL)
        val match = singleParagraphRegex.matchEntire(trimmed)

        return if (match != null) {
            // Unwrap single paragraph - return just the inline content
            match.groupValues[1]
        } else {
            // Multi-paragraph or complex content: normalize all paragraph margins
            html.replace(
                Regex("""<p([^>]*)>"""),
                """<p$1 style="margin-top: 0; margin-bottom: 0;">"""
            )
        }
    }

    /**
     * Wrap HTML content in a minimal document with optional font styling.
     *
     * Note: JEditorPane's Swing HTML parser crashes on complex embedded CSS
     * (NPE in CSS.getInternalCSSValue), so we return minimal HTML with only
     * inline styles for font/color to avoid the crash.
     *
     * @param bodyHtml HTML body content (output of [renderToHtml])
     * @param fontFamily Optional font family to apply
     * @param fontSizePx Optional font size in pixels
     * @param colorHex Optional color in hex format (e.g., "#000000")
     * @param isUser True if this is a user message
     * @return Complete HTML document string
     */
    fun wrapInHtmlDocument(
        bodyHtml: String,
        fontFamily: String? = null,
        fontSizePx: Int? = null,
        colorHex: String? = null,
        // Not read: the caller distinguishes user and assistant messages by the
        // tinted block around them, not by anything in the document. Kept
        // because MarkdownRendererTest still passes it, so removing it is a
        // test change, not a renderer change.
        @Suppress("unused") isUser: Boolean = false
    ): String {
        val styleBuilder = StringBuilder()
        styleBuilder.append("margin: 0; padding: 0; ")
        fontFamily?.let { styleBuilder.append("font-family: $it; ") }
        fontSizePx?.let { styleBuilder.append("font-size: ${it}px; ") }
        colorHex?.let { styleBuilder.append("color: $it; ") }
        val styleAttr = styleBuilder.toString().trim()
        val bodyStyle = if (styleAttr.isNotEmpty()) " style='$styleAttr'" else ""

        return buildString {
            append("<html><body$bodyStyle>")
            append(bodyHtml)
            append("</body></html>")
        }
    }
}
