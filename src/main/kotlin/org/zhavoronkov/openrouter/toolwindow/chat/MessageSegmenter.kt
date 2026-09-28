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
 *   preserving wrapping and minimizing component count. Blocks are rejoined with a
 *   blank line between them - see [appendBlock].
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

                else -> appendBlock(prose, node)
            }
        }
        flush(prose, segments)

        return segments
    }

    /**
     * Adds one top-level block to the prose being accumulated, separated from the previous one by
     * a blank line.
     *
     * A node's own `chars` stop at its last character and take no account of what separated it
     * from its neighbour, so appending them back to back hands the renderer a document whose
     * blocks are no longer blocks. That is not a cosmetic loss: a table needs a blank line before
     * it or the whole thing parses as one paragraph and renders as literal pipes, and a paragraph
     * after a list without one becomes a lazy continuation of the list's last item and is drawn
     * indented inside it.
     *
     * A blank line is always the right separator here because every child of the document is a
     * block: two blocks that were adjacent in the source parse the same way with a blank line
     * between them, and two that were separated by one need it back.
     */
    private fun appendBlock(prose: StringBuilder, node: Node) {
        if (prose.isNotEmpty()) prose.append(BLOCK_SEPARATOR)
        prose.append(node.chars.toString().trim())
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

    private const val BLOCK_SEPARATOR = "\n\n"
}
