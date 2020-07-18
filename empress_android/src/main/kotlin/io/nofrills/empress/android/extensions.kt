/*
 * Copyright 2020 Mateusz Armatys
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

package io.nofrills.empress.android

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentManager
import io.nofrills.empress.base.Empress
import io.nofrills.empress.base.EmpressApi
import io.nofrills.empress.base.EmpressBackend
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

/** Installs an empress instance into the [activity][this].
 * If an empress with the same [id][empressId] was already installed,
 * this method will return an existing instance.
 * @param empressId Id used to identify an [EmpressApi] instance.
 * @param empress Instance of [Empress] to install.
 * @param eventDispatcher A dispatcher to use for handling events in [empress].
 * @param requestDispatcher A dispatcher to use for handling requests in [empress].
 * @param retainInstance If true, the [empress] instance will be retained during configuration changes.
 * @return An instance of [EmpressApi] for communicating with [empress].
 */
fun <E : Empress<S>, S : Any> FragmentActivity.enthrone(
    empressId: String,
    empress: E,
    eventDispatcher: CoroutineDispatcher = Dispatchers.Main,
    requestDispatcher: CoroutineDispatcher = Dispatchers.Default,
    retainInstance: Boolean = true
): EmpressApi<E, S> {
    return getEmpressBackendInstance(
        supportFragmentManager,
        retainInstance = retainInstance,
        specFactory = {
            EmpressSpec(
                empress,
                eventDispatcher = eventDispatcher,
                requestDispatcher = requestDispatcher
            )
        },
        empressId = empressId
    )
}

/** Installs an empress instance into the [fragment][this].
 * If an empress with the same [id][empressId] was already installed,
 * this method will return an existing instance.
 * @param empressId Id used to identify an [EmpressApi] instance.
 * @param empress Instance of [Empress] to install.
 * @param eventDispatcher A dispatcher to use for handling events in [empress].
 * @param requestDispatcher A dispatcher to use for handling requests in [empress].
 * @param retainInstance If true, the [empress] instance will be retained during configuration changes.
 * @return An instance of [EmpressApi] for communicating with [empress].
 */
fun <E : Empress<S>, S : Any> Fragment.enthrone(
    empressId: String,
    empress: E,
    eventDispatcher: CoroutineDispatcher = Dispatchers.Main,
    requestDispatcher: CoroutineDispatcher = Dispatchers.Default,
    retainInstance: Boolean = true
): EmpressApi<E, S> {
    return getEmpressBackendInstance(
        childFragmentManager,
        retainInstance = retainInstance,
        specFactory = {
            EmpressSpec(
                empress,
                eventDispatcher = eventDispatcher,
                requestDispatcher = requestDispatcher
            )
        },
        empressId = empressId
    )
}

private fun <E : Empress<S>, S : Any> getEmpressBackendInstance(
    fragmentManager: FragmentManager,
    retainInstance: Boolean,
    specFactory: () -> EmpressSpec<E, S>,
    empressId: String
): EmpressBackend<E, S> {
    val fragmentTag = "io.nofrills.empress.empress-fragment-${empressId}"

    @Suppress("UNCHECKED_CAST")
    val fragment: EmpressFragment<E, S> =
        fragmentManager.findFragmentByTag(fragmentTag) as EmpressFragment<E, S>?
            ?: EmpressFragment<E, S>().also {
                fragmentManager.beginTransaction().add(it, fragmentTag).commitNow()
            }
    fragment.retainInstance = retainInstance
    fragment.initialize(specFactory)
    return fragment.backend
}

internal class EmpressSpec<E : Empress<S>, S : Any>(
    val empress: E,
    val eventDispatcher: CoroutineDispatcher,
    val requestDispatcher: CoroutineDispatcher
)
