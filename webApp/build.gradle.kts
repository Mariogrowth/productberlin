plugins { alias(libs.plugins.kotlinMultiplatform) }

kotlin {
    js {
        browser {
            commonWebpackConfig { outputFileName = "productberlin.js" }
        }
        binaries.executable()
    }
    sourceSets {
        // Package the UI module's assets alongside this module's HTML shell.
        named("jsMain") {
            resources.srcDir(project(":app-presentation").layout.projectDirectory.dir("src/jsMain/resources"))
        }
        jsMain.dependencies {
            implementation(project(":app-domain"))
            implementation(project(":app-data"))
            implementation(project(":app-presentation"))
            implementation(libs.wrappers.react)
            implementation(libs.wrappers.react.dom)
            implementation(libs.koin.core)
            implementation(libs.ktor.client.core)
        }
    }
}
