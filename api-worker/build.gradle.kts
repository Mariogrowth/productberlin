plugins { alias(libs.plugins.kotlinMultiplatform) }

kotlin {
    js {
        nodejs()
        useEsModules()
        binaries.library()
        generateTypeScriptDefinitions()
    }
    sourceSets {
        jsMain.dependencies {
            implementation(project(":app-domain"))
            implementation(project(":api-contract"))
            implementation(libs.coroutines.core)
            implementation(libs.serialization.json)
        }
    }
}
