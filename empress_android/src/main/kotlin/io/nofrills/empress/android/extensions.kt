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
import io.nofrills.empress.Empress
import io.nofrills.empress.EmpressApi
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

private const val DEFAULT_EMPRESS_ID = "default"

/** Installs an empress instance into the [activity][this].
 * If you install more than one [Empress] into the same activity,
 * you need to use different [IDs][id] for each instance.
 * @param empress Instance of [Empress] to install.
 * @param id A unique ID that will be associated with the [empress].
 * @param retainInstance If true, the [empress] instance will be retained during configuration changes.
 * @param dispatcher A dispatcher to use for [empress].
 * @return An instance of [EmpressApi] for communicating with [empress].
 */
fun <Event, Patch : Any, Request> FragmentActivity.enthrone(
    empress: Empress<Event, Patch, Request>,
    id: String = DEFAULT_EMPRESS_ID,
    retainInstance: Boolean = true,
    dispatcher: CoroutineDispatcher = Dispatchers.Default
): EmpressApi<Event, Patch> {
    return getEmpressInstance(id, empress, supportFragmentManager, retainInstance, dispatcher)
}

/** Installs an empress instance into the [fragment][this].
 * If you install more than one [Empress] into the same fragment,
 * you need to use different [IDs][id] for each instance.
 * @param empress Instance of [Empress] to install.
 * @param id A unique ID that will be associated with the [empress].
 * @param retainInstance If true, the [empress] instance will be retained during configuration changes.
 * @param dispatcher A dispatcher to use for [empress].
 * @return An instance of [EmpressApi] for communicating with [empress].
 */
fun <Event, Patch : Any, Request> Fragment.enthrone(
    empress: Empress<Event, Patch, Request>,
    id: String = DEFAULT_EMPRESS_ID,
    retainInstance: Boolean = true,
    dispatcher: CoroutineDispatcher = Dispatchers.Default
): EmpressApi<Event, Patch> {
    return getEmpressInstance(id, empress, childFragmentManager, retainInstance, dispatcher)
}

private fun <Event, Patch : Any, Request> getEmpressInstance(
    id: String,
    empress: Empress<Event, Patch, Request>,
    fragmentManager: FragmentManager,
    retainInstance: Boolean,
    dispatcher: CoroutineDispatcher
): EmpressApi<Event, Patch> {
    val fragmentTag = "io.nofrills.empress.fragment-$id"
    @Suppress("UNCHECKED_CAST")
    val fragment: EmpressFragment<Event, Patch, Request> =
        fragmentManager.findFragmentByTag(fragmentTag) as EmpressFragment<Event, Patch, Request>?
            ?: EmpressFragment<Event, Patch, Request>().also {
                fragmentManager.beginTransaction().add(it, fragmentTag).commitNow()
            }
    fragment.retainInstance = retainInstance
    fragment.initialize(dispatcher, empress)
    return fragment.empressBackend
}
