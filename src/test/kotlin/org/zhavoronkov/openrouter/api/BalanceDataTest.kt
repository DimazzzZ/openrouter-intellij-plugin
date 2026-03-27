package org.zhavoronkov.openrouter.api

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * Unit tests for [BalanceData] data class.
 *
 * Tests cover:
 * - Validation (negative values, zero timestamp)
 * - Percentage calculations
 * - Formatting methods
 * - Low balance detection
 * - Edge cases
 */
@DisplayName("BalanceData Tests")
class BalanceDataTest {

    companion object {
        private const val DEFAULT_TIMESTAMP = 1711526400000L // March 27, 2024
    }

    @Nested
    @DisplayName("Validation Tests")
    inner class ValidationTests {

        @Test
        @DisplayName("should reject negative totalCredits")
        fun rejectsNegativeTotalCredits() {
            val exception = assertThrows(IllegalArgumentException::class.java) {
                BalanceData(
                    totalCredits = -1.0,
                    totalUsage = 0.0,
                    remainingCredits = 0.0,
                    timestamp = DEFAULT_TIMESTAMP
                )
            }
            assertTrue(exception.message?.contains("totalCredits") == true)
        }

        @Test
        @DisplayName("should reject negative totalUsage")
        fun rejectsNegativeTotalUsage() {
            val exception = assertThrows(IllegalArgumentException::class.java) {
                BalanceData(
                    totalCredits = 100.0,
                    totalUsage = -1.0,
                    remainingCredits = 100.0,
                    timestamp = DEFAULT_TIMESTAMP
                )
            }
            assertTrue(exception.message?.contains("totalUsage") == true)
        }

        @Test
        @DisplayName("should reject zero timestamp")
        fun rejectsZeroTimestamp() {
            val exception = assertThrows(IllegalArgumentException::class.java) {
                BalanceData(
                    totalCredits = 100.0,
                    totalUsage = 0.0,
                    remainingCredits = 100.0,
                    timestamp = 0
                )
            }
            assertTrue(exception.message?.contains("timestamp") == true)
        }

        @Test
        @DisplayName("should reject negative timestamp")
        fun rejectsNegativeTimestamp() {
            val exception = assertThrows(IllegalArgumentException::class.java) {
                BalanceData(
                    totalCredits = 100.0,
                    totalUsage = 0.0,
                    remainingCredits = 100.0,
                    timestamp = -1
                )
            }
            assertTrue(exception.message?.contains("timestamp") == true)
        }

        @Test
        @DisplayName("should reject negative todayUsage when provided")
        fun rejectsNegativeTodayUsage() {
            val exception = assertThrows(IllegalArgumentException::class.java) {
                BalanceData(
                    totalCredits = 100.0,
                    totalUsage = 50.0,
                    remainingCredits = 50.0,
                    timestamp = DEFAULT_TIMESTAMP,
                    todayUsage = -5.0
                )
            }
            assertTrue(exception.message?.contains("todayUsage") == true)
        }

        @Test
        @DisplayName("should allow zero values for credits and usage")
        fun allowsZeroValues() {
            val data = BalanceData(
                totalCredits = 0.0,
                totalUsage = 0.0,
                remainingCredits = 0.0,
                timestamp = DEFAULT_TIMESTAMP
            )
            assertEquals(0.0, data.totalCredits)
            assertEquals(0.0, data.totalUsage)
        }

        @Test
        @DisplayName("should allow null todayUsage")
        fun allowsNullTodayUsage() {
            val data = BalanceData(
                totalCredits = 100.0,
                totalUsage = 50.0,
                remainingCredits = 50.0,
                timestamp = DEFAULT_TIMESTAMP,
                todayUsage = null
            )
            assertNull(data.todayUsage)
        }

        @Test
        @DisplayName("should allow zero todayUsage")
        fun allowsZeroTodayUsage() {
            val data = BalanceData(
                totalCredits = 100.0,
                totalUsage = 50.0,
                remainingCredits = 50.0,
                timestamp = DEFAULT_TIMESTAMP,
                todayUsage = 0.0
            )
            assertEquals(0.0, data.todayUsage)
        }
    }

