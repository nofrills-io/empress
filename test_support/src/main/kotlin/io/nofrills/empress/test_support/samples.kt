package io.nofrills.empress.test_support

import android.os.Parcelable
import io.nofrills.empress.MutableEmpress
import io.nofrills.empress.Empress
import io.nofrills.empress.Models
import io.nofrills.empress.RequestCommander
import kotlinx.android.parcel.Parcelize

sealed class Event {
    object Increment : Event()
}

sealed class Model {
    data class Counter(var count: Int) : Model()

    @Parcelize
    data class ParcelableCounter(var count: Int) : Model(), Parcelable
}

sealed class Request

class SampleEmpress : Empress<Event, Model, Request> {
    override fun initialize(): Collection<Model> {
        return listOf(Model.Counter(0), Model.ParcelableCounter(0))
    }

    override fun onEvent(
        event: Event,
        models: Models<Model>,
        requests: RequestCommander<Request>
    ): Collection<Model> {
        return when (event) {
            Event.Increment -> {
                val counter = models[Model.Counter::class]
                val parcelableCounter = models[Model.ParcelableCounter::class]
                listOf(
                    counter.copy(count = counter.count + 1),
                    parcelableCounter.copy(count = parcelableCounter.count + 1)
                )
            }
        }
    }

    override suspend fun onRequest(request: Request): Event {
        error("mock")
    }
}


class SampleMutableEmpress : MutableEmpress<Event, Model, Request> {
    override fun initialize(): Collection<Model> {
        return listOf(Model.Counter(0), Model.ParcelableCounter(0))
    }

    override fun onEvent(event: Event, models: Models<Model>, requests: RequestCommander<Request>) {
        return when (event) {
            Event.Increment -> {
                models[Model.Counter::class].count += 1
                models[Model.ParcelableCounter::class].count += 1
            }
        }
    }

    override suspend fun onRequest(request: Request): Event {
        error("mock")
    }
}
