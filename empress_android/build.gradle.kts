import com.android.build.gradle.internal.tasks.factory.dependsOn
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id("com.android.library")
    kotlin("android")
    kotlin("android.extensions")
    id("jacoco")
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

tasks.withType(Test::class) {
    extensions.configure(JacocoTaskExtension::class) {
        isIncludeNoLocationClasses = true
    }
}

val jacocoTestReport = tasks.register("jacocoTestReport", JacocoReport::class) {
    group = "Verification"
    description = "Generates code coverage report."
    dependsOn("testDebugUnitTest")

    reports {
        csv.isEnabled = false
        html.isEnabled = true
        xml.isEnabled = false
    }

    val fileFilter = listOf(
        "**/*Test*.*",
        "**/AutoValue_*.*",
        "**/*JavascriptBridge.class",
        "**/R.class",
        "**/R$*.class",
        "**/Manifest*.*",
        "android/**/*.*",
        "**/BuildConfig.*",
        "**/*\$ViewBinder*.*",
        "**/*\$ViewInjector*.*",
        "**/Lambda$*.class",
        "**/Lambda.class",
        "**/*Lambda.class",
        "**/*Lambda*.class",
        "**/*\$InjectAdapter.class",
        "**/*\$ModuleAdapter.class",
        "**/*\$ViewInjector*.class"
    )
    val kotlinDebugTree = fileTree("$buildDir/tmp/kotlin-classes/debug") { exclude(fileFilter) }
    val mainSrc = "${project.projectDir}/src/main/kotlin"

    sourceDirectories.setFrom(files(mainSrc))
    classDirectories.setFrom(files(kotlinDebugTree))
    executionData.setFrom(fileTree(buildDir) {
        include(setOf("jacoco/testDebugUnitTest.exec"))
    })
}

tasks.named("check").dependsOn(jacocoTestReport)

apply(from = "https://raw.githubusercontent.com/sky-uk/gradle-maven-plugin/${EmpressLib.mavPluginVersion}/gradle-mavenizer.gradle")
