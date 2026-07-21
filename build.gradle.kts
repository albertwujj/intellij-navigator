import java.util.Properties
import org.jetbrains.intellij.platform.gradle.tasks.RunIdeTask
import org.jetbrains.intellij.platform.gradle.tasks.aware.SplitModeAware

plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "1.9.22"
    id("org.jetbrains.kotlin.plugin.serialization") version "1.9.22"
    id("org.jetbrains.intellij.platform") version "2.2.1"
}

group = providers.gradleProperty("pluginGroup").get()
version = providers.gradleProperty("pluginVersion").get()

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    intellijPlatform {
        pycharmCommunity(providers.gradleProperty("platformVersion").get())
        bundledPlugin("PythonCore")
        bundledPlugin("Git4Idea")
        pluginVerifier()
        zipSigner()
        testFramework(org.jetbrains.intellij.platform.gradle.TestFrameworkType.Platform)
    }

    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.2")

    testImplementation("junit:junit:4.13.2")
}

kotlin {
    jvmToolchain(21)
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

intellijPlatform {
    pluginConfiguration {
        name = providers.gradleProperty("pluginName")
        version = providers.gradleProperty("pluginVersion")

        ideaVersion {
            sinceBuild = "241"
            untilBuild = "251.*"
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

    pluginVerification {
        ides {
            recommended()
        }
    }
}

val frontendProperties = Properties().apply {
    file("frontend-plugin/gradle.properties").inputStream().use(::load)
}
val frontendVersion = frontendProperties.getProperty("pluginVersion")
val frontendPluginZip = file("frontend-plugin/build/distributions/intellij-navigator-frontend-$frontendVersion.zip")
val frontendPluginDirectoryName = "intellij-navigator-frontend"
val splitModeProjectPath = providers.gradleProperty("splitModeProjectPath")
val jetBrainsClientProfilesRoot = file("${System.getProperty("user.home")}/Library/Application Support/JetBrains")

fun Project.requireFrontendPluginZip() {
    if (!frontendPluginZip.exists()) {
        throw GradleException(
            "Frontend plugin zip not found at ${frontendPluginZip.path}. Build it first: cd frontend-plugin && ./gradlew buildPlugin",
        )
    }
}

fun Project.unpackFrontendPlugin(targetDirectory: File) {
    requireFrontendPluginZip()

    targetDirectory.mkdirs()
    delete(targetDirectory.resolve(frontendPluginDirectoryName))
    copy {
        from(zipTree(frontendPluginZip))
        into(targetDirectory)
    }
}

fun RunIdeTask.installFrontendPlugin(targetDirectory: Provider<org.gradle.api.file.Directory>) {
    doFirst {
        val sandboxPluginsDirectory = targetDirectory.get().asFile
        project.unpackFrontendPlugin(sandboxPluginsDirectory)
    }
}

tasks.register("installFrontendPluginForSplitMode") {
    group = "intellij platform"
    description = "Installs the frontend plugin into the local JetBrains Client profile used by split mode."
    doLast {
        val clientProfileDirectories = jetBrainsClientProfilesRoot
            .listFiles()
            ?.filter { it.isDirectory && it.name.startsWith("JetBrainsClient") }
            ?.sortedBy { it.name }
            .orEmpty()

        if (clientProfileDirectories.isEmpty()) {
            logger.lifecycle(
                "No JetBrains Client profile found under ${jetBrainsClientProfilesRoot.path}; skipping frontend install. Launch split mode once to create the client profile, then rerun installFrontendPluginForSplitMode.",
            )
            return@doLast
        }

        clientProfileDirectories.forEach { profileDirectory ->
            project.unpackFrontendPlugin(profileDirectory.resolve("plugins"))
        }
    }
}

// Monolithic runIde keeps loading both separate plugins into one sandbox.
tasks.named<RunIdeTask>("runIde") {
    val sandboxPlugins = layout.buildDirectory.dir("idea-sandbox/PC-${providers.gradleProperty("platformVersion").get()}/plugins")
    installFrontendPlugin(sandboxPlugins)
    jvmArgumentProviders.add(CommandLineArgumentProvider {
        listOf("-Didea.plugin.in.sandbox.mode=false")
    })
}

intellijPlatformTesting.runIde.register("runIdeSplitMode") {
    splitMode = true
    splitModeTarget = SplitModeAware.SplitModeTarget.BACKEND
    task {
        dependsOn("installFrontendPluginForSplitMode")
        sandboxConfigFrontendDirectory.convention(sandboxConfigDirectory.map { it.dir("frontend") })
        sandboxPluginsFrontendDirectory.convention(sandboxPluginsDirectory.map { it.dir("frontend") })
        sandboxSystemFrontendDirectory.convention(sandboxSystemDirectory.map { it.dir("frontend") })
        sandboxLogFrontendDirectory.convention(sandboxLogDirectory.map { it.dir("frontend") })
        installFrontendPlugin(sandboxPluginsFrontendDirectory)
        argumentProviders.add(CommandLineArgumentProvider {
            splitModeProjectPath.orNull?.let(::listOf) ?: emptyList()
        })
    }
}

tasks {
    wrapper {
        gradleVersion = "8.5"
    }
}
