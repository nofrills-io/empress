package io.nofrills.empress.test_support

import android.annotation.SuppressLint
import android.os.Parcelable
import io.nofrills.empress.base.Empress
import kotlinx.parcelize.Parcelize

sealed class Model {
    data class Counter(var count: Int) : Model()

    @Parcelize
    @SuppressLint("ParcelCreator")
    data class ParcelableCounter(var count: Int) : Model(), Parcelable
}

class SampleEmpress : Empress() {
    val counter by model(Model.Counter(0))
    val parcelableCounter by model(Model.ParcelableCounter(0))

    suspend fun increment() = onEvent {
        counter.updateWith { it.copy(count = it.count + 1) }
        parcelableCounter.updateWith { it.copy(count = it.count + 1) }
    }
}
