package org.zhavoronkov.openrouter.statusbar

import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.zhavoronkov.openrouter.services.OpenRouterSettingsService

/**
 * Regression test for the status-bar auto-refresh classloader leak.
 *
 * Before the fix, [OpenRouterStatusBarWidget] ran its auto-refresh loop on a detached
 * pooled thread (while-true + Thread.sleep) that was not tied to the widget Disposable.
 * On plugin reload/update the stale thread survived, resolved [OpenRouterSettingsService]
 * through the *new* PluginClassLoader, and crashed with
 * "OpenRouterSettingsService cannot be cast to OpenRouterSettingsService".
 *
 * The fix parents an [com.intellij.util.Alarm] to the widget. This test proves the
 * timer is cancelled and disposed the moment the widget is disposed, so no tick can
 * outlive the widget (and therefore the classloader).
 */
class OpenRouterStatusBarWidgetDisposePlatformTest : BasePlatformTestCase() {

    fun testDisposeCancelsAutoRefreshTimer() {
        val prefs = OpenRouterSettingsService.getInstance().uiPreferencesManager
        val originalAutoRefresh = prefs.autoRefresh
        val originalInterval = prefs.refreshInterval
        prefs.autoRefresh = true
        prefs.refreshInterval = 1

        val factory = OpenRouterStatusBarWidgetFactory()
        val widget = factory.createWidget(project) as OpenRouterStatusBarWidget
        try {
            // The widget schedules its first refresh request during construction
            // (auto-refresh enabled), so the alarm should hold a pending request
            // and must not already be disposed.
            assertFalse(
                "Alarm must be live while the widget is alive",
                widget.isRefreshAlarmDisposedForTest()
            )

            Disposer.dispose(widget)

            // After dispose, the platform must have cancelled every request and
            // disposed the alarm so no stale tick can run against a new classloader.
            assertTrue(
                "Alarm must be disposed once the widget is disposed",
                widget.isRefreshAlarmDisposedForTest()
            )
            assertEquals(
                "No refresh request may outlive the disposed widget",
                0,
                widget.getPendingRefreshRequestCountForTest()
            )
        } finally {
            if (!Disposer.isDisposed(widget)) {
                Disposer.dispose(widget)
            }
            prefs.autoRefresh = originalAutoRefresh
            prefs.refreshInterval = originalInterval
        }
    }
}
