/*
 * Copyright 2019 Mateusz Armatys
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

package io.nofrills.empress.sample

import io.nofrills.empress.base.Empress
import kotlinx.coroutines.delay
import kotlin.math.abs

class SampleEmpress : Empress<Model, Signal>() {
    override fun initialModels(): Collection<Model> {
        return listOf(Model.Counter(0), Model.Sender(SenderState.Idle))
    }

    suspend fun cancelSendingCounter() = onEvent {
        val sender = get<Model.Sender>()
        val state = sender.state
        if (state is SenderState.Sending) {
            cancelRequest(state.requestId)
            update(Model.Sender(SenderState.Idle))
            signal(Signal.CounterSendCancelled)
        }
    }

    private suspend fun onCounterSent() = onEvent {
        update(Model.Sender(SenderState.Idle))
        signal(Signal.CounterSent)
    }

    suspend fun decrement() = onEvent {
        val counter = get<Model.Counter>()
        update(counter.copy(count = counter.count - 1))
    }

    suspend fun failure() = onEvent {
        throw OnEventFailure()
    }

    suspend fun failureInRequest() = onEvent {
        request { failedRequest() }
    }

    suspend fun increment() = onEvent {
        val counter = get<Model.Counter>()
        update(counter.copy(count = counter.count + 1))
    }

    suspend fun sendCounter() = onEvent {
        val state = get<Model.Sender>().state
        if (state is SenderState.Sending) {
            return@onEvent
        }
        val counter = get<Model.Counter>()
        val requestId = request { sendCounter(counter.count) }
        update(Model.Sender(SenderState.Sending(requestId)))
    }

    private suspend fun failedRequest() = onRequest {
        throw OnRequestFailure()
    }

    private suspend fun sendCounter(counterValue: Int) = onRequest {
        delay(abs(counterValue) * 1000L)
        onCounterSent()
    }
}
