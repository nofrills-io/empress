package io.nofrills.empress

import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow

interface Empress<Event, Patch : Any, Request> {
    fun initializer(): Collection<Patch>
    fun onEvent(
        event: Event,
        model: Model<Patch>,
        requests: Requests<Event, Request>
    ): Collection<Patch>

    suspend fun onRequest(request: Request): Event
}

// Event - denotes something that took place, based on an event, we synchronously change our model (patches)
// Patch - represents an update in the model
interface EmpressApi<Event, Patch : Any> {
    fun interrupt()
    suspend fun modelSnapshot(): Model<Patch>
    fun send(event: Event)
    fun updates(): Flow<Update<Event, Patch>>
}

interface Requests<Event, Request> {
    fun cancel(requestId: RequestId?): Boolean
    fun post(request: Request): RequestId
}

interface RequestHolder {
    fun isEmpty(): Boolean
    fun pop(requestId: RequestId): Job?
    fun push(requestId: RequestId, requestJob: Job)
    fun snapshot(): Sequence<Job>
}

interface RequestIdProducer {
    fun getNextRequestId(): RequestId
}
