package io.nofrills.empress

import kotlinx.coroutines.flow.Flow

interface Empress<Event, Patch : Any, Request> {
    fun initializer(): Collection<Patch>
    fun onEvent(event: Event, model: Model<Patch>): Effect<Patch, Request>
    suspend fun onRequest(request: Request): Event
}

// Event - denotes something that took place, based on an event, we synchronously change our model (patches)
// Patch - represents an update in the model
interface EmpressApi<Event, Patch : Any> {
    fun send(event: Event)
    fun updates(): Flow<Update<Event, Patch>>
}

interface EmpressBackend<Event, Patch : Any> {
    var model: Model<Patch>

    fun onCreate()
    fun onDestroy()

    fun sendEvent(event: Event)
    fun updates(): Flow<Update<Event, Patch>>
}
