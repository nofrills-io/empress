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
    compileOnly(project(":empress_annotations"))
    compileOnly(project(":empress_core"))
    compileOnly(Deps.autoServiceAnnotations)

    implementation(Deps.kotlinPoet)
    implementation(Deps.kotlinReflect)
    implementation(Deps.kotlinStdLib)

    kapt(Deps.autoService)
}

tasks.withType(KotlinCompile::class).all {
    kotlinOptions {
        allWarningsAsErrors = true
        jvmTarget = EmpressLib.jvmTarget
    }
}
