package io.nofrills.empress

import kotlinx.coroutines.delay

internal sealed class Event {
    object Decrement : Event()
    object Increment : Event()
    data class Send(val delayMillis: Long) : Event()
    object CancelSending : Event()
    object CounterSent : Event()
}

internal sealed class Patch {
    data class Counter(val count: Int) : Patch()
    data class Sender(val requestId: RequestId? = null) : Patch()
}

internal sealed class Request {
    data class Send(val delayMillis: Long) : Request()
}

internal class TestEmpress : Empress<Event, Patch, Request> {
    override fun initializer(): Collection<Patch> = listOf(
        Patch.Counter(0),
        Patch.Sender(null)
    )

    override fun onEvent(
        event: Event,
        model: Model<Patch>,
        requests: Requests<Event, Request>
    ): Collection<Patch> {
        return when (event) {
            Event.Decrement -> listOf(model.get<Patch.Counter>().let { it.copy(count = it.count - 1) })
            Event.Increment -> listOf(model.get<Patch.Counter>().let { it.copy(count = it.count + 1) })
            is Event.Send -> run<Collection<Patch>> {
                val requestId = requests.post(Request.Send(event.delayMillis))
                listOf(Patch.Sender(requestId))
            }
            Event.CancelSending -> run {
                val sender = model.get<Patch.Sender>()
                if (requests.cancel(sender.requestId)) {
                    listOf(Patch.Sender(null))
                } else {
                    emptyList()
                }
            }
            Event.CounterSent -> listOf(Patch.Sender(null))
        }
    }

    override suspend fun onRequest(request: Request): Event {
        return when (request) {
            is Request.Send -> run {
                delay(request.delayMillis)
                Event.CounterSent
            }
        }
    }
}
