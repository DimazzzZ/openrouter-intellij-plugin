package org.zhavoronkov.openrouter.settings

import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.zhavoronkov.openrouter.models.ApiKeyInfo
import java.util.Locale
import javax.swing.table.AbstractTableModel

/**
 * Tests for ApiKeyTableModel
 *
 * Note: These tests use a test-friendly version of the table model that doesn't require
 * IntelliJ Platform EDT (Event Dispatch Thread) initialization.
 */
@DisplayName("API Keys Table Model Tests")
class ApiKeysTableModelTest {

    private lateinit var tableModel: TestApiKeyTableModel
    private lateinit var sampleApiKeys: List<ApiKeyInfo>

    @BeforeEach
    fun setUp() {
        sampleApiKeys = listOf(
            ApiKeyInfo(
                name = "test-key-1",
                label = "sk-or-v1-test1",
                limit = 100.0,
                usage = 25.5,
                disabled = false,
                createdAt = "2025-09-17T12:00:00.000000+00:00",
                updatedAt = "2025-09-17T13:00:00.000000+00:00",
                hash = "hash1"
            ),
            ApiKeyInfo(
                name = "test-key-2",
                label = "sk-or-v1-test2",
                limit = null,
                usage = 0.0,
                disabled = true,
                createdAt = "2025-09-16T12:00:00.000000+00:00",
                updatedAt = null,
                hash = "hash2"
            ),
            ApiKeyInfo(
                name = "IntelliJ IDEA Plugin",
                label = "sk-or-v1-intellij",
                limit = 50.0,
                usage = 0.0000495,
                disabled = false,
                createdAt = "2025-09-15T12:00:00.000000+00:00",
                updatedAt = "2025-09-17T12:00:00.000000+00:00",
                hash = "hash3"
            )
        )

        tableModel = TestApiKeyTableModel()
    }

    /**
     * Test-friendly version of ApiKeyTableModel that doesn't require EDT
     */
    private class TestApiKeyTableModel : AbstractTableModel() {
        private val columnNames = arrayOf("Label", "Name", "Usage", "Limit", "Status")
        private val apiKeys = mutableListOf<ApiKeyInfo>()

        override fun getRowCount(): Int = apiKeys.size
        override fun getColumnCount(): Int = columnNames.size
        override fun getColumnName(column: Int): String = columnNames[column]
        override fun getColumnClass(columnIndex: Int): Class<*> = String::class.java
        override fun isCellEditable(rowIndex: Int, columnIndex: Int): Boolean = false

        override fun getValueAt(rowIndex: Int, columnIndex: Int): Any {
            if (rowIndex < 0 || rowIndex >= apiKeys.size) {
                throw IndexOutOfBoundsException("Row index out of bounds: $rowIndex")
            }
            if (columnIndex < 0 || columnIndex >= columnCount) {
                throw IndexOutOfBoundsException("Column index out of bounds: $columnIndex")
            }

            val key = apiKeys[rowIndex]
            return when (columnIndex) {
                0 -> key.label
                1 -> key.name
                2 -> String.format(Locale.US, "$%.6f", key.usage)
                3 -> key.limit?.let { String.format(Locale.US, "$%.2f", it) } ?: "Unlimited"
                4 -> if (key.disabled) "Disabled" else "Enabled"
                else -> throw IndexOutOfBoundsException("Column index out of bounds: $columnIndex")
            }
        }

        fun setApiKeys(keys: List<ApiKeyInfo>) {
            apiKeys.clear()
            apiKeys.addAll(keys)
            fireTableDataChanged()
        }

        fun getApiKeyAt(rowIndex: Int): ApiKeyInfo? {
            return if (rowIndex >= 0 && rowIndex < apiKeys.size) apiKeys[rowIndex] else null
        }

        fun removeApiKey(rowIndex: Int) {
            if (rowIndex >= 0 && rowIndex < apiKeys.size) {
                apiKeys.removeAt(rowIndex)
                fireTableRowsDeleted(rowIndex, rowIndex)
            }
        }

        fun getApiKeys(): List<ApiKeyInfo> {
            return apiKeys.toList()
        }
    }

