/*
 * Copyright 2020 Mateusz Armatys
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.nofrills.empress.base

import kotlinx.coroutines.delay

internal sealed class Model {
    data class Counter(val count: Int) : Model()
    data class Sender(val requestId: RequestId? = null) : Model()
}

internal sealed class Signal {
    object CounterSent : Signal()
    object SendingCancelled : Signal()
}

internal class SampleEmpress(private val models: Collection<Model>? = null) : Empress<Model, Signal>() {
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
