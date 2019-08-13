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

package io.nofrills.empress.test_support

import io.nofrills.empress.Empress
import io.nofrills.empress.builder.empressBuilder
import kotlinx.coroutines.delay
import kotlin.math.abs

fun buildEmpress(): Empress<Event, Patch, Request> {
    return empressBuilder("sample") {
        initializer { Patch.Counter(0) }
        initializer { Patch.Sender(null) }

        onEvent<Event.Decrement> {
            val counter = model.get<Patch.Counter>()
            listOf(counter.copy(count = counter.count - 1))
        }

        onEvent<Event.Increment> {
            val counter = model.get<Patch.Counter>()
            listOf(counter.copy(count = counter.count + 1))
        }

        onEvent<Event.SendCounter> {
            val counter = model.get<Patch.Counter>()
            val sender = model.get<Patch.Sender>()
            if (sender.requestId != null) {
                return@onEvent emptyList()
            }
            val requestId = requests.post(Request.SendCounter(counter.count))
            listOf(sender.copy(requestId = requestId))
        }

        onEvent<Event.CancelSendingCounter> {
            val requestId = model.get<Patch.Sender>().requestId ?: return@onEvent emptyList()
            requests.cancel(requestId)
            listOf(Patch.Sender(null))
        }

        onEvent<Event.CounterSent> {
            listOf(Patch.Sender(null))
        }

        onRequest<Request.SendCounter> {
            delay(1000L * abs(request.counterValue))
            Event.CounterSent
        }
    }
}