import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id("com.android.library")
    kotlin("android")
    kotlin("android.extensions")
}

android {
    compileSdkVersion(EmpressLib.compileSdkVersion)

    compileOptions {
        sourceCompatibility = EmpressLib.javaCompat
        targetCompatibility = EmpressLib.javaCompat
    }

    defaultConfig {
        minSdkVersion(EmpressLib.minSdkVersion)
        targetSdkVersion(EmpressLib.targetSdkVersion)
        versionCode = EmpressLib.versionCode
        versionName = EmpressLib.versionName
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    sourceSets["main"].java.srcDir("src/main/kotlin")
}

androidExtensions {
    isExperimental = true // for `@Parcelize` annotation
}

dependencies {
    implementation(project(":empress_android"))
    implementation(Deps.fragment)
}

tasks.withType(KotlinCompile::class).configureEach {
    kotlinOptions {
        jvmTarget = EmpressLib.jvmTarget
    }
}
