buildscript {
    repositories {
        google()
        jcenter()
    }
    dependencies {
        classpath("com.android.tools.build:gradle") { version(Vers.androidBuildTools) }
        classpath("org.jetbrains.dokka:dokka-gradle-plugin") { version(Vers.dokka) }
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin") { version(Vers.kotlin) }
    }
}

plugins {
    id("io.nofrills.multimodule") version "0.2.2-dev"
}

allprojects {
    repositories {
        google()
        jcenter()
    }
}

multimodule {
    android {
        compileSdkVersion(EmpressLib.compileSdkVersion)

        buildTypes {
            getByName("debug") {
                versionNameSuffix = "-debug"
            }
            getByName("release") {
                isMinifyEnabled = false
                proguardFiles(
                    getDefaultProguardFile("proguard-android-optimize.txt"),
                    "proguard-rules.pro"
                )
            }
        }

        defaultConfig {
            minSdkVersion(EmpressLib.minSdkVersion)
            targetSdkVersion(EmpressLib.targetSdkVersion)
            versionCode = EmpressLib.versionCode
            versionName = EmpressLib.versionName
            testInstrumentationRunner = "androidx.benchmark.junit4.AndroidBenchmarkRunner"
        }
    }

    dokka {
        configuration {
            externalDocumentationLink {
                url =
                    java.net.URL("https://kotlin.github.io/kotlinx.coroutines/kotlinx-coroutines-core/")
            }
            includes = listOf(
                "${rootProject.projectDir}/empress_core/module_doc.md",
                "${rootProject.projectDir}/empress_core/module_doc_backend.md",
                "${rootProject.projectDir}/empress_core/module_doc_consumable.md",
                "${rootProject.projectDir}/empress_android/module_doc.md",
                "${rootProject.projectDir}/empress_builder/module_doc.md"
            )
            moduleName = "empress"
        }
    }

    jacoco {}

    java {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    kotlin {
        allWarningsAsErrors = true
        coroutines = true
        coroutinesVersion = Vers.coroutines
        stdLib = true
    }

    publish {
        mavenPom {
            name.set("empress")
            url.set("https://nofrills.io/empress/")

            licenses {
                license {
                    name.set("Apache-2.0")
                    url.set("https://github.com/nofrills-io/empress/blob/master/LICENSE")
                }
            }

            scm {
                connection.set("https://github.com/nofrills-io/empress.git")
                developerConnection.set("https://github.com/nofrills-io/empress.git")
                url.set("https://nofrills.io/empress/")
            }
        }

        repositories {
            maven {
                name = "dist"
                url = uri("file://${buildDir}/dist")
            }
        }

        withDocs = true
        withSources = true
    }
}

subprojects {
    group = "com.github.nofrills-io"
    version = EmpressLib.versionName
}

tasks.register("clean", Delete::class.java) {
    delete(rootProject.buildDir)
}

tasks.register("publishDokka", Copy::class) {
    dependsOn("dokka")
    from(File(project.buildDir, "dokka"))
    destinationDir = rootProject.file("docs/dokka")

    doFirst {
        destinationDir.deleteRecursively()
    }
}
