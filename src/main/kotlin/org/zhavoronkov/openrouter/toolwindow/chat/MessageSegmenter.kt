package org.zhavoronkov.openrouter.toolwindow.chat

import com.vladsch.flexmark.ast.FencedCodeBlock
import com.vladsch.flexmark.util.ast.Node
import org.zhavoronkov.openrouter.utils.MarkdownRenderer

/**
 * Splits a Markdown message into prose and fenced-code segments.
 *
 * Contract:
 * - Only top-level [FencedCodeBlock] nodes become [MessageSegment.Code] segments.
 * - A container (list, blockquote, etc.) that CONTAINS a nested fence in its subtree
 *   is isolated into its own [MessageSegment.Prose] segment, so the view layer can
 *   render it with horizontal scrolling without affecting surrounding prose.
 * - Ordinary prose without nested fences stays accumulated in a single segment,
 *   preserving wrapping and minimizing component count.
 * - Indented code blocks (four-space indent, no fence) are NOT extracted or isolated;
 *   they stay in ordinary prose segments and rely on the view layer's `<pre>` scroll handling.
 *
 * Pure apart from flexmark, which is a plain JVM library — so this runs in the
 * fast headless test task.
 */
object MessageSegmenter {

    fun split(markdown: String): List<MessageSegment> {
        if (markdown.isBlank()) return emptyList()

        val segments = mutableListOf<MessageSegment>()
        val prose = StringBuilder()

        for (node in MarkdownRenderer.parse(markdown).children) {
            when {
                node is FencedCodeBlock -> {
                    flush(prose, segments)
                    segments += MessageSegment.Code(
                        language = node.info.toString().trim().ifBlank { null },
                        code = node.contentChars.toString().trimEnd('\n')
                    )
                }

                containsFencedCodeBlock(node) -> {
                    flush(prose, segments)
                    segments += MessageSegment.Prose(node.chars.toString().trim())
                    flush(prose, segments)
                }

                else -> {
                    prose.append(node.chars.toString())
                }
            }
        }
        flush(prose, segments)

        return segments
    }

    private fun containsFencedCodeBlock(node: Node): Boolean {
        for (child in node.children) {
            if (child is FencedCodeBlock) return true
            if (containsFencedCodeBlock(child)) return true
        }
        return false
    }

    private fun flush(buffer: StringBuilder, into: MutableList<MessageSegment>) {
        val text = buffer.toString().trim()
        if (text.isNotEmpty()) into += MessageSegment.Prose(text)
        buffer.setLength(0)
    }
}
