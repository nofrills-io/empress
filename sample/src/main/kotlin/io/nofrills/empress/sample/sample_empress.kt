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

class SampleEmpress : Empress<Signal>() {
    val counter = model(Counter(0))
    val sender = model<Sender>(Sender.Idle)

    suspend fun cancelSendingCounter() = onEvent {
        val state = sender.get()
        if (state is Sender.Sending) {
            cancelRequest(state.requestId)
            sender.update(Sender.Idle)
            signal(Signal.CounterSendCancelled)
        }
    }

    private suspend fun onCounterSent() = onEvent {
        sender.update(Sender.Idle)
        signal(Signal.CounterSent)
    }

    suspend fun decrement() = onEvent {
        counter.updateWith { it.copy(count = it.count - 1) }
    }

    suspend fun failure() = onEvent {
        throw OnEventFailure()
    }

    suspend fun failureInRequest() = onEvent {
        request { failedRequest() }
    }

    suspend fun increment() = onEvent {
        counter.updateWith { it.copy(count = it.count + 1) }
    }

    suspend fun sendCounter() = onEvent {
        val state = sender.get()
        if (state is Sender.Sending) {
            return@onEvent
        }
        val requestId = request { sendCounter(counter.get().count) }
        sender.update(Sender.Sending(requestId))
    }

    private suspend fun failedRequest() = onRequest {
        throw OnRequestFailure()
    }

    private suspend fun sendCounter(counterValue: Int) = onRequest {
        delay(abs(counterValue) * 1000L)
        onCounterSent()
    }
}
