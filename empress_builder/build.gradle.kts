import org.jetbrains.dokka.gradle.DokkaAndroidTask
import java.net.URL
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id("java-library")
    kotlin("jvm")
    id("jacoco")
    id("org.jetbrains.dokka-android") version "0.9.18"
}

java {
    sourceCompatibility = EmpressLib.javaCompat
    targetCompatibility = EmpressLib.javaCompat
}

dependencies {
    implementation(Deps.kotlinStdLib)
    implementation(project(":empress_core"))
    testImplementation(Deps.junit)
    testImplementation(Deps.coroutinesTest)
}

tasks.withType(KotlinCompile::class).all {
    kotlinOptions {
        allWarningsAsErrors = true
        freeCompilerArgs = freeCompilerArgs + listOf(
            "-Xuse-experimental=kotlinx.coroutines.ExperimentalCoroutinesApi"
        )
        jvmTarget = EmpressLib.jvmTarget
    }
}

tasks.named("check") {
    dependsOn(tasks.withType(JacocoReport::class))
}

val dokkaTasks = tasks.withType(DokkaAndroidTask::class) {
    externalDocumentationLink {
        url = URL("https://kotlin.github.io/kotlinx.coroutines/kotlinx-coroutines-core/")
    }
    jdkVersion = EmpressLib.jdkVersionNum
    val getTasks: (String) -> List<Task> = { projectPath ->
        project(projectPath).tasks.withType(KotlinCompile::class)
            .filter { !it.name.contains("test", ignoreCase = true) }
            .filter { !it.name.contains("debug", ignoreCase = true) }
    }
    kotlinTasks {
        defaultKotlinTasks() + getTasks(":empress_core") + getTasks(":empress_android")
    }
    includes = listOf(
        "${rootProject.projectDir}/empress_core/module_doc.md",
        "${rootProject.projectDir}/empress_android/module_doc.md",
        "${rootProject.projectDir}/empress_builder/module_doc.md"
    )
    moduleName = "empress"
}

tasks.register("publishDokka", Copy::class) {
    dependsOn(dokkaTasks)
    from(File(project.buildDir, "dokka"))
    destinationDir = rootProject.file("docs/dokka")
}

apply(from = "https://raw.githubusercontent.com/sky-uk/gradle-maven-plugin/${EmpressLib.mavPluginVersion}/gradle-mavenizer.gradle")
