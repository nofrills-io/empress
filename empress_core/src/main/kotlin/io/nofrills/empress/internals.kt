package io.nofrills.empress

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BroadcastChannel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlin.coroutines.CoroutineContext

class DefaultEmpressApi<Event, Patch : Any>(private val inoBackend: EmpressBackend<Event, Patch>) :
    EmpressApi<Event, Patch> {
    override fun send(event: Event, closeUpdates: Boolean) {
        inoBackend.sendEvent(event, closeUpdates)
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

    private val updates = BroadcastChannel<Update<Event, Patch>>(UPDATES_CHANNEL_CAPACITY)
    private val updatesFlow: Flow<Update<Event, Patch>> = updates.asFlow()

    override fun onDestroy() {
        updates.close()
        job.cancel()
    }

    override fun updates(): Flow<Update<Event, Patch>> {
        return updatesFlow
    }

    override fun sendEvent(event: Event, closeUpdates: Boolean): RequestHandle? {
        val effect = empress.onEvent(event, model)
        model = Model(model, effect.updatedPatches)

        launch {
            updates.send(Update(model, event))
            yield()
            if (closeUpdates && effect.request == null) {
                updates.close()
            }
        }

        val request = effect.request ?: return null
        val requestJob = launch {
            val eventFromRequest = empress.onRequest(request)
            yield()
            sendEvent(eventFromRequest, closeUpdates)
        }

        return JobRequestHandle(requestJob)
    }

    companion object {
        private const val UPDATES_CHANNEL_CAPACITY = 16
    }
}

internal class JobRequestHandle constructor(private val job: Job) : RequestHandle {
    override fun cancel() {
        job.cancel()
    }
}
