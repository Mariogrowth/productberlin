plugins { alias(libs.plugins.kotlinMultiplatform) }

kotlin {
    js {
        nodejs()
        useEsModules()
        binaries.library()
        generateTypeScriptDefinitions()
    }
    sourceSets {
        jsTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.coroutines.test)
            implementation(libs.ktor.client.mock)
        }
        jsMain.dependencies {
            implementation(project(":app-domain"))
            implementation(project(":api-contract"))
            implementation(libs.coroutines.core)
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.js)
            implementation(libs.serialization.json)
        }
    }
}
