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

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BroadcastChannel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import java.util.concurrent.ConcurrentHashMap

/** Default backend implementation.
 * @param empress Empress interface that we want to run.
 * @param idProducer Producer for request IDs.
 * @param requestHolder Request holder.
 * @param scope A coroutine scope in which we'll process incoming events.
 * @param storedPatches Patches that were previously stored, and should be used instead patches from [initializer][Empress.initializer].
 */
class DefaultEmpressBackend<Event, Patch : Any, Request> constructor(
    private val empress: Empress<Event, Patch, Request>,
    private val idProducer: RequestIdProducer,
    private val requestHolder: RequestHolder,
    private val scope: CoroutineScope,
    storedPatches: Collection<Patch>?
) : EmpressApi<Event, Patch> {

    private val empressApiChannel = Channel<Msg<Event, Patch>>()

    private var model: Model<Patch> = if (storedPatches == null) {
        Model.from(empress.initializer())
    } else {
        Model.from(storedPatches + empress.initializer(), skipDuplicates = true)
    }

    private val requests: Requests<Event, Request> by lazy {
        DefaultRequests(
            idProducer,
            empress::onRequest,
            requestHolder,
            scope,
            this::send
        )
    }

    private val updates = BroadcastChannel<Update<Event, Patch>>(UPDATES_CHANNEL_CAPACITY)
    private val updatesFlow: Flow<Update<Event, Patch>> = updates.asFlow()

    init {
        scope.launch {
            var isRunning = true
            for (msg in empressApiChannel) {
                @Suppress("UNUSED_VARIABLE")
                val unused: Any = when (msg) {
                    is Msg.Interrupt -> run {
                        isRunning = false
                        msg.response.complete(Unit)
                    }
                    is Msg.WithEvent -> run {
                        processEvent(msg.event)
                        msg.response.complete(Unit)
                    }
                    is Msg.GetModel -> msg.response.complete(model)
                }
                if ((!isRunning && empressApiChannel.isEmpty && requestHolder.isEmpty()) || empressApiChannel.isClosedForReceive) {
                    closeChannels()
                    break
                }
            }
        }.invokeOnCompletion {
            closeChannels(it)
        }
    }

    override suspend fun interrupt() {
        val response = CompletableDeferred<Unit>()
        empressApiChannel.send(Msg.Interrupt(response))
        response.await()
    }

    override suspend fun modelSnapshot(): Model<Patch> {
        val response = CompletableDeferred<Model<Patch>>()
        empressApiChannel.send(Msg.GetModel(response))
        return response.await()
    }

    override suspend fun send(event: Event) {
        val response = CompletableDeferred<Unit>()
        empressApiChannel.send(Msg.WithEvent(event, response))
        response.await()
    }

    override fun updates(): Flow<Update<Event, Patch>> {
        return updatesFlow
    }

    internal fun areChannelsClosed(): Boolean {
        return empressApiChannel.isClosedForSend && updates.isClosedForSend
    }

    private fun closeChannels(throwable: Throwable? = null) {
        empressApiChannel.close(throwable)
        updates.close(throwable)
    }

    private suspend fun processEvent(event: Event) {
        val updatedPatches = empress.onEvent(event, model, requests)
        model = Model.from(model, updatedPatches)

        updates.send(Update(model, event))

        requestHolder.snapshot()
            .filter { !it.isActive && !it.isCompleted }
            .forEach { it.start() }
    }

    companion object {
        private const val UPDATES_CHANNEL_CAPACITY = 16
    }
}

private sealed class Msg<Event, Patch : Any> {
    class Interrupt<Event, Patch : Any>(val response: CompletableDeferred<Unit>) :
        Msg<Event, Patch>()

    data class WithEvent<Event, Patch : Any>(
        val event: Event,
        val response: CompletableDeferred<Unit>
    ) : Msg<Event, Patch>()

    class GetModel<Event, Patch : Any>(val response: CompletableDeferred<Model<Patch>>) :
        Msg<Event, Patch>()
}

/** Default implementation for holding active requests. */
class DefaultRequestHolder : RequestHolder {
    private val requestMap: MutableMap<RequestId, Job> = ConcurrentHashMap()

    override fun isEmpty(): Boolean {
        return requestMap.isEmpty()
    }

    override fun pop(requestId: RequestId): Job? {
        return requestMap.remove(requestId)
    }

    override fun push(requestId: RequestId, requestJob: Job) {
        requestMap[requestId] = requestJob
    }

    override fun snapshot(): Sequence<Job> {
        return requestMap.values.asSequence()
    }
}

/** Default implementation for handling requests. */
internal class DefaultRequests<Event, Request> constructor(
    private val idProducer: RequestIdProducer,
    private val onRequest: suspend (Request) -> Event,
    private val requestHolder: RequestHolder,
    private val scope: CoroutineScope,
    private val sendEvent: suspend (Event) -> Unit
) : Requests<Event, Request> {
    override fun cancel(requestId: RequestId?): Boolean {
        requestId ?: return false
        val requestJob = requestHolder.pop(requestId) ?: return false

        // Since we have a non-null requestJob, it means it hasn't been completed yet
        requestJob.cancel()
        return true
    }

    override fun post(request: Request): RequestId {
        val requestId = idProducer.getNextRequestId()
        val job = scope.launch(start = CoroutineStart.LAZY) {
            val eventFromRequest = onRequest(request)
            if (requestHolder.pop(requestId) != null) {
                sendEvent(eventFromRequest)
            }
        }
        job.invokeOnCompletion {
            // make sure to clean up, in case there were errors in handling the request
            requestHolder.pop(requestId)
        }
        requestHolder.push(requestId, job)
        return requestId
    }
}

/** Default implementation for producing request IDs. */
class DefaultRequestIdProducer : RequestIdProducer {
    private var nextRequestId: Int = 0

    override fun getNextRequestId(): RequestId {
        nextRequestId += 1
        return RequestId(nextRequestId)
    }
}
