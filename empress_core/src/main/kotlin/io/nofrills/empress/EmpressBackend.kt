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

package io.nofrills.empress

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BroadcastChannel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex

/** Runs and manages an [Empress] instance.
 * @param empress Empress interface that we want to run.
 * @param scope A coroutine scope where events and requests will be processed.
 * @param storedPatches Patches that were previously stored, and should be used instead patches from [initializer][Empress.initializer].
 */
class EmpressBackend<Event, Patch : Any, Request> constructor(
    private val empress: Empress<Event, Patch, Request>,
    private val scope: CoroutineScope,
    storedPatches: Collection<Patch>? = null
) : EmpressApi<Event, Patch> {

    private val idProducer = RequestIdProducer()

    /** If interruption was requested, the mutex will be locked. */
    private val interruption = Mutex()

    private val modelHolder = ModelHolder(
        if (storedPatches == null) {
            Model.from(empress.initializer())
        } else {
            Model.from(
                storedPatches + empress.initializer(),
                skipDuplicates = true
            )
        }
    )

    private val requestHolder = RequestHolder()

    private val requests: Requests<Event, Request> by lazy {
        RequestsImpl(
            idProducer,
            empress::onRequest,
            requestHolder,
            scope,
            this::processEvent
        )
    }

    private val updates = BroadcastChannel<Update<Event, Patch>>(UPDATES_CHANNEL_CAPACITY)
    private val updatesFlow: Flow<Update<Event, Patch>> = updates.asFlow()

    init {
        scope.coroutineContext[Job]?.invokeOnCompletion {
            updates.close(it)
        }
    }

    override fun interrupt() {
        interruption.tryLock()
        interruptIfNeeded()
    }

    override suspend fun modelSnapshot(): Model<Patch> {
        return modelHolder.get()
    }

    override fun send(event: Event) = scope.launch(start = CoroutineStart.UNDISPATCHED) {
        processEvent(event)
    }

    override fun updates(): Flow<Update<Event, Patch>> {
        return updatesFlow
    }

    internal fun areChannelsClosed(): Boolean {
        return updates.isClosedForSend
    }

    fun hasEqualClass(empressClass: Class<*>): Boolean {
        return empress::class.java == empressClass
    }

    private fun interruptIfNeeded() {
        if (interruption.isLocked && requestHolder.isEmpty()) {
            updates.close()
        }
    }

    private suspend fun processEvent(event: Event) {
        val model = modelHolder.update { model ->
            val updatedPatches = empress.onEvent(event, model, requests)
            Model.from(model, updatedPatches)
        }
        updates.send(Update(model, event))

        interruptIfNeeded()

        requestHolder.snapshot()
            .filter { !it.isActive && !it.isCompleted }
            .forEach { it.start() }
    }

    companion object {
        private const val UPDATES_CHANNEL_CAPACITY = 16
    }
}