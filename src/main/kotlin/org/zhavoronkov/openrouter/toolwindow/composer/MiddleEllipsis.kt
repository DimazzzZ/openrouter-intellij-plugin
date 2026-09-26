package org.zhavoronkov.openrouter.toolwindow.composer

/**
 * Shortens text to a pixel width by removing characters from the middle.
 *
 * Model ids carry their distinguishing information at the head
 * ("anthropic/…" versus "openai/…"), so the head truncation the old fixed-width
 * combo produced ("enrouter/auto") destroyed exactly the part that matters.
 *
 * Takes a measuring function rather than font metrics so it stays pure and
 * testable in the fast headless test task.
 */
object MiddleEllipsis {

    private const val ELLIPSIS = "…"

    fun fit(text: String, maxWidth: Int, measure: (String) -> Int): String {
        if (text.isEmpty() || measure(text) <= maxWidth) return text

        var head = (text.length + 1) / 2
        var tail = text.length - head
        while (head + tail > 0 && measure(candidate(text, head, tail)) > maxWidth) {
            if (head > tail) head-- else tail--
        }

        val result = if (head + tail > 0) candidate(text, head, tail) else ELLIPSIS
        return if (measure(result) <= maxWidth) result else ""
    }

    private fun candidate(text: String, head: Int, tail: Int): String =
        text.take(head) + ELLIPSIS + text.takeLast(tail)
}
