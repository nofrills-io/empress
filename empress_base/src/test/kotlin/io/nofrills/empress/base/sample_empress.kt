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

internal class SampleEmpress : Empress<Model, Signal>() {
    val counter = model(Model.Counter(0))
    val sender = model(Model.Sender())

    suspend fun decrement() = onEvent {
        val count = counter.get().count
        counter.update(Model.Counter(count - 1))
    }

    suspend fun delta(d: Int) = onEvent {
        val count = counter.get().count
        counter.update(Model.Counter(count + d))
    }

    suspend fun increment() = onEvent {
        val count = counter.get().count
        counter.update(Model.Counter(count + 1))
    }

    suspend fun indirectIncrementAndSend() = onEvent {
        event { increment() }
        val count = counter.get().count
        val requestId = request { indirectSendCounter(count) }
        sender.update(Model.Sender(requestId))
    }

    suspend fun indirectSend() = onEvent {
        val count = counter.get().count
        val requestId = request { indirectSendCounter(count) }
        sender.update(Model.Sender(requestId))
    }

    suspend fun ping() = onEvent {}

    suspend fun sendCounter() = onEvent {
        if (sender.get().requestId != null) return@onEvent

        val count = counter.get().count
        val requestId = request { sendCounter(count) }
        sender.update(Model.Sender(requestId))
    }

    suspend fun sendCounterVariableCount() = onEvent {
        var count = counter.get().count
        val requestId = request { sendCounter(count) }
        count += 3
        sender.update(Model.Sender(requestId))
    }

    private suspend fun onCounterSent(sentValue: Int) = onEvent {
        signal(Signal.CounterSent(sentValue))
        sender.update(Model.Sender(null))
    }

    suspend fun cancelSending() = onEvent {
        val requestId = sender.get().requestId ?: return@onEvent
        cancelRequest(requestId)
        sender.update(Model.Sender(null))
        signal(Signal.SendingCancelled)
    }

    suspend fun generateError() = onEvent {
        counter.updateWith { it.copy(count = it.count + 1) }
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

internal class DuplicateModelEmpress : Empress<Model, Signal>() {
    val counter = model(Model.Counter(0))
    val anotherCounter = model(Model.Counter(0))
}

internal class OnEventError : Throwable()
internal class OnRequestError : Throwable()
