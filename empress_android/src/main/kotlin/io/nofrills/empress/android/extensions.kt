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

package io.nofrills.empress.android

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentManager
import io.nofrills.empress.*
import io.nofrills.empress.android.internal.EmpressFragment
import io.nofrills.empress.android.internal.MutableEmpressFragment
import io.nofrills.empress.android.internal.RulerFragment
import io.nofrills.empress.backend.RulerBackend
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

/** Installs an empress instance into the [activity][this].
 * If an empress with the same [id][Empress.id] was already installed,
 * this method will return an existing instance.
 * @param empress Instance of [Empress] to install.
 * @param eventDispatcher A dispatcher to use for handling events in [empress].
 * @param requestDispatcher A dispatcher to use for handling requests in [empress].
 * @param retainInstance If true, the [empress] instance will be retained during configuration changes.
 * @return An instance of [EmpressApi] for communicating with [empress].
 */
fun <E : Any, M : Any, R : Any> FragmentActivity.enthrone(
    empress: Empress<E, M, R>,
    eventDispatcher: CoroutineDispatcher = Dispatchers.Default,
    requestDispatcher: CoroutineDispatcher = Dispatchers.Default,
    retainInstance: Boolean = true
): EmpressApi<E, M> {
    return getRulerBackendInstance(
        empress,
        supportFragmentManager,
        eventDispatcher = eventDispatcher,
        requestDispatcher = requestDispatcher,
        retainInstance = retainInstance
    ) { EmpressFragment<E, M, R>() }
}

/** Installs an empress instance into the [fragment][this].
 * If an empress with the same [id][Empress.id] was already installed,
 * this method will return an existing instance.
 * @param empress Instance of [Empress] to install.
 * @param eventDispatcher A dispatcher to use for handling events in [empress].
 * @param requestDispatcher A dispatcher to use for handling requests in [empress].
 * @param retainInstance If true, the [empress] instance will be retained during configuration changes.
 * @return An instance of [EmpressApi] for communicating with [empress].
 */
fun <E : Any, M : Any, R : Any> Fragment.enthrone(
    empress: Empress<E, M, R>,
    eventDispatcher: CoroutineDispatcher = Dispatchers.Default,
    requestDispatcher: CoroutineDispatcher = Dispatchers.Default,
    retainInstance: Boolean = true
): EmpressApi<E, M> {
    return getRulerBackendInstance(
        empress,
        childFragmentManager,
        eventDispatcher = eventDispatcher,
        requestDispatcher = requestDispatcher,
        retainInstance = retainInstance
    ) { EmpressFragment<E, M, R>() }
}

/** Installs an [mutableEmpress] instance into the [activity][this].
 * If an mutableEmpress with the same [id][MutableEmpress.id] was already installed,
 * this method will return an existing instance.
 * @param mutableEmpress Instance of [MutableEmpress] to install.
 * @param eventDispatcher A dispatcher to use for handling events in [mutableEmpress].
 * @param requestDispatcher A dispatcher to use for handling requests in [mutableEmpress].
 * @param retainInstance If true, the [mutableEmpress] instance will be retained during configuration changes.
 * @return An instance of [MutableEmpressApi] for communicating with [mutableEmpress].
 */
fun <E : Any, M : Any, R : Any> FragmentActivity.enthrone(
    mutableEmpress: MutableEmpress<E, M, R>,
    eventDispatcher: CoroutineDispatcher = Dispatchers.Main,
    requestDispatcher: CoroutineDispatcher = Dispatchers.Default,
    retainInstance: Boolean = true
): MutableEmpressApi<E, M> {
    return getRulerBackendInstance(
        mutableEmpress,
        supportFragmentManager,
        eventDispatcher = eventDispatcher,
        requestDispatcher = requestDispatcher,
        retainInstance = retainInstance
    ) { MutableEmpressFragment<E, M, R>() }
}

/** Installs an [mutableEmpress] instance into the [fragment][this].
 * If an mutableEmpress with the same [id][MutableEmpress.id] was already installed,
 * this method will return an existing instance.
 * @param mutableEmpress Instance of [MutableEmpress] to install.
 * @param eventDispatcher A dispatcher to use for handling events in [mutableEmpress].
 * @param requestDispatcher A dispatcher to use for handling requests in [mutableEmpress].
 * @param retainInstance If true, the [mutableEmpress] instance will be retained during configuration changes.
 * @return An instance of [MutableEmpressApi] for communicating with [mutableEmpress].
 */
fun <E : Any, M : Any, R : Any> Fragment.enthrone(
    mutableEmpress: MutableEmpress<E, M, R>,
    eventDispatcher: CoroutineDispatcher = Dispatchers.Main,
    requestDispatcher: CoroutineDispatcher = Dispatchers.Default,
    retainInstance: Boolean = true
): MutableEmpressApi<E, M> {
    return getRulerBackendInstance(
        mutableEmpress,
        childFragmentManager,
        eventDispatcher = eventDispatcher,
        requestDispatcher = requestDispatcher,
        retainInstance = retainInstance
    ) { MutableEmpressFragment<E, M, R>() }
}

private fun <E : Any, M : Any, R : Any, B : RulerBackend<E, M, R>, RL : Ruler<E, M, R>, F : RulerFragment<E, M, R, B, RL>> getRulerBackendInstance(
    ruler: RL,
    fragmentManager: FragmentManager,
    eventDispatcher: CoroutineDispatcher,
    requestDispatcher: CoroutineDispatcher,
    retainInstance: Boolean,
    rulerFragmentFactory: () -> F
): B {
    val fragmentTag = "io.nofrills.empress.ruler-fragment-${ruler.id()}"
    @Suppress("UNCHECKED_CAST")
    val fragment: F =
        fragmentManager.findFragmentByTag(fragmentTag) as F?
            ?: rulerFragmentFactory().also {
                fragmentManager.beginTransaction().add(it, fragmentTag).commitNow()
            }
    fragment.retainInstance = retainInstance
    fragment.initialize(
        ruler,
        eventDispatcher = eventDispatcher,
        requestDispatcher = requestDispatcher
    )
    return fragment.backend
}
