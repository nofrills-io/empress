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
    id("io.nofrills.multimodule") version "0.7.0"
}

allprojects {
    repositories {
        google()
        jcenter()
    }
}

tasks.dokkaHtmlMultiModule.configure {
    moduleName.set("empress")
    removeChildTasks(project(":sample"))
    removeChildTasks(project(":test_support"))
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
            versionName = EmpressLib.versionName(project)
            testInstrumentationRunner = "androidx.benchmark.junit4.AndroidBenchmarkRunner"
        }
    }

    dokka {
        dokkaSourceSets.all {
            externalDocumentationLink {
                url.set(java.net.URL("https://kotlin.github.io/kotlinx.coroutines/kotlinx-coroutines-core/"))
            }
            project.file("${project.projectDir}/module_doc.md").also {
                if (it.exists()) {
                    includes.from(it)
                }
            }
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
        parcelizePlugin = true
    }

    publish {
        mavenPom {
            name.set(project.name.split("_").joinToString(" ") { it.capitalize() })
            description.set(project.description)
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
                url = uri("file://${project.rootProject.buildDir}/dist")
            }
        }

        withDocs = false
        withSources = true
    }
}

subprojects {
    group = property("group") ?: "com.github.nofrills-io"
    version = EmpressLib.versionName(this)
}

tasks.register("clean", Delete::class.java) {
    delete(rootProject.buildDir)
}

tasks.register("publishDokka", Copy::class) {
    dependsOn(":dokkaHtmlMultiModule")
    from(File(project.buildDir, "dokka/htmlMultiModule"))
    destinationDir = rootProject.file("docs/dokka")

    doFirst {
        destinationDir.deleteRecursively()
    }
}
