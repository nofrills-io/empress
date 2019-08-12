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
        testInstrumentationRunner = "androidx.benchmark.junit4.AndroidBenchmarkRunner"
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

    androidTestImplementation(project(":test_support"))
    androidTestImplementation(Deps.benchmarkJunit)
    androidTestImplementation(Deps.coroutinesTest)
    androidTestImplementation(Deps.espressoCore)
    androidTestImplementation(Deps.testExtJunit)
    androidTestImplementation(Deps.testRunner)
}

val dokkaTasks = tasks.withType(DokkaTask::class) {
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

tasks.register("publishDokka", Copy::class) {
    dependsOn(dokkaTasks)
    from(File(project.buildDir, "dokka"))
    destinationDir = rootProject.file("docs/dokka")
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
            "-Xuse-experimental=kotlinx.coroutines.ExperimentalCoroutinesApi",
            "-Xuse-experimental=kotlinx.coroutines.FlowPreview"
        )
        jvmTarget = EmpressLib.jvmTarget
    }
}

apply(from = "https://raw.githubusercontent.com/sky-uk/gradle-maven-plugin/${EmpressLib.mavPluginVersion}/gradle-mavenizer.gradle")
