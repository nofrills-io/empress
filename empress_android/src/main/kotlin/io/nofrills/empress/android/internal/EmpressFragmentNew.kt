package io.nofrills.empress.android.internal

import android.os.Bundle
import android.os.Parcelable
import androidx.fragment.app.Fragment
import io.nofrills.empress.android.EmpressSpecNew
import io.nofrills.empress.base.Empress
import io.nofrills.empress.base.EmpressBackend
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import java.util.ArrayList

class EmpressFragmentNew<E : Empress<M, S>, M : Any, S : Any> : Fragment() {
    internal lateinit var backend: EmpressBackend<E, M, S>
        private set
    private val job = Job()
    private var storedHandlerId: Long? = null
    private var storedModels: ArrayList<M>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        storedHandlerId = savedInstanceState?.let {
            if (it.containsKey(HANDLER_ID_KEY)) {
                it.getLong(HANDLER_ID_KEY)
            } else {
                null
            }
        }
        @Suppress("UNCHECKED_CAST")
        storedModels =
            savedInstanceState?.getParcelableArrayList<Parcelable>(MODELS_KEY) as ArrayList<M>?
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)

        val parcelablePatches = arrayListOf<Parcelable>()
        val models = backend.models()
        for (model in models) {
            if (model is Parcelable) {
                parcelablePatches.add(model)
            }
        }
        outState.putLong(HANDLER_ID_KEY, backend.lastHandlerId())
        outState.putParcelableArrayList(MODELS_KEY, parcelablePatches)
    }

    override fun onDestroy() {
        job.cancel()
        super.onDestroy()
    }

    internal fun initialize(specFactory: () -> EmpressSpecNew<E, M, S>) {
        if (!this::backend.isInitialized) {
           val result = specFactory()
           val eventHandlerScope = CoroutineScope(result.eventDispatcher + job)
           val requestHandlerScope = CoroutineScope(result.requestDispatcher + job)
            backend = EmpressBackend(result.empress, eventHandlerScope, requestHandlerScope, storedModels, storedHandlerId)
        }
    }

    companion object {
        private const val HANDLER_ID_KEY = "io.nofrills.empress.android.handler_id"
        private const val MODELS_KEY = "io.nofrills.empress.android.models"
    }
}
