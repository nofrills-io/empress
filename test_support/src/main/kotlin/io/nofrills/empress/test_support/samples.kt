package io.nofrills.empress.test_support

import android.os.Parcelable
import io.nofrills.empress.base.Empress
import kotlinx.android.parcel.Parcelize

sealed class Event {
    object Increment : Event()
}

sealed class Model {
    data class Counter(var count: Int) : Model()

    @Parcelize
    data class ParcelableCounter(var count: Int) : Model(), Parcelable
}

sealed class Signal

class SampleEmpress : Empress<Model, Signal>() {
    override fun initialModels(): Collection<Model> {
        return listOf(Model.Counter(0), Model.ParcelableCounter(0))
    }

    fun increment() = onEvent {
        val counter = get<Model.Counter>()
        val parcelableCounter = get<Model.ParcelableCounter>()
        update(counter.copy(count = counter.count + 1))
        update(parcelableCounter.copy(count = parcelableCounter.count + 1))
    }
}
