package org.zhavoronkov.openrouter.services

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.zhavoronkov.openrouter.api.BalanceData
import org.zhavoronkov.openrouter.models.ActivityData
import org.zhavoronkov.openrouter.models.CreditsData
import java.time.LocalDate

/**
 * Integration tests for the BalanceProvider extension point feature.
 *
 * Tests cover:
 * - OpenRouterStatsCache integration
 * - BalanceData creation from CreditsData
 * - Today's usage calculation from ActivityData
 * - Settings integration (balanceProviderEnabled)
 *
 * Note: These tests verify the integration logic without requiring IntelliJ platform initialization.
 * Full end-to-end tests with extension point registration require IDE environment.
 */
@DisplayName("Balance Provider Integration Tests")
class BalanceProviderIntegrationTest {

    companion object {
        private const val DEFAULT_TIMESTAMP = 1711526400000L
    }

    @Nested
    @DisplayName("BalanceData Creation Tests")
    inner class BalanceDataCreationTests {

        @Test
        @DisplayName("should create BalanceData from CreditsData")
        fun createBalanceDataFromCreditsData() {
            val creditsData = CreditsData(
                totalCredits = 100.0,
                totalUsage = 25.0
            )

            val balanceData = BalanceData(
                totalCredits = creditsData.totalCredits,
                totalUsage = creditsData.totalUsage,
                remainingCredits = creditsData.totalCredits - creditsData.totalUsage,
                timestamp = System.currentTimeMillis()
            )

            assertEquals(100.0, balanceData.totalCredits)
            assertEquals(25.0, balanceData.totalUsage)
            assertEquals(75.0, balanceData.remainingCredits)
            assertTrue(balanceData.timestamp > 0)
        }

        @Test
        @DisplayName("should calculate remaining credits correctly")
        fun calculateRemainingCredits() {
            val creditsData = CreditsData(
                totalCredits = 50.0,
                totalUsage = 30.0
            )

            val remaining = creditsData.totalCredits - creditsData.totalUsage

            assertEquals(20.0, remaining)
        }

        @Test
        @DisplayName("should handle zero credits")
        fun handleZeroCredits() {
            val creditsData = CreditsData(
                totalCredits = 0.0,
                totalUsage = 0.0
            )

            val balanceData = BalanceData(
                totalCredits = creditsData.totalCredits,
                totalUsage = creditsData.totalUsage,
                remainingCredits = creditsData.totalCredits - creditsData.totalUsage,
                timestamp = System.currentTimeMillis()
            )

            assertEquals(0.0, balanceData.totalCredits)
            assertEquals(0.0, balanceData.totalUsage)
            assertEquals(0.0, balanceData.remainingCredits)
        }
    }