    @Nested
    @DisplayName("Percentage Calculation Tests")
    inner class PercentageTests {

        @Test
        @DisplayName("remainingPercentage should calculate correctly")
        fun remainingPercentageCalculation() {
            val data = BalanceData(
                totalCredits = 100.0,
                totalUsage = 25.0,
                remainingCredits = 75.0,
                timestamp = DEFAULT_TIMESTAMP
            )
            assertEquals(75.0, data.remainingPercentage(), 0.001)
        }

        @Test
        @DisplayName("remainingPercentage should return 100 when totalCredits is zero")
        fun remainingPercentageWithZeroCredits() {
            val data = BalanceData(
                totalCredits = 0.0,
                totalUsage = 0.0,
                remainingCredits = 0.0,
                timestamp = DEFAULT_TIMESTAMP
            )
            assertEquals(100.0, data.remainingPercentage(), 0.001)
        }

        @Test
        @DisplayName("usagePercentage should calculate correctly")
        fun usagePercentageCalculation() {
            val data = BalanceData(
                totalCredits = 100.0,
                totalUsage = 25.0,
                remainingCredits = 75.0,
                timestamp = DEFAULT_TIMESTAMP
            )
            assertEquals(25.0, data.usagePercentage(), 0.001)
        }

        @Test
        @DisplayName("usagePercentage should return 0 when totalCredits is zero")
        fun usagePercentageWithZeroCredits() {
            val data = BalanceData(
                totalCredits = 0.0,
                totalUsage = 0.0,
                remainingCredits = 0.0,
                timestamp = DEFAULT_TIMESTAMP
            )
            assertEquals(0.0, data.usagePercentage(), 0.001)
        }
    }

    @Nested
    @DisplayName("Low Balance Detection Tests")
    inner class LowBalanceTests {

        @Test
        @DisplayName("isLowBalance should return true when remaining is less than 10%")
        fun lowBalanceDetection() {
            val data = BalanceData(
                totalCredits = 100.0,
                totalUsage = 92.0,
                remainingCredits = 8.0,
                timestamp = DEFAULT_TIMESTAMP
            )
            assertTrue(data.isLowBalance())
        }

        @Test
        @DisplayName("isLowBalance should return false when remaining is 10% or more")
        fun notLowBalance() {
            val data = BalanceData(
                totalCredits = 100.0,
                totalUsage = 90.0,
                remainingCredits = 10.0,
                timestamp = DEFAULT_TIMESTAMP
            )
            assertFalse(data.isLowBalance())
        }

        @Test
        @DisplayName("isLowBalance should return false when totalCredits is zero")
        fun lowBalanceWithZeroCredits() {
            val data = BalanceData(
                totalCredits = 0.0,
                totalUsage = 0.0,
                remainingCredits = 0.0,
                timestamp = DEFAULT_TIMESTAMP
            )
            // When totalCredits is 0, remainingPercentage returns 100, so not low balance
            assertFalse(data.isLowBalance())
        }
    }

    @Nested
    @DisplayName("Formatting Tests")
    inner class FormattingTests {

        @Test
        @DisplayName("formattedRemaining should format with dollar sign and two decimals")
        fun formattedRemaining() {
            val data = BalanceData(
                totalCredits = 100.0,
                totalUsage = 25.0,
                remainingCredits = 75.50,
                timestamp = DEFAULT_TIMESTAMP
            )
            assertEquals("\$75.50", data.formattedRemaining())
        }

        @Test
        @DisplayName("formattedRemaining should handle small values")
        fun formattedRemainingSmallValues() {
            val data = BalanceData(
                totalCredits = 1.0,
                totalUsage = 0.99,
                remainingCredits = 0.01,
                timestamp = DEFAULT_TIMESTAMP
            )
            assertEquals("\$0.01", data.formattedRemaining())
        }

        @Test
        @DisplayName("formattedTodayUsage should format when value is present")
        fun formattedTodayUsagePresent() {
            val data = BalanceData(
                totalCredits = 100.0,
                totalUsage = 25.0,
                remainingCredits = 75.0,
                timestamp = DEFAULT_TIMESTAMP,
                todayUsage = 5.25
            )
            assertEquals("\$5.25", data.formattedTodayUsage())
        }

        @Test
        @DisplayName("formattedTodayUsage should return N/A when null")
        fun formattedTodayUsageNull() {
            val data = BalanceData(
                totalCredits = 100.0,
                totalUsage = 25.0,
                remainingCredits = 75.0,
                timestamp = DEFAULT_TIMESTAMP,
                todayUsage = null
            )
            assertEquals("N/A", data.formattedTodayUsage())
        }

        @Test
        @DisplayName("toString should include all key values")
        fun toStringFormat() {
            val data = BalanceData(
                totalCredits = 100.0,
                totalUsage = 25.0,
                remainingCredits = 75.0,
                timestamp = DEFAULT_TIMESTAMP,
                todayUsage = 5.0
            )
            val str = data.toString()
            assertTrue(str.contains("75.00"))
            assertTrue(str.contains("25.00"))
            assertTrue(str.contains("100.00"))
            assertTrue(str.contains("5.00"))
        }
    }

