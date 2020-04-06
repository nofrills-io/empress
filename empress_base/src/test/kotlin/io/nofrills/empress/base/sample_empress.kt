package io.nofrills.empress.base

import kotlinx.coroutines.delay
import kotlin.random.Random

sealed class Model {
    data class Counter(val count: Int) : Model()
    data class Sender(val handlerId: HandlerId? = null) : Model()
}

sealed class Signal {
    object CounterSent : Signal()
    object SendingCancelled : Signal()
}

class SampleEmpress(private val models: Collection<Model>? = null) : Empress<Model, Signal>() {
    override fun initialModels(): Collection<Model> {
        return models ?: listOf(Model.Counter(0), Model.Sender())
    }

    suspend fun decrement() = handler {
        val count = get<Model.Counter>().count
        update(Model.Counter(count - 1))
    }

    suspend fun delta(d: Int, withDelay: Boolean = false, withAfterDelay: Boolean = false) =
        handler {
            if (withDelay) {
                delay(10)
            }
            val count = get<Model.Counter>().count
            update(Model.Counter(count + d))
            if (withAfterDelay) {
                delay(Random.nextLong(100))
            }
        }

    suspend fun increment() = handler {
        val count = get<Model.Counter>().count
        update(Model.Counter(count + 1))
    }

    suspend fun ping() = handler {
        get<Model.Counter>()
    }

    suspend fun sendCounter() = handler {
        if (get<Model.Sender>().handlerId != null) return@handler

        update(Model.Sender(handlerId()))
        val count = get<Model.Counter>().count
        delay(count * 1000L)
        signal(Signal.CounterSent)
        update(Model.Sender(null))
    }

    suspend fun cancelSending() = handler {
        val handlerId = get<Model.Sender>().handlerId ?: return@handler
        cancelHandler(handlerId)
        update(Model.Sender(null))
        signal(Signal.SendingCancelled)
    }
}
