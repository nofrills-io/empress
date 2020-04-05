package io.nofrills.empress.base

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
abstract class Empress<M : Any, S : Any> : BackendFacade<M, S>() {
    internal lateinit var backend: BackendFacade<M, S>

    internal abstract fun initialModels(): Collection<M>

    override fun all(): Collection<M> = backend.all()

    override fun cancelHandler(handlerId: HandlerId) = backend.cancelHandler(handlerId)

    override fun <T : M> get(modelClass: Class<T>): T = backend.get(modelClass)

    override suspend fun handlerId(): HandlerId = backend.handlerId()

    override suspend fun signal(signal: S) = backend.signal(signal)

    override suspend fun update(model: M) = backend.update(model)

    override fun queueSignal(signal: S) = backend.queueSignal(signal)

    override fun queueUpdate(model: M) = backend.queueUpdate(model)
}

interface EmpressApi<H : Any, M : Any, S : Any> {
    fun interrupt()
    fun post(fn: suspend H.() -> Unit)
    fun signals(): Flow<S>
    fun updates(): Flow<M>
}
