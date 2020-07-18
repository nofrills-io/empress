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
    data class Counter(val count: Long) : Model()

    data class Data(val text: String) : Model()

    sealed class Sender : Model() {
        object Idle : Sender()
        data class Loading(val requestId: RequestId) : Sender()
    }
}

internal sealed class CounterSignal {
    data class CounterSent(val sentValue: Long) : CounterSignal()
    object SendingCancelled : CounterSignal()
}

internal class SampleEmpress : Empress() {
    val counter = model(Model.Counter(0))
    val data = model(Model.Data(""))
    val sender = model<Model.Sender>(Model.Sender.Idle)

    val counterSignal = signal<CounterSignal>()

    suspend fun decrement() = onEvent {
        val count = counter.get().count
        counter.update(Model.Counter(count - 1))
    }

    suspend fun delta(d: Int) = onEvent {
        val count = counter.get().count
        counter.update(Model.Counter(count + d))
    }

    suspend fun append(s: String) = onEvent {
        data.update(Model.Data(data.get().text + s))
    }

    suspend fun increment() = onEvent {
        val count = counter.get().count
        counter.update(Model.Counter(count + 1))
    }

    suspend fun indirectIncrementAndSend() = onEvent {
        event { increment() }
        val count = counter.get().count
        val requestId = request { indirectSendCounter(count) }
        sender.update(Model.Sender.Loading(requestId))
    }

    suspend fun indirectSend() = onEvent {
        val count = counter.get().count
        val requestId = request { indirectSendCounter(count) }
        sender.update(Model.Sender.Loading(requestId))
    }

    suspend fun ping() = onEvent {}

    suspend fun sendCounter(skipIfLoading: Boolean = true) = onEvent {
        if (skipIfLoading && sender.get() is Model.Sender.Loading) return@onEvent

        val count = counter.get().count
        val requestId = request { sendCounter(count) }
        sender.update(Model.Sender.Loading(requestId))
    }

    suspend fun sendCounterVariableCount() = onEvent {
        var count = counter.get().count
        val requestId = request { sendCounter(count) }
        count += 3
        sender.update(Model.Sender.Loading(requestId))
    }

    private suspend fun onCounterSent(sentValue: Long) = onEvent {
        counterSignal.push(CounterSignal.CounterSent(sentValue))
        sender.update(Model.Sender.Idle)
    }

    suspend fun cancelSending() = onEvent {
        val loading = sender.get() as? Model.Sender.Loading ?: return@onEvent
        cancelRequest(loading.requestId)
        sender.update(Model.Sender.Idle)
        counterSignal.push(CounterSignal.SendingCancelled)
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

    private suspend fun indirectSendCounter(count: Long) = onRequest {
        sendCounter(count)
    }

    private suspend fun sendCounter(count: Long) = onRequest {
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

internal class DuplicateModelEmpress : Empress() {
    val counter = model(Model.Counter(0))
    val anotherCounter = model(Model.Counter(0))
}

internal class OnEventError : Throwable()
internal class OnRequestError : Throwable()
