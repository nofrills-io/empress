import java.net.URL
import org.jetbrains.dokka.gradle.DokkaTask
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id("com.android.library")
    kotlin("android")
    kotlin("android.extensions")
    id("org.jetbrains.dokka-android") version "0.9.18"
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

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }

    sourceSets["androidTest"].java.srcDir("src/androidTest/kotlin")
    sourceSets["main"].java.srcDir("src/main/kotlin")
    sourceSets["test"].java.srcDir("src/test/kotlin")
}

dependencies {
    api(project(":empress_core"))
    api(Deps.coroutinesAndroid)

    implementation(Deps.fragment)

    debugImplementation(Deps.fragmentTesting)

    testImplementation(project(":test_support"))
    testImplementation(Deps.junit)
    testImplementation(Deps.robolectric)

    androidTestImplementation(Deps.espressoCore)
    androidTestImplementation(Deps.testRunner)
}

configurations.configureEach {
    resolutionStrategy.dependencySubstitution.all {
        val requested = requested
        if (requested is ModuleComponentSelector && requested.group == "androidx.test" && requested.module == "core") {
            useTarget("androidx.test:core:1.2.0", "${Deps.fragmentTesting} uses old version")
        }
    }
}

tasks.withType(DokkaTask::class) {
    externalDocumentationLink {
        url = URL("https://kotlin.github.io/kotlinx.coroutines/kotlinx-coroutines-core/")
    }
    jdkVersion = EmpressLib.jdkVersionNum
    kotlinTasks {
        defaultKotlinTasks() + project(":empress_core").tasks.withType(KotlinCompile::class)
            .filter { !it.path.contains("test", ignoreCase = true) }
    }
    moduleName = "empress"
}

gradle.taskGraph.beforeTask {
    if (name.contains("ReleaseUnitTest")) {
        enabled = false
    }
}

tasks.withType(KotlinCompile::class).whenTaskAdded {
    kotlinOptions {
        allWarningsAsErrors = true
        freeCompilerArgs = freeCompilerArgs + listOf(
            "-Xuse-experimental=kotlinx.coroutines.FlowPreview"
        )
    }
}
