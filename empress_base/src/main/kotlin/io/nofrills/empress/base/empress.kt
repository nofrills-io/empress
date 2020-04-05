package io.nofrills.empress.base

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
import kotlin.reflect.KClass

data class HandlerId(val id: Long) : AbstractCoroutineContextElement(HandlerId) {
    companion object Key : CoroutineContext.Key<HandlerId>
}

/**
 * @param M Type of the model.
 * @param S Signal type (type of the Flow/Channel)
 */
abstract class Empress<M : Any, S : Any> : CoroutineScope {
    internal lateinit var backend: BackendFacade<M, S>

    override lateinit var coroutineContext: CoroutineContext
        internal set

    abstract fun initialModels(): Collection<M>

    fun CoroutineScope.all(): Collection<M> = backend.all()

    fun CoroutineScope.cancelHandler(handlerId: HandlerId) = backend.cancelHandler(handlerId)

    /** Returns a model with the given [modelClass]. */
    operator fun <T : M> CoroutineScope.get(modelClass: Class<T>): T = backend[modelClass]

    /** Returns a model with the given [modelClass]. */
    operator fun <T : M> CoroutineScope.get(modelClass: KClass<T>): T = get(modelClass.java)

    suspend fun CoroutineScope.handlerId(): HandlerId = backend.handlerId()

    suspend fun CoroutineScope.signal(signal: S) = backend.signal(signal)

    fun CoroutineScope.queueSignal(signal: S) = backend.queueSignal(signal)

    suspend fun CoroutineScope.update(model: M) = backend.update(model)

    fun CoroutineScope.queueUpdate(model: M) = backend.queueUpdate(model)
}

interface EmpressApi<E : Empress<M, S>, M : Any, S : Any> {
    fun interrupt()
    fun post(fn: suspend E.() -> Unit)
    fun signals(): Flow<S>
    fun updates(): Flow<M>
}
