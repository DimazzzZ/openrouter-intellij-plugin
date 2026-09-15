package org.zhavoronkov.openrouter.settings.presets

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.zhavoronkov.openrouter.models.Preset
import org.zhavoronkov.openrouter.models.PresetDesignatedVersion

@DisplayName("PresetsPageState Tests")
class PresetsPageStateTest {

    private fun preset(slug: String, config: Map<String, Any?>? = null, systemPrompt: String? = null) =
        Preset(
            id = slug,
            name = slug,
            slug = slug,
            designatedVersion = if (config != null || systemPrompt != null) {
                PresetDesignatedVersion(version = 1, systemPrompt = systemPrompt, config = config)
            } else {
                null
            }
        )

    @Nested
    @DisplayName("list & read")
    inner class ListRead {
        @Test
        @DisplayName("setPresets exposes the fetched list and clears load error")
        fun setPresetsClearsError() {
            val state = PresetsPageState()
            state.setLoadError("boom")
            state.setPresets(listOf(preset("email"), preset("blog")))
            assertEquals(2, state.visiblePresets().size)
            assertNull(state.loadError)
            assertEquals("2 presets", state.statusText())
        }

        @Test
        @DisplayName("load error keeps the last good list (spec behaviour 2)")
        fun loadErrorKeepsList() {
            val state = PresetsPageState()
            state.setPresets(listOf(preset("email")))
            state.setLoadError("offline")
            assertEquals(1, state.visiblePresets().size)
            assertEquals("Error: offline", state.statusText())
            assertEquals(PresetsPageState.EmptyState.NONE, state.emptyState())
        }

        @Test
        @DisplayName("emptyState reflects configuration and fetch results")
        fun emptyStates() {
            val state = PresetsPageState()
            state.isConfigured = false
            assertEquals(PresetsPageState.EmptyState.NOT_CONFIGURED, state.emptyState())
            state.isConfigured = true
            assertEquals(PresetsPageState.EmptyState.NO_PRESETS, state.emptyState())
            state.setLoadError("x")
            assertEquals(PresetsPageState.EmptyState.LOAD_FAILED, state.emptyState())
        }

        @Test
        @DisplayName("stale selection is dropped when the new list omits it")
        fun staleSelectionDropped() {
            val state = PresetsPageState()
            state.setPresets(listOf(preset("email")))
            state.selectedSlug = "email"
            state.setPresets(listOf(preset("blog")))
            assertNull(state.selectedSlug)
        }
    }

    @Nested
    @DisplayName("edit & passthrough")
    inner class Edit {
        @Test
        @DisplayName("beginEdit splits config into well-known fields + verbatim passthrough")
        fun beginEditSplits() {
            val state = PresetsPageState()
            val config = mapOf(
                "model" to "openai/gpt-4o",
                "temperature" to 0.3,
                "top_p" to 0.8,
                "provider" to mapOf("order" to listOf("anthropic")),
                "tools" to emptyList<Any?>()
            )
            state.setPresets(listOf(preset("email", config, systemPrompt = "be terse")))
            state.beginEdit("email")
            val editor = state.editor!!
            assertEquals("be terse", editor.systemPrompt)
            assertEquals("openai/gpt-4o", editor.wellKnown[PresetsPageState.WellKnownKey.MODEL])
            assertEquals(0.3, editor.wellKnown[PresetsPageState.WellKnownKey.TEMPERATURE])
            assertTrue(editor.passthrough.containsKey("provider"))
            assertTrue(editor.passthrough.containsKey("tools"))
            assertFalse(editor.passthrough.containsKey("model"), "well-known keys excluded from passthrough")
        }

        @Test
        @DisplayName("toConfig round-trips unknown keys and applies well-known edits")
        fun toConfigRoundTrips() {
            val state = PresetsPageState()
            val config = mapOf("model" to "a", "top_p" to 0.8, "provider" to mapOf("order" to listOf("x")))
            state.setPresets(listOf(preset("email", config)))
            state.beginEdit("email")
            state.updateWellKnown(PresetsPageState.WellKnownKey.MODEL, "openai/gpt-4o")
            state.updateWellKnown(PresetsPageState.WellKnownKey.TEMPERATURE, 0.5)
            val merged = state.editor!!.toConfig()
            assertEquals("openai/gpt-4o", merged["model"])
            assertEquals(0.5, merged["temperature"])
            assertEquals(0.8, merged["top_p"])
            assertTrue(merged.containsKey("provider"), "unknown key preserved through edit")
        }

        @Test
        @DisplayName("null well-known value removes the key from the config")
        fun nullRemovesKey() {
            val state = PresetsPageState()
            state.setPresets(listOf(preset("email", mapOf("model" to "a", "temperature" to 0.3))))
            state.beginEdit("email")
            state.updateWellKnown(PresetsPageState.WellKnownKey.TEMPERATURE, null)
            assertFalse(state.editor!!.toConfig().containsKey("temperature"))
        }

        @Test
        @DisplayName("isNewVersionOfExisting distinguishes create from update")
        fun createVsUpdate() {
            val state = PresetsPageState()
            state.setPresets(listOf(preset("email", mapOf("model" to "a"))))
            state.beginEdit("email")
            assertTrue(state.isNewVersionOfExisting())
            state.beginCreate("brand-new")
            assertFalse(state.isNewVersionOfExisting())
        }

        @Test
        @DisplayName("onChanged fires on mutations")
        fun onChangedFires() {
            val state = PresetsPageState()
            var count = 0
            state.onChanged = { count++ }
            state.setPresets(listOf(preset("email", mapOf("model" to "a"))))
            state.beginEdit("email")
            state.updateSystemPrompt("hi")
            assertTrue(count >= 3)
        }
    }
}
