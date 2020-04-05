package io.nofrills.empress.base

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.consumeAsFlow
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.coroutineContext

internal interface BackendFacade<M : Any, S : Any> {
    fun all(): Collection<M>
    fun cancelHandler(handlerId: HandlerId)
    operator fun <T : M> get(modelClass: Class<T>): T
    suspend fun handlerId(): HandlerId
    suspend fun signal(signal: S)
    suspend fun update(model: M)
    fun queueSignal(signal: S)
    fun queueUpdate(model: M)
}

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class EmpressBackend<E : Empress<M, S>, M : Any, S : Any>(
    private val empress: E,
    handlerScope: CoroutineScope,
    storedModels: Collection<M> = emptyList()
) : BackendFacade<M, S>, EmpressApi<E, M, S> {
    private val handlerJobMap = ConcurrentHashMap<HandlerId, Job>()

    private val modelChannel = Channel<M>(Channel.UNLIMITED)

    private val modelFlow = modelChannel.consumeAsFlow()

    private val modelMap = makeModelMap(empress.initialModels(), storedModels)

    private var nextHandlerId = 0L

    private val signalChannel = Channel<S>(Channel.UNLIMITED)

    private val signalFlow = signalChannel.consumeAsFlow()

    init {
        handlerScope.coroutineContext[Job]?.invokeOnCompletion {
            closeChannels()
        }
        empress.backend = this
        empress.coroutineContext =
            handlerScope.coroutineContext
    }

    // EmpressApi

    override fun interrupt() {
        closeChannels()
    }

    override fun signals(): Flow<S> {
        return signalFlow
    }

    override fun updates(): Flow<M> {
        return modelFlow
    }

    // BackendFacade

    override fun all(): Collection<M> = modelMap.values.toList()

    override fun cancelHandler(handlerId: HandlerId) {
        handlerJobMap[handlerId]?.cancel()
    }

    @Suppress("UNCHECKED_CAST")
    override fun <T : M> get(modelClass: Class<T>): T = modelMap.getValue(modelClass) as T

    override suspend fun handlerId(): HandlerId {
        return coroutineContext[HandlerId]!!
    }

    override suspend fun signal(signal: S) {
        signalChannel.send(signal)
        yield()
    }

    override suspend fun update(model: M) {
        modelMap[model::class.java] = model
        modelChannel.send(model)
        yield()
    }

    override fun queueSignal(signal: S) {
        signalChannel.offer(signal)
    }

    override fun queueUpdate(model: M) {
        modelMap[model::class.java] = model
        modelChannel.offer(model)
    }

    override fun post(fn: suspend E.() -> Unit) {
        val handlerId = getNextHandlerId()
        val job = Job()
        handlerJobMap[handlerId] = job
        val context = handlerId + job

        empress.launch(context, start = CoroutineStart.UNDISPATCHED) {
            fn.invoke(empress)
            handlerJobMap.remove(handlerId)
        }
    }

    private fun closeChannels() {
        modelChannel.close()
        signalChannel.close()
    }

    private fun getNextHandlerId(): HandlerId {
        nextHandlerId += 1
        return HandlerId(nextHandlerId)
    }

    companion object {
        internal fun <M : Any> makeModelMap(
            initialModels: Collection<M>,
            storedModels: Collection<M>
        ): ConcurrentHashMap<Class<out M>, M> {
            val modelMap = ConcurrentHashMap<Class<out M>, M>()
            for (model in initialModels) {
                check(modelMap.put(model::class.java, model) == null) {
                    "Model for ${model::class.java} was already added."
                }
            }

            val storedModelsMap = mutableMapOf<Class<out M>, M>()
            for (model in storedModels) {
                check(storedModelsMap.put(model::class.java, model) == null) {
                    "Model for ${model::class.java} was added more than once."
                }
            }

            modelMap.putAll(storedModelsMap)

            return modelMap
        }
    }
}
