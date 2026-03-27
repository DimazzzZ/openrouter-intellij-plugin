package org.zhavoronkov.openrouter.ui

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@DisplayName("SetupWizardConfig Tests")
class SetupWizardConfigTest {

    @Nested
    @DisplayName("Dialog dimensions")
    inner class DialogDimensionsTests {

        @Test
        fun `DIALOG_WIDTH is 700`() {
            assertEquals(700, SetupWizardConfig.DIALOG_WIDTH)
        }

        @Test
        fun `DIALOG_HEIGHT is 500`() {
            assertEquals(500, SetupWizardConfig.DIALOG_HEIGHT)
        }

        @Test
        fun `MODELS_TABLE_WIDTH is 650`() {
            assertEquals(650, SetupWizardConfig.MODELS_TABLE_WIDTH)
        }

        @Test
        fun `MODELS_TABLE_HEIGHT is 280`() {
            assertEquals(280, SetupWizardConfig.MODELS_TABLE_HEIGHT)
        }
    }

    @Nested
    @DisplayName("UI constants")
    inner class UIConstantsTests {

        @Test
        fun `TABLE_ROW_HEIGHT is 28`() {
            assertEquals(28, SetupWizardConfig.TABLE_ROW_HEIGHT)
        }

        @Test
        fun `CHECKBOX_COLUMN_WIDTH is 40`() {
            assertEquals(40, SetupWizardConfig.CHECKBOX_COLUMN_WIDTH)
        }

        @Test
        fun `NAME_COLUMN_WIDTH is 400`() {
            assertEquals(400, SetupWizardConfig.NAME_COLUMN_WIDTH)
        }

        @Test
        fun `URL_LABEL_FONT_SIZE is 12`() {
            assertEquals(12, SetupWizardConfig.URL_LABEL_FONT_SIZE)
        }
    }

    @Nested
    @DisplayName("Timeouts")
    inner class TimeoutsTests {

        @Test
        fun `KEY_VALIDATION_DEBOUNCE_MS is 500`() {
            assertEquals(500L, SetupWizardConfig.KEY_VALIDATION_DEBOUNCE_MS)
        }

        @Test
        fun `MODEL_LOADING_TIMEOUT_MS is 30 seconds`() {
            assertEquals(30000L, SetupWizardConfig.MODEL_LOADING_TIMEOUT_MS)
        }

        @Test
        fun `PKCE_SERVER_TIMEOUT_MS is 2 minutes`() {
            assertEquals(120000, SetupWizardConfig.PKCE_SERVER_TIMEOUT_MS)
        }
    }

    @Nested
    @DisplayName("Port configuration")
    inner class PortConfigurationTests {

        @Test
        fun `DEFAULT_PROXY_PORT is 8000`() {
            assertEquals(8000, SetupWizardConfig.DEFAULT_PROXY_PORT)
        }

        @Test
        fun `PKCE_PORT is 3000`() {
            assertEquals(3000, SetupWizardConfig.PKCE_PORT)
        }
    }

    @Nested
    @DisplayName("Wizard steps")
    inner class WizardStepsTests {

        @Test
        fun `STEP_WELCOME is 0`() {
            assertEquals(0, SetupWizardConfig.STEP_WELCOME)
        }

        @Test
        fun `STEP_PROVISIONING is 1`() {
            assertEquals(1, SetupWizardConfig.STEP_PROVISIONING)
        }

        @Test
        fun `STEP_MODELS is 2`() {
            assertEquals(2, SetupWizardConfig.STEP_MODELS)
        }

        @Test
        fun `STEP_COMPLETION is 3`() {
            assertEquals(3, SetupWizardConfig.STEP_COMPLETION)
        }

        @Test
        fun `steps are sequential`() {
            val steps = listOf(
                SetupWizardConfig.STEP_WELCOME,
                SetupWizardConfig.STEP_PROVISIONING,
                SetupWizardConfig.STEP_MODELS,
                SetupWizardConfig.STEP_COMPLETION
            )
            assertEquals((0..3).toList(), steps)
        }
    }

    @Nested
    @DisplayName("PKCE configuration")
    inner class PKCEConfigurationTests {

        @Test
        fun `PKCE_KEY_MIN_LENGTH is 10`() {
            assertEquals(10, SetupWizardConfig.PKCE_KEY_MIN_LENGTH)
        }
    }

    @Nested
    @DisplayName("String truncation")
    inner class StringTruncationTests {

        @Test
        fun `API_KEY_TRUNCATE_LENGTH is 10`() {
            assertEquals(10, SetupWizardConfig.API_KEY_TRUNCATE_LENGTH)
        }
    }

    @Nested
    @DisplayName("Logging configuration")
    inner class LoggingConfigurationTests {

        @Test
        fun `LOGGING_ENABLED is true`() {
            assertTrue(SetupWizardConfig.LOGGING_ENABLED)
        }

        @Test
        fun `DEBUG_LOGGING_ENABLED is false`() {
            assertFalse(SetupWizardConfig.DEBUG_LOGGING_ENABLED)
        }
    }
}
