package org.zhavoronkov.openrouter.testing

import com.intellij.testFramework.common.ThreadLeakTracker
import org.junit.jupiter.api.extension.BeforeAllCallback
import org.junit.jupiter.api.extension.ExtensionContext

/**
 * JUnit 5 extension that registers OkHttp and Jetty daemon thread names
 * with IntelliJ's ThreadLeakTracker so they are not flagged as leaks.
 *
 * OkHttp's TaskRunner and Jetty's thread pool create daemon threads that
 * persist across test methods. In @TestInstance(PER_CLASS) tests, these
 * threads are expected to live for the entire class lifecycle.
 *
 * Usage: @ExtendWith(OkHttpLeakSafeExtension::class) on the test class.
 */
class OkHttpLeakSafeExtension : BeforeAllCallback {

    override fun beforeAll(context: ExtensionContext) {
        registerKnownThreadPrefixes()
    }

    companion object {
        private var registered = false

        @Synchronized
        fun registerKnownThreadPrefixes() {
            if (registered) return
            registered = true

            try {
                val field = ThreadLeakTracker::class.java.getDeclaredField("wellKnownOffenders")
                field.isAccessible = true
                @Suppress("UNCHECKED_CAST")
                val offenders = field.get(null) as MutableSet<String>
                offenders.addAll(
                    listOf(
                        "OkHttp TaskRunner",
                        "OkHttp ", // covers "OkHttp openrouter.ai" etc.
                        "MockWebServer",
                        "qtp", // Jetty thread pool threads
                    )
                )
            } catch (_: Exception) {
                // If the field doesn't exist or can't be accessed, silently skip.
                // The extension is best-effort; tests still pass, just with leak warnings.
            }
        }
    }
}
