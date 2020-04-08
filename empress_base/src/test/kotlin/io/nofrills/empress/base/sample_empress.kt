package io.nofrills.empress.base

import kotlinx.coroutines.delay

sealed class Model {
    data class Counter(val count: Int) : Model()
    data class Sender(val requestId: RequestId? = null) : Model()
}

sealed class Signal {
    object CounterSent : Signal()
    object SendingCancelled : Signal()
}

class SampleEmpress(private val models: Collection<Model>? = null) : Empress<Model, Signal>() {
    override fun initialModels(): Collection<Model> {
        return models ?: listOf(Model.Counter(0), Model.Sender())
    }

    fun decrement() = onEvent {
        val count = get<Model.Counter>().count
        update(Model.Counter(count - 1))
    }

    fun delta(d: Int) = onEvent {
        val count = get<Model.Counter>().count
        update(Model.Counter(count + d))
    }

    fun increment() = onEvent {
        val count = get<Model.Counter>().count
        update(Model.Counter(count + 1))
    }

    fun ping() = onEvent {}

    fun sendCounter() = onEvent {
        if (get<Model.Sender>().requestId != null) return@onEvent

        val count = get<Model.Counter>().count
        val requestId = sendCounter(count)
        update(Model.Sender(requestId))
    }

    private fun onCounterSent() = onEvent {
        signal(Signal.CounterSent)
        update(Model.Sender(null))
    }

    fun cancelSending() = onEvent {
        val requestId = get<Model.Sender>().requestId ?: return@onEvent
        cancelRequest(requestId)
        update(Model.Sender(null))
        signal(Signal.SendingCancelled)
    }

    private fun sendCounter(count: Int) = onRequest {
        delay(count * 1000L)
        onCounterSent()
    }
}
