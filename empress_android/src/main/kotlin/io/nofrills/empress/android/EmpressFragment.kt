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
import io.nofrills.empress.base.StoredDataLoader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job

internal class EmpressFragment<E : Empress> : Fragment(), StoredDataLoader {
    lateinit var backend: EmpressBackend<E>
        private set

    private val job = Job()

    private var state: Bundle? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        state = savedInstanceState
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        storeEmpress(backend, outState)
    }

    private fun storeEmpress(empressBackend: EmpressBackend<*>, outState: Bundle) {
        val bundle = bundleEmpressData(empressBackend)
        outState.putBundle("$STATE_KEY${empressBackend.id}", bundle)

        for ((_, b) in empressBackend.getChildEmpressBackends()) {
            storeEmpress(b, outState)
        }
    }

    private fun bundleEmpressData(empressBackend: EmpressBackend<*>): Bundle {
        val bundle = Bundle()
        val modelsMap = empressBackend.loadedModels()
        for ((key, model) in modelsMap) {
            if (model is Parcelable) {
                bundle.putParcelable("$MODELS_KEY$key", model)
            }
        }
        bundle.putLong(REQUEST_ID_KEY, empressBackend.lastRequestId())
        return bundle
    }

    override fun onDestroy() {
        job.cancel()
        super.onDestroy()
    }

    fun initialize(empressSpec: EmpressSpec<E>) {
        if (!this::backend.isInitialized) {
            val eventHandlerScope = CoroutineScope(empressSpec.eventDispatcher + job)
            val requestHandlerScope = CoroutineScope(empressSpec.requestDispatcher + job)
            backend = EmpressBackend(
                empressSpec.id,
                empressSpec.empress,
                eventHandlerScope,
                requestHandlerScope,
                this
            )
        }
    }

    // StoredDataLoader

    override fun loadStoredModels(empressBackendId: String): Map<String, Any>? {
        val state = state ?: return null
        val bundleKey = "$STATE_KEY$empressBackendId"
        val bundle = state.getBundle(bundleKey) ?: return null
        val modelsMap = bundle.keySet()
            .filter { it.startsWith(MODELS_KEY) }
            .map { it to bundle.get(it) }
            .onEach { bundle.remove(it.first) }
            .map { it.first.removePrefix(MODELS_KEY) to it.second }
            .toMap()
        if (bundle.isEmpty) {
            state.remove(bundleKey)
        }
        return modelsMap
    }

    override fun loadStoredRequestId(empressBackendId: String): Long? {
        val state = state ?: return null
        val bundleKey = "$STATE_KEY$empressBackendId"
        val bundle = state.getBundle(bundleKey) ?: return null
        return if (bundle.containsKey(REQUEST_ID_KEY)) {
            val requestId = bundle.getLong(REQUEST_ID_KEY)
            bundle.remove(REQUEST_ID_KEY)
            if (bundle.isEmpty) {
                state.remove(bundleKey)
            }
            requestId
        } else null
    }

    companion object {
        private const val MODELS_KEY = "io.nofrills.empress.android.model."
        private const val REQUEST_ID_KEY = "io.nofrills.empress.android.request_id"
        private const val STATE_KEY = "io.nofrills.empress.android.state."
    }
}
