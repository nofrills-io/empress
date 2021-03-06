import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id("io.nofrills.multimodule.apk")
    kotlin("android.extensions")
}

submodule {
    dokkaAllowed.set(false)
    jacocoAllowed.set(false)
    publishAllowed.set(false)
}

android {
    defaultConfig {
        applicationId = "io.nofrills.empress.sample"
    }
}

androidExtensions {
    isExperimental = true // for `@Parcelize` annotation
}

dependencies {
    implementation(project(":empress_android"))
    implementation(Deps.lifecycleRuntimeKts)
    implementation(Deps.appCompat)
    implementation(Deps.constraintLayout)
    implementation(Deps.fragment)

    testImplementation(Deps.coroutinesTest)
    testImplementation(Deps.junit)
}

tasks.withType(KotlinCompile::class).configureEach {
    kotlinOptions {
        freeCompilerArgs = freeCompilerArgs + listOf(
            "-Xopt-in=kotlinx.coroutines.ExperimentalCoroutinesApi"
        )
    }
}
