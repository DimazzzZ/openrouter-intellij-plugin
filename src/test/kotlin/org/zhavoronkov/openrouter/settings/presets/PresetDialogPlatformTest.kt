package org.zhavoronkov.openrouter.settings.presets

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.zhavoronkov.openrouter.models.OutputSchema
import org.zhavoronkov.openrouter.models.ProviderRoutingPreferences
import javax.swing.JPanel

/** The preset dialog around its editor: its title, its focus, and OK following the editor's problem. */
class PresetDialogPlatformTest : BasePlatformTestCase() {

    private val schemas = listOf(OutputSchema("answer", schema = """{"type":"object"}"""))

    private fun dialog(draft: PresetDraft, isNew: Boolean, taken: List<String> = emptyList()) =
        PresetDialog(JPanel(), draft, isNew, taken, schemas)

    fun testANewPresetIsRefusedUntilItsSlugIsFree() {
        val dialog = dialog(PresetDraft.empty("research"), isNew = true, taken = listOf("research"))
        try {
            assertEquals("New Preset", dialog.title)
            assertFalse("a taken slug keeps OK disabled", dialog.isOKActionEnabled)
            assertTrue(dialog.doValidate()!!.message.contains("already exists"))
            assertSame(dialog.editor.slug, dialog.preferredFocusedComponent)

            dialog.editor.slug.text = "research-2"

            assertTrue("a free slug enables OK", dialog.isOKActionEnabled)
            assertNull(dialog.doValidate())
        } finally {
            dialog.close(0)
        }
    }

    fun testAnExistingPresetIsTitledByItsSlugAndOkFollowsTheEditor() {
        val dialog = dialog(PresetDraft.empty("research"), isNew = false, taken = listOf("research"))
        try {
            assertEquals("Edit Preset research", dialog.title)
            assertTrue(dialog.isOKActionEnabled)

            dialog.editor.maxTokens.text = "forty"
            assertFalse("a number that is not one disables OK", dialog.isOKActionEnabled)
            dialog.editor.maxTokens.text = "40"
            assertTrue(dialog.isOKActionEnabled)
        } finally {
            dialog.close(0)
        }
    }

    fun testTheRoutingDialogShowsTheRoutingItWasOpenedOn() {
        val current = ProviderRoutingPreferences(only = listOf("Azure"), sort = "price")
        val dialog = PresetDialog.RoutingDialog(JPanel(), current)
        try {
            assertEquals("Preset Provider Routing", dialog.title)
            assertEquals(current, dialog.form.value())
        } finally {
            dialog.close(0)
        }
    }
}
