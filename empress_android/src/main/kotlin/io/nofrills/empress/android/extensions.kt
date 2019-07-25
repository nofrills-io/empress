package io.nofrills.empress.android

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentManager
import io.nofrills.empress.Empress
import io.nofrills.empress.EmpressApi
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

private const val DEFAULT_EMPRESS_ID = "default"

fun <Event, Patch : Any, Request> FragmentActivity.empress(
    empress: Empress<Event, Patch, Request>,
    id: String = DEFAULT_EMPRESS_ID,
    retainInstance: Boolean = true,
    dispatcher: CoroutineDispatcher = Dispatchers.Default
): EmpressApi<Event, Patch> {
    return getEmpressInstance(id, empress, supportFragmentManager, retainInstance, dispatcher)
}

fun <Event, Patch : Any, Request> Fragment.empress(
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
