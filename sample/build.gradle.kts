import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id("com.android.application")
    kotlin("android")
    kotlin("android.extensions")
}

android {
    compileSdkVersion(EmpressLib.compileSdkVersion)

    defaultConfig {
        applicationId = "io.nofrills.empress.sample"
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

    sourceSets["androidTest"].java.srcDir("src/androidTest/kotlin")
    sourceSets["main"].java.srcDir("src/main/kotlin")
    sourceSets["test"].java.srcDir("src/test/kotlin")
}

androidExtensions {
    isExperimental = true // for `@Parcelize` annotation
}

dependencies {
    androidTestImplementation(Deps.testRunner)
    androidTestImplementation(Deps.espressoCore)

    implementation(project(":empress_android"))
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.2.0-alpha03")
    implementation(Deps.appCompat)
    implementation(Deps.constraintLayout)
    implementation(Deps.kotlinStdLib)

    testImplementation(Deps.coroutinesTest)
    testImplementation(Deps.junit)
}

tasks.withType(KotlinCompile::class).configureEach {
    kotlinOptions {
        freeCompilerArgs = freeCompilerArgs + listOf(
            "-Xuse-experimental=kotlinx.coroutines.ExperimentalCoroutinesApi"
        )
        jvmTarget = EmpressLib.jvmTarget
    }
}
