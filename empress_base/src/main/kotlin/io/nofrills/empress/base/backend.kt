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

private val handlerInstance = Event()

internal interface BackendFacade<M : Any, S : Any> {
    fun onEvent(fn: EventHandlerContext<M, S>.() -> Unit): Event
    fun onRequest(fn: suspend CoroutineScope.() -> Unit): Request
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
class EmpressBackend<E : Empress<M, S>, M : Any, S : Any>(
    private val empress: E,
    private val eventHandlerScope: CoroutineScope,
    private val requestHandlerScope: CoroutineScope,
    storedModels: Collection<M>? = null,
    initialRequestId: RequestId? = null
) : BackendFacade<M, S>, EmpressApi<E, M, S>, TestEmpressApi<E, M, S>, EventHandlerContext<M, S>() {
    private val dynamicLatch = DynamicLatch()

    private val handlerChannel = Channel<EventHandlerContext<M, S>.() -> Unit>(Channel.UNLIMITED)

    private val modelChannels = Collections.synchronizedList(arrayListOf<Channel<M>>())

    private val modelMap = makeModelMap(empress.initialModels(), storedModels ?: emptyList())

    private var lastRequestId = AtomicLong(initialRequestId ?: 0)

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

    override fun onEvent(fn: EventHandlerContext<M, S>.() -> Unit): Event {
        dynamicLatch.countUp()
        handlerChannel.offer(fn)
        return handlerInstance
    }

    override fun onRequest(fn: suspend CoroutineScope.() -> Unit): Request {
        val requestId = getNextRequestId()
        val job = requestHandlerScope.launch(start = CoroutineStart.LAZY, block = fn)
        requestJobMap[requestId] = job
        job.invokeOnCompletion {
            requestJobMap.remove(requestId)
            dynamicLatch.countDown()
        }
        dynamicLatch.countUp()
        job.start()
        return Request(requestId)
    }

    // TestEmpressApi

    override suspend fun interrupt() {
        dynamicLatch.close()
        closeChannels()
    }

    override fun models(): Collection<M> {
        return modelMap.values.toList()
    }

    // EmpressApi

    override fun post(fn: E.() -> Event) {
        fn.invoke(empress)
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
                    modelMap.values.forEach { emit(it) }
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

    @Suppress("UNCHECKED_CAST")
    override fun <T : M> get(modelClass: Class<T>): T = modelMap.getValue(modelClass) as T

    override fun signal(signal: S) {
        val channels = signalChannels.toList()
        for (chan in channels) {
            chan.offer(signal)
        }
    }

    override fun update(model: M) {
        modelMap[model::class.java] = model
        val channels = modelChannels.toList()
        for (chan in channels) {
            chan.offer(model)
        }
    }

    // Implementation details

    internal fun areChannelsClosedForSend(): Boolean {
        return modelChannels.toList().all { it.isClosedForSend } &&
                signalChannels.toList().all { it.isClosedForSend } &&
                handlerChannel.isClosedForSend
    }

    private fun closeChannels() {
        handlerChannel.close()
        modelChannels.toList().forEach { it.close() }
        signalChannels.toList().forEach { it.close() }
    }

    private fun getNextRequestId(): RequestId {
        return lastRequestId.incrementAndGet()
    }

    private fun launchHandlerProcessing() = eventHandlerScope.launch {
        for (handler in handlerChannel) {
            try {
                handler.invoke(this@EmpressBackend)
            } finally {
                dynamicLatch.countDown()
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
}
