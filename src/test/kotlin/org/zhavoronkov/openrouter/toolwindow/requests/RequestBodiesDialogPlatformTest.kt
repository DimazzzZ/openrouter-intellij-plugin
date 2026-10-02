package org.zhavoronkov.openrouter.toolwindow.requests

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.zhavoronkov.openrouter.requests.RequestBodies
import javax.swing.JPanel

/** The bodies dialog builds around whatever a request kept; what each tab says is pinned in RequestBodiesDialogTest. */
class RequestBodiesDialogPlatformTest : BasePlatformTestCase() {

    fun testTheDialogOpensOnARequestsBodies() {
        val dialog = RequestBodiesDialog(JPanel(), RequestBodies(sent = """{"model":"m"}""", failure = "boom"))
        try {
            assertEquals("Request Prompt and Reply", dialog.title)
            assertTrue("it can be closed", dialog.isOKActionEnabled)
        } finally {
            dialog.close(0)
        }
    }
}
