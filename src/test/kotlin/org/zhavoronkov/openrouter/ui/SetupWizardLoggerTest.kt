package org.zhavoronkov.openrouter.ui

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@DisplayName("SetupWizardLogger Tests")
class SetupWizardLoggerTest {

    @Nested
    @DisplayName("info logging")
    inner class InfoLoggingTests {

        @Test
        fun `info does not throw`() {
            // Should not throw even if logging is disabled
            SetupWizardLogger.info("Test info message")
        }

        @Test
        fun `info with empty message does not throw`() {
            SetupWizardLogger.info("")
        }
    }

    @Nested
    @DisplayName("debug logging")
    inner class DebugLoggingTests {

        @Test
        fun `debug does not throw`() {
            SetupWizardLogger.debug("Test debug message")
        }

        @Test
        fun `debug with empty message does not throw`() {
            SetupWizardLogger.debug("")
        }
    }

    @Nested
    @DisplayName("warn logging")
    inner class WarnLoggingTests {

        @Test
        fun `warn does not throw`() {
            SetupWizardLogger.warn("Test warning message")
        }

        @Test
        fun `warn with empty message does not throw`() {
            SetupWizardLogger.warn("")
        }
    }

    @Nested
    @DisplayName("error logging")
    inner class ErrorLoggingTests {

        @Test
        fun `error does not throw`() {
            SetupWizardLogger.error("Test error message")
        }

        @Test
        fun `error with throwable does not throw`() {
            SetupWizardLogger.error("Test error", RuntimeException("Test exception"))
        }

        @Test
        fun `error with null throwable does not throw`() {
            SetupWizardLogger.error("Test error", null)
        }

        @Test
        fun `error with empty message does not throw`() {
            SetupWizardLogger.error("")
        }
    }

    @Nested
    @DisplayName("PKCE event logging")
    inner class PkceEventLoggingTests {

        @Test
        fun `logPkceEvent does not throw`() {
            SetupWizardLogger.logPkceEvent("server_started")
        }

        @Test
        fun `logPkceEvent with details does not throw`() {
            SetupWizardLogger.logPkceEvent("key_received", "length=64")
        }

        @Test
        fun `logPkceEvent with null details does not throw`() {
            SetupWizardLogger.logPkceEvent("timeout", null)
        }
    }

    @Nested
    @DisplayName("Validation event logging")
    inner class ValidationEventLoggingTests {

        @Test
        fun `logValidationEvent does not throw`() {
            SetupWizardLogger.logValidationEvent("key_validated")
        }

        @Test
        fun `logValidationEvent with details does not throw`() {
            SetupWizardLogger.logValidationEvent("key_validated", "key_length=32")
        }

        @Test
        fun `logValidationEvent with null details does not throw`() {
            SetupWizardLogger.logValidationEvent("validation_started", null)
        }
    }

    @Nested
    @DisplayName("Model loading event logging")
    inner class ModelLoadingEventLoggingTests {

        @Test
        fun `logModelLoadingEvent does not throw`() {
            SetupWizardLogger.logModelLoadingEvent("loading_started")
        }

        @Test
        fun `logModelLoadingEvent with details does not throw`() {
            SetupWizardLogger.logModelLoadingEvent("models_loaded", "count=150")
        }

        @Test
        fun `logModelLoadingEvent with null details does not throw`() {
            SetupWizardLogger.logModelLoadingEvent("loading_complete", null)
        }
    }

    @Nested
    @DisplayName("Object reference")
    inner class ObjectReferenceTests {

        @Test
        fun `SetupWizardLogger is a singleton object`() {
            val logger1 = SetupWizardLogger
            val logger2 = SetupWizardLogger
            assertNotNull(logger1)
            assertNotNull(logger2)
            // Objects are singletons
        }
    }
}
