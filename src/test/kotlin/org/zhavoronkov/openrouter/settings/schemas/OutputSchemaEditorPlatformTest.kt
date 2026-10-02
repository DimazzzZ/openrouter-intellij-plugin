package org.zhavoronkov.openrouter.settings.schemas

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.util.ui.UIUtil
import org.zhavoronkov.openrouter.models.OutputSchema
import javax.swing.JPanel

/** The editor for one Output Schema: what it accepts, what it refuses, and what it says as you type. */
class OutputSchemaEditorPlatformTest : BasePlatformTestCase() {

    private val existing = OutputSchema(
        name = "features",
        strict = false,
        schema = """{"type":"object","properties":{"a":{},"b":{}}}"""
    )

    fun testTheEditorTakesANameAStrictFlagAndASchema() {
        val editor = OutputSchemaEditor(existing, takenNames = emptyList())

        assertEquals("features", editor.name.text)
        assertFalse(editor.strict.isSelected)
        assertEquals(existing.schema, editor.body.text)
        assertEquals(existing, editor.result())
    }

    fun testANewSchemaStartsStrictFromAValidTemplate() {
        val editor = OutputSchemaEditor(null, takenNames = emptyList())

        assertTrue(editor.strict.isSelected)
        assertEquals(OutputSchema.BodyCheck.Valid(0), OutputSchema.validateBody(editor.body.text))
        assertEquals("A schema needs a name", editor.problem()?.message)
    }

    fun testValidJsonIsConfirmedWithItsFieldCountAsItIsTyped() {
        val editor = OutputSchemaEditor(null, takenNames = emptyList())
        editor.name.text = "features"

        editor.body.text = existing.schema

        assertEquals("Valid JSON object, 2 fields", editor.status.text)
        assertFalse("a valid schema must not read as an error", editor.status.foreground == UIUtil.getErrorForeground())
    }

    fun testMalformedJsonIsRejectedWithAMessageAndCannotBeSaved() {
        val editor = OutputSchemaEditor(existing, takenNames = emptyList())

        editor.body.text = """{"type": "object","""

        assertTrue(editor.status.text, editor.status.text.startsWith("Not valid JSON"))
        assertEquals(UIUtil.getErrorForeground(), editor.status.foreground)
        assertSame("the problem must point at the schema field", editor.body, editor.problem()?.field)
    }

    fun testJsonThatIsNotAnObjectIsRejected() {
        val editor = OutputSchemaEditor(existing, takenNames = emptyList())

        editor.body.text = """["type", "object"]"""

        assertEquals("A schema must be a JSON object", editor.problem()?.message)
    }

    fun testANameAnotherSchemaHasIsRejected() {
        val editor = OutputSchemaEditor(null, takenNames = listOf("features"))

        editor.name.text = "Features"

        assertSame(editor.name, editor.problem()?.field)
        assertEquals("A schema named 'features' already exists", editor.problem()?.message)
    }

    /** DialogWrapper runs this on OK and keeps the dialog open while it reports a problem. */
    fun testTheDialogRefusesOkWhileTheSchemaIsInvalid() {
        val dialog = OutputSchemaDialog(JPanel(), existing, takenNames = emptyList())
        try {
            dialog.editor.body.text = "{"
            assertNotNull("malformed JSON must be refused", dialog.doValidate())

            dialog.editor.body.text = existing.schema
            assertNull("a valid schema must be accepted", dialog.doValidate())
        } finally {
            dialog.close(0)
        }
    }

    fun testTheDialogOpensWithTheCaretInTheName() {
        val dialog = OutputSchemaDialog(JPanel(), null, takenNames = emptyList())
        try {
            assertSame(dialog.editor.name, dialog.preferredFocusedComponent)
        } finally {
            dialog.close(0)
        }
    }

    /**
     * What the dialog says, and whether OK can be pressed, follow the fields on every keystroke, in
     * either field: a message about an empty name must not outlive the name being typed.
     */
    fun testTheDialogFollowsTheNameAsItIsTyped() {
        val dialog = OutputSchemaDialog(JPanel(), null, takenNames = emptyList())
        try {
            assertFalse("an empty name must keep OK disabled", dialog.isOKActionEnabled)
            assertEquals("A schema needs a name", dialog.editor.status.text)

            dialog.editor.name.text = "My test schema"
            assertFalse("a name with spaces must keep OK disabled", dialog.isOKActionEnabled)
            assertEquals(
                "the line must say what is wrong with the name now, not what was wrong before",
                "Use only letters, digits, _ and - (no spaces), up to 64 characters",
                dialog.editor.status.text
            )

            dialog.editor.name.text = "my_test_schema"
            assertTrue("a valid name and schema must enable OK", dialog.isOKActionEnabled)
            assertEquals("Valid JSON object, 0 fields", dialog.editor.status.text)

            dialog.editor.body.text = "{"
            assertFalse("breaking the schema must disable OK again", dialog.isOKActionEnabled)
        } finally {
            dialog.close(0)
        }
    }
}
