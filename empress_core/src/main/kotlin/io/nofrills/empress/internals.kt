package io.nofrills.empress

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BroadcastChannel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.CoroutineContext

class DefaultEmpressBackend<Event, Patch : Any, Request> constructor(
    coroutineContext: CoroutineContext,
    private val empress: Empress<Event, Patch, Request>,
    private val idProducer: RequestIdProducer,
    private val requestHolder: RequestHolder,
    storedPatches: Collection<Patch>?
) : EmpressApi<Event, Patch>, EmpressBackend<Patch> {

    override var model: Model<Patch> = if (storedPatches == null) {
        Model(empress.initializer())
    } else {
        Model(storedPatches + empress.initializer(), skipDuplicates = true)
    }

    private val scope: CoroutineScope
    private val updates = BroadcastChannel<Update<Event, Patch>>(UPDATES_CHANNEL_CAPACITY)
    private val updatesFlow: Flow<Update<Event, Patch>> = updates.asFlow()

    init {
        val job = coroutineContext[Job]
            ?: error("Coroutine context does not contain a Job element ($coroutineContext)")
        job.invokeOnCompletion { updates.close() }
        scope = CoroutineScope(coroutineContext)
    }

    override fun updates(): Flow<Update<Event, Patch>> {
        return flow {
            emit(Update<Event, Patch>(model))
            updatesFlow.collect {
                emit(it)
            }
        }
    }

    override fun send(event: Event, closeUpdates: Boolean) {
        val requestExecutor = DefaultRequests(
            closeUpdates,
            idProducer,
            empress::onRequest,
            requestHolder,
            scope,
            this::send
        )
        val updatedPatches = empress.onEvent(event, model, requestExecutor)
        val hasNoRequests = requestHolder.isEmpty()
        model = Model(model, updatedPatches)

        scope.launch {
            updates.send(Update(model, event))
            yield()
            if (closeUpdates && hasNoRequests) {
                updates.close()
            }
        }

        requestHolder.snapshot()
            .filter { !it.isActive && !it.isCompleted }
            .forEach { it.start() }
    }

    companion object {
        private const val UPDATES_CHANNEL_CAPACITY = 16
    }
}

class DefaultRequestHolder : RequestHolder {
    private val requestMap: MutableMap<RequestId, Job> = ConcurrentHashMap()

    override fun isEmpty(): Boolean {
        return requestMap.isEmpty()
    }

    override fun pop(requestId: RequestId): Job? {
        return requestMap.remove(requestId)
    }

    override fun push(requestId: RequestId, requestJob: Job) {
        requestMap[requestId] = requestJob
    }

    override fun snapshot(): Collection<Job> {
        return requestMap.values.toList()
    }
}

internal class DefaultRequests<Event, Request>(
    private val closeUpdates: Boolean,
    private val idProducer: RequestIdProducer,
    private val onRequest: suspend (Request) -> Event,
    private val requestHolder: RequestHolder,
    private val scope: CoroutineScope,
    private val send: (Event, Boolean) -> Unit
) : Requests<Event, Request> {
    override fun cancel(requestId: RequestId?): Boolean {
        requestId ?: return false
        val requestJob = requestHolder.pop(requestId) ?: return false

        // Since we have a non-null requestJob, it means it hasn't been completed yet
        requestJob.cancel()
        return true
    }

    override fun post(request: Request): RequestId {
        val requestId = idProducer.getNextRequestId()
        val job = scope.launch(start = CoroutineStart.LAZY) {
            val eventFromRequest = onRequest(request)
            if (requestHolder.pop(requestId) != null) {
                send(eventFromRequest, closeUpdates)
            }
        }
        job.invokeOnCompletion {
            // make sure to clean up, in case there were errors in handling the request
            requestHolder.pop(requestId)
        }
        requestHolder.push(requestId, job)
        return requestId
    }
}

class DefaultRequestIdProducer : RequestIdProducer {
    private var nextRequestId: RequestId = 0

    override fun getNextRequestId(): RequestId {
        nextRequestId += 1
        return nextRequestId
    }
}
