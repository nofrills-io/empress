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

import org.gradle.api.JavaVersion

object EmpressLib {
    const val compileSdkVersion = 28
    val javaCompat = JavaVersion.VERSION_1_8
    const val jdkVersionNum = 8
    const val jvmTarget = "1.8"
    const val mavPluginVersion = "1.0.4"
    const val minSdkVersion = 21
    const val targetSdkVersion = 28
    const val versionCode = 4
    const val versionName = "0.4.0"
}

object Ver {
    const val androidxFragment = "1.2.0-rc01"
    const val coroutines = "1.3.2"
    const val kotlin = "1.3.50"
}

object Deps {
    const val androidxAnnotations = "androidx.annotation:annotation:1.1.0"
    const val appCompat = "androidx.appcompat:appcompat:1.1.0"
    const val constraintLayout = "androidx.constraintlayout:constraintlayout:1.1.3"
    const val coroutinesAndroid =
        "org.jetbrains.kotlinx:kotlinx-coroutines-android:${Ver.coroutines}"
    const val coroutinesCore = "org.jetbrains.kotlinx:kotlinx-coroutines-core:${Ver.coroutines}"
    const val fragment = "androidx.fragment:fragment:${Ver.androidxFragment}"
    const val kotlinStdLib = "org.jetbrains.kotlin:kotlin-stdlib-jdk8:${Ver.kotlin}"

    // Testing
    const val coroutinesTest = "org.jetbrains.kotlinx:kotlinx-coroutines-test:${Ver.coroutines}"
    const val fragmentTesting = "androidx.fragment:fragment-testing:${Ver.androidxFragment}"
    const val junit = "junit:junit:4.12"
    const val robolectric = "org.robolectric:robolectric:4.3.1"
}
