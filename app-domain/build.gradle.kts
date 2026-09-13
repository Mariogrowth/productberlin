plugins { alias(libs.plugins.kotlinMultiplatform) }

kotlin {
    js {
        browser()
        nodejs()
    }
    sourceSets {
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.coroutines.test)
        }
    }
}
