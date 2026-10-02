package org.zhavoronkov.openrouter.toolwindow.requests

import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.ui.components.ActionLink
import com.intellij.util.ui.UIUtil
import java.awt.datatransfer.DataFlavor

/** A request's links: copying its generation id puts exactly that id on the clipboard. */
class RequestDetailsPlatformTest : BasePlatformTestCase() {

    fun testCopyingTheGenerationIdPutsItOnTheClipboard() {
        val links = UIUtil.findComponentsOfType(RequestDetails.links("gen-42"), ActionLink::class.java)

        links.single { it.text == "Copy generation id" }.doClick()

        assertEquals("gen-42", CopyPasteManager.getInstance().getContents<String>(DataFlavor.stringFlavor))
    }

    fun testARequestWithNoGenerationHasNoLinks() {
        assertEquals(0, RequestDetails.links(null).componentCount)
    }
}
