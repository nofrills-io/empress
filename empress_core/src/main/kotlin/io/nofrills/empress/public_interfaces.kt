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

import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlin.reflect.KClass

/** Allows to access the models. */
interface Models<M : Any> {
    /** Returns all models. */
    fun all(): Collection<M>

    /** Returns a model with the given [modelClass]. */
    operator fun <T : M> get(modelClass: Class<T>): T

    /** Returns a model with the given [modelClass]. */
    operator fun <T : M> get(modelClass: KClass<T>): T
}

/** Initializes the models. */
interface ModelInitializer<M : Any> {
    /** Initializer should return a collection of all possible models. */
    fun initialize(): Collection<M>
}

/** Event handler for models with immutable fields. */
interface ImmutableEventHandler<E : Any, M : Any, R : Any> {
    /** Handles an incoming [event].
     * @param event An event that was triggered.
     * @param models Current models.
     * @param requests Allows to post new requests or cancel existing ones.
     * @return A collection of updated patches. You should only return patches that have changed.
     *  If nothing has changed, you can return an empty collection. Based on updated patches,
     *  a new [update][Update] will be sent.
     */
    fun onEvent(event: E, models: Models<M>, requests: RequestCommander<R>): Collection<M>
}

/** Event handler for models with mutable fields. */
interface MutableEventHandler<E : Any, M : Any, R : Any> {
    /** Handles an incoming [event].
     * @param event An event that was triggered.
     * @param models Current models.
     * @param requests Allows to post new requests or cancel existing ones.
     */
    fun onEvent(event: E, models: Models<M>, requests: RequestCommander<R>)
}

interface EventCommander<E : Any> {
    /** Allows to listen for event that have been handled. */
    fun events(): Flow<E>

    /** Sends an [event] for processing. */
    fun post(event: E): Job
}

/** Represents a running request. */
interface RequestId

/** Manages requests. */
interface RequestCommander<R : Any> {
    /** Cancels a request
     * @param requestId An id of a request.
     * @return True if request was cancelled; false if [requestId] was null, or the request was already completed.
     */
    fun cancel(requestId: RequestId?): Boolean

    /** Posts a new [request] for execution. */
    fun post(request: R): RequestId
}

interface RequestHandler<E : Any, R : Any> {
    /** Handles an incoming [request].
     * @return An event with the result, that will be fed back to an event handler.
     */
    suspend fun onRequest(request: R): E
}

/** Common interface for defining models and handling requests. */
interface Ruler<E : Any, M : Any, R : Any> : ModelInitializer<M>, RequestHandler<E, R> {
    /** Returns an ID for this instance.
     * Useful if you want to install more than one rulers into an activity,
     * or if you want to share the same model instance.
     */
    fun id(): String = javaClass.name
}

/** Common interface for managing a [Ruler]. */
interface RulerApi {
    /** Interrupts event processing loop.
     * Usually only needed in tests.
     */
    fun interrupt()
}

/** Allows to define (mutable) models, event and request handlers. */
interface Emperor<E : Any, M : Any, R : Any> : Ruler<E, M, R>, MutableEventHandler<E, M, R>

/** Allows to manage an [Emperor] instance. */
interface EmperorApi<E : Any, M : Any> : EventCommander<E>, RulerApi {
    /** Returns current models. */
    fun models(): Models<M>
}

/** Allows to define (immutable) models, event and request handlers. */
interface Empress<E : Any, M : Any, R : Any> : Ruler<E, M, R>, ImmutableEventHandler<E, M, R>

/** Represents an update, that resulted from processing an [event].
 * @property all Set of all models
 * @property event Event that triggered the update.
 * @property updated Models that were updated as a result of processing an event.
 */
interface Update<E : Any, M : Any> {
    val all: Models<M>
    val event: E
    val updated: Collection<M>
}

/** Allows to manage an [Empress] instance. */
interface EmpressApi<E : Any, M : Any> : EventCommander<E>, RulerApi {
    /** Return current snapshot of the model.
     * Usually you want to obtain whole model when starting the application.
     */
    suspend fun models(): Models<M>

    /** Allows to listen for [updates][Update]. */
    fun updates(): Flow<Update<E, M>>
}
