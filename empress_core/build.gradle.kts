import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id("io.nofrills.multimodule.jar")
}

dependencies {
    api(Deps.coroutinesCore)
    api(Deps.kotlinStdLib)
    testImplementation(Deps.junit)
    testImplementation(Deps.coroutinesTest)
}

tasks.withType(KotlinCompile::class).configureEach {
    kotlinOptions {
        freeCompilerArgs = freeCompilerArgs + listOf(
            "-Xopt-in=kotlin.RequiresOptIn"
        )
    }
}

tasks.withType(Test::class) {
    testLogging {
        showStandardStreams = false
    }
}

description = "The core for the Empress framework."
