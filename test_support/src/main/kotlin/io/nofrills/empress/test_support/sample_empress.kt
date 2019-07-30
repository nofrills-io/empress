package io.nofrills.empress.test_support

import android.os.Parcelable
import io.nofrills.empress.*
import kotlinx.android.parcel.Parcelize
import kotlinx.coroutines.delay
import kotlin.math.abs

sealed class Event {
    object Decrement : Event()
    object Increment : Event()
    object SendCounter : Event()
    object CancelSendingCounter : Event()
    object CounterSent : Event()
    object GetFailure : Event()
    object GetFailureWithRequest : Event()
}

sealed class Patch {
    @Parcelize
    data class Counter(val count: Int) : Patch(), Parcelable

    data class Sender(val requestId: RequestId?) : Patch()
}

sealed class Request {
    class SendCounter(val counterValue: Int) : Request()
    object GetFailure : Request()
}

class SampleEmpress : Empress<Event, Patch, Request> {
    override fun initializer(): Collection<Patch> = listOf(Patch.Counter(0), Patch.Sender(null))

    override fun onEvent(
        event: Event,
        model: Model<Patch>,
        requests: Requests<Event, Request>
    ): Collection<Patch> {
        return when (event) {
            Event.Decrement -> changeCount(-1, model)
            Event.Increment -> changeCount(1, model)
            Event.SendCounter -> sendCurrentCount(model, requests)
            Event.CancelSendingCounter -> cancelSendingCounter(model, requests)
            Event.CounterSent -> onSent(model)
            Event.GetFailure -> throw OnEventFailure()
            Event.GetFailureWithRequest -> run {
                requests.post(Request.GetFailure)
                emptyList<Patch>()
            }
        }
    }

    override suspend fun onRequest(request: Request): Event {
        return when (request) {
            is Request.SendCounter -> run {
                delay(abs(request.counterValue) * 1000L)
                Event.CounterSent
            }
            Request.GetFailure -> throw OnRequestFailure()
        }
    }

    private fun changeCount(d: Int, model: Model<Patch>): Collection<Patch> {
        val counter = model.get<Patch.Counter>()
        return listOf(counter.copy(count = counter.count + d))
    }

    private fun sendCurrentCount(
        model: Model<Patch>,
        requests: Requests<Event, Request>
    ): Collection<Patch> {
        val sender = model.get<Patch.Sender>()
        if (sender.requestId != null) {
            return listOf()
        }

        val counter = model.get<Patch.Counter>()
        val requestId = requests.post(Request.SendCounter(counter.count))
        return listOf(sender.copy(requestId = requestId))
    }

    private fun cancelSendingCounter(
        model: Model<Patch>,
        requests: Requests<Event, Request>
    ): Collection<Patch> {
        val sender = model.get<Patch.Sender>()
        val requestId = sender.requestId ?: return listOf()
        requests.cancel(requestId)
        return listOf(sender.copy(requestId = null))
    }

    private fun onSent(model: Model<Patch>): Collection<Patch> {
        return listOf(model.get<Patch.Sender>().copy(requestId = null))
    }
}

class OnEventFailure : Throwable()

class OnRequestFailure : Throwable()
