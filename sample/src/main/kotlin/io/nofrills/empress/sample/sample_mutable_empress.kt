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

import io.nofrills.empress.MutableEmpress
import io.nofrills.empress.builder.MutableEmpress
import kotlinx.coroutines.delay
import kotlin.math.abs

val sampleMutableEmpress: MutableEmpress<Event, MutModel, Request> by lazy {
    MutableEmpress<Event, MutModel, Request> {
        initializer { MutModel.Counter(0) }
        initializer { MutModel.Sender(null) }

        onEvent<Event.CancelSendingCounter> {
            val sender = models[MutModel.Sender::class]
            requests.cancel(sender.requestId)
            sender.requestId = null
        }

        onEvent<Event.CounterSent> { models[MutModel.Sender::class].requestId = null }

        onEvent<Event.Decrement> { models[MutModel.Counter::class].count -= 1 }

        onEvent<Event.GetFailure> { throw OnEventFailure() }

        onEvent<Event.GetFailureWithRequest> { requests.post(Request.GetFailure) }

        onEvent<Event.Increment> { models[MutModel.Counter::class].count += 1 }

        onEvent<Event.SendCounter> {
            val sender = models[MutModel.Sender::class]
            if (sender.requestId != null) {
                return@onEvent
            }
            val counter = models[MutModel.Counter::class]
            sender.requestId = requests.post(Request.SendCounter(counter.count))
        }

        onRequest<Request.GetFailure> { throw OnRequestFailure() }

        onRequest<Request.SendCounter> {
            delay(abs(request.counterValue) * 1000L)
            Event.CounterSent
        }
    }
}
