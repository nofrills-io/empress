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
    val javaCompat = JavaVersion.VERSION_1_7
    const val jdkVersionNum = 7
    const val minSdkVersion = 21
    const val targetSdkVersion = 28
    const val versionCode = 1
    const val versionName = "0.0.1"
}

object Ver {
    const val coroutines = "1.3.0-RC"
    const val kotlin = "1.3.41"
}

object Deps {
    const val appCompat = "androidx.appcompat:appcompat:1.0.2"
    const val constraintLayout = "androidx.constraintlayout:constraintlayout:1.1.3"
    const val coroutinesAndroid =
        "org.jetbrains.kotlinx:kotlinx-coroutines-android:${Ver.coroutines}"
    const val coroutinesCore = "org.jetbrains.kotlinx:kotlinx-coroutines-core:${Ver.coroutines}"
    const val coroutinesTest = "org.jetbrains.kotlinx:kotlinx-coroutines-test:${Ver.coroutines}"
    const val espressoCore = "androidx.test.espresso:espresso-core:3.2.0"
    const val fragment = "androidx.fragment:fragment:1.0.0"
    const val fragmentTesting = "androidx.fragment:fragment-testing:1.1.0-rc03"
    const val junit = "junit:junit:4.12"
    const val kotlinStdLib = "org.jetbrains.kotlin:kotlin-stdlib-jdk7:${Ver.kotlin}"
    const val robolectric = "org.robolectric:robolectric:4.3"
    const val testRunner = "androidx.test:runner:1.2.0"
}
