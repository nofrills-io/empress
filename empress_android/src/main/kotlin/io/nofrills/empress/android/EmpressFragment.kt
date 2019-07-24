package io.nofrills.empress.android

import android.os.Bundle
import android.os.Parcelable
import androidx.fragment.app.Fragment
import io.nofrills.empress.DefaultEmpressBackend
import io.nofrills.empress.DefaultRequestHolder
import io.nofrills.empress.DefaultRequestIdProducer
import io.nofrills.empress.Empress
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking

internal class EmpressFragment<Event, Patch : Any, Request> : Fragment() {
    private val job = Job()
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default + job)

    internal lateinit var empressBackend: DefaultEmpressBackend<Event, Patch, Request>
    private var storedPatches: ArrayList<Patch>? = null

    init {
        retainInstance = true
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        @Suppress("UNCHECKED_CAST")
        storedPatches =
            savedInstanceState?.getParcelableArrayList<Parcelable>(PATCHES_KEY) as ArrayList<Patch>?
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)

        val model = runBlocking { empressBackend.modelSnapshot() }
        val parcelablePatches = arrayListOf<Parcelable>()
        for (patch in model.all()) {
            if (patch is Parcelable) {
                parcelablePatches.add(patch)
            }
        }
        outState.putParcelableArrayList(PATCHES_KEY, parcelablePatches)
        // TODO being here, empress should pause processing
        // and resume at some point later;
        // this is because if we really will terminate, any further changes
        // to the model won't be persisted
    }

    override fun onDestroy() {
        job.cancel()
        super.onDestroy()
    }

    internal fun initialize(empress: Empress<Event, Patch, Request>) {
        if (!this::empressBackend.isInitialized) {
            val requestIdProducer = DefaultRequestIdProducer()
            val requestStorage = DefaultRequestHolder()
            empressBackend = DefaultEmpressBackend(
                empress,
                requestIdProducer,
                requestStorage,
                scope,
                storedPatches
            )
        }
    }

    companion object {
        private const val PATCHES_KEY = "io.nofrills.updated"
    }
}
