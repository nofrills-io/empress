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
    data class CounterSent(val sentValue: Int) : Signal()
    object SendingCancelled : Signal()
}

internal class SampleEmpress(private val models: Collection<Model>? = null) :
    Empress<Model, Signal>() {
    override fun initialModels(): Collection<Model> {
        return models ?: listOf(Model.Counter(0), Model.Sender())
    }

    suspend fun decrement() = onEvent {
        val count = get<Model.Counter>().count
        update(Model.Counter(count - 1))
    }

    suspend fun delta(d: Int) = onEvent {
        val count = get<Model.Counter>().count
        update(Model.Counter(count + d))
    }

    suspend fun increment() = onEvent {
        val count = get<Model.Counter>().count
        update(Model.Counter(count + 1))
    }

    suspend fun indirectIncrementAndSend() = onEvent {
        event { increment() }
        val count = get<Model.Counter>().count
        val requestId = request { indirectSendCounter(count) }
        update(Model.Sender(requestId))
    }

    suspend fun indirectSend() = onEvent {
        val count = get<Model.Counter>().count
        val requestId = request { indirectSendCounter(count) }
        update(Model.Sender(requestId))
    }

    suspend fun ping() = onEvent {}

    suspend fun sendCounter() = onEvent {
        if (get<Model.Sender>().requestId != null) return@onEvent

        val count = get<Model.Counter>().count
        val requestId = request { sendCounter(count) }
        update(Model.Sender(requestId))
    }

    suspend fun sendCounterVariableCount() = onEvent {
        var count = get<Model.Counter>().count
        val requestId = request { sendCounter(count) }
        count += 3
        update(Model.Sender(requestId))
    }

    private suspend fun onCounterSent(sentValue: Int) = onEvent {
        signal(Signal.CounterSent(sentValue))
        update(Model.Sender(null))
    }

    suspend fun cancelSending() = onEvent {
        val requestId = get<Model.Sender>().requestId ?: return@onEvent
        cancelRequest(requestId)
        update(Model.Sender(null))
        signal(Signal.SendingCancelled)
    }

    suspend fun generateError() = onEvent {
        val counter = get<Model.Counter>()
        update(Model.Counter(counter.count + 1))
        throw OnEventError()
    }

    suspend fun generateErrorIndirect() = onEvent {
        event { increment() }
        event { generateError() }
        event { increment() }
    }

    suspend fun errorInRequest() = onEvent {
        event { increment() }
        request { onRequestError() }
        event { increment() }
    }

    suspend fun errorInRequestIndirect() = onEvent {
        event { increment() }
        request { onRequestErrorIndirect() }
        event { increment() }
    }

    private suspend fun indirectSendCounter(count: Int) = onRequest {
        sendCounter(count)
    }

    private suspend fun sendCounter(count: Int) = onRequest {
        delay(count * 1000L)
        onCounterSent(count)
    }

    private suspend fun onRequestError() = onRequest {
        throw OnRequestError()
    }

    private suspend fun onRequestErrorIndirect() = onRequest {
        onRequestError()
    }
}

internal class OnEventError : Throwable()
internal class OnRequestError : Throwable()