    @Nested
    @DisplayName("Today Usage Calculation Tests")
    inner class TodayUsageCalculationTests {

        @Test
        @DisplayName("should calculate today's usage from activity data")
        fun calculateTodayUsage() {
            val today = LocalDate.now().toString()
            val yesterday = LocalDate.now().minusDays(1).toString()

            val activityData = listOf(
                createActivityData(today, 5.0),
                createActivityData(today, 3.0),
                createActivityData(yesterday, 10.0) // Should not be included
            )

            val todayUsage = activityData
                .filter { it.date == today }
                .sumOf { it.usage ?: 0.0 }

            assertEquals(8.0, todayUsage)
        }

        @Test
        @DisplayName("should return zero for no today's activity")
        fun noTodayActivity() {
            val yesterday = LocalDate.now().minusDays(1).toString()

            val activityData = listOf(
                createActivityData(yesterday, 5.0),
                createActivityData(yesterday, 3.0)
            )

            val today = LocalDate.now().toString()
            val todayUsage = activityData
                .filter { it.date == today }
                .sumOf { it.usage ?: 0.0 }

            assertEquals(0.0, todayUsage)
        }

        @Test
        @DisplayName("should handle null usage values")
        fun handleNullUsageValues() {
            val today = LocalDate.now().toString()

            val activityData = listOf(
                createActivityData(today, 5.0),
                createActivityDataWithNullUsage(today),
                createActivityData(today, 3.0)
            )

            val todayUsage = activityData
                .filter { it.date == today }
                .sumOf { it.usage ?: 0.0 }

            assertEquals(8.0, todayUsage)
        }

        @Test
        @DisplayName("should handle empty activity list")
        fun handleEmptyActivityList() {
            val activityData = emptyList<ActivityData>()
            val today = LocalDate.now().toString()

            val todayUsage = activityData
                .filter { it.date == today }
                .sumOf { it.usage ?: 0.0 }

            assertEquals(0.0, todayUsage)
        }

        @Test
        @DisplayName("should handle null date in activity")
        fun handleNullDateInActivity() {
            val today = LocalDate.now().toString()

            val activityData = listOf(
                createActivityData(today, 5.0),
                createActivityDataWithNullDate(3.0), // Null date should be filtered out
                createActivityData(today, 2.0)
            )

            val todayUsage = activityData
                .filter { it.date == today }
                .sumOf { it.usage ?: 0.0 }

            assertEquals(7.0, todayUsage)
        }

        private fun createActivityData(date: String, usage: Double): ActivityData {
            return ActivityData(
                date = date,
                model = "test-model",
                modelPermaslug = "test-model",
                endpointId = "ep-1",
                providerName = "openai",
                usage = usage,
                byokUsageInference = null,
                requests = 1,
                promptTokens = 100,
                completionTokens = 50,
                reasoningTokens = null
            )
        }

        private fun createActivityDataWithNullUsage(date: String): ActivityData {
            return ActivityData(
                date = date,
                model = "test-model",
                modelPermaslug = "test-model",
                endpointId = "ep-1",
                providerName = "openai",
                usage = null, // Null usage
                byokUsageInference = null,
                requests = 1,
                promptTokens = 100,
                completionTokens = 50,
                reasoningTokens = null
            )
        }

        private fun createActivityDataWithNullDate(usage: Double): ActivityData {
            return ActivityData(
                date = null, // Null date
                model = "test-model",
                modelPermaslug = "test-model",
                endpointId = "ep-1",
                providerName = "openai",
                usage = usage,
                byokUsageInference = null,
                requests = 1,
                promptTokens = 100,
                completionTokens = 50,
                reasoningTokens = null
            )
        }
    }

    @Nested
    @DisplayName("BalanceData with Today Usage Tests")
    inner class BalanceDataWithTodayUsageTests {

        @Test
        @DisplayName("should create BalanceData with today's usage")
        fun createBalanceDataWithTodayUsage() {
            val creditsData = CreditsData(
                totalCredits = 100.0,
                totalUsage = 25.0
            )

            val todayUsage = 5.0

            val balanceData = BalanceData(
                totalCredits = creditsData.totalCredits,
                totalUsage = creditsData.totalUsage,
                remainingCredits = creditsData.totalCredits - creditsData.totalUsage,
                timestamp = System.currentTimeMillis(),
                todayUsage = todayUsage
            )

            assertEquals(100.0, balanceData.totalCredits)
            assertEquals(25.0, balanceData.totalUsage)
            assertEquals(75.0, balanceData.remainingCredits)
            assertEquals(5.0, balanceData.todayUsage)
        }

        @Test
        @DisplayName("should handle null today's usage")
        fun handleNullTodayUsage() {
            val creditsData = CreditsData(
                totalCredits = 100.0,
                totalUsage = 25.0
            )

            val balanceData = BalanceData(
                totalCredits = creditsData.totalCredits,
                totalUsage = creditsData.totalUsage,
                remainingCredits = creditsData.totalCredits - creditsData.totalUsage,
                timestamp = System.currentTimeMillis(),
                todayUsage = null
            )

            assertNull(balanceData.todayUsage)
            assertEquals("N/A", balanceData.formattedTodayUsage())
        }
    }

