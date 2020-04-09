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

class Handler internal constructor()
// TODO use class Request internal constructor(internal val id: RequestId) when returning from onRequest
// and then make the RequestId constructor public; should be available for testing
data class RequestId internal constructor(val id: Long)

abstract class EventHandler<M : Any, S : Any> {
    abstract fun cancelRequest(requestId: RequestId): Boolean
    abstract fun <T : M> get(modelClass: Class<T>): T
    abstract fun signal(signal: S)
    abstract fun update(model: M)

    inline fun <reified T : M> get() = get(T::class.java)
}

/**
 * @param M Type of the model.
 * @param S Signal type (type of the Flow/Channel)
 */
abstract class Empress<M : Any, S : Any> {
    internal lateinit var backend: BackendFacade<M, S>
    abstract fun initialModels(): Collection<M>

    protected fun onEvent(fn: EventHandler<M, S>.() -> Unit): Handler = backend.onEvent(fn)
    protected fun onRequest(fn: suspend CoroutineScope.() -> Unit): RequestId =
        backend.onRequest(fn)
}

interface EmpressApi<E : Any, M : Any, S : Any> {
    suspend fun interrupt()
    fun models(): Collection<M>
    fun post(fn: E.() -> Handler)
    fun signals(): Flow<S>
    fun updates(): Flow<M>
}
