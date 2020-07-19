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

internal class EmpressFragment<E : Empress> : Fragment() {
    lateinit var backend: EmpressBackend<E>
        private set
    private val job = Job()
    private var storedHandlerId: Long? = null
    private var storedModels: Map<String, Any>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        savedInstanceState?.let { bundle ->
            require(bundle.containsKey(REQUEST_ID_KEY))

            storedHandlerId = bundle.getLong(REQUEST_ID_KEY)
            storedModels = bundle.keySet()
                .filter { it.startsWith(MODELS_KEY) }
                .map { it.removePrefix(MODELS_KEY) to bundle.get(it) }
                .toMap()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)

        val modelsMap = backend.loadedModels()
        for ((key, model) in modelsMap) {
            if (model is Parcelable) {
                outState.putParcelable("$MODELS_KEY$key", model)
            }
        }
        outState.putLong(REQUEST_ID_KEY, backend.lastRequestId())
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
        private const val MODELS_KEY = "io.nofrills.empress.android.model."
    }
}
