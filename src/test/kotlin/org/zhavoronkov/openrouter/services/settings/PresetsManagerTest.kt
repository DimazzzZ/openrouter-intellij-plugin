package org.zhavoronkov.openrouter.services.settings

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.zhavoronkov.openrouter.models.OpenRouterSettings

@DisplayName("PresetsManager Tests")
class PresetsManagerTest {

    private lateinit var settings: OpenRouterSettings
    private lateinit var manager: PresetsManager
    private var changeCount: Int = 0

    @BeforeEach
    fun setUp() {
        settings = OpenRouterSettings()
        changeCount = 0
        manager = PresetsManager(settings) { changeCount++ }
    }

    @Nested
    @DisplayName("Built-in Presets")
    inner class BuiltInPresetsTests {

        @Test
        fun `built-in presets contains auto router`() {
            val autoPreset = PresetsManager.BUILT_IN_PRESETS.find { it.id == "openrouter/auto" }
            assertTrue(autoPreset != null)
            assertEquals("Auto Router", autoPreset?.name)
        }

        @Test
        fun `built-in presets contains free models`() {
            val freePreset = PresetsManager.BUILT_IN_PRESETS.find { it.id == "openrouter/free" }
            assertTrue(freePreset != null)
            assertEquals("Free Models", freePreset?.name)
        }

        @Test
        fun `preset prefix is correct`() {
            assertEquals("@preset/", PresetsManager.PRESET_PREFIX)
        }
    }

    @Nested
    @DisplayName("Add Custom Preset")
    inner class AddCustomPresetTests {

        @Test
        fun `add valid preset returns true`() {
            val result = manager.addCustomPreset("email-copywriter")

            assertTrue(result)
            assertTrue(manager.hasCustomPreset("email-copywriter"))
            assertEquals(1, changeCount)
        }

        @Test
        fun `add duplicate preset returns false`() {
            manager.addCustomPreset("email-copywriter")
            val result = manager.addCustomPreset("email-copywriter")

            assertFalse(result)
            assertEquals(1, manager.getCustomPresets().size)
            assertEquals(1, changeCount)
        }

        @Test
        fun `add blank preset returns false`() {
            val result = manager.addCustomPreset("   ")

            assertFalse(result)
            assertTrue(manager.getCustomPresets().isEmpty())
            assertEquals(0, changeCount)
        }

        @Test
        fun `add preset with prefix strips prefix`() {
            manager.addCustomPreset("@preset/my-preset")

            assertTrue(manager.hasCustomPreset("my-preset"))
            assertEquals(1, manager.getCustomPresets().size)
        }
    }

    @Nested
    @DisplayName("Remove Custom Preset")
    inner class RemoveCustomPresetTests {

        @Test
        fun `remove existing preset returns true`() {
            manager.addCustomPreset("email-copywriter")
            val result = manager.removeCustomPreset("email-copywriter")

            assertTrue(result)
            assertFalse(manager.hasCustomPreset("email-copywriter"))
            assertEquals(2, changeCount)
        }

        @Test
        fun `remove non-existing preset returns false`() {
            val result = manager.removeCustomPreset("non-existing")

            assertFalse(result)
            assertEquals(0, changeCount)
        }
    }

    @Nested
    @DisplayName("Set Custom Presets")
    inner class SetCustomPresetsTests {

        @Test
        fun `set presets replaces existing`() {
            manager.addCustomPreset("old-preset")
            manager.setCustomPresets(listOf("new-preset-1", "new-preset-2"))

            assertEquals(2, manager.getCustomPresets().size)
            assertFalse(manager.hasCustomPreset("old-preset"))
            assertTrue(manager.hasCustomPreset("new-preset-1"))
            assertTrue(manager.hasCustomPreset("new-preset-2"))
        }

        @Test
        fun `set presets filters blank entries`() {
            manager.setCustomPresets(listOf("valid-preset", "   ", "another-preset"))

            assertEquals(2, manager.getCustomPresets().size)
        }
    }

    @Nested
    @DisplayName("Clear Custom Presets")
    inner class ClearCustomPresetsTests {

        @Test
        fun `clear removes all presets`() {
            manager.addCustomPreset("preset-1")
            manager.addCustomPreset("preset-2")
            manager.clearCustomPresets()

            assertTrue(manager.getCustomPresets().isEmpty())
            assertEquals(3, changeCount)
        }
    }

    @Nested
    @DisplayName("Preset Model ID")
    inner class PresetModelIdTests {

        @Test
        fun `get preset model id adds prefix`() {
            val modelId = manager.getPresetModelId("email-copywriter")

            assertEquals("@preset/email-copywriter", modelId)
        }

        @Test
        fun `is preset model id returns true for valid preset`() {
            assertTrue(manager.isPresetModelId("@preset/email-copywriter"))
        }

        @Test
        fun `is preset model id returns false for regular model`() {
            assertFalse(manager.isPresetModelId("openai/gpt-4"))
        }

        @Test
        fun `is preset model id returns false for built-in router`() {
            assertFalse(manager.isPresetModelId("openrouter/auto"))
        }
    }

    @Nested
    @DisplayName("Extract Preset Slug")
    inner class ExtractPresetSlugTests {

        @Test
        fun `extract slug from preset model id`() {
            val slug = manager.extractPresetSlug("@preset/email-copywriter")

            assertEquals("email-copywriter", slug)
        }

        @Test
        fun `extract slug returns null for non-preset`() {
            val slug = manager.extractPresetSlug("openai/gpt-4")

            assertNull(slug)
        }
    }

    @Nested
    @DisplayName("Slug Normalization")
    inner class SlugNormalizationTests {

        @Test
        fun `normalize converts to lowercase`() {
            manager.addCustomPreset("My-Preset")

            assertTrue(manager.hasCustomPreset("my-preset"))
        }

        @Test
        fun `normalize replaces special characters with hyphens`() {
            manager.addCustomPreset("my preset!")

            assertTrue(manager.hasCustomPreset("my-preset"))
        }

        @Test
        fun `normalize collapses multiple hyphens`() {
            manager.addCustomPreset("my---preset")

            assertTrue(manager.hasCustomPreset("my-preset"))
        }

        @Test
        fun `normalize trims leading and trailing hyphens`() {
            manager.addCustomPreset("-my-preset-")

            assertTrue(manager.hasCustomPreset("my-preset"))
        }

        @Test
        fun `normalize handles complex input`() {
            manager.addCustomPreset("  @preset/My Cool Preset!!!  ")

            // Input starts with spaces, so @preset/ prefix isn't at the start
            // After normalization: spaces → trim → "@preset/my cool preset!!!" → "-preset-my-cool-preset"
            assertTrue(manager.hasCustomPreset("preset-my-cool-preset"))
        }

        @Test
        fun `normalize handles prefix at start correctly`() {
            manager.addCustomPreset("@preset/My Cool Preset")

            assertTrue(manager.hasCustomPreset("my-cool-preset"))
        }
    }
}
