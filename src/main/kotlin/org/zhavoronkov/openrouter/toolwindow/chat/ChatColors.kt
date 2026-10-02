package org.zhavoronkov.openrouter.toolwindow.chat

import com.intellij.ui.JBColor

/**
 * The colour the chat uses for anything that asks the reader to act - a reply cut off at the token
 * limit, a selection that blocks sending - so every such warning reads as the same kind of thing.
 * A theme can set `Chat.warningForeground`; without it the fallback shows.
 */
internal val CHAT_WARNING_FOREGROUND: JBColor =
    JBColor.namedColor("Chat.warningForeground", JBColor(0x9E6A00, 0xE0A94A))
