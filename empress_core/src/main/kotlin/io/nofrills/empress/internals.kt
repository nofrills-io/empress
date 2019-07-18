package io.nofrills.empress

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BroadcastChannel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlin.coroutines.CoroutineContext

class DefaultEmpressApi<Event, Patch : Any>(private val inoBackend: EmpressBackend<Event, Patch>) :
    EmpressApi<Event, Patch> {
    override fun send(event: Event) {
        inoBackend.sendEvent(event)
    }

    override fun updates(): Flow<Update<Event, Patch>> {
        return flow {
            emit(Update.Initial<Event, Patch>(inoBackend.model))
            inoBackend.updates().collect {
                emit(it)
            }
        }
    }
}

class DefaultEmpressBackend<Event, Patch : Any, Request>(
    private val empress: Empress<Event, Patch, Request>,
    storedPatches: Collection<Patch>?
) : EmpressBackend<Event, Patch>, CoroutineScope {
    private val supervisorJob = SupervisorJob()
    override val coroutineContext: CoroutineContext = Dispatchers.Main + supervisorJob
    override var model: Model<Patch> = if (storedPatches == null) {
        Model(empress.initializer())
    } else {
        Model(storedPatches + empress.initializer(), skipDuplicates = true)
    }

    private val events: Channel<Event> = Channel(capacity = Channel.UNLIMITED)
    private val requests: Channel<Request> = Channel(capacity = Channel.UNLIMITED)
    private val updates =
        BroadcastChannel<Update<Event, Patch>>(capacity = UPDATES_CHANNEL_CAPACITY)

    override fun onCreate() {
        launch {
            coroutineScope {
                launch {
                    for (request in requests) {
                        processRequest(request)
                    }
                }
                launch {
                    for (event in events) {
                        processEvent(event)
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        supervisorJob.cancel()
        requests.cancel()
        events.cancel()
        updates.cancel()
    }

    override suspend fun updates(): Flow<Update<Event, Patch>> {
        return updates.asFlow()
    }

    override fun sendEvent(event: Event) {
        check(events.offer(event))
    }

    private suspend fun processRequest(request: Request) {
        val event = empress.onRequest(request)
        sendEvent(event)
    }

    private suspend fun processEvent(incomingEvent: Event) {
        val effect = empress.onEvent(incomingEvent, model)
        model = Model(model, effect.updatedPatches)
        updates.send(Update.FromEvent(incomingEvent, model))

        if (effect.request != null) {
            requests.send(effect.request)
        }
    }

    companion object {
        private const val UPDATES_CHANNEL_CAPACITY = 16
    }
}
