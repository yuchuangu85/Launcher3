plugins {
    id(libs.plugins.android.library.get().pluginId)
    id(libs.plugins.kotlin.android.get().pluginId)
    id("org.jetbrains.kotlin.plugin.compose")
    id("kotlin-parcelize")
}

android {
    namespace = "com.android.personalcontext.ace.client"

    sourceSets {
        named("main") {
            java { setSrcDirs(listOf("../src/com/android/personalcontext/ace/client/")) }
            res.setSrcDirs(
                listOf("../src/com/android/personalcontext/ace/client/clientsdk/compat/res")
            )
            manifest.srcFile("AndroidManifest.xml")
        }
    }

    buildFeatures { compose = true }
}

dependencies {
    api(project(":frameworks:libs:systemui:ace:common"))
    api(libs.androidx.annotation)
    api(libs.compose.runtime)
    api(libs.androidx.core.ktx)
    api(libs.androidx.core.animation)
    api(libs.androidx.recyclerview)
    api(libs.androidx.dynamicanimation)
    api(libs.androidx.window)
    api(libs.compose.material3)
}
