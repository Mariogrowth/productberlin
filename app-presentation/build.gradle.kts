plugins { alias(libs.plugins.kotlinMultiplatform) }

kotlin {
    js { browser() }
    sourceSets {
        jsMain.dependencies {
            implementation(project(":app-domain"))
            implementation(libs.wrappers.react)
            implementation(libs.wrappers.react.dom)
            implementation(libs.coroutines.core)
        }
    }
}
