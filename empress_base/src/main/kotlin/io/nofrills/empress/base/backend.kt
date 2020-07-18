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

/** Extends [EmpressApi] with additional methods useful in unit tests. */
interface TestEmpressApi<E : Any> : EmpressApi<E> {
    fun <T : Any> get(modelClass: Class<out T>): T

    /** Interrupts event processing loop. */
    suspend fun interrupt()

    /** Returns current models. */
    fun models(): Collection<Any>
}

/** Runs and manages an Empress instance.
 * @param empress Empress instance that we want to run.
 * @param eventHandlerScope A coroutine scope where events will be processed.
 * @param requestHandlerScope A coroutine scope where requests will be processed.
 * @param storedModels Models that were previously stored, which will be used instead of the initial values.
 * @param initialRequestId The number from which to start generating requests IDs.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class EmpressBackend<E : Empress> constructor(
    private val empress: E,
    private val eventHandlerScope: CoroutineScope,
    private val requestHandlerScope: CoroutineScope,
    storedModels: Collection<Any>? = null,
    initialRequestId: Long? = null
) : BackendFacade, EmpressApi<E>, TestEmpressApi<E>, EventHandlerContext() {
    private val dynamicLatch = DynamicLatch()

    private val eventChannel = Channel<EventHandlerContext.() -> Unit>(Channel.UNLIMITED)

    private var lastRequestId = AtomicLong(initialRequestId ?: 0)

    private val requestJobMap = ConcurrentHashMap<RequestId, Job>()

    private val signalChannelsMap = ConcurrentHashMap<Class<out Any>, MutableList<Channel<Any>>>()

    init {
        empress.backend = this
        storedModels?.let { loadStoredModels(it) }
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

    override fun <T : Any> get(modelClass: Class<out T>): T {
        @Suppress("UNCHECKED_CAST")
        return empress.modelStateFlows.getValue(modelClass).value as T
    }

    override suspend fun interrupt() {
        dynamicLatch.close()
        closeChannels()
    }

    override fun models(): Collection<Any> {
        return empress.modelStateFlows.values.map { it.value }
    }

    // EmpressApi

    override fun <T : Any> listen(
        fn: E.() -> ModelDeclaration<T>
    ): StateFlow<T> {
        val modelClass = fn(empress).modelClass
        @Suppress("UNCHECKED_CAST")
        return empress.modelStateFlows.getValue(modelClass) as StateFlow<T>
    }

    override fun post(fn: suspend E.() -> EventDeclaration) {
        eventHandlerScope.launch(
            CoroutineName("empress-api-post"),
            start = CoroutineStart.UNDISPATCHED
        ) {
            fn.invoke(empress)
        }
    }

    override fun <T : Any> signals(fn: E.() -> SignalDeclaration<T>): Flow<T> {
        val signalClass = fn(empress).signalClass
        val channel = Channel<T>(Channel.UNLIMITED)
        return channel
            .consumeAsFlow()
            .onStart {
                synchronized(signalChannelsMap) {
                    val list = signalChannelsMap[signalClass] ?: Collections.synchronizedList(
                        mutableListOf<Channel<Any>>()
                    ).also {
                        signalChannelsMap[signalClass] = it
                    }
                    @Suppress("UNCHECKED_CAST")
                    list.add(channel as Channel<Any>)
                }
            }
            .onCompletion {
                @Suppress("UNCHECKED_CAST")
                signalChannelsMap.getValue(signalClass).remove(channel as Channel<Any>)
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
        @Suppress("UNCHECKED_CAST")
        return empress.modelStateFlows.getValue(modelClass).value as T
    }

    override fun <T : Any> ModelDeclaration<T>.update(value: T) {
        empress.modelStateFlows.getValue(modelClass).value = value
    }

    override fun <T : Any> SignalDeclaration<T>.push(signal: T) {
        synchronized(signalChannelsMap) {
            signalChannelsMap.values.forEach { channels ->
                channels.forEach {
                    val added = it.offer(signal)
                    check(added)
                }
            }
        }
    }

    // Implementation details

    internal fun areChannelsClosedForSend(): Boolean {
        return synchronized(signalChannelsMap) {
            signalChannelsMap.values.all { channels ->
                channels.all { it.isClosedForSend }
            } && eventChannel.isClosedForSend
        }
    }

    private fun closeChannels() {
        eventChannel.close()
        synchronized(signalChannelsMap) { signalChannelsMap.values.toList() }.forEach { channels ->
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

    private fun loadStoredModels(storedModels: Collection<Any>) {
        val loaded = mutableSetOf<Class<out Any>>()
        for (m in storedModels) {
            check(loaded.add(m::class.java)) { "Model ${m.javaClass} was already loaded." }
            empress.modelStateFlows.getValue(m::class.java).value = m
        }
    }

    private class SameEventHandler : AbstractCoroutineContextElement(SameEventHandler) {
        companion object Key : CoroutineContext.Key<SameEventHandler>
    }
}
