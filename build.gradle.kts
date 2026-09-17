import org.jetbrains.intellij.platform.gradle.tasks.VerifyPluginTask
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
        val platformVersion = project.findProperty("platformVersion") as String? ?: "2025.3.6"
        // Since 2025.3, JetBrains unified the distribution — use intellijIdea()
        // instead of the removed intellijIdeaCommunity()/intellijIdeaUltimate() split.
        intellijIdea(platformVersion)

        // Test framework for plugin tests
        testFramework(org.jetbrains.intellij.platform.gradle.TestFrameworkType.Platform)
    }
}

// Configure Java toolchain to use Java 21 (required by IntelliJ Platform 2024.2+; still current for 2025.3)
java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

// Configure IntelliJ Platform Plugin (2.x)
intellijPlatform {
    // buildSearchableOptions launches a headless IDE (~1 min) to index the
    // settings pages for Search Everywhere. Only the published zip needs it,
    // so it is opt-in via -Prelease (release.yml passes it); local
    // buildPlugin / verifyPlugin runs skip the IDE launch entirely.
    buildSearchableOptions = project.hasProperty("release")

    pluginConfiguration {
        ideaVersion {
            sinceBuild = project.findProperty("pluginSinceBuild") as String? ?: "253"
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
        // Android subsystems are irrelevant to this plugin and only add
        // verifier work.
        subsystemsToCheck = VerifyPluginTask.Subsystems.WITHOUT_ANDROID

        failureLevel = listOf(
            FailureLevel.COMPATIBILITY_PROBLEMS,
            FailureLevel.SCHEDULED_FOR_REMOVAL_API_USAGES,
            FailureLevel.INTERNAL_API_USAGES,
            FailureLevel.EXPERIMENTAL_API_USAGES,
            FailureLevel.OVERRIDE_ONLY_API_USAGES,
            FailureLevel.NON_EXTENDABLE_API_USAGES,
        )

        ides {
            // What to verify against:
            //  verifierIdes (gradle.properties)     the default for everyone —
            //                                       local runs, PR CI and
            //                                       releases share one pinned
            //                                       build so they cannot drift.
            //                                       Override per run with
            //                                       -PverifierIdes=IU-2026.2.2
            //  -PverifierLocalIde=/path/to/IDE.app  an already-installed IDE,
            //                                       for checking a newer IDE by
            //                                       hand (`fast-build.sh verify
            //                                       local`). No IDE download,
            //                                       but the verifier still
            //                                       fetches that IDE's bundled
            //                                       -plugin dependencies once.
            //  neither                              recommended(), i.e. every
            //                                       supported line — only
            //                                       reachable if verifierIdes
            //                                       is removed from
            //                                       gradle.properties.
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

    named("check") {
        dependsOn("platformTest")
    }

    // Normalize the sandbox IDE locale to en_US. Our host locale is en_RU,
    // which makes the platform's Elevation service log a noisy
    // MissingResourceException (messages.ElevationBundle has no en_RU bundle)
    // every time Settings is opened. Pinning en_US silences that dev-only
    // noise without affecting the shipped plugin.
    runIde {
        jvmArgs("-Duser.language=en", "-Duser.country=US")
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
                // CI has no display, so headless is what these tests really run
                // under there. Matching it locally keeps a headless-only failure
                // (e.g. JComponent.setDragEnabled(true) throwing HeadlessException)
                // from reaching CI.
                systemProperty("java.awt.headless", "true")
                maxParallelForks = 1

                reports {
                    junitXml.required.set(true)
                    html.required.set(true)
                }
            }
        }
        register("functionalTest") {
            task {
                description = "Runs functional/integration tests (@Tag(\"functional\")). " +
                    "These exercise production code that transitively touches IntelliJ " +
                    "platform classes (e.g. PluginLogger -> com.intellij.openapi.diagnostic.Logger), " +
                    "so they need the shared TestApplication classpath that only a " +
                    "testIde runner provisions — a plain Test task cannot resolve those classes."
                group = "verification"

                useJUnitPlatform {
                    includeTags("functional")
                }
                // Functional tests are opt-in (see TESTING.md): they call real
                // HTTP endpoints / a MockWebServer port and require a local .env
                // with OPENROUTER_API_KEY, so they must not run in CI. Gate on
                // -Pfunctional exactly like the fast `test` task does; without it
                // the task is skipped instead of erroring in @BeforeAll.
                // `project` cannot be touched at execution time under the
                // configuration cache, so capture the flag now and close over it.
                val functionalEnabled = project.hasProperty("functional")
                onlyIf { functionalEnabled }
                systemProperty("openrouter.testMode", "true")
                systemProperty("java.awt.headless", "true")
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
                    // Swing views, dialogs, and table renderers (pure UI, not unit-testable).
                    // Named explicitly so pure-logic files in the same packages count toward coverage.
                    "org.zhavoronkov.openrouter.ui.ModelVariantChipRenderer",
                    "org.zhavoronkov.openrouter.ui.ModelVariantChipRenderer\$*",
                    "org.zhavoronkov.openrouter.ui.OpenRouterStatsPopup",
                    "org.zhavoronkov.openrouter.ui.OpenRouterStatsPopup\$*",
                    "org.zhavoronkov.openrouter.ui.SetupWizardDialog",
                    "org.zhavoronkov.openrouter.ui.SetupWizardDialog\$*",
                    "org.zhavoronkov.openrouter.ui.VariantChipTableCellRenderer",
                    "org.zhavoronkov.openrouter.ui.VariantChipTableCellRenderer\$*",
                    // IntelliJ Configurable glue and Swing settings panels (framework wiring / views).
                    "org.zhavoronkov.openrouter.settings.ApiKeyDialogManager",
                    "org.zhavoronkov.openrouter.settings.ApiKeyDialogManager\$*",
                    "org.zhavoronkov.openrouter.settings.FavoriteModelsConfigurable",
                    "org.zhavoronkov.openrouter.settings.FavoriteModelsConfigurable\$*",
                    "org.zhavoronkov.openrouter.settings.FavoriteModelsSettingsPanel",
                    "org.zhavoronkov.openrouter.settings.FavoriteModelsSettingsPanel\$*",
                    "org.zhavoronkov.openrouter.settings.OpenRouterConfigurable",
                    "org.zhavoronkov.openrouter.settings.OpenRouterConfigurable\$*",
                    "org.zhavoronkov.openrouter.settings.OpenRouterSettingsPanel",
                    "org.zhavoronkov.openrouter.settings.OpenRouterSettingsPanel\$*",
                    "org.zhavoronkov.openrouter.settings.PresetsConfigurable",
                    "org.zhavoronkov.openrouter.settings.PresetsConfigurable\$*",
                    "org.zhavoronkov.openrouter.settings.PresetsSettingsPanel",
                    "org.zhavoronkov.openrouter.settings.PresetsSettingsPanel\$*",
                    "org.zhavoronkov.openrouter.settings.ProviderRoutingConfigurable",
                    "org.zhavoronkov.openrouter.settings.ProviderRoutingConfigurable\$*",
                    "org.zhavoronkov.openrouter.settings.ProviderRoutingSettingsPanel",
                    "org.zhavoronkov.openrouter.settings.ProviderRoutingSettingsPanel\$*",
                    // Swing table column/toolbar wiring in the favorites subpackage.
                    "org.zhavoronkov.openrouter.settings.favorites.FavoriteModelsTableColumns",
                    "org.zhavoronkov.openrouter.settings.favorites.FavoriteModelsTableColumns\$*",
                    "org.zhavoronkov.openrouter.settings.favorites.FavoriteModelsToolbarActions",
                    "org.zhavoronkov.openrouter.settings.favorites.FavoriteModelsToolbarActions\$*",
                    // Startup activities and actions are IntelliJ lifecycle/command wiring.
                    "org.zhavoronkov.openrouter.startup.*",
                    "org.zhavoronkov.openrouter.startup.*\$*",
                    "org.zhavoronkov.openrouter.actions.*",
                    "org.zhavoronkov.openrouter.actions.*\$*",
                    // Phase 0.5 EXCLUDE: async/EDT/Swing orchestration whose bodies are
                    // ApplicationManager.invokeLater + Dispatchers.IO/Main launches, ServerSocket,
                    // BrowserUtil, JTable/JButton/JBLabel wiring, or Messages dialogs. Not unit-
                    // testable under the fast :test task (need a platform runner). The pure-logic
                    // helpers in the same packages remain in scope.
                    "org.zhavoronkov.openrouter.settings.ApiKeyManager",
                    "org.zhavoronkov.openrouter.settings.ApiKeyManager\$*",
                    "org.zhavoronkov.openrouter.settings.IntellijApiKeyManager",
                    "org.zhavoronkov.openrouter.settings.IntellijApiKeyManager\$*",
                    "org.zhavoronkov.openrouter.settings.ModelsDataManager",
                    "org.zhavoronkov.openrouter.settings.ModelsDataManager\$*",
                    "org.zhavoronkov.openrouter.settings.ProxyServerManager",
                    "org.zhavoronkov.openrouter.settings.ProxyServerManager\$*",
                    "org.zhavoronkov.openrouter.ui.PkceAuthHandler",
                    "org.zhavoronkov.openrouter.ui.PkceAuthHandler\$*",
                    // Phase B3: AI Assistant provider integration.
                    // openSettings() calls ShowSettingsUtil.getInstance().showSettingsDialog(...),
                    // which requires the IntelliJ platform (see platformTest task). Exception
                    // handlers in validateModelConfiguration() and onProviderStateChanged() are
                    // defensive paths that only execute if mocked services throw — not realistic
                    // in unit tests. The pure configuration/validation logic on this class is
                    // covered by OpenRouterModelConfigurationProviderTest. The no-arg constructor
                    // is also platform-bound (calls OpenRouterSettingsService.getInstance()).
                    "org.zhavoronkov.openrouter.aiassistant.OpenRouterModelConfigurationProvider",
                    "org.zhavoronkov.openrouter.aiassistant.OpenRouterModelConfigurationProvider\$*",
                    // OpenRouterChatModelProvider: sendChatRequest/sendCompletionRequest configured
                    // branches call makeOpenRouterRequest, which opens an OkHttp connection to
                    // openrouter.ai. Network-bound — belongs to functional/integration testing, not
                    // the fast :test task. The pure-logic surface (request body creation, response
                    // parsing, token estimation, streaming flag, not-configured short-circuits) is
                    // covered by OpenRouterChatModelProviderTest + OpenRouterChatModelProviderLogicTest.
                    "org.zhavoronkov.openrouter.aiassistant.OpenRouterChatModelProvider",
                    "org.zhavoronkov.openrouter.aiassistant.OpenRouterChatModelProvider\$*"
                )
            }
        }
    }
}
