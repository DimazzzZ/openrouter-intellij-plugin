import org.jetbrains.intellij.tasks.PublishPluginTask

plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "2.1.10"
    id("org.jetbrains.intellij") version "1.17.4"
    id("io.gitlab.arturbosch.detekt") version "1.23.7"
}

group = project.findProperty("pluginGroup") ?: "org.zhavoronkov"
version = project.findProperty("pluginVersion") ?: "0.3.0"

// Configure Java toolchain to use Java 17 specifically
java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("com.squareup.okhttp3:okhttp:4.11.0")
    implementation("com.google.code.gson:gson:2.10.1")

    // Use IntelliJ's bundled coroutines library to avoid classloader conflicts
    compileOnly("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")

    // Embedded HTTP server for AI Assistant integration
    implementation("org.eclipse.jetty:jetty-server:11.0.18")
    implementation("org.eclipse.jetty:jetty-servlet:11.0.18")
    implementation("jakarta.servlet:jakarta.servlet-api:5.0.0")

    // Test dependencies
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.9.2")
    testImplementation("org.junit.jupiter:junit-jupiter-params:5.9.2")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.9.2")
    testImplementation("org.mockito:mockito-core:5.7.0")
    testImplementation("org.mockito:mockito-junit-jupiter:5.7.0")
    testImplementation("org.mockito.kotlin:mockito-kotlin:5.1.0")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.11.0")
    testImplementation("org.assertj:assertj-core:3.24.2")


    // Detekt plugins
    detektPlugins("io.gitlab.arturbosch.detekt:detekt-formatting:1.23.7")
}

// Configure IntelliJ Plugin
intellij {
    version.set(project.findProperty("platformVersion") as String? ?: "2024.1.6")
    type.set(project.findProperty("platformType") as String? ?: "IC")

    plugins.set(listOf(/* Plugin Dependencies */))
}

// Configure Detekt
detekt {
    buildUponDefaultConfig = true
    allRules = false
    config.setFrom("$projectDir/config/detekt/detekt.yml")
    baseline = file("$projectDir/config/detekt/baseline.xml")
}

// Configure publishing token for publishPlugin task
val publishPluginTask = tasks.named<PublishPluginTask>("publishPlugin")
publishPluginTask.configure {
    val publishToken = System.getenv("PUBLISH_TOKEN") ?: project.findProperty("publishToken") as String?
    publishToken?.let { token.set(it) }
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

    jvmTarget = "17"
    ignoreFailures = true
}

tasks {
    // Set JVM compatibility versions
    withType<JavaCompile> {
        sourceCompatibility = "17"
        targetCompatibility = "17"
    }
    withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
        kotlinOptions.jvmTarget = "17"
    }

    // Configure Detekt tasks
    withType<io.gitlab.arturbosch.detekt.Detekt>().configureEach {
        jvmTarget = "17"
        ignoreFailures = true  // Don't fail the build on Detekt issues during development
    }

    // Configure tests
    test {
        useJUnitPlatform()
        systemProperty("java.awt.headless", "true")
        jvmArgs = listOf(
            "-Dnet.bytebuddy.experimental=true",  // For Mockito Java 21+ compatibility
            "--add-opens=java.base/java.lang=ALL-UNNAMED",
            "--add-opens=java.base/java.util=ALL-UNNAMED"
        )
        testLogging {
            events("passed", "skipped", "failed")
        }
    }

    // Configure plugin metadata
    patchPluginXml {
        version.set(project.findProperty("pluginVersion") as String? ?: "0.3.0")
        sinceBuild.set(project.findProperty("pluginSinceBuild") as String? ?: "241")
        untilBuild.set(project.findProperty("pluginUntilBuild") as String? ?: "253.*")
    }
}
