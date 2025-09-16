plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "1.9.10"
    id("org.jetbrains.intellij") version "1.17.3"
}

group = project.findProperty("pluginGroup") ?: "com.openrouter"
version = project.findProperty("pluginVersion") ?: "1.0.0"

repositories {
    mavenCentral()
}

dependencies {
    implementation("com.squareup.okhttp3:okhttp:4.11.0")
    implementation("com.google.code.gson:gson:2.10.1")
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.9.2")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.9.2")
}

// Configure Gradle IntelliJ Plugin
intellij {
    version.set("2023.2.5")
    type.set("IC") // Target IDE Platform
    
    plugins.set(listOf(/* Plugin Dependencies */))
}

tasks {
    // Set the JVM compatibility versions
    withType<JavaCompile> {
        sourceCompatibility = "17"
        targetCompatibility = "17"
    }
    withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
        kotlinOptions.jvmTarget = "17"
    }

    patchPluginXml {
        version.set(project.findProperty("pluginVersion") as String? ?: "1.0.0")
        sinceBuild.set(project.findProperty("pluginSinceBuild") as String? ?: "232")
        untilBuild.set(project.findProperty("pluginUntilBuild") as String? ?: "252.*")

        // Plugin metadata from gradle.properties
        pluginId.set(project.findProperty("pluginId") as String? ?: "com.openrouter.intellij-plugin")
        pluginDescription.set(project.findProperty("pluginDescription") as String? ?: "OpenRouter plugin for IntelliJ IDEA")
    }

    signPlugin {
        certificateChain.set(System.getenv("CERTIFICATE_CHAIN"))
        privateKey.set(System.getenv("PRIVATE_KEY"))
        password.set(System.getenv("PRIVATE_KEY_PASSWORD"))
    }

    publishPlugin {
        token.set(System.getenv("PUBLISH_TOKEN"))
    }

    test {
        useJUnitPlatform()
    }

    // Custom task to update version
    register("updateVersion") {
        description = "Updates the plugin version in gradle.properties"
        group = "versioning"

        doLast {
            val newVersion = providers.gradleProperty("newVersion").orNull
                ?: throw GradleException("Please specify newVersion: ./gradlew updateVersion -PnewVersion=1.2.0")

            val gradlePropsFile = layout.projectDirectory.file("gradle.properties").asFile
            val content = gradlePropsFile.readText()
            val updatedContent = content.replace(
                Regex("pluginVersion = .*"),
                "pluginVersion = $newVersion"
            )
            gradlePropsFile.writeText(updatedContent)

            println("Updated plugin version to $newVersion in gradle.properties")
            println("Don't forget to update CHANGELOG.md and commit the changes!")
        }
    }

    // Task to show current version info
    register("showVersion") {
        description = "Shows current plugin version and metadata"
        group = "versioning"

        doLast {
            val pluginName = providers.gradleProperty("pluginName").orNull
            val pluginVersion = providers.gradleProperty("pluginVersion").orNull
            val pluginGroup = providers.gradleProperty("pluginGroup").orNull
            val pluginId = providers.gradleProperty("pluginId").orNull
            val pluginRepositoryUrl = providers.gradleProperty("pluginRepositoryUrl").orNull
            val pluginSinceBuild = providers.gradleProperty("pluginSinceBuild").orNull
            val pluginUntilBuild = providers.gradleProperty("pluginUntilBuild").orNull

            println("Plugin Information:")
            println("==================")
            println("Name: $pluginName")
            println("Version: $pluginVersion")
            println("Group: $pluginGroup")
            println("ID: $pluginId")
            println("Repository: $pluginRepositoryUrl")
            println("Since Build: $pluginSinceBuild")
            println("Until Build: $pluginUntilBuild")
        }
    }
}
