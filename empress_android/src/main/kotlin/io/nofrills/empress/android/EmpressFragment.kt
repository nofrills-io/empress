package io.nofrills.empress.android

import android.os.Bundle
import android.os.Parcelable
import androidx.fragment.app.Fragment
import io.nofrills.empress.EmpressBackend

internal class EmpressFragment<Event, Patch : Any> : Fragment() {
    internal lateinit var empressBackend: EmpressBackend<Event, Patch>
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
        for (patch in empressBackend.model) {
            if (patch is Parcelable) {
                parcelablePatches.add(patch)
            }
        }
        outState.putParcelableArrayList(PATCHES_KEY, parcelablePatches)
    }

    override fun onDestroy() {
        empressBackend.onDestroy()
        super.onDestroy()
    }

    internal fun initialize(fn: (Collection<Patch>?) -> EmpressBackend<Event, Patch>) {
        if (!this::empressBackend.isInitialized) {
            empressBackend = fn(storedPatches)
            empressBackend.onCreate()
        }
    }

    companion object {
        private const val PATCHES_KEY = "io.nofrills.patches"
    }
}
