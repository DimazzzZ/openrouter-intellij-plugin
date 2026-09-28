package org.zhavoronkov.openrouter.services.settings

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.zhavoronkov.openrouter.models.OpenRouterSettings
import org.zhavoronkov.openrouter.models.OutputSchema

@DisplayName("OutputSchemasManager")
class OutputSchemasManagerTest {

    private lateinit var settings: OpenRouterSettings
    private var notifications = 0
    private lateinit var manager: OutputSchemasManager

    private val features = OutputSchema("features", strict = true, schema = """{"type":"object"}""")
    private val summary = OutputSchema("summary", strict = false, schema = """{"type":"object","properties":{}}""")

    @BeforeEach
    fun setUp() {
        settings = OpenRouterSettings()
        notifications = 0
        manager = OutputSchemasManager(settings) { notifications++ }
    }

    @Test
    @DisplayName("fresh settings hold no schemas")
    fun `fresh settings hold no schemas`() {
        assertEquals(emptyList<OutputSchema>(), manager.all())
    }

    @Test
    @DisplayName("saved schemas read back in order, and saving notifies once")
    fun `saved schemas read back in order`() {
        manager.replaceAll(listOf(summary, features))

        assertEquals(listOf(summary, features), manager.all())
        assertEquals(1, notifications)
    }

    @Test
    @DisplayName("saving what is already saved does not notify")
    fun `saving what is already saved does not notify`() {
        manager.replaceAll(listOf(features))
        manager.replaceAll(listOf(features.copy()))

        assertEquals(1, notifications)
    }

    @Test
    @DisplayName("editing a schema that was read out does not change what is saved")
    fun `editing a schema that was read out does not change what is saved`() {
        manager.replaceAll(listOf(features))

        manager.all().single().name = "renamed"

        assertEquals("features", manager.all().single().name)
    }
}
