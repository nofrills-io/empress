package io.nofrills.empress.base

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow

class Handler internal constructor()
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
    internal abstract fun initialModels(): Collection<M>

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
