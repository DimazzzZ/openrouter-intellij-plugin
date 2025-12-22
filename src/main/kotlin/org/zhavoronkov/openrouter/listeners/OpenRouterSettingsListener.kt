package org.zhavoronkov.openrouter.listeners

import com.intellij.util.messages.Topic

/**
 * Listener for OpenRouter settings changes
 */
interface OpenRouterSettingsListener {
    companion object {
        val TOPIC = Topic.create("OpenRouter Settings Changed", OpenRouterSettingsListener::class.java)
    }

    fun onSettingsChanged()
}
