package org.zhavoronkov.openrouter.toolwindow.chat

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@DisplayName("ChatParamsState")
class ChatParamsStateTest {

    private fun defaults(
        reasoningIndex: Int = 0,
        reasoningValue: String? = "Default",
        verbosityIndex: Int = 0,
        verbosityValue: String? = "Default",
        routerVisible: Boolean = false,
        routerLabel: String? = null,
        routerValue: String? = null
    ) = ChatParamsState.Selection(
        reasoningIndex = reasoningIndex,
        reasoningValue = reasoningValue,
        verbosityIndex = verbosityIndex,
        verbosityValue = verbosityValue,
        routerVisible = routerVisible,
        routerLabel = routerLabel,
        routerValue = routerValue
    )

    @Nested
    @DisplayName("hasNonDefaultSelection")
    inner class HasNonDefaultSelection {

        @Test
        @DisplayName("everything default is not flagged")
        fun `everything default is not flagged`() {
            assertFalse(ChatParamsState.hasNonDefaultSelection(defaults()))
        }

        @Test
        @DisplayName("reasoning alone non-default is flagged")
        fun `reasoning alone non-default is flagged`() {
            val selection = defaults(reasoningIndex = 3, reasoningValue = "High")
            assertTrue(ChatParamsState.hasNonDefaultSelection(selection))
        }

        @Test
        @DisplayName("verbosity alone non-default is flagged")
        fun `verbosity alone non-default is flagged`() {
            val selection = defaults(verbosityIndex = 2, verbosityValue = "Medium")
            assertTrue(ChatParamsState.hasNonDefaultSelection(selection))
        }

        @Test
        @DisplayName("router alone non-default is flagged")
        fun `router alone non-default is flagged`() {
            val selection = defaults(routerVisible = true, routerLabel = "Cost tier", routerValue = "cheap")
            assertTrue(ChatParamsState.hasNonDefaultSelection(selection))
        }

        @Test
        @DisplayName("a router value left over from a hidden control does not count")
        fun `hidden router with a stale value does not count`() {
            val selection = defaults(routerVisible = false, routerLabel = "Cost tier", routerValue = "cheap")
            assertFalse(ChatParamsState.hasNonDefaultSelection(selection))
        }

        @Test
        @DisplayName("a null router value means no selection")
        fun `null router value means no selection`() {
            val selection = defaults(routerVisible = true, routerLabel = "Cost tier", routerValue = null)
            assertFalse(ChatParamsState.hasNonDefaultSelection(selection))
        }

        @Test
        @DisplayName("an empty-string router value means no selection")
        fun `empty string router value means no selection`() {
            val selection = defaults(routerVisible = true, routerLabel = "Cost tier", routerValue = "")
            assertFalse(ChatParamsState.hasNonDefaultSelection(selection))
        }

        @Test
        @DisplayName("a whitespace-only router value means no selection")
        fun `whitespace only router value means no selection`() {
            val selection = defaults(routerVisible = true, routerLabel = "Cost tier", routerValue = "   ")
            assertFalse(ChatParamsState.hasNonDefaultSelection(selection))
        }

        @Test
        @DisplayName("free text typed into the editable router combo counts")
        fun `free text typed into the editable router combo counts`() {
            val selection = defaults(routerVisible = true, routerLabel = "Model class", routerValue = "my-custom-tag")
            assertTrue(ChatParamsState.hasNonDefaultSelection(selection))
        }

        @Test
        @DisplayName("all three non-default at once is flagged")
        fun `all three non-default at once is flagged`() {
            val selection = defaults(
                reasoningIndex = 1,
                reasoningValue = "None",
                verbosityIndex = 1,
                verbosityValue = "Low",
                routerVisible = true,
                routerLabel = "Cost tier",
                routerValue = "cheap"
            )
            assertTrue(ChatParamsState.hasNonDefaultSelection(selection))
        }
    }

    @Nested
    @DisplayName("activeSummary")
    inner class ActiveSummary {

        @Test
        @DisplayName("everything default reports the placeholder")
        fun `everything default reports the placeholder`() {
            assertEquals("Send parameters", ChatParamsState.activeSummary(defaults()))
        }

        @Test
        @DisplayName("reasoning alone is reported by itself")
        fun `reasoning alone is reported by itself`() {
            val selection = defaults(reasoningIndex = 3, reasoningValue = "High")
            assertEquals("Reasoning: High", ChatParamsState.activeSummary(selection))
        }

        @Test
        @DisplayName("verbosity alone is reported by itself")
        fun `verbosity alone is reported by itself`() {
            val selection = defaults(verbosityIndex = 2, verbosityValue = "Medium")
            assertEquals("Verbosity: Medium", ChatParamsState.activeSummary(selection))
        }

        @Test
        @DisplayName("router alone is reported using its label")
        fun `router alone is reported using its label`() {
            val selection = defaults(routerVisible = true, routerLabel = "Cost tier", routerValue = "cheap")
            assertEquals("Cost tier: cheap", ChatParamsState.activeSummary(selection))
        }

        @Test
        @DisplayName("a hidden router with a stale value is omitted from the summary")
        fun `hidden router with a stale value is omitted`() {
            val selection = defaults(
                reasoningIndex = 2,
                reasoningValue = "Low",
                routerVisible = false,
                routerLabel = "Cost tier",
                routerValue = "cheap"
            )
            assertEquals("Reasoning: Low", ChatParamsState.activeSummary(selection))
        }

        @Test
        @DisplayName("all three combine in reasoning, verbosity, router order")
        fun `all three combine in a fixed order`() {
            val selection = defaults(
                reasoningIndex = 3,
                reasoningValue = "High",
                verbosityIndex = 1,
                verbosityValue = "Low",
                routerVisible = true,
                routerLabel = "Cost tier",
                routerValue = "cheap"
            )
            assertEquals(
                "Reasoning: High · Verbosity: Low · Cost tier: cheap",
                ChatParamsState.activeSummary(selection)
            )
        }

        @Test
        @DisplayName("a whitespace-only router value is omitted from the summary")
        fun `whitespace only router value is omitted`() {
            val selection = defaults(routerVisible = true, routerLabel = "Cost tier", routerValue = "  ")
            assertEquals("Send parameters", ChatParamsState.activeSummary(selection))
        }
    }
}
