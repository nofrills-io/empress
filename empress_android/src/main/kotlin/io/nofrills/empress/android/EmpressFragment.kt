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

import android.os.Bundle
import android.os.Parcelable
import androidx.fragment.app.Fragment
import io.nofrills.empress.base.Empress
import io.nofrills.empress.base.EmpressBackend
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import java.util.ArrayList

internal class EmpressFragment<E : Empress> : Fragment() {
    lateinit var backend: EmpressBackend<E>
        private set
    private val job = Job()
    private var storedHandlerId: Long? = null
    private var storedModels: ArrayList<Any>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        savedInstanceState?.let {
            require(it.containsKey(REQUEST_ID_KEY))
            require(it.containsKey(MODELS_KEY))

            storedHandlerId = it.getLong(REQUEST_ID_KEY)
            @Suppress("UNCHECKED_CAST")
            storedModels = it.getParcelableArrayList<Parcelable>(MODELS_KEY) as ArrayList<Any>?
        }
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
        outState.putLong(REQUEST_ID_KEY, backend.lastRequestId())
        outState.putParcelableArrayList(MODELS_KEY, parcelablePatches)
    }

    override fun onDestroy() {
        job.cancel()
        super.onDestroy()
    }

    fun initialize(specFactory: () -> EmpressSpec<E>) {
        if (!this::backend.isInitialized) {
            val result = specFactory()
            val eventHandlerScope = CoroutineScope(result.eventDispatcher + job)
            val requestHandlerScope = CoroutineScope(result.requestDispatcher + job)
            backend = EmpressBackend(
                result.empress,
                eventHandlerScope,
                requestHandlerScope,
                storedModels,
                storedHandlerId
            )
        }
    }

    companion object {
        private const val REQUEST_ID_KEY = "io.nofrills.empress.android.request_id"
        private const val MODELS_KEY = "io.nofrills.empress.android.models"
    }
}