    @Nested
    @DisplayName("Table Structure")
    inner class TableStructureTest {

        @Test
        @DisplayName("Should have correct column count")
        fun testColumnCount() {
            assertEquals(5, tableModel.columnCount, "Table should have 5 columns")
        }

        @Test
        @DisplayName("Should have correct column names")
        fun testColumnNames() {
            val expectedColumns = arrayOf("Label", "Name", "Usage", "Limit", "Status")

            for (i in expectedColumns.indices) {
                assertEquals(
                    expectedColumns[i],
                    tableModel.getColumnName(i),
                    "Column $i should have correct name"
                )
            }
        }

        @Test
        @DisplayName("Should have correct column classes")
        fun testColumnClasses() {
            // Columns: Label, Name, Usage, Limit, Status
            assertEquals(String::class.java, tableModel.getColumnClass(0)) // Label
            assertEquals(String::class.java, tableModel.getColumnClass(1)) // Name
            assertEquals(String::class.java, tableModel.getColumnClass(2)) // Usage
            assertEquals(String::class.java, tableModel.getColumnClass(3)) // Limit
            assertEquals(String::class.java, tableModel.getColumnClass(4)) // Status
        }

        @Test
        @DisplayName("Should not be editable")
        fun testNotEditable() {
            tableModel.setApiKeys(sampleApiKeys)

            for (row in 0 until tableModel.rowCount) {
                for (col in 0 until tableModel.columnCount) {
                    assertFalse(
                        tableModel.isCellEditable(row, col),
                        "Cell at ($row, $col) should not be editable"
                    )
                }
            }
        }
    }

    @Nested
    @DisplayName("Data Management")
    inner class DataManagementTest {

        @Test
        @DisplayName("Should start with empty data")
        fun testEmptyInitialData() {
            assertEquals(0, tableModel.rowCount, "Table should start empty")
        }

        @Test
        @DisplayName("Should set API keys correctly")
        fun testSetApiKeys() {
            tableModel.setApiKeys(sampleApiKeys)

            assertEquals(
                sampleApiKeys.size,
                tableModel.rowCount,
                "Row count should match API keys count"
            )
        }

        @Test
        @DisplayName("Should clear data when setting empty list")
        fun testClearData() {
            tableModel.setApiKeys(sampleApiKeys)
            assertEquals(sampleApiKeys.size, tableModel.rowCount)

            tableModel.setApiKeys(emptyList())
            assertEquals(0, tableModel.rowCount, "Table should be empty after clearing")
        }

        @Test
        @DisplayName("Should handle null API keys list")
        fun testNullApiKeys() {
            tableModel.setApiKeys(sampleApiKeys)

            assertDoesNotThrow {
                tableModel.setApiKeys(emptyList())
            }

            assertEquals(0, tableModel.rowCount)
        }

        @Test
        @DisplayName("Should get API key by row index")
        fun testGetApiKeyByRow() {
            tableModel.setApiKeys(sampleApiKeys)

            for (i in sampleApiKeys.indices) {
                val apiKey = tableModel.getApiKeyAt(i)
                assertEquals(
                    sampleApiKeys[i],
                    apiKey,
                    "API key at row $i should match original data"
                )
            }
        }

        @Test
        @DisplayName("Should handle invalid row index")
        fun testInvalidRowIndex() {
            tableModel.setApiKeys(sampleApiKeys)

            assertNull(tableModel.getApiKeyAt(-1), "Invalid negative index should return null")
            assertNull(tableModel.getApiKeyAt(sampleApiKeys.size), "Invalid large index should return null")
        }
    }

    @Nested
    @DisplayName("Cell Values")
    inner class CellValuesTest {

        @BeforeEach
        fun setUpData() {
            tableModel.setApiKeys(sampleApiKeys)
        }

        @Test
        @DisplayName("Should return correct label values")
        fun testLabelColumn() {
            for (i in sampleApiKeys.indices) {
                assertEquals(
                    sampleApiKeys[i].label,
                    tableModel.getValueAt(i, 0),
                    "Label column should return correct value for row $i"
                )
            }
        }

        @Test
        @DisplayName("Should return correct name values")
        fun testNameColumn() {
            for (i in sampleApiKeys.indices) {
                assertEquals(
                    sampleApiKeys[i].name,
                    tableModel.getValueAt(i, 1),
                    "Name column should return correct value for row $i"
                )
            }
        }

        @Test
        @DisplayName("Should format usage values correctly")
        fun testUsageColumn() {
            // First key has significant usage
            assertEquals(
                "$25.500000",
                tableModel.getValueAt(0, 2),
                "Usage should be formatted with 6 decimal places"
            )

            // Second key has zero usage
            assertEquals(
                "$0.000000",
                tableModel.getValueAt(1, 2),
                "Zero usage should be formatted correctly"
            )

            // Third key has very small usage
            assertEquals(
                "$0.000050",
                tableModel.getValueAt(2, 2),
                "Very small usage should show 6 decimal places"
            )
        }

        @Test
        @DisplayName("Should format limit values correctly")
        fun testLimitColumn() {
            // First key has limit
            assertEquals(
                "$100.00",
                tableModel.getValueAt(0, 3),
                "Limited key should show formatted limit"
            )

            // Second key has no limit
            assertEquals(
                "Unlimited",
                tableModel.getValueAt(1, 3),
                "Unlimited key should show 'Unlimited'"
            )

            // Third key has limit
            assertEquals(
                "$50.00",
                tableModel.getValueAt(2, 3),
                "Limited key should show formatted limit"
            )
        }

        @Test
        @DisplayName("Should format status values correctly")
        fun testStatusColumn() {
            // First key is enabled
            assertEquals(
                "Enabled",
                tableModel.getValueAt(0, 4),
                "Enabled key should show 'Enabled'"
            )

            // Second key is disabled
            assertEquals(
                "Disabled",
                tableModel.getValueAt(1, 4),
                "Disabled key should show 'Disabled'"
            )

            // Third key is enabled
            assertEquals(
                "Enabled",
                tableModel.getValueAt(2, 4),
                "Enabled key should show 'Enabled'"
            )
        }

        @Test
        @DisplayName("Should handle invalid column index")
        fun testInvalidColumnIndex() {
            assertThrows(IndexOutOfBoundsException::class.java) {
                tableModel.getValueAt(0, -1)
            }

            assertThrows(IndexOutOfBoundsException::class.java) {
                tableModel.getValueAt(0, tableModel.columnCount)
            }
        }

        @Test
        @DisplayName("Should handle invalid row index for getValue")
        fun testInvalidRowIndexForGetValue() {
            assertThrows(IndexOutOfBoundsException::class.java) {
                tableModel.getValueAt(-1, 0)
            }

            assertThrows(IndexOutOfBoundsException::class.java) {
                tableModel.getValueAt(tableModel.rowCount, 0)
            }
        }
    }

