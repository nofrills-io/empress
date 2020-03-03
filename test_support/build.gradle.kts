plugins {
    id("io.nofrills.multimodule.aar")
    kotlin("android.extensions")
}

androidExtensions {
    isExperimental = true // for `@Parcelize` annotation
}

dependencies {
    implementation(project(":empress_android"))
    implementation(Deps.fragment)
}
