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
    implementation(project(":empress_android"))
    implementation(project(":test_support"))
    implementation(Deps.appCompat)
    implementation(Deps.constraintLayout)
    implementation(Deps.kotlinStdLib)
    testImplementation(Deps.junit)
    androidTestImplementation(Deps.testRunner)
    androidTestImplementation(Deps.espressoCore)
}