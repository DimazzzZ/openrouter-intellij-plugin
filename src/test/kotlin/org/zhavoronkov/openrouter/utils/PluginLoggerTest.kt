package org.zhavoronkov.openrouter.utils

import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * Unit tests for [PluginLogger].
 *
 * Platform-bound coverage exclusions (documented in TESTING.md):
 * - The lazy-init `createLogger` catch arms (IllegalStateException /
 *   NoClassDefFoundError) require an environment where
 *   com.intellij.openapi.diagnostic.Logger.getInstance itself fails to
 *   resolve. In our unit-test JVM the IntelliJ test framework supplies a
 *   working Logger implementation, so those branches never trip.
 * - error(...) and warn(msg, throwable) delegates: IntelliJ's
 *   TestLoggerFactory rethrows any warn-with-throwable or error call as
 *   an AssertionError in test mode, so we cannot exercise those paths
 *   here without opting into an artificial catch. They are exercised
 *   implicitly by platform test suites (BasePlatformTestCase-based).
 * - The debugEnabled / testMode lazy flags are read once per JVM from
 *   system properties. This suite covers the default
 *   (debugEnabled = false) surface. Toggling those flags reliably
 *   requires a fresh classloader.
 *
 * What we do cover: the info() delegates on the five nested loggers
 * (Service / Settings / StatusBar / Models / Startup), the warn(message)
 * message-only variants, the debug() delegates (which no-op when
 * debugEnabled=false but still exercise the guard branch), Settings.production,
 * top-level info/debug/warn convenience methods, isDebugEnabled, and
 * logConfiguration.
 */
@DisplayName("PluginLogger Tests")
class PluginLoggerTest {

    private val throwable = RuntimeException("test-only")

    @Test
    @DisplayName("Service logger delegates info/debug without throwing")
    fun serviceDelegates() {
        assertDoesNotThrow {
            PluginLogger.Service.info("info-msg")
            PluginLogger.Service.debug("debug-msg")
            PluginLogger.Service.debug("debug-msg", throwable)
        }
    }

    @Test
    @DisplayName("Service warn/error route through debug when openrouter.testMode=true")
    fun serviceWarnErrorRouteThroughDebugInTestMode() {
        // Gradle sets openrouter.testMode=true for the test task, so warn(...) and
        // error(...) on the Service logger fall through to debug(...) rather than
        // invoking Logger#warn / Logger#error (which TestLoggerFactory would rethrow).
        assertDoesNotThrow {
            PluginLogger.Service.warn("warn-msg")
            PluginLogger.Service.warn("warn-msg", throwable)
            PluginLogger.Service.error("error-msg")
            PluginLogger.Service.error("error-msg", throwable)
        }
    }

    @Test
    @DisplayName("Top-level warn/error convenience delegates via Service testMode routing")
    fun topLevelWarnErrorDelegates() {
        assertDoesNotThrow {
            PluginLogger.warn("warn-msg")
            PluginLogger.warn("warn-msg", throwable)
            PluginLogger.error("error-msg")
            PluginLogger.error("error-msg", throwable)
        }
    }

    @Test
    @DisplayName("Settings/StatusBar/Models/Startup warn(message) without throwable are safe under TestLoggerFactory")
    fun perObjectWarnMessageOnly() {
        // TestLoggerFactory only rethrows on warn(throwable) and error(...); a bare
        // warn(String) is emitted as a log record without failing the test.
        assertDoesNotThrow {
            PluginLogger.Settings.warn("settings-warn")
            PluginLogger.StatusBar.warn("statusbar-warn")
            PluginLogger.Models.warn("models-warn")
            PluginLogger.Startup.warn("startup-warn")
        }
    }

    @Test
    @DisplayName("Settings logger delegates info/debug/production without throwing")
    fun settingsDelegates() {
        assertDoesNotThrow {
            PluginLogger.Settings.info("info-msg")
            PluginLogger.Settings.debug("debug-msg")
            PluginLogger.Settings.debug("debug-msg", throwable)
            PluginLogger.Settings.production("prod-msg")
        }
    }

    @Test
    @DisplayName("StatusBar logger delegates info/debug without throwing")
    fun statusBarDelegates() {
        assertDoesNotThrow {
            PluginLogger.StatusBar.info("info-msg")
            PluginLogger.StatusBar.debug("debug-msg")
        }
    }

    @Test
    @DisplayName("Models logger delegates info/debug without throwing")
    fun modelsDelegates() {
        assertDoesNotThrow {
            PluginLogger.Models.info("info-msg")
            PluginLogger.Models.debug("debug-msg")
        }
    }

    @Test
    @DisplayName("Startup logger delegates info/debug without throwing")
    fun startupDelegates() {
        assertDoesNotThrow {
            PluginLogger.Startup.info("info-msg")
            PluginLogger.Startup.debug("debug-msg")
        }
    }

    @Test
    @DisplayName("Top-level convenience methods for info and debug delegate without throwing")
    fun topLevelConvenience() {
        assertDoesNotThrow {
            PluginLogger.info("info-msg")
            PluginLogger.debug("debug-msg")
            PluginLogger.debug("debug-msg", throwable)
        }
    }

    @Test
    @DisplayName("isDebugEnabled returns a Boolean and logConfiguration runs cleanly")
    fun configurationSurface() {
        val enabled: Boolean = PluginLogger.isDebugEnabled()
        assertNotNull(enabled)
        assertDoesNotThrow { PluginLogger.logConfiguration() }
    }
}
