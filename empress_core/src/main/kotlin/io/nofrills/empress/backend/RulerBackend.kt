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

package io.nofrills.empress.backend

import io.nofrills.empress.EventCommander
import io.nofrills.empress.Models
import io.nofrills.empress.ModelInitializer
import io.nofrills.empress.RequestCommander
import io.nofrills.empress.Ruler
import io.nofrills.empress.RulerApi
import io.nofrills.empress.internal.RequestCommanderImpl
import io.nofrills.empress.internal.RequestHolder
import io.nofrills.empress.internal.RequestIdProducer
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BroadcastChannel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.sync.Mutex
import java.util.concurrent.ConcurrentHashMap

/** Common backend for running and managing a [Ruler]. */
@UseExperimental(ExperimentalCoroutinesApi::class, FlowPreview::class)
abstract class RulerBackend<E : Any, M : Any, R : Any> constructor(
    private val ruler: Ruler<E, M, R>,
    private val eventHandlerScope: CoroutineScope,
    requestHandlerScope: CoroutineScope
) : EventCommander<E>, RulerApi {
    private val eventChannel = Channel<E>()

    private val handledEvents = BroadcastChannel<E>(HANDLED_EVENTS_CHANNEL_CAPACITY)

    private val handledEventsFlow: Flow<E> = handledEvents.asFlow()

    /** If interruption was requested, the mutex will be locked. */
    private val interruption = Mutex()

    private val requestHolder = RequestHolder()

    internal val requestCommander: RequestCommander<R> =
        RequestCommanderImpl(
            RequestIdProducer(),
            ruler,
            requestHolder,
            requestHandlerScope
        ) { sendEvent(it) }

    init {
        eventHandlerScope.coroutineContext[Job]?.invokeOnCompletion {
            closeChannels()
        }
        launchEventProcessing()
    }

    override fun events(): Flow<E> {
        return handledEventsFlow
    }

    override fun post(event: E) {
        eventHandlerScope.launch(start = CoroutineStart.UNDISPATCHED) {
            sendEvent(event)
        }
    }

    internal open fun areChannelsClosedForSend(): Boolean {
        return eventChannel.isClosedForSend && handledEvents.isClosedForSend
    }

    /** Returns `true` if the internal [Ruler] class is equal to the given [rulerClass]. */
    fun hasEqualClass(rulerClass: Class<*>): Boolean {
        return ruler::class.java == rulerClass
    }

    internal fun hasEqualId(id: String): Boolean {
        return ruler.id() == id
    }

    override fun interrupt() {
        interruption.tryLock()
        interruptIfNeeded()
    }

    internal open fun closeChannels() {
        eventChannel.close()
        handledEvents.close()
    }

    private fun interruptIfNeeded() {
        if (interruption.isLocked && requestHolder.isEmpty()) {
            closeChannels()
        }
    }

    private fun launchEventProcessing() = eventHandlerScope.launch {
        for (event in eventChannel) {
            processEvent(event)
            handledEvents.send(event)
            interruptIfNeeded()
        }
    }

    private suspend fun sendEvent(event: E) {
        eventChannel.send(event)
    }

    internal abstract suspend fun modelSnapshot(): Models<M>

    internal abstract suspend fun processEvent(event: E)

    companion object {
        internal const val HANDLED_EVENTS_CHANNEL_CAPACITY = 16

        internal fun <M : Any> makeModelMap(
            storedModels: Collection<M>,
            modelInitializer: ModelInitializer<M>? = null
        ): Map<Class<out M>, M> {
            val storedModelsMap = mutableMapOf<Class<out M>, M>()
            for (model in storedModels) {
                check(storedModelsMap.put(model::class.java, model) == null) {
                    "Model for ${model::class.java} was added more than once."
                }
            }

            val modelMap = ConcurrentHashMap<Class<out M>, M>()
            if (modelInitializer != null) {
                for (model in modelInitializer.initialize()) {
                    check(modelMap.put(model::class.java, model) == null) {
                        "Model for ${model::class.java} was already added."
                    }
                }
            }
            modelMap.putAll(storedModelsMap)

            return modelMap
        }
    }
}
