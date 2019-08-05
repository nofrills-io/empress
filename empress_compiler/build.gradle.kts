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
    compileOnly(Deps.autoService)
    implementation(project(":empress_annotations"))
    implementation(Deps.kotlinPoet)
    implementation(Deps.kotlinReflect)
    implementation(Deps.kotlinStdLib)
}

tasks.withType(KotlinCompile::class).all {
    kotlinOptions {
        allWarningsAsErrors = true
    }
}
