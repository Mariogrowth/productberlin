import org.jetbrains.kotlin.gradle.targets.js.nodejs.NodeJsEnvSpec
import org.jetbrains.kotlin.gradle.targets.js.nodejs.NodeJsRootPlugin
import org.jetbrains.kotlin.gradle.targets.js.yarn.YarnPlugin
import org.jetbrains.kotlin.gradle.targets.js.yarn.YarnRootExtension
import org.jlleitschuh.gradle.ktlint.KtlintExtension

plugins {
    base
    alias(libs.plugins.kotlinMultiplatform) apply false
    alias(libs.plugins.kotlinSerialization) apply false
    alias(libs.plugins.ktlint)
}

val ktlintVersion = libs.versions.ktlint.get()

subprojects {
    apply(plugin = "org.jlleitschuh.gradle.ktlint")
}

allprojects {
    extensions.configure<KtlintExtension> {
        version.set(ktlintVersion)
        filter {
            exclude("**/build/**", "**/.gradle/**", "**/node_modules/**")
        }
    }
}

// Root tasks cover the build scripts and every application module.
tasks.named("ktlintCheck") {
    dependsOn(subprojects.map { "${it.path}:ktlintCheck" })
}

tasks.named("ktlintFormat") {
    dependsOn(subprojects.map { "${it.path}:ktlintFormat" })
}

tasks.named("check") {
    dependsOn("ktlintCheck")
}

// Keep one current React runtime across modules while using the latest published wrapper types.
val reactVersion = libs.versions.react.get()
plugins.withType<YarnPlugin> {
    extensions.configure<YarnRootExtension> {
        resolution("react", reactVersion)
        resolution("react-dom", reactVersion)
    }
}

// The IDE run action must serve both the UI and its D1-backed API.
plugins.withType<NodeJsRootPlugin> {
    val node = extensions.getByType<NodeJsEnvSpec>()
    tasks.register<Exec>("runLocal") {
        group = "application"
        description = "Build, initialize local D1, and serve the website and API on port 8787."
        dependsOn(":api-worker:jsNodeProductionLibraryDistribution", ":webApp:jsBrowserDistribution")
        dependsOn("kotlinNodeJsSetup")
        workingDir(rootDir)
        executable(node.executable.get())
        args("scripts/local-dev.mjs")
    }
}
