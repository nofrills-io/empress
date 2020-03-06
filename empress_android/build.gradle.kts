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
    api(project(":empress_core"))
    api(Deps.coroutinesAndroid)

    implementation(Deps.fragment)

    debugImplementation(Deps.fragmentTesting)

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
