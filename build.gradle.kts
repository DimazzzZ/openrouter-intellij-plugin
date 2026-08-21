import org.jetbrains.intellij.platform.gradle.tasks.VerifyPluginTask.FailureLevel

plugins {
    id("java")
    kotlin("jvm") version "2.1.20"
    id("io.gitlab.arturbosch.detekt") version "1.23.8"
    id("org.jetbrains.kotlinx.kover") version "0.9.1"
    id("org.jetbrains.intellij.platform") version "2.18.1"
}

group = project.findProperty("pluginGroup") ?: "org.zhavoronkov"
version = project.findProperty("pluginVersion") ?: "0.5.0"

// Capture version to a local before using in processResources. Referencing
// `version` directly inside the task action would capture the Project itself,
// which the configuration cache cannot serialize. This is the only thing that
// was blocking CC for this build.
val pluginVersionValue = version.toString()

tasks.processResources {
    // Re-bind to a local inside the task configuration block: a top-level
    // `val` in a .kts script is a field on the script object, so referencing
    // it directly from the action lambda captures the script itself
    // ("cannot serialize Gradle script object references"). Capturing a plain
    // local does not.
    val version = pluginVersionValue
    filesMatching("openrouter.properties") {
        expand("pluginVersion" to version)
    }
}

repositories {
    mavenCentral()
    // IntelliJ Platform Gradle Plugin 2.x repositories
    intellijPlatform {
        defaultRepositories()
    }
}

// Force patched versions of vulnerable transitive dependencies
configurations.all {
    resolutionStrategy {
        force("junit:junit:4.13.1") // CVE-2020-15250
        force("com.squareup.okio:okio-jvm:3.4.0") // CVE-2023-3635
        force("com.fasterxml.jackson.core:jackson-core:2.21.1") // CVE-2025-52999
    }
}

dependencies {
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.google.code.gson:gson:2.11.0")

    // Markdown rendering (flexmark-java)
    implementation("com.vladsch.flexmark:flexmark:0.64.8")
    implementation("com.vladsch.flexmark:flexmark-util:0.64.8")
    implementation("com.vladsch.flexmark:flexmark-ext-tables:0.64.8")
    implementation("com.vladsch.flexmark:flexmark-ext-gfm-strikethrough:0.64.8")
    implementation("com.vladsch.flexmark:flexmark-ext-autolink:0.64.8")
    implementation("com.vladsch.flexmark:flexmark-ext-gfm-tasklist:0.64.8")

    // Embedded HTTP server for AI Assistant integration (Jetty 12)
    implementation("org.eclipse.jetty:jetty-server:12.1.6")
    implementation("org.eclipse.jetty.ee10:jetty-ee10-servlet:12.1.6")
    implementation("jakarta.servlet:jakarta.servlet-api:6.0.0")

    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    // Bridges JUnit 3/4-style tests (e.g. IntelliJ's BasePlatformTestCase,
    // which extends junit.framework.TestCase) onto the JUnit Platform so
    // `platformTest` actually discovers and runs integration tests.
    testRuntimeOnly("org.junit.vintage:junit-vintage-engine:5.11.4")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0") {
        // Exclude the transitive kotlinx-coroutines-core-jvm — IntelliJ's
        // bundled lib/util-8.jar ships a patched version (1.10.1-intellij-5)
        // that includes `runBlockingWithParallelismCompensation`. If the plain
        // 1.9.0 core JAR lands on the classpath, the PathClassLoader resolves
        // `kotlinx.coroutines.BuildersKt` from it (missing the method) before
        // reaching util-8.jar, causing NoSuchMethodError during tearDown.
        exclude(group = "org.jetbrains.kotlinx", module = "kotlinx-coroutines-core")
        exclude(group = "org.jetbrains.kotlinx", module = "kotlinx-coroutines-core-jvm")
        exclude(group = "org.jetbrains.kotlinx", module = "kotlinx-coroutines-bom")
    }
    testImplementation("org.mockito:mockito-core:5.7.0")
    testImplementation("org.mockito:mockito-junit-jupiter:5.7.0")
    testImplementation("org.mockito.kotlin:mockito-kotlin:5.1.0")
    testImplementation("org.assertj:assertj-core:3.27.7")

    // Detekt plugins
    detektPlugins("io.gitlab.arturbosch.detekt:detekt-formatting:1.23.8")

    // IntelliJ Platform dependencies (2.x plugin style)
    intellijPlatform {
        val platformVersion = project.findProperty("platformVersion") as String? ?: "2024.2"
        val platformType = project.findProperty("platformType") as String? ?: "IC"
        
        when (platformType) {
            "IU" -> intellijIdeaUltimate(platformVersion)
            else -> intellijIdeaCommunity(platformVersion)
        }

        // Test framework for plugin tests
        testFramework(org.jetbrains.intellij.platform.gradle.TestFrameworkType.Platform)
    }
}

