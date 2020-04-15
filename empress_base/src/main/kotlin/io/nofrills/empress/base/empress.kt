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

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlin.reflect.KClass

/** Opaque representation for an event handler. */
class Event internal constructor()

/** An ID for a request, which can be used to [cancel][EventHandlerContext.cancelRequest] it. */
data class RequestId(private val id: Long)

/** Representation for a request handler. */
class Request internal constructor()

interface ModelRepository<M : Any> {
    /** Returns a model with given [modelClass]. */
    fun <T : M> get(modelClass: Class<T>): T

    fun <T : M> get(modelClass: KClass<T>): T = get(modelClass.java)
}

/** Context for defining an event handler.
 * @see Empress.onEvent
 */
abstract class EventHandlerContext<M : Any, S : Any> : ModelRepository<M> {
    /** Cancels a request with given [requestId]. */
    abstract fun cancelRequest(requestId: RequestId): Boolean

    /** Executes an event in current context. */
    abstract fun event(fn: suspend () -> Event)

    /** Schedules a request for execution. */
    abstract fun request(fn: suspend () -> Request): RequestId

    /** Pushes a [signal] that can be later obtained in [EmpressApi.signals]. */
    abstract fun signal(signal: S)

    /** Pushes an updated [model] that can be later obtained in [EmpressApi.updates]. */
    abstract fun update(model: M)

    /** Returns a model with given [type][T]. */
    inline fun <reified T : M> get() = get(T::class.java)
}

/** Allows you define your initial models, event and request handlers.
 * @param M Model type.
 * @param S Signal type.
 */
abstract class Empress<M : Any, S : Any> {
    internal lateinit var backend: BackendFacade<M, S>

    /** Initializer should return a collection of all possible models. */
    abstract fun initialModels(): Collection<M>

    /** Allows to define an event handler.
     * @param fn The definition of the event handler
     */
    protected suspend fun onEvent(fn: EventHandlerContext<M, S>.() -> Unit): Event =
        backend.onEvent(fn)

    /** Allows to define a request handler.
     * @param fn The definition of the request handler.
     */
    protected suspend fun onRequest(fn: suspend CoroutineScope.() -> Unit): Request =
        backend.onRequest(fn)
}

interface EventCommander<E : Any> {
    /** Allows to call an event handler defined in [E]. */
    fun post(fn: suspend E.() -> Event)
}

/** Allows to communicate with your [Empress] instance. */
interface EmpressApi<E : Any, M : Any, S : Any> : EventCommander<E>, ModelRepository<M> {
    /** Allows to listen for signals sent from [Empress]. */
    fun signals(): Flow<S>

    /** Allows to listen for model updates sent from [Empress].
     * @param withCurrentModels If `true`, you will also receive current state of your models.
     */
    fun updates(withCurrentModels: Boolean = true): Flow<M>
}
