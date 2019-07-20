package io.nofrills.empress

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BroadcastChannel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.sendBlocking
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.selects.whileSelect
import kotlin.coroutines.CoroutineContext

class DefaultEmpressApi<Event, Patch : Any>(private val inoBackend: EmpressBackend<Event, Patch>) :
    EmpressApi<Event, Patch> {
    override fun send(event: Event) {
        inoBackend.sendEvent(event)
    }

    override fun updates(): Flow<Update<Event, Patch>> {
        return flow {
            emit(Update<Event, Patch>(inoBackend.model))
            inoBackend.updates().collect {
                emit(it)
            }
        }
    }
}

class DefaultEmpressBackend<Event, Patch : Any, Request> constructor(
    dispatcher: CoroutineDispatcher,
    private val empress: Empress<Event, Patch, Request>,
    storedPatches: Collection<Patch>? = null
) : EmpressBackend<Event, Patch>, CoroutineScope {
    private val coroutineExceptionHandler = CoroutineExceptionHandler { _, throwable ->
        onDestroy()
        throw throwable
    }
    private val job = Job()
    override val coroutineContext: CoroutineContext = dispatcher + job + coroutineExceptionHandler
    override var model: Model<Patch> = if (storedPatches == null) {
        Model(empress.initializer())
    } else {
        Model(storedPatches + empress.initializer(), skipDuplicates = true)
    }

    private val events: Channel<Event> = Channel(capacity = Channel.UNLIMITED)
    private val interruption: Channel<Unit> = Channel()
    private val requests: Channel<Request> = Channel(capacity = Channel.UNLIMITED)
    private val updates =
        BroadcastChannel<Update<Event, Patch>>(capacity = UPDATES_CHANNEL_CAPACITY)
    private val updatesFlow: Flow<Update<Event, Patch>> = updates.asFlow()

    override fun interrupt() {
        if (!interruption.isClosedForSend) {
            interruption.sendBlocking(Unit)
            interruption.close()
        }
    }

    override fun onCreate() {
        launch {
            var isRunning = true
            whileSelect {
                events.onReceive { event ->
                    processEvent(event)
                    isRunning || !requests.isEmpty
                }
                requests.onReceive { request ->
                    processRequest(request)
                    // return true, since we still want
                    // to process at least one more event
                    // (a request always produces an event)
                    true
                }
                interruption.onReceive {
                    isRunning = false
                    !events.isEmpty || !requests.isEmpty
                }
            }
            closeChannels()
        }
    }

    override fun onDestroy() {
        closeChannels()
        job.cancel()
    }

    override fun updates(): Flow<Update<Event, Patch>> {
        return updatesFlow
    }

    override fun sendEvent(event: Event) {
        events.sendBlocking(event)
        // TODO could return some kind of a handle, that will allow to cancel a request (if it's associated with the event)
    }

    private fun closeChannels() {
        events.close()
        requests.close()
        updates.close()
        interruption.close()
    }

    private suspend fun processEvent(incomingEvent: Event) {
        val effect = empress.onEvent(incomingEvent, model)
        model = Model(model, effect.updatedPatches)
        updates.send(Update(model, incomingEvent))

        if (effect.request != null) {
            // TODO instead of sending a request, launch it in `async`?
            requests.send(effect.request)
        }
    }

    private suspend fun processRequest(request: Request) {
        // TODO we'll have to wait for the request, which blocks the loop in `onCreate`, and in result we cannot process any other incoming events
        val event = empress.onRequest(request)
        sendEvent(event)
    }

    companion object {
        private const val UPDATES_CHANNEL_CAPACITY = 16
    }
}
