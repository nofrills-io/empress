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
import kotlinx.coroutines.flow.StateFlow
import kotlin.reflect.KProperty

/** Opaque representation for an event handler. */
class EventDeclaration internal constructor()

class ModelDelegate<T : Any> internal constructor(private val initialValue: T) {
    operator fun getValue(thisRef: Any, property: KProperty<*>): ModelDeclaration<T> {
        return ModelDeclaration(property.name, initialValue)
    }
}

class ModelDeclaration<T> internal constructor(
    internal val key: String,
    internal val initialValue: T
)

class SignalDelegate<T : Any> internal constructor() {
    operator fun getValue(thisRef: Any, property: KProperty<*>): SignalDeclaration<T> {
        return SignalDeclaration(property.name)
    }
}

class SignalDeclaration<T> internal constructor(internal val key: String) {
    @Suppress("unused")
    private val unused: T? = null
}

/** An ID for a request, which can be used to [cancel][EventHandlerContext.cancelRequest] it. */
data class RequestId(private val id: Long)

/** Representation for a request handler. */
class RequestDeclaration internal constructor()

/** Context for defining an event handler.
 * @see Empress.onEvent
 */
abstract class EventHandlerContext {
    /** Cancels a request with given [requestId]. */
    abstract fun cancelRequest(requestId: RequestId): Boolean

    /** Executes an event in current context. */
    abstract fun event(fn: suspend () -> EventDeclaration)

    /** Schedules a request for execution.
     * @param scope The coroutine scope where the request will execute; if null, a scope will be provided by the backend.
     * @param fn The request handler to execute.
     */
    abstract fun request(
        scope: CoroutineScope? = null,
        fn: suspend () -> RequestDeclaration
    ): RequestId

    abstract fun <T : Any> ModelDeclaration<T>.get(): T

    abstract fun <T : Any> ModelDeclaration<T>.update(value: T)

    fun <T : Any> ModelDeclaration<T>.updateWith(updater: (T) -> T) {
        val oldValue = get()
        val newValue = updater(oldValue)
        update(newValue)
    }

    abstract fun <T : Any> SignalDeclaration<T>.push(signal: T)
}

/** Allows you define your initial models, event and request handlers. */
abstract class Empress {
    internal lateinit var backend: BackendFacade

    protected fun <T : Any> model(initialValue: T): ModelDelegate<T> {
        return ModelDelegate(initialValue)
    }

    protected fun <T : Any> signal(): SignalDelegate<T> {
        return SignalDelegate()
    }

    /** Allows to define an event handler.
     * @param fn The definition of the event handler
     */
    protected suspend fun onEvent(fn: EventHandlerContext.() -> Unit): EventDeclaration =
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

interface ModelListener<E : Any> {
    fun <T : Any> model(fn: E.() -> ModelDeclaration<T>): StateFlow<T>
}

interface SignalListener<E : Any> {
    fun <T : Any> signal(fn: E.() -> SignalDeclaration<T>): Flow<T>
}

/** Allows to communicate with your [Empress] instance. */
interface EmpressApi<E : Any> : EventCommander<E>, ModelListener<E>, SignalListener<E>
