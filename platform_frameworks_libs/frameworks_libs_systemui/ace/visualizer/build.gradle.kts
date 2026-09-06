plugins {
    id(libs.plugins.android.library.get().pluginId)
    id(libs.plugins.kotlin.android.get().pluginId)
    id("org.jetbrains.kotlin.plugin.compose")
    id("kotlin-parcelize")
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.android.personalcontext.ace.visualizer"

    sourceSets {
        named("main") {
            java { setSrcDirs(listOf("../src/com/android/personalcontext/ace/visualizer/")) }
            res.setSrcDirs(listOf("../src/com/android/personalcontext/ace/visualizer/res"))
            manifest.srcFile("AndroidManifest.xml")
        }
    }

    buildFeatures { compose = true }
}

dependencies {
    api(project(":frameworks:libs:systemui:ace:common"))
    api(libs.androidx.annotation)
    api(libs.androidx.core.ktx)
    api(libs.compose.runtime)
    api(libs.androidx.core.animation)
    api(libs.androidx.recyclerview)
    api(libs.androidx.dynamicanimation)
    api(libs.androidx.window)
    api(libs.compose.material3)
    api(libs.compose.material.icons.extended)

    // Lifecycle and ViewModel
    api(libs.lifecycle.common)
    api(libs.lifecycle.runtime.ktx)
    api(libs.lifecycle.viewmodel)
    api(libs.lifecycle.viewmodel.ktx)
    api(libs.androidx.activity.compose)

    // Dagger
    api(libs.dagger)
    ksp(libs.dagger.compiler)
}
