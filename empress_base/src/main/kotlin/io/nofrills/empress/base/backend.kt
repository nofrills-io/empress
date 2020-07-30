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

import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.coroutineContext

private val eventInstance = EventDeclaration()
private val requestInstance = RequestDeclaration()

internal interface BackendFacade {
    suspend fun onEvent(fn: EventHandlerContext.() -> Unit): EventDeclaration
    suspend fun onRequest(fn: suspend CoroutineScope.() -> Unit): RequestDeclaration
}

interface StoredDataLoader {
    fun loadStoredModels(empressBackendId: String): Map<String, Any>?
    fun loadStoredRequestId(empressBackendId: String): Long?
}

/** Extends [EmpressApi] with additional methods useful in unit tests. */
interface TestEmpressApi<E : Any> : EmpressApi<E> {
    /** Returns any models that have been used by [E]. */
    fun loadedModels(): Map<String, Any>

    /** Interrupts event processing loop. */
    suspend fun interrupt()
}

/** Runs and manages an Empress instance.
 * @param id Unique ID for this backend.
 * @param empress Empress instance that we want to run.
 * @param eventHandlerScope A coroutine scope where events will be processed.
 * @param requestHandlerScope A coroutine scope where requests will be processed.
 * @param storedDataLoader Allows to load any stored models and request id.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class EmpressBackend<E : Empress> constructor(
    val id: String,
    private val empress: E,
    private val eventHandlerScope: CoroutineScope,
    private val requestHandlerScope: CoroutineScope,
    private val storedDataLoader: StoredDataLoader? = null
) : BackendFacade, EmpressApi<E>, TestEmpressApi<E>, EventHandlerContext() {
    private val dynamicLatch = DynamicLatch()

    private val eventChannel = Channel<EventHandlerContext.() -> Unit>(Channel.UNLIMITED)

    private var lastRequestId = AtomicLong(0)

    private val modelStateFlowsMap = ConcurrentHashMap<String, MutableStateFlow<Any>>()

    private val requestJobMap = ConcurrentHashMap<RequestId, Job>()

    private val signalChannelsMap = ConcurrentHashMap<String, MutableList<Channel<Any>>>()

    init {
        empress.backend = this
        storedDataLoader?.loadStoredModels(id)?.let { loadStoredModels(it) }
        storedDataLoader?.loadStoredRequestId(id)?.let { lastRequestId.set(it) }
        launchHandlerProcessing()
        eventHandlerScope.coroutineContext[Job]?.invokeOnCompletion {
            closeChannels()
        }
    }

    fun lastRequestId(): Long {
        return lastRequestId.get()
    }

    // BackendFacade

    override suspend fun onEvent(fn: EventHandlerContext.() -> Unit): EventDeclaration {
        if (coroutineContext[SameEventHandler] != null) {
            fn(this)
        } else {
            dynamicLatch.countUp()
            val added = eventChannel.offer(fn)
            check(added)
        }
        return eventInstance
    }

    override suspend fun onRequest(fn: suspend CoroutineScope.() -> Unit): RequestDeclaration {
        coroutineScope {
            fn(this)
        }
        return requestInstance
    }

    // TestEmpressApi

    override fun loadedModels(): Map<String, Any> {
        return modelStateFlowsMap.mapValues {
            it.value.value
        }
    }

    override suspend fun interrupt() {
        dynamicLatch.close()
        closeChannels()
    }

    override fun <T : Any> model(
        fn: E.() -> ModelDeclaration<T>
    ): StateFlow<T> {
        val modelDeclaration = fn(empress)
        return stateFlowForModel(modelDeclaration)
    }

    override fun post(fn: suspend E.() -> EventDeclaration) {
        eventHandlerScope.launch(
            CoroutineName("empress-api-post"),
            start = CoroutineStart.UNDISPATCHED
        ) {
            fn.invoke(empress)
        }
    }

    override fun <T : Any> signal(fn: E.() -> SignalDeclaration<T>): Flow<T> {
        val signalKey = fn(empress).key
        val channel = Channel<T>(Channel.UNLIMITED)
        return channel
            .consumeAsFlow()
            .onStart {
                val newList = Collections.synchronizedList(mutableListOf<Channel<Any>>())
                val list = signalChannelsMap.putIfAbsent(signalKey, newList) ?: newList
                @Suppress("UNCHECKED_CAST")
                list.add(channel as Channel<Any>)
            }
            .onCompletion {
                @Suppress("UNCHECKED_CAST")
                signalChannelsMap.getValue(signalKey).remove(channel as Channel<Any>)
            }
    }

    // EventHandler

    override fun cancelRequest(requestId: RequestId): Boolean {
        val job = requestJobMap[requestId] ?: return false
        job.cancel()
        return true
    }

    override fun event(fn: suspend () -> EventDeclaration) {
        eventHandlerScope.launch(
            CoroutineName("empress-event") + SameEventHandler(),
            start = CoroutineStart.UNDISPATCHED
        ) {
            fn()
        }
    }

    override fun request(fn: suspend () -> RequestDeclaration): RequestId {
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

    override fun <T : Any> ModelDeclaration<T>.get(): T {
        return stateFlowForModel(this).value
    }

    override fun <T : Any> ModelDeclaration<T>.update(value: T) {
        stateFlowForModel(this).value = value
    }

    override fun <T : Any> SignalDeclaration<T>.push(signal: T) {
        val channels = signalChannelsMap[key] ?: return
        synchronized(channels) { channels.toList() }.forEach {
            val added = it.offer(signal)
            check(added)
        }
    }

    // Implementation details

    internal fun areChannelsClosedForSend(): Boolean {
        return signalChannelsMap.values.all { channels ->
            synchronized(channels) { channels.toList() }.all { it.isClosedForSend }
        } && eventChannel.isClosedForSend
    }

    private fun closeChannels() {
        eventChannel.close()
        signalChannelsMap.values.forEach { channels ->
            synchronized(channels) { channels.toList() }.forEach { it.close() }
        }
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

    private fun loadStoredModels(storedModels: Map<String, Any>) {
        for ((key, model) in storedModels) {
            stateFlowForModel(ModelDeclaration(key, model))
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T : Any> stateFlowForModel(modelDeclaration: ModelDeclaration<T>): MutableStateFlow<T> {
        val stateFlow = MutableStateFlow(modelDeclaration.initialValue)
        return (modelStateFlowsMap.putIfAbsent(
            modelDeclaration.key,
            stateFlow as MutableStateFlow<Any>
        ) ?: stateFlow) as MutableStateFlow<T>
    }

    private class SameEventHandler : AbstractCoroutineContextElement(SameEventHandler) {
        companion object Key : CoroutineContext.Key<SameEventHandler>
    }
}
