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
            "-Xuse-experimental=kotlin.Experimental"
        )
    }
}

tasks.withType(Test::class) {
    testLogging {
        showStandardStreams = false
    }
}