// Configure Java toolchain to use Java 21 (required by IntelliJ Platform 2024.2+)
java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

// Configure IntelliJ Platform Plugin (2.x)
intellijPlatform {
    pluginConfiguration {
        ideaVersion {
            sinceBuild = project.findProperty("pluginSinceBuild") as String? ?: "242"
            untilBuild = provider { null }  // No upper bound - compatible with all future versions
        }
    }

    pluginVerification {
        // Fail the build on the whole non-public-API surface — not just
        // COMPATIBILITY_PROBLEMS (the default). JetBrains penalizes plugins
        // that reach into internal or experimental platform API.
        //
        // MISSING_DEPENDENCIES and NOT_DYNAMIC are deliberately NOT included:
        // the report lists unavailable *optional* dependencies we do not
        // control, so they would fail spuriously.
        //
        // DEPRECATED_API_USAGES is excluded: deprecations are informational
        // (the API still works), whereas SCHEDULED_FOR_REMOVAL_API_USAGES have
        // a hard deadline. JetBrains's own docs still recommend the deprecated
        // CredentialAttributes constructor, so there's no replacement yet.
        failureLevel = listOf(
            FailureLevel.COMPATIBILITY_PROBLEMS,
            FailureLevel.SCHEDULED_FOR_REMOVAL_API_USAGES,
            FailureLevel.INTERNAL_API_USAGES,
            FailureLevel.EXPERIMENTAL_API_USAGES,
            FailureLevel.OVERRIDE_ONLY_API_USAGES,
            FailureLevel.NON_EXTENDABLE_API_USAGES,
        )

        ides {
            // Three ways to pick what to verify against, fastest first:
            //  -PverifierLocalIde=/path/to/IDE.app  an already-installed IDE
            //                                       (local loop; no download)
            //  -PverifierIdes=IC-2024.2[,IC-2025.1] an explicit, pinned set
            //                                       (PR CI; one IDE is enough
            //                                       to catch API breakage)
            //  neither                              recommended(), i.e. every
            //                                       supported line — thorough
            //                                       but several GB, so it is
            //                                       reserved for releases.
            val localIde = project.findProperty("verifierLocalIde") as String?
            val verifierIdesProperty = project.findProperty("verifierIdes") as String?
            val pinnedIdes = verifierIdesProperty
                ?.split(',')
                ?.map(String::trim)
                ?.filter(String::isNotEmpty)
                .orEmpty()

            // Fail loudly rather than quietly widening scope: if the property
            // was supplied but yields nothing (empty, blank, or a stray comma),
            // silently falling through to recommended() would turn a typo into
            // a multi-GB sweep of every supported line.
            if (verifierIdesProperty != null && pinnedIdes.isEmpty()) {
                throw GradleException(
                    "-PverifierIdes was supplied but resolved to no IDE notations " +
                        "(got \"$verifierIdesProperty\"). Pass e.g. -PverifierIdes=IC-2025.1, " +
                        "or omit it entirely to verify against recommended()."
                )
            }

            when {
                localIde != null -> local(localIde)
                // IPGP 2.18.1 removed the `ide(String)` overload. Explicit,
                // pinned notations now go through `create(Provider<List<String>>)`.
                pinnedIdes.isNotEmpty() -> create(providers.provider { pinnedIdes })
                else -> recommended()
            }
        }
    }

    signing {
        certificateChain = providers.environmentVariable("CERTIFICATE_CHAIN")
        privateKey = providers.environmentVariable("PRIVATE_KEY")
        password = providers.environmentVariable("PRIVATE_KEY_PASSWORD")
    }

    publishing {
        token = providers.environmentVariable("PUBLISH_TOKEN")
    }
}

