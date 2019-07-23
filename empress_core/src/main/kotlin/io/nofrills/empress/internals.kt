package io.nofrills.empress

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BroadcastChannel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.actor
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import java.util.concurrent.ConcurrentHashMap

class DefaultEmpressBackend<Event, Patch : Any, Request> constructor(
    private val empress: Empress<Event, Patch, Request>,
    private val idProducer: RequestIdProducer,
    private val requestHolder: RequestHolder,
    private val scope: CoroutineScope,
    storedPatches: Collection<Patch>?
) : EmpressApi<Event, Patch> {

    private var model: Model<Patch> = if (storedPatches == null) {
        Model(empress.initializer())
    } else {
        Model(storedPatches + empress.initializer(), skipDuplicates = true)
    }
    private val modelChannel =
        BroadcastChannel<Model<Patch>>(capacity = Channel.CONFLATED).apply { offer(model) }

    private val requests: Requests<Event, Request> by lazy {
        DefaultRequests(
            idProducer,
            empress::onRequest,
            requestHolder,
            scope,
            this::sendSuspending
        )
    }

    private val updates = BroadcastChannel<Update<Event, Patch>>(UPDATES_CHANNEL_CAPACITY)
    private val updatesFlow: Flow<Update<Event, Patch>> = updates.asFlow()

    init {
        val job =
            scope.coroutineContext[Job] ?: error("Coroutine context does not contain a Job element")
        job.invokeOnCompletion {
            modelChannel.close()
            updates.close()
        }
    }

    private val empressActor = scope.actor<Msg<Event>>(start = CoroutineStart.LAZY) {
        var isRunning = true
        for (msg in channel) {
            @Suppress("UNUSED_VARIABLE")
            val unused: Any = when (msg) {
                is Msg.Interrupt -> isRunning = false
                is Msg.WithEvent -> processEvent(msg.event)
            }
            if ((!isRunning && channel.isEmpty && requestHolder.isEmpty()) || channel.isClosedForReceive) {
                modelChannel.close()
                updates.close()
                break
            }
        }
    }

    override fun interrupt() {
        scope.launch {
            empressActor.send(Msg.Interrupt())
        }
    }

    override suspend fun modelSnapshot(): Model<Patch> {
        val channel = modelChannel.openSubscription()
        val model = channel.receive()
        channel.cancel()
        return model
    }

    override fun send(event: Event) {
        scope.launch {
            empressActor.send(Msg.WithEvent(event))
        }
    }

    override fun updates(): Flow<Update<Event, Patch>> {
        return updatesFlow
    }

    internal fun areChannelsClosed(): Boolean {
        return empressActor.isClosedForSend && modelChannel.isClosedForSend && updates.isClosedForSend
    }

    private suspend fun processEvent(event: Event) {
        val updatedPatches = empress.onEvent(event, model, requests)
        model = Model(model, updatedPatches)

        modelChannel.send(model)
        updates.send(Update(model, event))

        requestHolder.snapshot()
            .filter { !it.isActive && !it.isCompleted }
            .forEach { it.start() }
    }

    private suspend fun sendSuspending(event: Event) {
        empressActor.send(Msg.WithEvent(event))
    }

    companion object {
        private const val UPDATES_CHANNEL_CAPACITY = 16
    }
}

private sealed class Msg<Event> {
    class Interrupt<Event> : Msg<Event>()
    data class WithEvent<Event>(val event: Event) : Msg<Event>()
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

    override fun snapshot(): Sequence<Job> {
        return requestMap.values.asSequence()
    }
}

internal class DefaultRequests<Event, Request> constructor(
    private val idProducer: RequestIdProducer,
    private val onRequest: suspend (Request) -> Event,
    private val requestHolder: RequestHolder,
    private val scope: CoroutineScope,
    private val sendEvent: suspend (Event) -> Unit
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
                sendEvent(eventFromRequest)
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
