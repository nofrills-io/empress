package io.nofrills.empress.android

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentManager
import io.nofrills.empress.android.internal.EmpressFragmentNew
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
fun <E: Empress<M, S>,M : Any, S : Any> FragmentActivity.enthrone(
    empressId: String,
    empress: E,
    eventDispatcher: CoroutineDispatcher = Dispatchers.Main,
    requestDispatcher: CoroutineDispatcher = Dispatchers.Default,
    retainInstance: Boolean = true
): EmpressApi<E, M, S> {
    return getEmpressBackendInstance(
        supportFragmentManager,
        retainInstance = retainInstance,
        specFactory = {
            EmpressSpecNew(
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
fun <E: Empress<M, S>,M : Any, S : Any> Fragment.enthrone(
    empressId: String,
    empress: E,
    eventDispatcher: CoroutineDispatcher = Dispatchers.Main,
    requestDispatcher: CoroutineDispatcher = Dispatchers.Default,
    retainInstance: Boolean = true
): EmpressApi<E, M, S> {
    return getEmpressBackendInstance(
        childFragmentManager,
        retainInstance = retainInstance,
        specFactory = {
            EmpressSpecNew(
                empress,
                eventDispatcher = eventDispatcher,
                requestDispatcher = requestDispatcher
            )
        },
        empressId = empressId
    )
}

private fun <E : Empress<M, S>, M : Any, S : Any> getEmpressBackendInstance(
    fragmentManager: FragmentManager,
    retainInstance: Boolean,
    specFactory: () -> EmpressSpecNew<E, M, S>,
    empressId: String
): EmpressBackend<E, M, S> {
    val fragmentTag = "io.nofrills.empress.empress-fragment-${empressId}"
    @Suppress("UNCHECKED_CAST")
    val fragment: EmpressFragmentNew<E, M, S> =
        fragmentManager.findFragmentByTag(fragmentTag) as EmpressFragmentNew<E, M, S>?
            ?: EmpressFragmentNew<E, M, S>().also {
                fragmentManager.beginTransaction().add(it, fragmentTag).commitNow()
            }
    fragment.retainInstance = retainInstance
    fragment.initialize(specFactory)
    return fragment.backend
}

internal class EmpressSpecNew<E: Empress<M, S>, M: Any, S: Any> (
    val empress: E,
    val eventDispatcher: CoroutineDispatcher,
    val requestDispatcher: CoroutineDispatcher
)
