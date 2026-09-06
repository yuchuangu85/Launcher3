plugins {
    id(libs.plugins.android.library.get().pluginId)
    id(libs.plugins.kotlin.android.get().pluginId)
    id(libs.plugins.kotlin.kapt.get().pluginId)
}

android {
    namespace = "com.google.android.ambient.app"
    sourceSets {
        named("main") {
            java.setSrcDirs(listOf("src"))
            manifest.srcFile("AndroidManifest.xml")
        }
    }
}

dependencies {
    implementation("androidx.core:core")
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("com.google.guava:guava:30.1.1-jre")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-guava:1.7.3")
    implementation(libs.androidx.appsearch)
    implementation(libs.androidx.appsearch.platform.storage)
    implementation(libs.androidx.appsearch.builtin.types)
    kapt("androidx.appsearch:appsearch-compiler:1.1.0-beta01")
}