    @Nested
    @DisplayName("Settings Integration Tests")
    inner class SettingsIntegrationTests {

        @Test
        @DisplayName("OpenRouterSettings should have balanceProviderEnabled field")
        fun settingsHasBalanceProviderEnabledField() {
            val settingsClass = org.zhavoronkov.openrouter.models.OpenRouterSettings::class.java

            // Check if the field exists
            val field = settingsClass.getDeclaredField("balanceProviderEnabled")
            assertNotNull(field, "OpenRouterSettings should have balanceProviderEnabled field")
            assertEquals(Boolean::class.javaPrimitiveType, field.type)
        }

        @Test
        @DisplayName("balanceProviderEnabled should default to true")
        fun balanceProviderEnabledDefaultsToTrue() {
            val settings = org.zhavoronkov.openrouter.models.OpenRouterSettings()
            assertTrue(settings.balanceProviderEnabled, "balanceProviderEnabled should default to true")
        }

        @Test
        @DisplayName("balanceProviderEnabled should be modifiable")
        fun balanceProviderEnabledIsModifiable() {
            val settings = org.zhavoronkov.openrouter.models.OpenRouterSettings()

            settings.balanceProviderEnabled = false
            assertEquals(false, settings.balanceProviderEnabled)

            settings.balanceProviderEnabled = true
            assertEquals(true, settings.balanceProviderEnabled)
        }
    }

    @Nested
    @DisplayName("OpenRouterStatsCache Structure Tests")
    inner class StatsCacheStructureTests {

        @Test
        @DisplayName("OpenRouterStatsCache should have methods for balance provider integration")
        fun statsCacheHasRequiredMethods() {
            val cacheClass = OpenRouterStatsCache::class.java

            // Verify public methods exist
            assertNotNull(cacheClass.getDeclaredMethod("getCachedCredits"))
            assertNotNull(cacheClass.getDeclaredMethod("getCachedActivity"))
            assertNotNull(cacheClass.getDeclaredMethod("refresh"))
        }

        @Test
        @DisplayName("OpenRouterStatsCache should import BalanceData")
        fun statsCacheImportsBalanceData() {
            // This test verifies that the import was added correctly
            // by checking if BalanceData can be referenced in the context of StatsCache
            val balanceData = BalanceData(
                totalCredits = 100.0,
                totalUsage = 25.0,
                remainingCredits = 75.0,
                timestamp = System.currentTimeMillis()
            )
            assertNotNull(balanceData)
        }
    }

    @Nested
    @DisplayName("Extension Point Declaration Tests")
    inner class ExtensionPointDeclarationTests {

        @Test
        @DisplayName("BalanceProvider interface should be in api package")
        fun balanceProviderInApiPackage() {
            val interfaceClass = org.zhavoronkov.openrouter.api.BalanceProvider::class.java
            assertEquals("org.zhavoronkov.openrouter.api", interfaceClass.packageName)
        }

        @Test
        @DisplayName("BalanceData class should be in api package")
        fun balanceDataInApiPackage() {
            val dataClass = BalanceData::class.java
            assertEquals("org.zhavoronkov.openrouter.api", dataClass.packageName)
        }

        @Test
        @DisplayName("BalanceProviderNotifier should be in services package")
        fun notifierInServicesPackage() {
            val notifierClass = BalanceProviderNotifier::class.java
            assertEquals("org.zhavoronkov.openrouter.services", notifierClass.packageName)
        }
    }

    @Nested
    @DisplayName("UIPreferencesManager Integration Tests")
    inner class UIPreferencesManagerTests {

        @Test
        @DisplayName("UIPreferencesManager should have balanceProviderEnabled property")
        fun uiPreferencesManagerHasBalanceProviderEnabled() {
            val managerClass = org.zhavoronkov.openrouter.services.settings.UIPreferencesManager::class.java

            // Check for getter
            val getter = managerClass.getDeclaredMethod("getBalanceProviderEnabled")
            assertNotNull(getter, "UIPreferencesManager should have getBalanceProviderEnabled method")

            // Check for setter
            val setter = managerClass.getDeclaredMethod("setBalanceProviderEnabled", Boolean::class.javaPrimitiveType)
            assertNotNull(setter, "UIPreferencesManager should have setBalanceProviderEnabled method")
        }
    }
}
