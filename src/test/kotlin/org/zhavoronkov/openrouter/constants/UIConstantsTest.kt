package org.zhavoronkov.openrouter.constants

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@DisplayName("UIConstants Tests")
class UIConstantsTest {

    @Nested
    @DisplayName("Dialog Dimensions")
    inner class DialogDimensionsTests {

        @Test
        fun `DEFAULT_DIALOG_WIDTH is 700`() {
            assertEquals(700, UIConstants.DEFAULT_DIALOG_WIDTH)
        }

        @Test
        fun `DEFAULT_DIALOG_HEIGHT is 500`() {
            assertEquals(500, UIConstants.DEFAULT_DIALOG_HEIGHT)
        }

        @Test
        fun `MIN_DIALOG_WIDTH is 600`() {
            assertEquals(600, UIConstants.MIN_DIALOG_WIDTH)
        }

        @Test
        fun `MIN_DIALOG_HEIGHT is 400`() {
            assertEquals(400, UIConstants.MIN_DIALOG_HEIGHT)
        }
    }

    @Nested
    @DisplayName("Table Configuration")
    inner class TableConfigurationTests {

        @Test
        fun `TABLE_ROW_HEIGHT is 28`() {
            assertEquals(28, UIConstants.TABLE_ROW_HEIGHT)
        }

        @Test
        fun `CHECKBOX_COLUMN_WIDTH is 40`() {
            assertEquals(40, UIConstants.CHECKBOX_COLUMN_WIDTH)
        }

        @Test
        fun `NAME_COLUMN_WIDTH is 400`() {
            assertEquals(400, UIConstants.NAME_COLUMN_WIDTH)
        }

        @Test
        fun `DEFAULT_TABLE_WIDTH is 650`() {
            assertEquals(650, UIConstants.DEFAULT_TABLE_WIDTH)
        }

        @Test
        fun `DEFAULT_TABLE_HEIGHT is 280`() {
            assertEquals(280, UIConstants.DEFAULT_TABLE_HEIGHT)
        }
    }

    @Nested
    @DisplayName("Font Sizes")
    inner class FontSizesTests {

        @Test
        fun `FONT_SIZE_SMALL is 10`() {
            assertEquals(10, UIConstants.FONT_SIZE_SMALL)
        }

        @Test
        fun `FONT_SIZE_NORMAL is 12`() {
            assertEquals(12, UIConstants.FONT_SIZE_NORMAL)
        }

        @Test
        fun `FONT_SIZE_LARGE is 14`() {
            assertEquals(14, UIConstants.FONT_SIZE_LARGE)
        }

        @Test
        fun `FONT_SIZE_HEADING is 16`() {
            assertEquals(16, UIConstants.FONT_SIZE_HEADING)
        }
    }

    @Nested
    @DisplayName("Spacing and Padding")
    inner class SpacingTests {

        @Test
        fun `SPACING_SMALL is 4`() {
            assertEquals(4, UIConstants.SPACING_SMALL)
        }

        @Test
        fun `SPACING_NORMAL is 8`() {
            assertEquals(8, UIConstants.SPACING_NORMAL)
        }

        @Test
        fun `SPACING_LARGE is 16`() {
            assertEquals(16, UIConstants.SPACING_LARGE)
        }

        @Test
        fun `SPACING_XLARGE is 24`() {
            assertEquals(24, UIConstants.SPACING_XLARGE)
        }
    }

    @Nested
    @DisplayName("Border Sizes")
    inner class BorderSizesTests {

        @Test
        fun `BORDER_THIN is 1`() {
            assertEquals(1, UIConstants.BORDER_THIN)
        }

        @Test
        fun `BORDER_NORMAL is 2`() {
            assertEquals(2, UIConstants.BORDER_NORMAL)
        }

        @Test
        fun `BORDER_THICK is 3`() {
            assertEquals(3, UIConstants.BORDER_THICK)
        }
    }

    @Nested
    @DisplayName("Component Sizes")
    inner class ComponentSizesTests {

        @Test
        fun `PASSWORD_FIELD_COLUMNS is 30`() {
            assertEquals(30, UIConstants.PASSWORD_FIELD_COLUMNS)
        }

        @Test
        fun `TEXT_FIELD_COLUMNS is 40`() {
            assertEquals(40, UIConstants.TEXT_FIELD_COLUMNS)
        }

        @Test
        fun `BUTTON_MIN_WIDTH is 80`() {
            assertEquals(80, UIConstants.BUTTON_MIN_WIDTH)
        }

        @Test
        fun `ICON_SIZE_SMALL is 16`() {
            assertEquals(16, UIConstants.ICON_SIZE_SMALL)
        }

        @Test
        fun `ICON_SIZE_NORMAL is 24`() {
            assertEquals(24, UIConstants.ICON_SIZE_NORMAL)
        }

        @Test
        fun `ICON_SIZE_LARGE is 32`() {
            assertEquals(32, UIConstants.ICON_SIZE_LARGE)
        }
    }

    @Nested
    @DisplayName("Progress and Loading")
    inner class ProgressTests {

        @Test
        fun `PROGRESS_BAR_HEIGHT is 20`() {
            assertEquals(20, UIConstants.PROGRESS_BAR_HEIGHT)
        }

        @Test
        fun `LOADING_SPINNER_SIZE is 32`() {
            assertEquals(32, UIConstants.LOADING_SPINNER_SIZE)
        }
    }

    @Nested
    @DisplayName("Validation")
    inner class ValidationTests {

        @Test
        fun `MIN_KEY_DISPLAY_LENGTH is 10`() {
            assertEquals(10, UIConstants.MIN_KEY_DISPLAY_LENGTH)
        }

        @Test
        fun `MAX_SENSITIVE_DISPLAY_CHARS is 20`() {
            assertEquals(20, UIConstants.MAX_SENSITIVE_DISPLAY_CHARS)
        }
    }

    @Nested
    @DisplayName("Layout Constants")
    inner class LayoutConstantsTests {

        @Test
        fun `LABEL_COLUMN_WIDTH is 170`() {
            assertEquals(170, UIConstants.LABEL_COLUMN_WIDTH)
        }

        @Test
        fun `CONTROL_COLUMN_MIN_WIDTH is 200`() {
            assertEquals(200, UIConstants.CONTROL_COLUMN_MIN_WIDTH)
        }

        @Test
        fun `ACTION_COLUMN_WIDTH is 100`() {
            assertEquals(100, UIConstants.ACTION_COLUMN_WIDTH)
        }
    }
}
