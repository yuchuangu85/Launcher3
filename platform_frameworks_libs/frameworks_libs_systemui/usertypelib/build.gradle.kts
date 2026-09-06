plugins {
    id(libs.plugins.android.library.get().pluginId)
    id(libs.plugins.kotlin.android.get().pluginId)
    id("kotlin-parcelize")
}

android {
    namespace = "com.android.users"

    sourceSets {
        named("main") {
            java.setSrcDirs(listOf("src"))
            manifest.srcFile("AndroidManifest.xml")
        }
    }
}
