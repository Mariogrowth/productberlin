plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.kotlinSerialization)
}

kotlin {
    js {
        browser()
        nodejs()
    }
    sourceSets {
        commonMain.dependencies {
            implementation(project(":app-domain"))
            implementation(project(":api-contract"))
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.serialization.json)
            implementation(libs.serialization.json)
        }
        jsMain.dependencies { implementation(libs.ktor.client.js) }
        commonTest.dependencies {
            implementation(libs.ktor.client.mock)
            implementation(libs.kotlin.test)
            implementation(libs.coroutines.test)
        }
    }
}