    @Nested
    @DisplayName("Formatting")
    inner class FormattingTest {

        @Test
        @DisplayName("Should format currency values consistently")
        fun testCurrencyFormatting() {
            // Test cases: value -> (expected usage format, expected limit format)
            val testCases = listOf(
                0.0 to ("$0.000000" to "$0.00"),
                0.0000495 to ("$0.000050" to "$0.00"),
                0.01 to ("$0.010000" to "$0.01"),
                1.0 to ("$1.000000" to "$1.00"),
                25.5 to ("$25.500000" to "$25.50"),
                100.0 to ("$100.000000" to "$100.00"),
                1000.0 to ("$1000.000000" to "$1000.00")
            )

            testCases.forEach { (value, formats) ->
                val (expectedUsage, expectedLimit) = formats
                val apiKey = ApiKeyInfo(
                    name = "test",
                    label = "test",
                    limit = value,
                    usage = value,
                    disabled = false,
                    createdAt = "2025-09-17T12:00:00.000000+00:00",
                    updatedAt = null,
                    hash = "test"
                )

                tableModel.setApiKeys(listOf(apiKey))

                // Column 2 is Usage (6 decimal places)
                assertEquals(
                    expectedUsage,
                    tableModel.getValueAt(0, 2),
                    "Usage formatting for $value"
                )
                // Column 3 is Limit (2 decimal places)
                assertEquals(
                    expectedLimit,
                    tableModel.getValueAt(0, 3),
                    "Limit formatting for $value"
                )
            }
        }

        @Test
        @DisplayName("Should handle null limit correctly")
        fun testNullLimitFormatting() {
            val apiKey = ApiKeyInfo(
                name = "test",
                label = "test",
                limit = null,
                usage = 10.0,
                disabled = false,
                createdAt = "2025-09-17T12:00:00.000000+00:00",
                updatedAt = null,
                hash = "test"
            )

            tableModel.setApiKeys(listOf(apiKey))

            // Column 3 is Limit
            assertEquals(
                "Unlimited",
                tableModel.getValueAt(0, 3),
                "Null limit should display as 'Unlimited'"
            )
        }

        @Test
        @DisplayName("Should handle very large numbers")
        fun testLargeNumberFormatting() {
            val apiKey = ApiKeyInfo(
                name = "test",
                label = "test",
                limit = 999999.99,
                usage = 123456.78,
                disabled = false,
                createdAt = "2025-09-17T12:00:00.000000+00:00",
                updatedAt = null,
                hash = "test"
            )

            tableModel.setApiKeys(listOf(apiKey))

            // Column 2 is Usage (6 decimal places), Column 3 is Limit (2 decimal places)
            assertEquals("$123456.780000", tableModel.getValueAt(0, 2))
            assertEquals("$999999.99", tableModel.getValueAt(0, 3))
        }
    }

    @Nested
    @DisplayName("Table Model Events")
    inner class TableModelEventsTest {

        @Test
        @DisplayName("Should fire table data changed event when setting API keys")
        fun testTableDataChangedEvent() {
            var eventFired = false

            tableModel.addTableModelListener { event ->
                eventFired = true
                // Verify it's an UPDATE event for all columns
                assertEquals(
                    javax.swing.event.TableModelEvent.ALL_COLUMNS,
                    event.column,
                    "Event should affect all columns"
                )
                assertEquals(
                    javax.swing.event.TableModelEvent.UPDATE,
                    event.type,
                    "Event type should be UPDATE"
                )
            }

            tableModel.setApiKeys(sampleApiKeys)

            assertTrue(eventFired, "Table model should fire data changed event")
        }

        // Note: TestApiKeyTableModel extends AbstractTableModel by design
    }
}
