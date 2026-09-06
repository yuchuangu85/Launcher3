plugins {
    id(libs.plugins.android.library.get().pluginId)
    id(libs.plugins.kotlin.android.get().pluginId)
    id("kotlin-parcelize")
}

android {
    namespace = "com.android.personalcontext.ace.common"

    sourceSets {
        named("main") {
            java {
                setSrcDirs(
                    listOf(
                        "../src/com/android/personalcontext/ace/common/",
                        "../src/com/android/personalcontext/ace/common/wrappers/",
                    )
                )
            }
            manifest.srcFile("AndroidManifest.xml")
        }
    }
}

dependencies {
    api(libs.androidx.annotation)
    api(libs.androidx.core.ktx)
}
