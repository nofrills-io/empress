package io.nofrills.empress.sample

import android.os.Parcelable
import io.nofrills.empress.Effect
import io.nofrills.empress.Empress
import io.nofrills.empress.Model
import kotlinx.android.parcel.Parcelize
import kotlinx.coroutines.delay
import kotlin.math.abs

sealed class Event {
    object Decrement : Event()
    object Increment : Event()
    object SendCounter : Event()
    object CounterSent : Event()
}

sealed class Patch {
    @Parcelize
    data class Counter(val count: Int) : Patch(), Parcelable

    data class Sender(val isSending: Boolean) : Patch()
}

sealed class Request {
    class SendCounter(val counterValue: Int) : Request()
}

class CounterEmpress : Empress<Event, Patch, Request> {
    override fun initializer(): Collection<Patch> = listOf(Patch.Counter(0), Patch.Sender(false))

    override fun onEvent(event: Event, model: Model<Patch>): Effect<Patch, Request> {
        return when (event) {
            Event.Decrement -> changeCount(-1, model)
            Event.Increment -> changeCount(1, model)
            Event.SendCounter -> sendCurrentCount(model)
            Event.CounterSent -> onSent(model)
        }
    }

    override suspend fun onRequest(request: Request): Event {
        return when (request) {
            is Request.SendCounter -> run {
                delay(abs(request.counterValue) * 1000L)
                Event.CounterSent
            }
        }
    }

    private fun changeCount(d: Int, model: Model<Patch>): Effect<Patch, Request> {
        val counter = model.get<Patch.Counter>()
        return Effect(counter.copy(count = counter.count + d))
    }

    private fun sendCurrentCount(model: Model<Patch>): Effect<Patch, Request> {
        val sender = model.get<Patch.Sender>()
        if (sender.isSending) {
            return Effect()
        }

        val counter = model.get<Patch.Counter>()
        return Effect(
            sender.copy(isSending = true),
            Request.SendCounter(counter.count)
        )
    }

    private fun onSent(model: Model<Patch>): Effect<Patch, Request> {
        return Effect(
            model.get<Patch.Sender>().copy(isSending = false)
        )
    }
}
