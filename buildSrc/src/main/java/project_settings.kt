import org.gradle.api.Action
import org.gradle.api.artifacts.ExternalModuleDependency
import org.gradle.api.artifacts.MutableVersionConstraint
import org.gradle.kotlin.dsl.DependencyHandlerScope
import org.gradle.kotlin.dsl.accessors.runtime.addDependencyTo

/*
 * Copyright 2019 Mateusz Armatys
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

object EmpressLib {
    const val compileSdkVersion = 28
    const val minSdkVersion = 18
    const val targetSdkVersion = 28
    const val versionCode = 8
    const val versionName = "0.7.0-beta1"
}

object Vers {
    val androidBuildTools = Ver(preferred = "3.6.1", required = "3.6.0")
    val coroutines = Ver(preferred = "1.3.5", required = "1.3.3")
    val dokka = Ver(preferred = "0.10.1", required = "0.10.1")
    val kotlin = Ver(preferred = "1.3.70", required = "1.3.70")
}

object Deps {
    private val coroutines = DepGroup("org.jetbrains.kotlinx", Vers.coroutines)
    private val androidXFragment = DepGroup("androidx.fragment", "1.2.2", "1.2.0")
    private val kotlin = DepGroup("org.jetbrains.kotlin", Vers.kotlin)

    const val androidxAnnotations = "androidx.annotation:annotation:1.1.0"
    const val appCompat = "androidx.appcompat:appcompat:1.1.0"
    const val constraintLayout = "androidx.constraintlayout:constraintlayout:1.1.3"
    const val lifecycleRuntimeKts = "androidx.lifecycle:lifecycle-runtime-ktx:2.2.0"

    val coroutinesCore = Dep(coroutines, "kotlinx-coroutines-core")
    val fragment = Dep(androidXFragment, "fragment")
    val kotlinReflect = Dep(kotlin, "kotlin-reflect")
    val kotlinStdLib = Dep(kotlin, "kotlin-stdlib-jdk8")

    // Testing
    val coroutinesTest = Dep(coroutines, "kotlinx-coroutines-test")
    val fragmentTesting = Dep(androidXFragment, "fragment-testing")

    const val junit = "junit:junit:4.13"
    const val robolectric = "org.robolectric:robolectric:4.3.1"
}

class DepGroup(val name: String, val version: Ver) {
    constructor(name: String, preferred: String, required: String) : this(
        name,
        Ver(preferred, required)
    )
}

class Dep(
    val name: String,
    val version: Ver
) {
    constructor(group: DepGroup, name: String) : this("${group.name}:$name", group.version)
}

class Ver(
    private val preferred: String?,
    private val required: String
) : Action<MutableVersionConstraint> {
    override fun execute(t: MutableVersionConstraint) {
        t.require(required)
        preferred?.let { t.prefer(it) }
    }
}

fun DependencyHandlerScope.api(dep: Dep) {
    addDependencyTo<ExternalModuleDependency>(this, "api", dep.name) {
        version(dep.version)
    }
}

fun DependencyHandlerScope.compileOnly(dep: Dep) {
    addDependencyTo<ExternalModuleDependency>(this, "compileOnly", dep.name) {
        version(dep.version)
    }
}

fun DependencyHandlerScope.debugImplementation(dep: Dep) {
    addDependencyTo<ExternalModuleDependency>(this, "debugImplementation", dep.name) {
        version(dep.version)
    }
}

fun DependencyHandlerScope.implementation(dep: Dep) {
    addDependencyTo<ExternalModuleDependency>(this, "implementation", dep.name) {
        version(dep.version)
    }
}

fun DependencyHandlerScope.testImplementation(dep: Dep) {
    addDependencyTo<ExternalModuleDependency>(this, "testImplementation", dep.name) {
        version(dep.version)
    }
}
