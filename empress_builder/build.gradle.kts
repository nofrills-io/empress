plugins {
    id("io.nofrills.multimodule.jar")
}

dependencies {
    api(project(":empress_core"))

    compileOnly("org.jetbrains.kotlin:kotlin-reflect:${Ver.kotlin}")

    implementation(Deps.androidxAnnotations)
    implementation(Deps.kotlinStdLib)

    testImplementation("org.jetbrains.kotlin:kotlin-reflect:${Ver.kotlin}")
    testImplementation(Deps.coroutinesTest)
    testImplementation(Deps.junit)
}
