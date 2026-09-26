package org.zhavoronkov.openrouter.toolwindow.chat

/**
 * One piece of a rendered message.
 *
 * Splitting a message lets the widest content — code and tables — get its own
 * horizontal scroll without turning scrolling on for the prose around it, and
 * moves the hardest part of the width-to-height problem onto components whose
 * height is trivial to compute.
 */
sealed interface MessageSegment {

    data class Prose(val markdown: String) : MessageSegment

    data class Code(val language: String?, val code: String) : MessageSegment
}
