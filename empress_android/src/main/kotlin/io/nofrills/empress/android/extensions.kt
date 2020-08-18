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

import androidx.compose.runtime.*
import androidx.fragment.app.*
import io.nofrills.empress.base.*
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow

/** Installs an empress instance into the [activity][this].
 * If an empress with the same [id][empressId] was already installed,
 * this method will return an existing instance.
 * @param empressId Id used to identify an [EmpressApi] instance.
 * @param empressFactory Factory for your [Empress] instance.
 * @param eventDispatcher A dispatcher to use for handling events in [Empress].
 * @param requestDispatcher A dispatcher to use for handling requests in [Empress].
 * @param retainInstance If true, the [Empress] instance will be retained during configuration changes.
 * @return An instance of [EmpressApi] for communicating with [Empress].
 */
fun <E : Empress> FragmentActivity.enthrone(
    empressId: String,
    empressFactory: () -> E,
    eventDispatcher: CoroutineDispatcher = Dispatchers.Main,
    requestDispatcher: CoroutineDispatcher = Dispatchers.Default,
    retainInstance: Boolean = true
): EmpressApi<E> {
    return getEmpressBackendInstance(
        EmpressSpec(
            empressId,
            empressFactory,
            eventDispatcher = eventDispatcher,
            requestDispatcher = requestDispatcher
        ),
        supportFragmentManager,
        retainInstance = retainInstance
    )
}

fun FragmentActivity.dethrone(empressApi: EmpressApi<*>) {
    dethroneEmpress(empressApi, supportFragmentManager)
}

fun FragmentActivity.dethrone(empressId: String) {
    dethroneEmpress(empressId, supportFragmentManager)
}

/** Installs an empress instance into the [fragment][this].
 * If an empress with the same [id][empressId] was already installed,
 * this method will return an existing instance.
 * @param empressId Id used to identify an [EmpressApi] instance.
 * @param empressFactory Factory for your [Empress] instance.
 * @param eventDispatcher A dispatcher to use for handling events in [Empress].
 * @param requestDispatcher A dispatcher to use for handling requests in [Empress].
 * @param retainInstance If true, the [Empress] instance will be retained during configuration changes.
 * @return An instance of [EmpressApi] for communicating with [Empress].
 */
fun <E : Empress> Fragment.enthrone(
    empressId: String,
    empressFactory: () -> E,
    eventDispatcher: CoroutineDispatcher = Dispatchers.Main,
    requestDispatcher: CoroutineDispatcher = Dispatchers.Default,
    retainInstance: Boolean = true
): EmpressApi<E> {
    return getEmpressBackendInstance(
        EmpressSpec(
            empressId,
            empressFactory,
            eventDispatcher = eventDispatcher,
            requestDispatcher = requestDispatcher
        ),
        childFragmentManager,
        retainInstance = retainInstance
    )
}

fun Fragment.dethrone(empressApi: EmpressApi<*>) {
    dethroneEmpress(empressApi, childFragmentManager)
}

fun Fragment.dethrone(empressId: String) {
    dethroneEmpress(empressId, childFragmentManager)
}

private fun <E : Empress> getEmpressBackendInstance(
    empressSpec: EmpressSpec<E>,
    fragmentManager: FragmentManager,
    retainInstance: Boolean
): EmpressBackend<E> {
    val fragmentTag = getEmpressFragmentTag(empressSpec.id)

    @Suppress("UNCHECKED_CAST")
    val fragment: EmpressFragment<E> =
        fragmentManager.findFragmentByTag(fragmentTag) as EmpressFragment<E>?
            ?: EmpressFragment<E>().also {
                fragmentManager.beginTransaction().add(it, fragmentTag).commitNow()
            }
    fragment.retainInstance = retainInstance
    fragment.initialize(empressSpec)
    return fragment.backend
}

private fun dethroneEmpress(empressId: String, fragmentManager: FragmentManager) {
    val fragmentTag = getEmpressFragmentTag(empressId)
    val fragment = fragmentManager.findFragmentByTag(fragmentTag) as EmpressFragment<*>? ?: return
    fragmentManager.beginTransaction()
        .remove(fragment)
        .commitNowAllowingStateLoss()
}

private fun dethroneEmpress(empressApi: EmpressApi<*>, fragmentManager: FragmentManager) {
    val fragments = fragmentManager.fragments
        .filter { it is EmpressFragment<*> && it.backend == empressApi }
    if (fragments.isNotEmpty()) {
        val tx = fragmentManager.beginTransaction()
        fragments.forEach { tx.remove(it) }
        tx.commitNowAllowingStateLoss()
    }
}

private fun getEmpressFragmentTag(empressId: String): String {
    return "io.nofrills.empress.empress-fragment-${empressId}"
}

internal class EmpressSpec<E : Empress>(
    val id: String,
    val empressFactory: () -> E,
    val eventDispatcher: CoroutineDispatcher,
    val requestDispatcher: CoroutineDispatcher
)

private class ObservableMutableState<T>(
    private val flow: MutableStateFlow<T>,
    private val state: MutableState<T>
) : MutableState<T> by state {
    override var value: T
        get() = state.value
        set(value) {
            flow.value = value
            state.value = value
        }
}

fun <E : Any, T : Any> StateListener<E>.state(
    policy: SnapshotMutationPolicy<T> = structuralEqualityPolicy(),
    fn: E.() -> StateDeclaration<T>
): MutableState<T> {
    val flow = fn(empress).flow
    return ObservableMutableState(flow, mutableStateOf(flow.value, policy))
}
