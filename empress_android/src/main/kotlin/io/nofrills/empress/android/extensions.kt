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

private val DEFAULT_EMPRESS_DISPATCHER = Dispatchers.Default

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
fun <E : Any, M : Any, R : Any> FragmentActivity.enthrone(
    empressId: String,
    empress: Empress<E, M, R>,
    eventDispatcher: CoroutineDispatcher = DEFAULT_EMPRESS_DISPATCHER,
    requestDispatcher: CoroutineDispatcher = DEFAULT_EMPRESS_DISPATCHER,
    retainInstance: Boolean = true
): EmpressApi<E, M> {
    return getRulerBackendInstance(
        supportFragmentManager,
        retainInstance = retainInstance,
        rulerFactory = {
            EmpressSpec(
                empress,
                eventDispatcher = eventDispatcher,
                requestDispatcher = requestDispatcher
            )
        },
        rulerFragmentFactory = { EmpressFragment<E, M, R>() },
        rulerId = empressId
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
fun <E : Any, M : Any, R : Any> Fragment.enthrone(
    empressId: String,
    empress: Empress<E, M, R>,
    eventDispatcher: CoroutineDispatcher = DEFAULT_EMPRESS_DISPATCHER,
    requestDispatcher: CoroutineDispatcher = DEFAULT_EMPRESS_DISPATCHER,
    retainInstance: Boolean = true
): EmpressApi<E, M> {
    return getRulerBackendInstance(
        childFragmentManager,
        retainInstance = retainInstance,
        rulerFactory = {
            EmpressSpec(
                empress,
                eventDispatcher = eventDispatcher,
                requestDispatcher = requestDispatcher
            )
        },
        rulerFragmentFactory = { EmpressFragment<E, M, R>() },
        rulerId = empressId
    )
}

/** Installs an [mutableEmpress] instance into the [activity][this].
 * If an mutableEmpress with the same [id][mutableEmpressId] was already installed,
 * this method will return an existing instance.
 * @param mutableEmpressId Id used to identify an [MutableEmpressApi] instance.
 * @param mutableEmpress Instance of [MutableEmpress] to install.
 * @param eventDispatcher A dispatcher to use for handling events in [mutableEmpress].
 * @param requestDispatcher A dispatcher to use for handling requests in [mutableEmpress].
 * @param retainInstance If true, the [mutableEmpress] instance will be retained during configuration changes.
 * @return An instance of [MutableEmpressApi] for communicating with [mutableEmpress].
 */
fun <E : Any, M : Any, R : Any> FragmentActivity.enthrone(
    mutableEmpressId: String,
    mutableEmpress: MutableEmpress<E, M, R>,
    eventDispatcher: CoroutineDispatcher = DEFAULT_EMPRESS_DISPATCHER,
    requestDispatcher: CoroutineDispatcher = DEFAULT_EMPRESS_DISPATCHER,
    retainInstance: Boolean = true
): MutableEmpressApi<E, M> {
    return getRulerBackendInstance(
        supportFragmentManager,
        retainInstance = retainInstance,
        rulerFactory = {
            MutableEmpressSpec(
                mutableEmpress,
                eventDispatcher = eventDispatcher,
                requestDispatcher = requestDispatcher
            )
        },
        rulerFragmentFactory = { MutableEmpressFragment<E, M, R>() },
        rulerId = mutableEmpressId
    )
}

/** Installs an [mutableEmpress] instance into the [fragment][this].
 * If an mutableEmpress with the same [id][mutableEmpressId] was already installed,
 * this method will return an existing instance.
 * @param mutableEmpressId Id used to identify an [MutableEmpressApi] instance.
 * @param mutableEmpress Instance of [MutableEmpress] to install.
 * @param eventDispatcher A dispatcher to use for handling events in [mutableEmpress].
 * @param requestDispatcher A dispatcher to use for handling requests in [mutableEmpress].
 * @param retainInstance If true, the [mutableEmpress] instance will be retained during configuration changes.
 * @return An instance of [MutableEmpressApi] for communicating with [mutableEmpress].
 */
fun <E : Any, M : Any, R : Any> Fragment.enthrone(
    mutableEmpressId: String,
    mutableEmpress: MutableEmpress<E, M, R>,
    eventDispatcher: CoroutineDispatcher = DEFAULT_EMPRESS_DISPATCHER,
    requestDispatcher: CoroutineDispatcher = DEFAULT_EMPRESS_DISPATCHER,
    retainInstance: Boolean = true
): MutableEmpressApi<E, M> {
    return getRulerBackendInstance(
        childFragmentManager,
        retainInstance = retainInstance,
        rulerFactory = {
            MutableEmpressSpec(
                mutableEmpress,
                eventDispatcher = eventDispatcher,
                requestDispatcher = requestDispatcher
            )
        },
        rulerFragmentFactory = { MutableEmpressFragment<E, M, R>() },
        rulerId = mutableEmpressId
    )
}

private fun <E : Any, M : Any, R : Any, B : RulerBackend<E, M, R>, RL : Ruler<E, M, R>, F : RulerFragment<E, M, R, B, RL>> getRulerBackendInstance(
    fragmentManager: FragmentManager,
    retainInstance: Boolean,
    rulerFactory: () -> RulerSpec<E, M, R, RL>,
    rulerFragmentFactory: () -> F,
    rulerId: String
): B {
    val fragmentTag = "io.nofrills.empress.ruler-fragment-${rulerId}"
    @Suppress("UNCHECKED_CAST")
    val fragment: F =
        fragmentManager.findFragmentByTag(fragmentTag) as F?
            ?: rulerFragmentFactory().also {
                fragmentManager.beginTransaction().add(it, fragmentTag).commitNow()
            }
    fragment.retainInstance = retainInstance
    fragment.initialize(rulerFactory)
    return fragment.backend
}

internal abstract class RulerSpec<E : Any, M : Any, R : Any, RL : Ruler<E, M, R>>(
    internal val ruler: RL,
    internal val eventDispatcher: CoroutineDispatcher,
    internal val requestDispatcher: CoroutineDispatcher
)

private class EmpressSpec<E : Any, M : Any, R : Any>(
    empress: Empress<E, M, R>,
    eventDispatcher: CoroutineDispatcher,
    requestDispatcher: CoroutineDispatcher
) : RulerSpec<E, M, R, Empress<E, M, R>>(
    ruler = empress,
    eventDispatcher = eventDispatcher,
    requestDispatcher = requestDispatcher
)

private class MutableEmpressSpec<E : Any, M : Any, R : Any>(
    mutableEmpress: MutableEmpress<E, M, R>,
    eventDispatcher: CoroutineDispatcher,
    requestDispatcher: CoroutineDispatcher
) : RulerSpec<E, M, R, MutableEmpress<E, M, R>>(
    ruler = mutableEmpress,
    eventDispatcher = eventDispatcher,
    requestDispatcher = requestDispatcher
)
