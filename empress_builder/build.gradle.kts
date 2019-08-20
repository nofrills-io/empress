import java.net.URL
import org.jetbrains.dokka.gradle.DokkaTask
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id("java-library")
    kotlin("jvm")
    id("org.jetbrains.dokka-android") version "0.9.18"
}

java {
    sourceCompatibility = EmpressLib.javaCompat
    targetCompatibility = EmpressLib.javaCompat
}

dependencies {
    implementation(Deps.kotlinStdLib)
    implementation(project(":empress_core"))
}

tasks.withType(KotlinCompile::class).all {
    kotlinOptions {
        allWarningsAsErrors = true
        jvmTarget = EmpressLib.jvmTarget
    }
}

val dokkaTasks = tasks.withType(DokkaTask::class) {
    externalDocumentationLink {
        url = URL("https://kotlin.github.io/kotlinx.coroutines/kotlinx-coroutines-core/")
    }
    jdkVersion = EmpressLib.jdkVersionNum
    val getTasks: (String) -> List<Task> = { projectPath ->
        project(projectPath).tasks.withType(KotlinCompile::class)
            .filter { !it.path.contains("test", ignoreCase = true) }
    }
    kotlinTasks {
        defaultKotlinTasks() + getTasks(":empress_core") + getTasks(":empress_android")
    }
    moduleName = "empress"
}

tasks.register("publishDokka", Copy::class) {
    dependsOn(dokkaTasks)
    from(File(project.buildDir, "dokka"))
    destinationDir = rootProject.file("docs/dokka")
}

apply(from = "https://raw.githubusercontent.com/sky-uk/gradle-maven-plugin/${EmpressLib.mavPluginVersion}/gradle-mavenizer.gradle")
