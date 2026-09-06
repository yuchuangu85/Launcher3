plugins {
    id(libs.plugins.android.library.get().pluginId)
    id(libs.plugins.kotlin.android.get().pluginId)
}

android {
    namespace = "com.android.launcher3.icons"

    defaultConfig {
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        testApplicationId = "com.android.launcher3.icons.tests"
    }

    sourceSets {
        named("main") {
            java.setSrcDirs(listOf("src", "src_full_lib"))
            manifest.srcFile("AndroidManifest.xml")
            res.setSrcDirs(listOf("res"))
        }

        named("androidTest") { java.setSrcDirs(listOf("tests/src")) }
    }
}

dependencies {
    implementation("androidx.core:core")
    api(project(":NexusLauncher:Flags"))
    api(project(":frameworks:base:packages:SystemUI:SystemUISharedFlags"))
    api(project(":UserTypeLib"))

    androidTestImplementation(libs.androidx.test.rules)
    androidTestImplementation(libs.androidx.junit)
}