// Configure Detekt
detekt {
    buildUponDefaultConfig = true
    allRules = false
    config.setFrom("$projectDir/config/detekt/detekt.yml")
    basePath = projectDir.absolutePath
}

tasks {
    // Set JVM compatibility versions
    withType<JavaCompile> {
        sourceCompatibility = "21"
        targetCompatibility = "21"
    }
    withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
        compilerOptions.jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
    }

    withType<io.gitlab.arturbosch.detekt.Detekt>().configureEach {
        jvmTarget = "21"
        // ignoreFailures is deliberately NOT set: lint gates the build.
        reports {
            sarif.required.set(true)
            txt.required.set(true)
            html.required.set(true)
            xml.required.set(false)
        }
    }

    test {
        useJUnitPlatform {
            if (!project.hasProperty("functional")) {
                excludeTags("functional")
            }
        }
        filter {
            // Platform tests (BasePlatformTestCase subclasses) need IntelliJ's
            // shared TestApplication, which only the `platformTest` task sets
            // up. Exclude them here so the fast unit task doesn't try (and fail)
            // to run them.
            excludeTestsMatching("*PlatformTest")
            excludeTestsMatching("*SmokeTest")
        }
        systemProperty("openrouter.testMode", "true")
        systemProperty("java.awt.headless", "true")

        maxParallelForks = Runtime.getRuntime().availableProcessors().coerceAtMost(4)
        forkEvery = 100
        maxHeapSize = "1g"

        // Mockito needs these on JDK 21; ByteBuddy experimental unblocks
        // inline mock making, and the --add-opens calls let Mockito reach into
        // java.base internals it reflects on.
        jvmArgs(
            "-Dnet.bytebuddy.experimental=true",
            "--add-opens=java.base/java.lang=ALL-UNNAMED",
            "--add-opens=java.base/java.util=ALL-UNNAMED"
        )

        reports {
            junitXml.required.set(true)
            html.required.set(true)
        }
    }

    register<Test>("functionalTest") {
        description = "Runs functional/integration tests that require external dependencies"
        group = "verification"

        useJUnitPlatform {
            includeTags("functional")
        }
        systemProperty("openrouter.testMode", "true")
        maxParallelForks = 1

        reports {
            junitXml.required.set(true)
            html.required.set(true)
        }
    }

    named("check") {
        dependsOn("platformTest")
    }
}

intellijPlatformTesting {
    testIde {
        register("platformTest") {
            task {
                description = "Runs IntelliJ Platform tests that need the shared TestApplication."
                group = "verification"

                useJUnitPlatform()
                filter {
                    includeTestsMatching("*PlatformTest")
                    includeTestsMatching("*SmokeTest")
                }
                systemProperty("openrouter.testMode", "true")
                maxParallelForks = 1

                reports {
                    junitXml.required.set(true)
                    html.required.set(true)
                }
            }
        }
    }
}

// Configure Kover code coverage exclusions
kover {
    reports {
        filters {
            excludes {
                classes(
                    "org.zhavoronkov.openrouter.ui.*",
                    "org.zhavoronkov.openrouter.ui.*\$*",
                    "org.zhavoronkov.openrouter.service.*Service",
                    "org.zhavoronkov.openrouter.service.*Service\$*",
                    "org.zhavoronkov.openrouter.settings.*",
                    "org.zhavoronkov.openrouter.settings.*\$*",
                    "org.zhavoronkov.openrouter.startup.*",
                    "org.zhavoronkov.openrouter.actions.*"
                )
            }
        }
    }
}
