package org.zhavoronkov.openrouter.actions

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@DisplayName("Actions Tests")
class ActionsTest {

    @Nested
    @DisplayName("OpenSettingsAction")
    inner class OpenSettingsActionTests {

        @Test
        fun `action can be instantiated`() {
            val action = OpenSettingsAction()
            assertNotNull(action)
        }

        @Test
        fun `action has correct text`() {
            val action = OpenSettingsAction()
            assertNotNull(action.templatePresentation.text)
        }

        @Test
        fun `action has correct description`() {
            val action = OpenSettingsAction()
            assertNotNull(action.templatePresentation.description)
        }

        @Test
        fun `action has icon`() {
            val action = OpenSettingsAction()
            assertNotNull(action.templatePresentation.icon)
        }
    }

    @Nested
    @DisplayName("RefreshQuotaAction")
    inner class RefreshQuotaActionTests {

        @Test
        fun `action can be instantiated`() {
            val action = RefreshQuotaAction()
            assertNotNull(action)
        }

        @Test
        fun `action has correct text`() {
            val action = RefreshQuotaAction()
            assertNotNull(action.templatePresentation.text)
        }

        @Test
        fun `action has correct description`() {
            val action = RefreshQuotaAction()
            assertNotNull(action.templatePresentation.description)
        }

        @Test
        fun `action has icon`() {
            val action = RefreshQuotaAction()
            assertNotNull(action.templatePresentation.icon)
        }
    }

    @Nested
    @DisplayName("ShowUsageAction")
    inner class ShowUsageActionTests {

        @Test
        fun `action can be instantiated`() {
            val action = ShowUsageAction()
            assertNotNull(action)
        }

        @Test
        fun `action has correct text`() {
            val action = ShowUsageAction()
            assertNotNull(action.templatePresentation.text)
        }

        @Test
        fun `action has correct description`() {
            val action = ShowUsageAction()
            assertNotNull(action.templatePresentation.description)
        }

        @Test
        fun `action has icon`() {
            val action = ShowUsageAction()
            assertNotNull(action.templatePresentation.icon)
        }
    }
}
