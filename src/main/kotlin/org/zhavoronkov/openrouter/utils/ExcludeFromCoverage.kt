package org.zhavoronkov.openrouter.utils

/**
 * Takes the annotated function, constructor or class out of the Kover report, on purpose and for
 * the [reason] given: code no test should run - it opens a dialog or a browser, reaches the
 * network, or only hands a component to the platform. Kover reads it through `annotatedBy` in
 * build.gradle.kts; TESTING.md lists the rules for using it.
 */
@Retention(AnnotationRetention.BINARY)
@Target(
    AnnotationTarget.CLASS,
    AnnotationTarget.FUNCTION,
    AnnotationTarget.CONSTRUCTOR,
    AnnotationTarget.PROPERTY_GETTER,
    AnnotationTarget.PROPERTY_SETTER
)
annotation class ExcludeFromCoverage(val reason: String)
