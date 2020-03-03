buildscript {
    repositories {
        google()
        jcenter()
    }
    dependencies {
        classpath("com.android.tools.build:gradle:3.6.1")
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:${Ver.kotlin}")
        classpath("io.nofrills:multimodule:0.1.0")
    }
}

plugins {
    id("io.nofrills.multimodule") version "0.1.0"
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

    java {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    kotlin {
        allWarningsAsErrors = true
        jvmTarget = EmpressLib.jvmTarget
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
