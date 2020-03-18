plugins {
    id("io.nofrills.multimodule.jar")
}

dependencies {
    api(project(":empress_core"))

    compileOnly(Deps.kotlinReflect)

    implementation(Deps.androidxAnnotations)
    implementation(Deps.kotlinStdLib)

    testImplementation(Deps.kotlinReflect)
    testImplementation(Deps.coroutinesTest)
    testImplementation(Deps.junit)
}
