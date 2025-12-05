package org.zhavoronkov.openrouter.ui

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class OpenRouterStatsDialogTest {

    @Test
    fun `OpenRouterStatsPopup should extend DialogWrapper for modal behavior`() {
        // Verify that the class extends DialogWrapper instead of creating JBPopup
        // This ensures modal behavior where users must click close button to dismiss
        val dialogClass = OpenRouterStatsPopup::class.java
        val superClass = dialogClass.superclass

        assertEquals(
            "DialogWrapper",
            superClass.simpleName,
            "OpenRouterStatsPopup should extend DialogWrapper for modal behavior"
        )
    }

    @Test
    fun `showDialog method should be available for public API`() {
        // Verify that the public showDialog method exists
        val dialogClass = OpenRouterStatsPopup::class.java
        val showDialogMethod = dialogClass.methods.find { it.name == "showDialog" }

        assertNotNull(showDialogMethod, "showDialog method should be available")
        assertEquals(0, showDialogMethod!!.parameterCount, "showDialog should take no parameters")
    }

    @Test
    fun `dialog should have proper constructor for testing`() {
        // Verify the test-friendly constructor exists
        val dialogClass = OpenRouterStatsPopup::class.java
        val constructors = dialogClass.constructors

        // Should have at least the test-friendly constructor with 3 parameters
        val testConstructor = constructors.find { it.parameterCount == 3 }
        assertNotNull(testConstructor, "Test-friendly constructor should exist")
    }
}
