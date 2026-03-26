plugins {
    id("java")
    kotlin("jvm") version "2.0.21"
    id("io.gitlab.arturbosch.detekt") version "1.23.8"
    id("org.jetbrains.kotlinx.kover") version "0.9.1"
    id("org.jetbrains.intellij.platform") version "2.3.0"
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
    }
}

dependencies {
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.google.code.gson:gson:2.11.0")

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
        ides {
            recommended()
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
    baseline = file("$projectDir/config/detekt/baseline.xml")
    basePath = projectDir.absolutePath
}

// Configure Detekt SARIF reporting task
tasks.register<io.gitlab.arturbosch.detekt.Detekt>("detektSarif") {
    description = "Runs detekt and generates SARIF report for GitHub Code Scanning"
    group = "verification"

    setSource(files("src/main/kotlin"))
    include("**/*.kt")
    exclude("**/test/**", "**/*Test.kt")

    reports {
        sarif.required.set(true)
        sarif.outputLocation.set(file("build/reports/detekt/detekt.sarif"))
        html.required.set(false)
        txt.required.set(true)
        xml.required.set(false)
    }

    jvmTarget = "21"
    basePath = projectDir.absolutePath
    ignoreFailures = true
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
        ignoreFailures = true  // Don't fail the build on Detekt issues during development
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
