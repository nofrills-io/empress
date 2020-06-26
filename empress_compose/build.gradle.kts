plugins {
    id("io.nofrills.multimodule.aar")
    kotlin("android.extensions")
}

android {
    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
}

dependencies {
    api(project(":empress_base"))
    api("androidx.compose:compose-runtime:0.1.0-dev14")

    testImplementation(project(":test_support"))
    testImplementation(Deps.coroutinesTest)
    testImplementation(Deps.junit)
    testImplementation(Deps.robolectric)
}

gradle.taskGraph.beforeTask {
    if (name.contains("ReleaseUnitTest")) {
        enabled = false
    }
}

description = "Integration for Jetpack Compose"
