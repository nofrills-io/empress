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
    api(Deps.composeRuntime)
    api(Deps.coroutinesCore)

    implementation(Deps.fragment)

    debugImplementation(Deps.fragmentTesting)

    testImplementation(project(":test_support"))
    testImplementation(Deps.coroutinesTest)
    testImplementation(Deps.junit)
    testImplementation(Deps.robolectric)
}

tasks.withType(org.jetbrains.kotlin.gradle.tasks.KotlinCompile::class.java).configureEach {
    kotlinOptions {
        jvmTarget = "1.8"
        freeCompilerArgs = freeCompilerArgs + listOf(
            "-Xopt-in=kotlinx.coroutines.ExperimentalCoroutinesApi",
            "-Xallow-jvm-ir-dependencies", "-Xskip-prerelease-check"
        )
    }
}

gradle.taskGraph.beforeTask {
    if (name.contains("ReleaseUnitTest")) {
        enabled = false
    }
}

description = "Android framework for ruling your app."
