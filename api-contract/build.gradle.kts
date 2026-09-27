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
        commonMain.dependencies { implementation(libs.serialization.json) }
        commonTest.dependencies { implementation(libs.kotlin.test) }
    }
}
