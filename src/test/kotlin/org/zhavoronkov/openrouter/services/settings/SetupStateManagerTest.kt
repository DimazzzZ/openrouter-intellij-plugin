package org.zhavoronkov.openrouter.services.settings

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.zhavoronkov.openrouter.models.OpenRouterSettings

@DisplayName("SetupStateManager Tests")
class SetupStateManagerTest {

    private lateinit var settings: OpenRouterSettings
    private lateinit var manager: SetupStateManager
    private var stateChangedCount: Int = 0

    @BeforeEach
    fun setup() {
        settings = OpenRouterSettings()
        stateChangedCount = 0
        manager = SetupStateManager(settings) { stateChangedCount++ }
    }

    @Nested
    @DisplayName("hasSeenWelcome")
    inner class HasSeenWelcomeTests {

        @Test
        fun `returns false when not set`() {
            assertFalse(manager.hasSeenWelcome())
        }

        @Test
        fun `returns true when settings has seen welcome`() {
            settings.hasSeenWelcome = true
            assertTrue(manager.hasSeenWelcome())
        }
    }

    @Nested
    @DisplayName("setHasSeenWelcome")
    inner class SetHasSeenWelcomeTests {

        @Test
        fun `sets value in settings`() {
            manager.setHasSeenWelcome(true)
            assertTrue(settings.hasSeenWelcome)
        }

        @Test
        fun `triggers state changed callback`() {
            manager.setHasSeenWelcome(true)
            assertEquals(1, stateChangedCount)
        }

        @Test
        fun `can set to false`() {
            settings.hasSeenWelcome = true
            manager.setHasSeenWelcome(false)
            assertFalse(settings.hasSeenWelcome)
            assertEquals(1, stateChangedCount)
        }
    }

    @Nested
    @DisplayName("hasCompletedSetup")
    inner class HasCompletedSetupTests {

        @Test
        fun `returns false when not set`() {
            assertFalse(manager.hasCompletedSetup())
        }

        @Test
        fun `returns true when settings has completed setup`() {
            settings.hasCompletedSetup = true
            assertTrue(manager.hasCompletedSetup())
        }
    }

    @Nested
    @DisplayName("setHasCompletedSetup")
    inner class SetHasCompletedSetupTests {

        @Test
        fun `sets value in settings`() {
            manager.setHasCompletedSetup(true)
            assertTrue(settings.hasCompletedSetup)
        }

        @Test
        fun `triggers state changed callback`() {
            manager.setHasCompletedSetup(true)
            assertEquals(1, stateChangedCount)
        }

        @Test
        fun `can set to false`() {
            settings.hasCompletedSetup = true
            manager.setHasCompletedSetup(false)
            assertFalse(settings.hasCompletedSetup)
            assertEquals(1, stateChangedCount)
        }
    }

    @Nested
    @DisplayName("Integration")
    inner class IntegrationTests {

        @Test
        fun `multiple operations trigger multiple callbacks`() {
            manager.setHasSeenWelcome(true)
            manager.setHasCompletedSetup(true)
            manager.setHasSeenWelcome(false)

            assertEquals(3, stateChangedCount)
        }

        @Test
        fun `state is consistent after operations`() {
            manager.setHasSeenWelcome(true)
            manager.setHasCompletedSetup(true)

            assertTrue(manager.hasSeenWelcome())
            assertTrue(manager.hasCompletedSetup())
        }
    }
}
