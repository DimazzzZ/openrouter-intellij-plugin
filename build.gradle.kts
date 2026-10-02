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
        // `openrouter.debug` turns on PluginLogger.Service.debug, which logs which key each
        // endpoint actually authenticates with (truncated preview only, never the whole key).
        // Off in a released build; on here because runIde is the diagnostic surface.
        jvmArgs("-Duser.language=en", "-Duser.country=US", "-Dopenrouter.debug=true")
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
                // Code no test should run, each marked where it is with its reason; see TESTING.md.
                annotatedBy("org.zhavoronkov.openrouter.utils.ExcludeFromCoverage")
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
                    // RouterDefaults* joins its siblings below: the Configurable is pure platform
                    // glue and the panel is a UI-DSL v2 form that resolves OpenRouterSettingsService
                    // in a field initializer, so neither constructs under the fast :test task. The
                    // logic they read - RouterCatalog, RouterRequestBuilder - is NOT excluded.
                    "org.zhavoronkov.openrouter.settings.RouterDefaultsConfigurable",
                    "org.zhavoronkov.openrouter.settings.RouterDefaultsConfigurable\$*",
                    "org.zhavoronkov.openrouter.settings.RouterDefaultsSettingsPanel",
                    "org.zhavoronkov.openrouter.settings.RouterDefaultsSettingsPanel\$*",
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
                    // Shares ProviderRoutingSettingsPanel.kt with the panel above but is a separate
                    // top-level class, so the pattern above does not reach it: a DialogWrapper, same
                    // category as every other dialog listed here.
                    "org.zhavoronkov.openrouter.settings.ProviderChooserDialog",
                    "org.zhavoronkov.openrouter.settings.ProviderChooserDialog\$*",
                    // Swing table column/toolbar wiring in the favorites subpackage.
                    "org.zhavoronkov.openrouter.settings.favorites.FavoriteModelsTableColumns",
                    "org.zhavoronkov.openrouter.settings.favorites.FavoriteModelsTableColumns\$*",
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
                    "org.zhavoronkov.openrouter.aiassistant.OpenRouterChatModelProvider\$*",
                    // Chat list view: Swing list/scroll-pane/popup-menu wiring extracted from
                    // ChatPanel in the chat UI redesign seams ticket. Not unit-testable under
                    // the fast :test task (needs a platform runner for JBList/JBScrollPane).
                    "org.zhavoronkov.openrouter.toolwindow.chat.ChatListView",
                    "org.zhavoronkov.openrouter.toolwindow.chat.ChatListView\$*",
                    "org.zhavoronkov.openrouter.toolwindow.chat.ChatListCellRenderer",
                    "org.zhavoronkov.openrouter.toolwindow.chat.ChatListCellRenderer\$*",
                    // Chat composer: Swing input/token-counter/Send wiring extracted from
                    // ChatPanel in the chat UI redesign seams ticket. Not unit-testable under
                    // the fast :test task (needs a platform runner for JBTextArea/JBScrollPane).
                    "org.zhavoronkov.openrouter.toolwindow.chat.ChatComposer",
                    "org.zhavoronkov.openrouter.toolwindow.chat.ChatComposer\$*",
                    // ComposerLayout: Swing LayoutManager that measures real components and
                    // places them via ComposerLayoutPolicy, plus the combo renderer that
                    // delegates to it. Not unit-testable under the fast :test task (needs a
                    // platform runner for JComponent/ComboBox). ComposerLayoutPolicy and
                    // MiddleEllipsis, the pure logic these consult, are NOT excluded here.
                    "org.zhavoronkov.openrouter.toolwindow.composer.ComposerLayout",
                    "org.zhavoronkov.openrouter.toolwindow.composer.ComposerLayout\$*",
                    "org.zhavoronkov.openrouter.toolwindow.composer.MiddleEllipsisComboRenderer",
                    "org.zhavoronkov.openrouter.toolwindow.composer.MiddleEllipsisComboRenderer\$*",
                    // Chat conversation view: Swing messages-area/loading-indicator wiring
                    // extracted from ChatPanel in the chat UI redesign seams ticket. Not
                    // unit-testable under the fast :test task (needs a platform runner for
                    // JBScrollPane/JEditorPane).
                    "org.zhavoronkov.openrouter.toolwindow.chat.ChatConversationView",
                    "org.zhavoronkov.openrouter.toolwindow.chat.ChatConversationView\$*",
                    // Chat toolbar: single ActionToolbar strip replacing the three-row
                    // header/back/model chrome, added in the chat UI redesign. Not
                    // unit-testable under the fast :test task (needs a platform runner
                    // for ActionManager/ActionToolbar).
                    "org.zhavoronkov.openrouter.toolwindow.chat.ChatToolbar",
                    "org.zhavoronkov.openrouter.toolwindow.chat.ChatToolbar\$*",
                    // Send-parameters popup: UI DSL v2 form built from JBPopupFactory,
                    // added in Task 10 of the chat UI redesign to replace the
                    // FlowLayout-in-BoxLayout row that silently clipped wrapped
                    // controls. Not unit-testable under the fast :test task (needs a
                    // platform runner for ComboBox/JBPopupFactory).
                    "org.zhavoronkov.openrouter.toolwindow.chat.ChatParamsPopup",
                    "org.zhavoronkov.openrouter.toolwindow.chat.ChatParamsPopup\$*",
                    // Message rendering: Swing views added in Task 12 of the chat UI
                    // redesign to measure message heights at the real viewport width
                    // and replace the role-prefix rows with the D6 visuals. Not
                    // unit-testable under the fast :test task (needs a platform
                    // runner for JEditorPane/JScrollPane/Scrollable). MessageSegment
                    // and MessageSegmenter, the pure logic these consume, are NOT
                    // excluded here.
                    "org.zhavoronkov.openrouter.toolwindow.chat.MessagesPanel",
                    "org.zhavoronkov.openrouter.toolwindow.chat.MessagesPanel\$*",
                    "org.zhavoronkov.openrouter.toolwindow.chat.WrappingEditorPane",
                    "org.zhavoronkov.openrouter.toolwindow.chat.WrappingEditorPane\$*",
                    "org.zhavoronkov.openrouter.toolwindow.chat.MessageView",
                    "org.zhavoronkov.openrouter.toolwindow.chat.MessageView\$*",
                    "org.zhavoronkov.openrouter.toolwindow.chat.CodeSegmentView",
                    "org.zhavoronkov.openrouter.toolwindow.chat.CodeSegmentView\$*",
                    // Task 13: code/table segments scroll horizontally inside their
                    // own segment instead of clipping. HorizontallyScrollingPane and
                    // the wheel-forwarding helper are top-level declarations in
                    // CodeSegmentView.kt (the existing CodeSegmentView$* wildcard
                    // above only covers nested classes of CodeSegmentView itself, not
                    // separate top-level ones), and CodeSegmentViewKt is the facade
                    // class Kotlin generates for that file's top-level function. Same
                    // "needs a platform runner for JBScrollPane" reasoning as every
                    // other Swing view in this package.
                    "org.zhavoronkov.openrouter.toolwindow.chat.HorizontallyScrollingPane",
                    "org.zhavoronkov.openrouter.toolwindow.chat.HorizontallyScrollingPane\$*",
                    "org.zhavoronkov.openrouter.toolwindow.chat.CodeSegmentViewKt",
                    // Status tab panel: Swing view extracted from OpenRouterToolWindowContent
                    // in the status tab redesign. Not unit-testable under the fast :test task
                    // (needs a platform runner for JBLabel/JBScrollPane/GridBagLayout wiring).
                    "org.zhavoronkov.openrouter.toolwindow.status.StatusTabPanel",
                    "org.zhavoronkov.openrouter.toolwindow.status.StatusTabPanel\$*",
                    // Defect D fix: the Scrollable content view createContentPanel() wraps in a
                    // JBScrollPane. Swing view (GridBagLayout/Scrollable wiring) - same reasoning
                    // as org.zhavoronkov.openrouter.toolwindow.chat.MessagesPanel, its sibling on
                    // the chat side of this repo. BreakdownColumnPolicy, the pure logic that
                    // actually decides what fits once this panel hands rowsPanel a real width, is
                    // NOT excluded - it runs under the fast :test task instead.
                    "org.zhavoronkov.openrouter.toolwindow.status.StatusContentPanel",
                    "org.zhavoronkov.openrouter.toolwindow.status.StatusContentPanel\$*",
                    // Task 8: the balance block and its spend sparkline. Swing views needing a
                    // platform runner for JBLabel/BoxLayout/custom-paint - same reasoning as
                    // every other Swing view in this package.
                    "org.zhavoronkov.openrouter.toolwindow.status.BalanceBlock",
                    "org.zhavoronkov.openrouter.toolwindow.status.BalanceBlock\$*",
                    "org.zhavoronkov.openrouter.toolwindow.status.SparklineView",
                    "org.zhavoronkov.openrouter.toolwindow.status.SparklineView\$*",
                    // Task 9: the per-model breakdown block and its period selector. Swing view
                    // needing a platform runner for ComboBox/GridBagLayout - same reasoning as
                    // every other Swing view in this package. AnalyticsBreakdown, the pure
                    // request-building/row-mapping logic it consults, is NOT excluded here - it
                    // runs under the fast :test task instead (see AnalyticsBreakdownTest).
                    "org.zhavoronkov.openrouter.toolwindow.status.BreakdownBlock",
                    "org.zhavoronkov.openrouter.toolwindow.status.BreakdownBlock\$*",
                    // Task 10: the API key spend-cap block. Swing view needing a platform runner
                    // for JBLabel/BoxLayout - same reasoning as every other Swing view in this
                    // package. KeyLimit, the pure logic deciding whether a cap exists and what
                    // `used` pairs with it, is NOT excluded here - it runs under the fast :test
                    // task instead (see KeyLimitTest).
                    "org.zhavoronkov.openrouter.toolwindow.status.KeyLimitBlock",
                    "org.zhavoronkov.openrouter.toolwindow.status.KeyLimitBlock\$*",
                    // Task 11: DEGRADED's explanatory banner. Swing view needing a platform
                    // runner for JBLabel/JButton/JBUI.CurrentTheme - same reasoning as every
                    // other Swing view in this package. ActivationRefreshGate and DegradedSpend,
                    // the pure logic Task 11 adds alongside it, are NOT excluded here - they run
                    // under the fast :test task instead (see their own *Test.kt files).
                    "org.zhavoronkov.openrouter.toolwindow.status.DegradedNoticeBlock",
                    "org.zhavoronkov.openrouter.toolwindow.status.DegradedNoticeBlock\$*",
                    // ChatPanel: the last un-excluded member of the chat view family. A JPanel
                    // mixing ComboBox/JBUI wiring with PathManager-backed chat persistence, all
                    // private, with no seam - same reasoning as every ChatListView/ChatComposer/
                    // MessagesPanel sibling already listed above. ChatParamsState, MessageSegmenter
                    // and the composer policies, the pure logic it consults, are NOT excluded.
                    "org.zhavoronkov.openrouter.toolwindow.ChatPanel",
                    "org.zhavoronkov.openrouter.toolwindow.ChatPanel\$*",
                    // Status bar widget: an EditorBasedWidget driving JBPopupFactory, BrowserUtil,
                    // Messages and Alarm. Platform surface, not logic - StatusBarStatsFormatter,
                    // which owns the numbers it renders, is NOT excluded and is covered instead.
                    "org.zhavoronkov.openrouter.statusbar.OpenRouterStatusBarWidget",
                    "org.zhavoronkov.openrouter.statusbar.OpenRouterStatusBarWidget\$*",
                    // AI Assistant integration helper: every method resolves an application
                    // service or reaches for ActionManager/ShowSettingsUtil. Platform wiring.
                    "org.zhavoronkov.openrouter.integration.AIAssistantIntegrationHelper",
                    "org.zhavoronkov.openrouter.integration.AIAssistantIntegrationHelper\$*",
                    // Logging facade. Every branch is gated on one of two `by lazy` flags read from
                    // system properties, so within a single JVM each `if` has exactly one reachable
                    // side; the rest are `logger?.info(...)` null-edges that cannot fire because
                    // createLogger only returns null when the platform itself throws. Nothing here
                    // is domain logic - it delegates to com.intellij...Logger.
                    "org.zhavoronkov.openrouter.utils.PluginLogger",
                    "org.zhavoronkov.openrouter.utils.PluginLogger\$*",
                    // Favorites toolbar: AnAction subclasses, the same surface already excluded as
                    // `...openrouter.actions.*`. Listed by their own names on purpose - they live in
                    // FavoriteModelsToolbarActions.kt, but no class of that name exists, so a
                    // filename-shaped pattern would silently match nothing.
                    "org.zhavoronkov.openrouter.settings.favorites.AddWithPresetAction",
                    "org.zhavoronkov.openrouter.settings.favorites.AddWithPresetAction\$*",
                    "org.zhavoronkov.openrouter.settings.favorites.CapabilitiesFilterAction",
                    "org.zhavoronkov.openrouter.settings.favorites.CapabilitiesFilterAction\$*",
                    "org.zhavoronkov.openrouter.settings.favorites.ChoiceFilterAction",
                    "org.zhavoronkov.openrouter.settings.favorites.ChoiceFilterAction\$*",
                    "org.zhavoronkov.openrouter.settings.favorites.ClearFiltersAction",
                    "org.zhavoronkov.openrouter.settings.favorites.ClearFiltersAction\$*",
                    "org.zhavoronkov.openrouter.settings.favorites.FavoritesOnlyToggleAction",
                    "org.zhavoronkov.openrouter.settings.favorites.FavoritesOnlyToggleAction\$*",
                    "org.zhavoronkov.openrouter.settings.favorites.MoveFavoriteAction",
                    "org.zhavoronkov.openrouter.settings.favorites.MoveFavoriteAction\$*",
                    "org.zhavoronkov.openrouter.settings.favorites.PresetsAction",
                    "org.zhavoronkov.openrouter.settings.favorites.PresetsAction\$*",
                    "org.zhavoronkov.openrouter.settings.favorites.RefreshCatalogAction",
                    "org.zhavoronkov.openrouter.settings.favorites.RefreshCatalogAction\$*",
                    // Jetty lifecycle: binds a port, builds handlers, starts and stops the server.
                    // The request handling it wires up is covered through the servlets themselves.
                    "org.zhavoronkov.openrouter.proxy.OpenRouterProxyServer",
                    "org.zhavoronkov.openrouter.proxy.OpenRouterProxyServer\$*",
                    "org.zhavoronkov.openrouter.services.OpenRouterProxyService",
                    "org.zhavoronkov.openrouter.services.OpenRouterProxyService\$*",
                    // Plugin load/unload callbacks - IntelliJ lifecycle wiring, same category as
                    // the startup activities already excluded above.
                    "org.zhavoronkov.openrouter.listeners.PluginLifecycleListener",
                    "org.zhavoronkov.openrouter.listeners.PluginLifecycleListener\$*",
                    // Tool window and status bar factories: each builds its component and hands it to
                    // the platform. What they build is covered through the components themselves.
                    "org.zhavoronkov.openrouter.toolwindow.OpenRouterToolWindowFactory",
                    "org.zhavoronkov.openrouter.toolwindow.OpenRouterToolWindowFactory\$*",
                    "org.zhavoronkov.openrouter.statusbar.OpenRouterStatusBarWidgetFactory",
                    "org.zhavoronkov.openrouter.statusbar.OpenRouterStatusBarWidgetFactory\$*",
                    // Saves a preset through the application's OpenRouterService and PresetCopyService
                    // singletons, with no seam to stand in for either; PresetDraft.config(), what it
                    // sends, is covered on its own.
                    "org.zhavoronkov.openrouter.settings.presets.PresetWriter",
                    "org.zhavoronkov.openrouter.settings.presets.PresetWriter\$*",
                    // Extension-point fan-out to other plugins: resolves the EP area and forwards to
                    // whatever is registered. Nothing registers under the fast :test task, so the
                    // loop body is unreachable there.
                    "org.zhavoronkov.openrouter.services.BalanceProviderNotifier",
                    "org.zhavoronkov.openrouter.services.BalanceProviderNotifier\$*"
                )
            }
        }
    }
}
