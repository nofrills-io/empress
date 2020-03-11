plugins {
    id("io.nofrills.multimodule.aar")
    kotlin("android.extensions")
}

submodule {
    dokkaAllowed.set(false)
    jacocoAllowed.set(false)
    publishAllowed.set(false)
}

androidExtensions {
    isExperimental = true // for `@Parcelize` annotation
}

dependencies {
    implementation(project(":empress_android"))
    implementation(Deps.fragment)
}
