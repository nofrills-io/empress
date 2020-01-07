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

import io.nofrills.empress.consumable.consumableOf
import io.nofrills.empress.Empress
import io.nofrills.empress.builder.Empress
import kotlinx.coroutines.delay
import kotlin.math.abs

val sampleEmpress: Empress<Event, Model, Request> by lazy {
    Empress<Event, Model, Request> {
        initializer { Model.Counter(0) }
        initializer { Model.Sender(SenderState.Idle) }

        onEvent<Event.CancelSendingCounter> {
            val sender = models[Model.Sender::class]
            val state = sender.consumableState.peek()
            if (state is SenderState.Sending) {
                requests.cancel(state.requestId)
                listOf(Model.Sender(SenderState.Cancelled) { Event.SenderStateConsumed })
            } else {
                listOf()
            }
        }

        onEvent<Event.CounterSent> { listOf(Model.Sender(SenderState.Sent) { Event.SenderStateConsumed }) }

        onEvent<Event.Decrement> {
            val counter = models[Model.Counter::class]
            listOf(counter.copy(count = counter.count - 1))
        }

        onEvent<Event.GetFailure> { throw OnEventFailure() }

        onEvent<Event.GetFailureWithRequest> {
            requests.post(Request.GetFailure)
            emptyList()
        }

        onEvent<Event.Increment> {
            val counter = models[Model.Counter::class]
            listOf(counter.copy(count = counter.count + 1))
        }

        onEvent<Event.SendCounter> {
            val state = models[Model.Sender::class].consumableState.peek()
            if (state is SenderState.Sending) {
                return@onEvent emptyList()
            }
            val counter = models[Model.Counter::class]
            val requestId = requests.post(Request.SendCounter(counter.count))
            listOf(Model.Sender(SenderState.Sending(requestId)))
        }

        onEvent<Event.SenderStateConsumed> {
            val sender = models[Model.Sender::class]
            listOf(sender.copy(consumableState = consumableOf(SenderState.Idle)))
        }

        onRequest<Request.GetFailure> { throw OnRequestFailure() }

        onRequest<Request.SendCounter> {
            delay(abs(request.counterValue) * 1000L)
            Event.CounterSent
        }
    }
}
