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
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import java.util.Collections
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
    fun <T : M> get(modelClass: Class<out T>): T

    /** Interrupts event processing loop. */
    suspend fun interrupt()

    /** Returns current models. */
    fun models(): Collection<M>
}

/** Runs and manages an Empress instance.
 * @param empress Empress instance that we want to run.
 * @param eventHandlerScope A coroutine scope where events will be processed.
 * @param requestHandlerScope A coroutine scope where requests will be processed.
 * @param storedModels Models that were previously stored, which will be used instead of the initial values.
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

    private val requestJobMap = ConcurrentHashMap<RequestId, Job>()

    private val signalChannels = Collections.synchronizedList(arrayListOf<Channel<S>>())

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

    override suspend fun onEvent(fn: EventHandlerContext<M, S>.() -> Unit): Event {
        if (coroutineContext[SameEventHandler] != null) {
            fn(this)
        } else {
            dynamicLatch.countUp()
            val added = eventChannel.offer(fn)
            check(added)
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

    override fun <T : M> get(modelClass: Class<out T>): T {
        @Suppress("UNCHECKED_CAST")
        return empress.modelStateFlows.getValue(modelClass).value as T
    }

    override suspend fun interrupt() {
        dynamicLatch.close()
        closeChannels()
    }

    override fun models(): Collection<M> {
        return empress.modelStateFlows.map { it.value.value }
    }

    // EmpressApi

    @ExperimentalCoroutinesApi
    override fun <T : M> listen(fn: E.() -> Mod<T>): StateFlow<T> {
        return fn(empress).flow
    }

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
                check(added)
            }
        }
    }

    override fun <T : M> Mod<T>.get(): T {
        return this.flow.value
    }

    override fun <T : M> Mod<T>.update(value: T) {
        this.flow.value = value
    }

    // Implementation details

    internal fun areChannelsClosedForSend(): Boolean {
        synchronized(signalChannels) {
            return signalChannels.all { it.isClosedForSend } && eventChannel.isClosedForSend
        }
    }

    private fun closeChannels() {
        eventChannel.close()
        synchronized(signalChannels) { signalChannels.toList() }.let { channelList ->
            channelList.forEach { it.close() }
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

    private fun loadStoredModels(storedModels: Collection<M>) {
        val loaded = mutableSetOf<Class<out M>>()
        for (m in storedModels) {
            check(loaded.add(m::class.java)) { "Model ${m.javaClass} was already loaded." }
            empress.modelStateFlows.getValue(m::class.java).value = m
        }
    }

    private class SameEventHandler : AbstractCoroutineContextElement(SameEventHandler) {
        companion object Key : CoroutineContext.Key<SameEventHandler>
    }
}
