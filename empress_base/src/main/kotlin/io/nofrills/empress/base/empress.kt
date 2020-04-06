package io.nofrills.empress.base

import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

data class HandlerId(val id: Long) : AbstractCoroutineContextElement(HandlerId) {
    companion object Key : CoroutineContext.Key<HandlerId>
}

/**
 * @param M Type of the model.
 * @param S Signal type (type of the Flow/Channel)
 */
abstract class Empress<M : Any, S : Any> {
    internal lateinit var backend: BackendFacade<M, S>

    internal abstract fun initialModels(): Collection<M>

    protected fun cancelHandler(handlerId: HandlerId) = backend.cancelHandler(handlerId)

    protected fun <T : M> get(modelClass: Class<T>): T = backend.get(modelClass)

    protected inline fun <reified T : M> get(): T = get(T::class.java)

    protected suspend fun handlerId(): HandlerId = backend.handlerId()

    protected suspend fun signal(signal: S) = backend.signal(signal)

    protected suspend fun update(model: M) = backend.update(model)

    protected fun queueSignal(signal: S) = backend.queueSignal(signal)

    protected fun queueUpdate(model: M) = backend.queueUpdate(model)
}

interface EmpressApi<H : Any, M : Any, S : Any> {
    fun interrupt()
    fun models(): Collection<M>
    fun post(fn: suspend H.() -> Unit)
    fun signals(): Flow<S>
    fun updates(): Flow<M>
}
