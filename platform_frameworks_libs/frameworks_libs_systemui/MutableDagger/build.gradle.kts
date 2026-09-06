plugins {
    kotlin("jvm")
}

sourceSets {
    main {
        kotlin.srcDirs(listOf(
            "src_annotations",
            "src_processor"
        ))
        resources.srcDirs(listOf("resources"))
    }
}

dependencies {
    implementation(libs.ksp.symbol)
    implementation(libs.dagger.android)
}