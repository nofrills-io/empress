plugins {
    id("io.nofrills.multimodule.aar")
}

submodule {
    dokkaAllowed.set(false)
    jacocoAllowed.set(false)
    publishAllowed.set(false)
}

dependencies {
    implementation(project(":empress_android"))
    implementation(Deps.fragment)
}
