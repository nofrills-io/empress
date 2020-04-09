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
    api(Deps.coroutinesCore)

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

description = "Android framework for ruling your app."
