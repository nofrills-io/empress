package io.nofrills.empress

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BroadcastChannel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.sendBlocking
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.selects.whileSelect
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

class DefaultEmpressBackend<Event, Patch : Any, Request> constructor(
    dispatcher: CoroutineDispatcher,
    private val empress: Empress<Event, Patch, Request>,
    storedPatches: Collection<Patch>? = null
) : EmpressBackend<Event, Patch>, CoroutineScope {
    private val supervisorJob = SupervisorJob()
    override val coroutineContext: CoroutineContext = dispatcher + supervisorJob
    override var model: Model<Patch> = if (storedPatches == null) {
        Model(empress.initializer())
    } else {
        Model(storedPatches + empress.initializer(), skipDuplicates = true)
    }

    private val events: Channel<Event> = Channel(capacity = Channel.UNLIMITED)
    private val halt: Channel<Unit> = Channel()
    private val requests: Channel<Request> = Channel(capacity = Channel.UNLIMITED)
    private val updates =
        BroadcastChannel<Update<Event, Patch>>(capacity = UPDATES_CHANNEL_CAPACITY)

    override fun halt() {
        if (!halt.isClosedForSend) {
            halt.sendBlocking(Unit)
            halt.close()
        }
    }

    override fun onCreate() {
        launch {
            var isRunning = true
            whileSelect {
                events.onReceive { event ->
                    val hasPendingRequest = processEvent(event)
                    isRunning || hasPendingRequest
                }
                requests.onReceive { request ->
                    processRequest(request)
                    true
                }
                halt.onReceive {
                    isRunning = false
                    events.poll() != null || requests.poll() != null
                }
            }
            closeChannels()
        }
    }

    override fun onDestroy() {
        closeChannels()
        supervisorJob.cancel()
    }

    override fun updates(): Flow<Update<Event, Patch>> {
        return updates.asFlow()
    }

    override fun sendEvent(event: Event) {
        events.sendBlocking(event)
    }

    private fun closeChannels() {
        events.close()
        requests.close()
        updates.close()
        halt.close()
    }

    private suspend fun processEvent(incomingEvent: Event): Boolean {
        val effect = empress.onEvent(incomingEvent, model)
        model = Model(model, effect.updatedPatches)
        updates.send(Update.FromEvent(incomingEvent, model))

        if (effect.request != null) {
            requests.send(effect.request)
            return true
        }
        return false
    }

    private suspend fun processRequest(request: Request) {
        val event = empress.onRequest(request)
        sendEvent(event)
    }

    companion object {
        private const val UPDATES_CHANNEL_CAPACITY = 16
    }
}
