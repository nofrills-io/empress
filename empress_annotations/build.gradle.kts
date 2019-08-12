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
    implementation(Deps.kotlinStdLib)
}

tasks.withType(KotlinCompile::class).all {
    kotlinOptions {
        allWarningsAsErrors = true
        jvmTarget = EmpressLib.jvmTarget
    }
}
