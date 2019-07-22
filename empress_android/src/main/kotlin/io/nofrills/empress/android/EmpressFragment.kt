package io.nofrills.empress.android

import android.os.Bundle
import android.os.Parcelable
import androidx.fragment.app.Fragment
import io.nofrills.empress.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job

internal class EmpressFragment<Event, Patch : Any, Request> : Fragment() {
    private val job = Job()
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Main + job)

    internal lateinit var empressBackend: DefaultEmpressBackend<Event, Patch, Request>
    private val requestIdProducer by lazy { DefaultRequestIdProducer() }
    private val requestStorage by lazy { DefaultRequestHolder() }
    private var storedPatches: ArrayList<Patch>? = null

    init {
        retainInstance = true
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        storedPatches =
            savedInstanceState?.getParcelableArrayList<Parcelable>(PATCHES_KEY) as ArrayList<Patch>?
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        val parcelablePatches = arrayListOf<Parcelable>()
        for (patch in empressBackend.model.all()) {
            if (patch is Parcelable) {
                parcelablePatches.add(patch)
            }
        }
        outState.putParcelableArrayList(PATCHES_KEY, parcelablePatches)
    }

    override fun onDestroy() {
        job.cancel()
        super.onDestroy()
    }

    internal fun initialize(empress: Empress<Event, Patch, Request>) {
        if (!this::empressBackend.isInitialized) {
            empressBackend = DefaultEmpressBackend(
                scope.coroutineContext,
                empress,
                requestIdProducer,
                requestStorage,
                storedPatches
            )
        }
    }

    companion object {
        private const val PATCHES_KEY = "io.nofrills.updated"
    }
}
