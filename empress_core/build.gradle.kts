import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id("java-library")
    kotlin("jvm")
}

java {
    sourceCompatibility = EmpressLib.javaCompat
    targetCompatibility = EmpressLib.javaCompat
}

dependencies {
    api(Deps.coroutinesCore)
    api(Deps.kotlinStdLib)
    testImplementation(Deps.junit)
    testImplementation(Deps.coroutinesTest)
}

tasks.withType(KotlinCompile::class).all {
    kotlinOptions {
        allWarningsAsErrors = true
        freeCompilerArgs = freeCompilerArgs + listOf(
            "-Xuse-experimental=kotlinx.coroutines.ExperimentalCoroutinesApi",
            "-Xuse-experimental=kotlinx.coroutines.FlowPreview"
        )
        jvmTarget = EmpressLib.jvmTarget
    }
}

tasks.withType(Test::class) {
    testLogging {
        showStandardStreams = false
    }
}