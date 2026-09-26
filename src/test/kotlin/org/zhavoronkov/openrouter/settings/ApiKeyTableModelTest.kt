package org.zhavoronkov.openrouter.settings

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.zhavoronkov.openrouter.models.ApiKeyInfo

/**
 * [ApiKeyTableModel] is a top-level class sharing `OpenRouterSettingsPanel.kt` with the panel it
 * feeds, but unlike the panel it is a plain `AbstractTableModel`: no application service, no
 * display. It holds the rows and decides how each cell reads, which makes it ordinary logic and
 * testable under the fast headless `test` task.
 */
@DisplayName("ApiKeyTableModel")
class ApiKeyTableModelTest {

    private lateinit var model: ApiKeyTableModel

    @BeforeEach
    fun setUp() {
        model = ApiKeyTableModel()
    }

    private fun key(
        label: String = "Key",
        name: String = "key-name",
        usage: Double = 1.5,
        limit: Double? = 10.0,
        disabled: Boolean = false
    ) = ApiKeyInfo(
        name = name,
        label = label,
        limit = limit,
        usage = usage,
        disabled = disabled,
        createdAt = "2026-01-01T00:00:00Z",
        updatedAt = null,
        hash = "hash-$name"
    )

    @Nested
    @DisplayName("Shape")
    inner class Shape {

        @Test
        @DisplayName("an empty model has no rows but keeps its five named columns")
        fun `an empty model still names its columns`() {
            assertEquals(0, model.rowCount)
            assertEquals(5, model.columnCount)
            assertEquals(listOf("Label", "Name", "Usage", "Limit", "Status"), (0..4).map { model.getColumnName(it) })
        }

        @Test
        @DisplayName("setting keys replaces the previous rows rather than appending to them")
        fun `setting keys replaces the rows`() {
            model.setApiKeys(listOf(key(name = "a"), key(name = "b")))
            model.setApiKeys(listOf(key(name = "c")))

            assertEquals(1, model.rowCount)
            assertEquals("c", model.getValueAt(0, 1))
        }
    }

    @Nested
    @DisplayName("Cell rendering")
    inner class CellRendering {

        @Test
        @DisplayName("each column reads the field it is named after")
        fun `every column reads its own field`() {
            model.setApiKeys(listOf(key(label = "Prod", name = "prod-key", usage = 1.5, limit = 10.0)))

            assertEquals("Prod", model.getValueAt(0, 0))
            assertEquals("prod-key", model.getValueAt(0, 1))
            assertEquals("$1.5000", model.getValueAt(0, 2))
            assertEquals("$10.00", model.getValueAt(0, 3))
            assertEquals("Active", model.getValueAt(0, 4))
        }

        @Test
        @DisplayName("a key with no limit reads as Unlimited, never as a fabricated zero")
        fun `an absent limit reads as Unlimited`() {
            model.setApiKeys(listOf(key(limit = null)))

            assertEquals("Unlimited", model.getValueAt(0, 3))
        }

        @Test
        @DisplayName("a disabled key reads as Inactive")
        fun `a disabled key reads as Inactive`() {
            model.setApiKeys(listOf(key(disabled = true)))

            assertEquals("Inactive", model.getValueAt(0, 4))
        }

        @Test
        @DisplayName("a column index outside the five named ones reads blank rather than throwing")
        fun `an unknown column reads blank`() {
            model.setApiKeys(listOf(key()))

            assertEquals("", model.getValueAt(0, 99))
        }
    }

    @Nested
    @DisplayName("Row access and removal")
    inner class RowAccess {

        @Test
        @DisplayName("a row can be read back by index")
        fun `a row reads back by index`() {
            val first = key(name = "first")
            model.setApiKeys(listOf(first, key(name = "second")))

            assertEquals(first, model.getApiKeyAt(0))
        }

        @Test
        @DisplayName("an index outside the rows answers null rather than throwing")
        fun `an out of range index answers null`() {
            model.setApiKeys(listOf(key()))

            assertNull(model.getApiKeyAt(-1))
            assertNull(model.getApiKeyAt(1))
        }

        @Test
        @DisplayName("removing a row drops exactly that row")
        fun `removing a row drops only it`() {
            model.setApiKeys(listOf(key(name = "a"), key(name = "b"), key(name = "c")))

            model.removeApiKey(1)

            assertEquals(listOf("a", "c"), model.getApiKeys().map { it.name })
        }

        @Test
        @DisplayName("removing an index outside the rows leaves the model untouched")
        fun `removing an out of range index is a no-op`() {
            model.setApiKeys(listOf(key(name = "a")))

            model.removeApiKey(-1)
            model.removeApiKey(7)

            assertEquals(listOf("a"), model.getApiKeys().map { it.name })
        }

        @Test
        @DisplayName("getApiKeys hands out a copy, so a caller cannot mutate the model through it")
        fun `getApiKeys hands out a copy`() {
            model.setApiKeys(listOf(key(name = "a")))

            val snapshot = model.getApiKeys()
            model.removeApiKey(0)

            assertEquals(1, snapshot.size)
            assertEquals(0, model.rowCount)
        }
    }
}
