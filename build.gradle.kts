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

    // Test dependencies
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation("org.mockito:mockito-core:5.7.0")
    testImplementation("org.mockito:mockito-junit-jupiter:5.7.0")
    testImplementation("org.mockito.kotlin:mockito-kotlin:5.1.0")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
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

    // Configure Detekt tasks
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

    // Configure tests
    test {
        useJUnitPlatform()
        systemProperty("java.awt.headless", "true")
        systemProperty("openrouter.testMode", "true")
        jvmArgs = listOf(
            "-Dnet.bytebuddy.experimental=true",  // For Mockito Java 21+ compatibility
            "--add-opens=java.base/java.lang=ALL-UNNAMED",
            "--add-opens=java.base/java.util=ALL-UNNAMED",
            "-Djava.util.logging.config.file=${project.projectDir}/src/test/resources/test-log.properties"
        )
        testLogging {
            events("passed", "skipped", "failed")
        }
    }
}
