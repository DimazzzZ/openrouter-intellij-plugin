package org.zhavoronkov.openrouter.listeners

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("OpenRouterSettingsListener Tests")
class OpenRouterSettingsListenerTopicTest {

    @Test
    fun `TOPIC is properly initialized`() {
        assertNotNull(OpenRouterSettingsListener.TOPIC)
    }

    @Test
    fun `TOPIC has correct display name`() {
        assertEquals("OpenRouter Settings Changed", OpenRouterSettingsListener.TOPIC.displayName)
    }

    @Test
    fun `listener interface can be implemented`() {
        var settingsChangedCalled = false
        
        val listener = object : OpenRouterSettingsListener {
            override fun onSettingsChanged() {
                settingsChangedCalled = true
            }
        }
        
        listener.onSettingsChanged()
        
        assertEquals(true, settingsChangedCalled)
    }
}
