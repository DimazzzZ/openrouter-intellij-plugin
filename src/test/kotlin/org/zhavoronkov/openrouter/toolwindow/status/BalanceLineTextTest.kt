package org.zhavoronkov.openrouter.toolwindow.status

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("BalanceLineText")
class BalanceLineTextTest {

    // --- Defect C: an unknown figure must still name its own row, never a bare em dash ---------

    @Test
    @DisplayName("an unknown remaining figure keeps the word 'remaining'")
    fun `unknown remaining keeps its descriptor`() {
        assertEquals("— remaining", BalanceLineText.remaining(null))
    }

    @Test
    @DisplayName("a known remaining figure renders its dollar amount")
    fun `known remaining renders its figure`() {
        assertEquals("$12.34 remaining", BalanceLineText.remaining(12.34))
    }

    @Test
    @DisplayName("a zero or absent total keeps the word 'total'")
    fun `unknown total keeps its descriptor`() {
        assertEquals("of — total", BalanceLineText.total(0.0))
    }

    @Test
    @DisplayName("a real, positive total renders its dollar amount")
    fun `known total renders its figure`() {
        assertEquals("of $100.00 total", BalanceLineText.total(100.0))
    }

    @Test
    @DisplayName("an unknown burn rate keeps the words 'burn rate'")
    fun `unknown burn rate keeps its descriptor`() {
        assertEquals("—/day burn rate", BalanceLineText.burnRate(null))
    }

    @Test
    @DisplayName("a known burn rate renders its dollar amount")
    fun `known burn rate renders its figure`() {
        assertEquals("$3.30/day burn rate", BalanceLineText.burnRate(3.3))
    }

    @Test
    @DisplayName("an unknown days-left is blank - the whole row is hidden, not dashed")
    fun `unknown days left is blank`() {
        assertEquals("", BalanceLineText.daysLeft(null))
    }

    @Test
    @DisplayName("a known days-left renders its figure")
    fun `known days left renders its figure`() {
        assertEquals("10 days left", BalanceLineText.daysLeft(10.0))
    }
}