    @Nested
    @DisplayName("Data Class Tests")
    inner class DataClassTests {

        @Test
        @DisplayName("should support copy with modified values")
        fun copySupport() {
            val original = BalanceData(
                totalCredits = 100.0,
                totalUsage = 25.0,
                remainingCredits = 75.0,
                timestamp = DEFAULT_TIMESTAMP
            )
            val copied = original.copy(totalUsage = 50.0)

            assertEquals(100.0, copied.totalCredits)
            assertEquals(50.0, copied.totalUsage)
            assertEquals(75.0, copied.remainingCredits) // Not automatically recalculated
        }

        @Test
        @DisplayName("should have correct equality")
        fun equalityCheck() {
            val data1 = BalanceData(
                totalCredits = 100.0,
                totalUsage = 25.0,
                remainingCredits = 75.0,
                timestamp = DEFAULT_TIMESTAMP
            )
            val data2 = BalanceData(
                totalCredits = 100.0,
                totalUsage = 25.0,
                remainingCredits = 75.0,
                timestamp = DEFAULT_TIMESTAMP
            )
            assertEquals(data1, data2)
            assertEquals(data1.hashCode(), data2.hashCode())
        }

        @Test
        @DisplayName("should use default source value")
        fun defaultSourceValue() {
            val data = BalanceData(
                totalCredits = 100.0,
                totalUsage = 25.0,
                remainingCredits = 75.0,
                timestamp = DEFAULT_TIMESTAMP
            )
            assertEquals(BalanceData.SOURCE_OPENROUTER, data.source)
        }

        @Test
        @DisplayName("should allow custom source value")
        fun customSourceValue() {
            val data = BalanceData(
                totalCredits = 100.0,
                totalUsage = 25.0,
                remainingCredits = 75.0,
                timestamp = DEFAULT_TIMESTAMP,
                source = "custom-source"
            )
            assertEquals("custom-source", data.source)
        }
    }

    @Nested
    @DisplayName("Edge Cases")
    inner class EdgeCases {

        @Test
        @DisplayName("should handle very large values")
        fun largeValues() {
            val data = BalanceData(
                totalCredits = 1_000_000.0,
                totalUsage = 500_000.0,
                remainingCredits = 500_000.0,
                timestamp = DEFAULT_TIMESTAMP
            )
            assertEquals(50.0, data.remainingPercentage(), 0.001)
            assertFalse(data.isLowBalance())
        }

        @Test
        @DisplayName("should handle very small decimal values")
        fun smallDecimalValues() {
            val data = BalanceData(
                totalCredits = 0.001,
                totalUsage = 0.0001,
                remainingCredits = 0.0009,
                timestamp = DEFAULT_TIMESTAMP
            )
            assertEquals(90.0, data.remainingPercentage(), 0.1)
        }

        @Test
        @DisplayName("should handle remaining greater than total (edge case)")
        fun remainingGreaterThanTotal() {
            // This is technically invalid but we don't validate the relationship
            val data = BalanceData(
                totalCredits = 100.0,
                totalUsage = 0.0,
                remainingCredits = 150.0, // Invalid but allowed
                timestamp = DEFAULT_TIMESTAMP
            )
            assertEquals(150.0, data.remainingPercentage(), 0.001)
        }
    }
}
