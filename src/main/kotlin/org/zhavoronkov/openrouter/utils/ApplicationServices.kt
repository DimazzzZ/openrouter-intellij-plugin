package org.zhavoronkov.openrouter.utils

import com.intellij.openapi.application.Application
import com.intellij.openapi.application.ApplicationManager

/**
 * Resolves an application-level service, answering `null` instead of throwing when there is no
 * application to resolve it from.
 *
 * The idiom this replaces looked safe but was not:
 *
 * ```kotlin
 * try { SomeService.getInstance() } catch (e: IllegalStateException) { null }
 * ```
 *
 * `SomeService.getInstance()` is `ApplicationManager.getApplication().getService(...)`, and
 * `getApplication()` returns `null` when no application is up. Calling `getService` on it raises a
 * `NullPointerException`, which that `catch` does not cover - so the fallback the surrounding code
 * promised was unreachable and the exception escaped instead. Checking for the application first is
 * what makes the fallback real; the `IllegalStateException` arm stays for the case the platform IS
 * up but the service is not yet initialised.
 */
internal fun <T : Any> applicationServiceOrNull(serviceClass: Class<T>): T? {
    val application = ApplicationManager.getApplication() ?: return null
    return serviceOf(application, serviceClass)
}

@ExcludeFromCoverage("the platform refusing a service while it starts or shuts down, which no test can stage")
private fun <T : Any> serviceOf(application: Application, serviceClass: Class<T>): T? = try {
    application.getService(serviceClass)
} catch (e: IllegalStateException) {
    PluginLogger.Service.debug("Service ${serviceClass.simpleName} not available: ${e.message}")
    null
}
