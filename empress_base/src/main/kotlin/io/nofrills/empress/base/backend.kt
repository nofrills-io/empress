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

package io.nofrills.empress.base

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onStart
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.coroutineContext

private val eventInstance = Event()
private val requestInstance = Request()

internal interface BackendFacade<M : Any, S : Any> {
    suspend fun onEvent(fn: EventHandlerContext<M, S>.() -> Unit): Event
    suspend fun onRequest(fn: suspend CoroutineScope.() -> Unit): Request
}

/** Extends [EmpressApi] with additional methods useful in unit tests. */
interface TestEmpressApi<E : Any, M : Any, S : Any> : EmpressApi<E, M, S> {
    /** Interrupts event processing loop. */
    suspend fun interrupt()

    /** Returns current models. */
    fun models(): Collection<M>
}

/** Runs and manages an Empress instance.
 * @param empress Empress instance that we want to run.
 * @param eventHandlerScope A coroutine scope where events will be processed.
 * @param requestHandlerScope A coroutine scope where requests will be processed.
 * @param storedModels Models that were previously stored, which will be used instead of the ones returned from [Empress.initialModels] function.
 * @param initialRequestId The number from which to start generating requests IDs.
 */
@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class EmpressBackend<E : Empress<M, S>, M : Any, S : Any> constructor(
    private val empress: E,
    private val eventHandlerScope: CoroutineScope,
    private val requestHandlerScope: CoroutineScope,
    storedModels: Collection<M>? = null,
    initialRequestId: Long? = null
) : BackendFacade<M, S>, EmpressApi<E, M, S>, TestEmpressApi<E, M, S>, EventHandlerContext<M, S>() {
    private val dynamicLatch = DynamicLatch()

    private val eventChannel = Channel<EventHandlerContext<M, S>.() -> Unit>(Channel.UNLIMITED)

    private var lastRequestId = AtomicLong(initialRequestId ?: 0)

    private val modelChannels = Collections.synchronizedList(arrayListOf<Channel<M>>())

    private val modelMap = makeModelMap(empress.initialModels(), storedModels ?: emptyList())

    private val requestJobMap = ConcurrentHashMap<RequestId, Job>()

    private val signalChannels = Collections.synchronizedList(arrayListOf<Channel<S>>())

    init {
        empress.backend = this
        launchHandlerProcessing()
        eventHandlerScope.coroutineContext[Job]?.invokeOnCompletion {
            closeChannels()
        }
    }

    fun lastRequestId(): Long {
        return lastRequestId.get()
    }

    // BackendFacade

    override suspend fun onEvent(fn: EventHandlerContext<M, S>.() -> Unit): Event {
        if (coroutineContext[SameEventHandler] != null) {
            fn(this)
        } else {
            dynamicLatch.countUp()
            val added = eventChannel.offer(fn)
            assert(added)
        }
        return eventInstance
    }

    override suspend fun onRequest(fn: suspend CoroutineScope.() -> Unit): Request {
        coroutineScope {
            fn(this)
        }
        return requestInstance
    }

    // TestEmpressApi

    override suspend fun interrupt() {
        dynamicLatch.close()
        closeChannels()
    }

    override fun models(): Collection<M> {
        return synchronized(modelMap) { modelMap.values.toList() }
    }

    // EmpressApi

    override fun post(fn: suspend E.() -> Event) {
        eventHandlerScope.launch(
            CoroutineName("empress-api-post"),
            start = CoroutineStart.UNDISPATCHED
        ) {
            fn.invoke(empress)
        }
    }

    override fun signals(): Flow<S> {
        val channel = Channel<S>(Channel.UNLIMITED)
        return channel
            .consumeAsFlow()
            .onStart { signalChannels.add(channel) }
            .onCompletion { signalChannels.remove(channel) }
    }

    override fun updates(withCurrentModels: Boolean): Flow<M> {
        val channel = Channel<M>(Channel.UNLIMITED)
        return channel
            .consumeAsFlow()
            .onStart {
                modelChannels.add(channel)
                if (withCurrentModels) {
                    val currentModels = synchronized(modelMap) { modelMap.values.toList() }
                    currentModels.forEach { emit(it) }
                }
            }
            .onCompletion { modelChannels.remove(channel) }
    }

    // EventHandler

    override fun cancelRequest(requestId: RequestId): Boolean {
        val job = requestJobMap[requestId] ?: return false
        job.cancel()
        return true
    }

    override fun event(fn: suspend () -> Event) {
        eventHandlerScope.launch(
            CoroutineName("empress-event") + SameEventHandler(),
            start = CoroutineStart.UNDISPATCHED
        ) {
            fn()
        }
    }

    @Suppress("UNCHECKED_CAST")
    override fun <T : M> get(modelClass: Class<T>): T = modelMap.getValue(modelClass) as T

    override fun request(fn: suspend () -> Request): RequestId {
        val requestId = getNextRequestId()
        val job = requestHandlerScope.launch(
            CoroutineName("empress-request-$requestId"),
            start = CoroutineStart.LAZY
        ) {
            fn.invoke()
        }
        requestJobMap[requestId] = job
        job.invokeOnCompletion {
            requestJobMap.remove(requestId)
            dynamicLatch.countDown()
        }
        dynamicLatch.countUp()
        job.start()
        return requestId
    }

    override fun signal(signal: S) {
        synchronized(signalChannels) {
            for (chan in signalChannels) {
                val added = chan.offer(signal)
                assert(added)
            }
        }
    }

    override fun update(model: M) {
        modelMap[model::class.java] = model
        synchronized(modelChannels) {
            for (chan in modelChannels) {
                val added = chan.offer(model)
                assert(added)
            }
        }
    }

    // Implementation details

    internal fun areChannelsClosedForSend(): Boolean {
        synchronized(modelChannels) {
            synchronized(signalChannels) {
                return modelChannels.all { it.isClosedForSend } &&
                        signalChannels.all { it.isClosedForSend } &&
                        eventChannel.isClosedForSend
            }
        }
    }

    private fun closeChannels() {
        eventChannel.close()
        modelChannels.toList().forEach { it.close() }
        signalChannels.toList().forEach { it.close() }
    }

    private fun getNextRequestId(): RequestId {
        return RequestId(lastRequestId.incrementAndGet())
    }

    private fun launchHandlerProcessing() {
        eventHandlerScope.launch(CoroutineName("empress-event-receiver")) {
            for (handler in eventChannel) {
                try {
                    handler.invoke(this@EmpressBackend)
                } finally {
                    dynamicLatch.countDown()
                }
            }
        }
    }

    companion object {
        private fun <M : Any> makeModelMap(
            initialModels: Collection<M>,
            storedModels: Collection<M>
        ): ConcurrentHashMap<Class<out M>, M> {
            val modelMap = ConcurrentHashMap<Class<out M>, M>()
            for (model in initialModels) {
                check(modelMap.put(model::class.java, model) == null) {
                    "Model for ${model::class.java} was already added."
                }
            }

            val storedModelsMap = mutableMapOf<Class<out M>, M>()
            for (model in storedModels) {
                check(storedModelsMap.put(model::class.java, model) == null) {
                    "Model for ${model::class.java} was added more than once."
                }
            }

            modelMap.putAll(storedModelsMap)

            return modelMap
        }
    }

    private class SameEventHandler : AbstractCoroutineContextElement(SameEventHandler) {
        companion object Key : CoroutineContext.Key<SameEventHandler>
    }
}
