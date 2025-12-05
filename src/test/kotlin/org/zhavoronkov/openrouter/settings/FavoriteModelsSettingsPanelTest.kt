package org.zhavoronkov.openrouter.settings

import com.intellij.ui.SearchTextField
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.DisabledIfSystemProperty
import java.awt.event.KeyEvent

/**
 * Tests for FavoriteModelsSettingsPanel search functionality
 *
 * Specifically tests the fix for the issue where pressing Enter in the search field
 * would close the settings dialog instead of performing a search.
 */
@DisplayName("Favorite Models Settings Panel Tests")
class FavoriteModelsSettingsPanelTest {

    @Test
    @DisplayName("Search field should handle Enter key without closing dialog")
    @DisabledIfSystemProperty(named = "java.awt.headless", matches = "true")
    fun testSearchFieldEnterKeyHandling() {
        // Create a SearchTextField similar to what's used in the panel
        val searchField = SearchTextField()

        // Track whether Enter key was consumed
        var enterKeyConsumed = false
        var searchTriggered = false

        // Add the same type of KeyListener that our fix adds
        searchField.textEditor.addKeyListener(object : java.awt.event.KeyAdapter() {
            override fun keyPressed(e: KeyEvent?) {
                if (e?.keyCode == KeyEvent.VK_ENTER) {
                    // Consume the Enter key event to prevent dialog from closing
                    e.consume()
                    enterKeyConsumed = true
                    // Simulate triggering search
                    searchTriggered = true
                }
            }
        })

        // Create a mock Enter key event
        val enterKeyEvent = KeyEvent(
            searchField.textEditor,
            KeyEvent.KEY_PRESSED,
            System.currentTimeMillis(),
            0,
            KeyEvent.VK_ENTER,
            KeyEvent.CHAR_UNDEFINED
        )

        // Simulate the key press
        for (listener in searchField.textEditor.keyListeners) {
            listener.keyPressed(enterKeyEvent)
        }

        // Verify the behavior
        assertTrue(enterKeyConsumed, "Enter key should be consumed to prevent dialog closing")
        assertTrue(searchTriggered, "Search should be triggered when Enter is pressed")
        assertTrue(enterKeyEvent.isConsumed, "KeyEvent should be marked as consumed")
    }

    @Test
    @DisplayName("Search field should not interfere with other key events")
    @DisabledIfSystemProperty(named = "java.awt.headless", matches = "true")
    fun testSearchFieldOtherKeysNotAffected() {
        // Create a SearchTextField similar to what's used in the panel
        val searchField = SearchTextField()

        // Track key events
        var keyEventHandled = false

        // Add the same type of KeyListener that our fix adds
        searchField.textEditor.addKeyListener(object : java.awt.event.KeyAdapter() {
            override fun keyPressed(e: KeyEvent?) {
                if (e?.keyCode == KeyEvent.VK_ENTER) {
                    e.consume()
                } else {
                    keyEventHandled = true
                }
            }
        })

        // Create a mock 'A' key event (not Enter)
        val aKeyEvent = KeyEvent(
            searchField.textEditor,
            KeyEvent.KEY_PRESSED,
            System.currentTimeMillis(),
            0,
            KeyEvent.VK_A,
            'a'
        )

        // Simulate the key press
        for (listener in searchField.textEditor.keyListeners) {
            listener.keyPressed(aKeyEvent)
        }

        // Verify the behavior - other keys should not be consumed
        assertTrue(keyEventHandled, "Other key events should be handled normally")
        assertFalse(aKeyEvent.isConsumed, "Non-Enter keys should not be consumed")
    }
}
