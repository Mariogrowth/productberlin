plugins { alias(libs.plugins.kotlinMultiplatform) }

kotlin {
    js {
        browser {
            testTask {
                useKarma { useChromeHeadless() }
            }
        }
    }
    sourceSets {
        jsTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.coroutines.test)
        }
        jsMain.dependencies {
            implementation(project(":app-domain"))
            implementation(libs.wrappers.react)
            implementation(libs.wrappers.react.dom)
            implementation(libs.coroutines.core)
        }
    }
}

// A token or style change must rerun browser assertions even when Kotlin is unchanged.
tasks.named("jsBrowserTest") {
    inputs.file("src/jsMain/resources/design-system.css")
    inputs.dir("karma.config.d")
}
