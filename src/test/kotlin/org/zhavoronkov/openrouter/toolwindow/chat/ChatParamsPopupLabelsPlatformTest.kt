package org.zhavoronkov.openrouter.toolwindow.chat

import com.intellij.openapi.ui.ComboBox
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import java.awt.Container
import javax.swing.JComponent
import javax.swing.JLabel

/**
 * Regression test for the two-column rebuild: labels now sit to the LEFT of
 * their controls (see the class doc on [ChatParamsPopup] and the D7
 * correction in the redesign spec), the same layout
 * [RouterDefaultsSettingsPanel] already uses for these three parameters in
 * Settings - and that panel's convention is a trailing colon on a
 * left-of-control label. This test now asserts the colon IS present, the
 * opposite of what it asserted back when labels sat ABOVE their controls
 * (where a colon had nothing to its right and read as a typo).
 */
class ChatParamsPopupLabelsPlatformTest : BasePlatformTestCase() {

    private fun <T> findDescendants(root: java.awt.Component, type: Class<T>, into: MutableList<T>) {
        if (type.isInstance(root)) {
            @Suppress("UNCHECKED_CAST")
            into += root as T
        }
        if (root is Container) {
            root.components.forEach { findDescendants(it, type, into) }
        }
    }

    private fun buildForm(popup: ChatParamsPopup): JComponent {
        val method = ChatParamsPopup::class.java.getDeclaredMethod("buildForm")
        method.isAccessible = true
        return method.invoke(popup) as JComponent
    }

    private fun labelTexts(popup: ChatParamsPopup): List<String> {
        val labels = mutableListOf<JLabel>()
        findDescendants(buildForm(popup), JLabel::class.java, labels)
        return labels.map { it.text }
    }

    fun testReasoningAndVerbosityLabelsHaveTrailingColon() {
        val popup = ChatParamsPopup(ComboBox(arrayOf("a")), ComboBox(arrayOf("b")), ComboBox(arrayOf("c")))

        val texts = labelTexts(popup)

        assertTrue("expected a 'Reasoning:' label, got $texts", texts.contains("Reasoning:"))
        assertTrue("expected a 'Verbosity:' label, got $texts", texts.contains("Verbosity:"))
    }

    fun testRouterParamLabelHasTrailingColonWhenVisible() {
        val popup = ChatParamsPopup(ComboBox(arrayOf("a")), ComboBox(arrayOf("b")), ComboBox(arrayOf("c")))
        popup.setRouterParam("Cost tier", "Prefers cheaper models", visible = true)

        val texts = labelTexts(popup)

        assertTrue("expected a 'Cost tier:' label, got $texts", texts.contains("Cost tier:"))
    }
}
