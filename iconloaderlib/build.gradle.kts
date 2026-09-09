plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

val frameworkJar = file("${rootDir}/prebuilts/libs/framework.jar")

gradle.projectsEvaluated {
    tasks.withType<JavaCompile>().configureEach {
        classpath = files(frameworkJar, classpath)
    }
    tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
        libraries.from(files(frameworkJar))
    }
}

android {
    compileSdk = 36
    namespace = "com.android.launcher3.icons"

    defaultConfig {
        vectorDrawables.useSupportLibrary = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    kotlinOptions {
        jvmTarget = "21"
    }

    sourceSets {
        named("main") {
            java.setSrcDirs(listOf("src", "src_full_lib"))
            manifest.srcFile("AndroidManifest.xml")
            res.setSrcDirs(listOf("res"))
        }
    }
}

dependencies {
    implementation("androidx.core:core:1.13.1")
    api(project(":flags"))
    compileOnly(fileTree("${rootDir}/prebuilts/libs") { include("*.jar") })
}
