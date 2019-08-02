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

import android.os.Bundle
import android.os.Parcelable
import androidx.fragment.app.Fragment
import io.nofrills.empress.EmpressBackend
import io.nofrills.empress.Empress
import kotlinx.coroutines.*
import kotlin.coroutines.CoroutineContext

internal class EmpressFragment<Event, Patch : Any, Request> : Fragment(), CoroutineScope {
    override lateinit var coroutineContext: CoroutineContext
        private set

    private val job = Job()

    internal lateinit var empressBackend: EmpressBackend<Event, Patch, Request>
    private var storedPatches: ArrayList<Patch>? = null

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
    }

    override fun onDestroy() {
        job.cancel()
        super.onDestroy()
    }

    internal fun initialize(
        dispatcher: CoroutineDispatcher,
        empress: Empress<Event, Patch, Request>
    ) {
        if (!this::empressBackend.isInitialized) {
            coroutineContext = dispatcher + job
            empressBackend = EmpressBackend(
                empress,
                this@EmpressFragment,
                storedPatches
            )
        }
    }

    companion object {
        private const val PATCHES_KEY = "io.nofrills.updated"
    }
}
