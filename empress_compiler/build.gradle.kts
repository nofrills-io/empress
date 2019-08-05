import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id("java-library")
    kotlin("jvm")
    kotlin("kapt")
}

java {
    sourceCompatibility = EmpressLib.javaCompat
    targetCompatibility = EmpressLib.javaCompat
}

dependencies {
    implementation(project(":empress_annotations"))
    implementation(Deps.autoService)
    implementation(Deps.kotlinPoet)
    implementation(Deps.kotlinReflect)
    implementation(Deps.kotlinStdLib)

    kapt(Deps.autoService)
}

tasks.withType(KotlinCompile::class).all {
    kotlinOptions {
        allWarningsAsErrors = true
    }
}
