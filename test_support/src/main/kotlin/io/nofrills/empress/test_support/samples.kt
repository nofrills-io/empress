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
    val counter = model(Model.Counter(0))
    val parcelableCounter = model(Model.ParcelableCounter(0))

    suspend fun increment() = onEvent {
        counter.updateWith { it.copy(count = it.count + 1) }
        parcelableCounter.updateWith { it.copy(count = it.count + 1) }
    }
}
