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

interface Models<M : Any> {
    fun all(): Collection<M>
    operator fun <T : M> get(modelClass: Class<T>): T
    operator fun <T : M> get(modelClass: KClass<T>): T
}

interface ModelInitializer<M : Any> {
    /** Initializer should return a collection of all possible models. */
    fun initialize(): Collection<M>
}

interface ImmutableEventHandler<E : Any, M : Any, R : Any> {
    /** Handles an incoming [event].
     * @param event An event that was triggered.
     * @param models Set of current models.
     * @param requests Allows to post new requests or cancel existing ones.
     * @return A collection of updated patches. You should only return patches that have changed.
     *  If nothing has changed, you can return an empty collection. Based on updated patches,
     *  a new [update][Update] will be sent.
     */
    fun onEvent(event: E, models: Models<M>, requests: RequestCommander<R>): Collection<M>
}

interface MutableEventHandler<E : Any, M : Any, R : Any> {
    fun onEvent(event: E, models: Models<M>, requests: RequestCommander<R>)
}

interface EventCommander<E : Any> {
    fun events(): Flow<E>

    /** Sends an [event] for processing. */
    fun post(event: E): Job
}

/** Represents a running request. */
interface RequestId

interface RequestCommander<R : Any> {
    fun cancel(requestId: RequestId?): Boolean
    fun post(request: R): RequestId
}

interface RequestHandler<E : Any, R : Any> {
    /** Handles an incoming [request].
     * @return An event with the result, that will be fed back to an event handler.
     */
    suspend fun onRequest(request: R): E
}

interface Ruler<E : Any, M : Any, R : Any> : ModelInitializer<M>, RequestHandler<E, R> {
    /** Returns an ID for this instance.
     * Useful if you want to install more than one rulers into an activity,
     * or if you want to share the same model instance.
     */
    fun id(): String = javaClass.name
}

interface RulerApi {
    fun interrupt()
    // TODO possibly include "models" method
}

interface Emperor<E : Any, M : Any, R : Any> : Ruler<E, M, R>, MutableEventHandler<E, M, R>

interface EmperorApi<E : Any, M : Any> : EventCommander<E>, RulerApi {
    fun models(): Models<M>
}

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

/** Interface for interacting with a running [Empress] instance. */
interface EmpressApi<E : Any, M : Any> : EventCommander<E>, RulerApi {
    /** Return current snapshot of the model.
     * Usually you want to obtain whole model when starting the application.
     */
    suspend fun models(): Models<M>

    /** Allows to listen for [updates][Update]. */
    fun updates(): Flow<Update<E, M>>
}
