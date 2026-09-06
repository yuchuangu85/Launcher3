plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.android.systemui.surfaceeffects.core"
    compileSdk = 33

    defaultConfig {
        minSdk = 33
    }

    sourceSets {
        getByName("main") {
            java.srcDirs("src")
            kotlin.srcDirs("src")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    kotlinOptions {
        jvmTarget = "1.8"
    }
}

dependencies {
    api(libs.androidx.annotation)
    api(libs.androidx.core.animation)
    api(libs.androidx.core.ktx)
    api(libs.compose.runtime)
}

