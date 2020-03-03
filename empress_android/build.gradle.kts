import com.android.build.gradle.internal.tasks.factory.dependsOn

plugins {
    id("io.nofrills.multimodule.aar")
    kotlin("android.extensions")
    id("jacoco")
}

android {
    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
}

dependencies {
    api(project(":empress_core"))
    api(Deps.coroutinesAndroid)

    implementation(Deps.fragment)

    debugImplementation(Deps.fragmentTesting)

    testImplementation(project(":test_support"))
    testImplementation(Deps.coroutinesTest)
    testImplementation(Deps.junit)
    testImplementation(Deps.robolectric)
}

gradle.taskGraph.beforeTask {
    if (name.contains("ReleaseUnitTest")) {
        enabled = false
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
    dependsOn(tasks.withType(Test::class))

    reports {
        csv.isEnabled = false
        html.isEnabled = true
        xml.isEnabled = true
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
