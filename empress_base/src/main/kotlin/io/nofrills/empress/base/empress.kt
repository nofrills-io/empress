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
import java.util.Collections

/** Opaque representation for an event handler. */
class EventDeclaration internal constructor()

class ModelDeclaration<T> internal constructor(internal val modelClass: Class<out T>)

/** An ID for a request, which can be used to [cancel][EventHandlerContext.cancelRequest] it. */
data class RequestId(private val id: Long)

/** Representation for a request handler. */
class RequestDeclaration internal constructor()

/** Context for defining an event handler.
 * @see Empress.onEvent
 */
abstract class EventHandlerContext<M : Any, S : Any> {
    /** Cancels a request with given [requestId]. */
    abstract fun cancelRequest(requestId: RequestId): Boolean

    /** Executes an event in current context. */
    abstract fun event(fn: suspend () -> EventDeclaration)

    /** Schedules a request for execution. */
    abstract fun request(fn: suspend () -> RequestDeclaration): RequestId

    /** Pushes a [signal] that can be later obtained in [EmpressApi.signals]. */
    abstract fun signal(signal: S)

    abstract fun <T : M> ModelDeclaration<T>.get(): T

    abstract fun <T : M> ModelDeclaration<T>.update(value: T)

    fun <T : M> ModelDeclaration<T>.updateWith(updater: (T) -> T) {
        val oldValue = get()
        val newValue = updater(oldValue)
        update(newValue)
    }
}

/** Allows you define your initial models, event and request handlers.
 * @param M Model type.
 * @param S Signal type.
 */
abstract class Empress<M : Any, S : Any> {
    internal lateinit var backend: BackendFacade<M, S>

    internal val modelValues = Collections.synchronizedMap(mutableMapOf<Class<out M>, M>())

    protected fun <T : M> model(initialValue: T): ModelDeclaration<T> {
        val wasNotYetAdded = modelValues.put(initialValue::class.java, initialValue) == null
        check(wasNotYetAdded) { "The value for class ${initialValue::class} has been already added." }
        return ModelDeclaration(initialValue::class.java)
    }

    /** Allows to define an event handler.
     * @param fn The definition of the event handler
     */
    protected suspend fun onEvent(fn: EventHandlerContext<M, S>.() -> Unit): EventDeclaration =
        backend.onEvent(fn)

    /** Allows to define a request handler.
     * @param fn The definition of the request handler.
     */
    protected suspend fun onRequest(fn: suspend CoroutineScope.() -> Unit): RequestDeclaration =
        backend.onRequest(fn)
}

/** Allows to execute an event handler. */
interface EventCommander<E : Any> {
    /** Schedules a call to an event handler defined in [E]. */
    fun post(fn: suspend E.() -> EventDeclaration)
}

interface ModelListener<E : Any, M : Any> {
    fun listen(withCurrentValues: Boolean = true): Flow<M>

    fun <T : M> listen(withCurrentValues: Boolean = true, fn: E.() -> ModelDeclaration<T>): Flow<T>
}

/** Allows to communicate with your [Empress] instance. */
interface EmpressApi<E : Any, M : Any, S : Any> : EventCommander<E>, ModelListener<E, M> {
    /** Allows to listen for signals sent from [Empress]. */
    fun signals(): Flow<S>
}
