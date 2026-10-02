package org.zhavoronkov.openrouter.toolwindow.chat

import com.intellij.openapi.ui.ComboBox
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import java.awt.Container
import javax.swing.JButton

/** Send can be blocked by a selection the Model cannot serve, independently of a reply in flight. */
class ChatComposerSendBlockPlatformTest : BasePlatformTestCase() {

    private fun sendButton(composer: ChatComposer): JButton {
        val found = mutableListOf<JButton>()
        fun walk(c: java.awt.Component) {
            if (c is JButton && c.text == "Send") found += c
            if (c is Container) c.components.forEach(::walk)
        }
        walk(composer.component)
        return found.single()
    }

    fun testABlockDisablesSendAndSaysWhy() {
        val composer = ChatComposer().apply { attachModelCombo(ComboBox(arrayOf("some/model"))) }

        composer.setSendBlocked("some/model does not support JSON output. Choose another output mode to send.")

        val send = sendButton(composer)
        assertFalse("a blocked Send must not be clickable", send.isEnabled)
        assertFalse(composer.canSend)
        assertEquals(
            "some/model does not support JSON output. Choose another output mode to send.",
            send.toolTipText
        )
    }

    fun testAReplyArrivingDoesNotLiftABlock() {
        val composer = ChatComposer().apply { attachModelCombo(ComboBox(arrayOf("some/model"))) }
        composer.setBusy(true)
        composer.setSendBlocked("blocked")

        composer.setBusy(false)

        assertFalse("finishing a reply must not re-enable a blocked Send", sendButton(composer).isEnabled)
    }

    fun testLiftingTheBlockEnablesSendAgain() {
        val composer = ChatComposer().apply { attachModelCombo(ComboBox(arrayOf("some/model"))) }
        composer.setSendBlocked("blocked")

        composer.setSendBlocked(null)

        assertTrue(sendButton(composer).isEnabled)
        assertTrue(composer.canSend)
        assertNull(sendButton(composer).toolTipText)
    }
}
