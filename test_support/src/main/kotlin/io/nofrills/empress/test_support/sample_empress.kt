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

import android.os.Parcelable
import io.nofrills.empress.*
import kotlinx.android.parcel.Parcelize
import kotlinx.coroutines.delay
import kotlin.math.abs

sealed class Event {
    object Decrement : Event()
    object Increment : Event()
    object SendCounter : Event()
    object CancelSendingCounter : Event()
    object CounterSent : Event()
    object GetFailure : Event()
    object GetFailureWithRequest : Event()
}

sealed class Patch {
    @Parcelize
    data class Counter(val count: Int) : Patch(), Parcelable

    data class Sender(val requestId: RequestId?) : Patch()
}

sealed class Request {
    class SendCounter(val counterValue: Int) : Request()
    object GetFailure : Request()
}

class SampleEmpress(private val id: String? = null) : Empress<Event, Patch, Request> {
    override fun id(): String {
        return id ?: super.id()
    }

    override fun initializer(): Collection<Patch> = listOf(Patch.Counter(0), Patch.Sender(null))

    override fun onEvent(
        event: Event,
        model: Model<Patch>,
        requests: Requests<Event, Request>
    ): Collection<Patch> {
        return when (event) {
            Event.Decrement -> changeCount(-1, model)
            Event.Increment -> changeCount(1, model)
            Event.SendCounter -> sendCurrentCount(model, requests)
            Event.CancelSendingCounter -> cancelSendingCounter(model, requests)
            Event.CounterSent -> onSent(model)
            Event.GetFailure -> throw OnEventFailure()
            Event.GetFailureWithRequest -> run {
                requests.post(Request.GetFailure)
                emptyList<Patch>()
            }
        }
    }

    override suspend fun onRequest(request: Request): Event {
        return when (request) {
            is Request.SendCounter -> run {
                delay(abs(request.counterValue) * 1000L)
                Event.CounterSent
            }
            Request.GetFailure -> throw OnRequestFailure()
        }
    }

    private fun changeCount(d: Int, model: Model<Patch>): Collection<Patch> {
        val counter = model.get<Patch.Counter>()
        return listOf(counter.copy(count = counter.count + d))
    }

    private fun sendCurrentCount(
        model: Model<Patch>,
        requests: Requests<Event, Request>
    ): Collection<Patch> {
        val sender = model.get<Patch.Sender>()
        if (sender.requestId != null) {
            return listOf()
        }

        val counter = model.get<Patch.Counter>()
        val requestId = requests.post(Request.SendCounter(counter.count))
        return listOf(sender.copy(requestId = requestId))
    }

    private fun cancelSendingCounter(
        model: Model<Patch>,
        requests: Requests<Event, Request>
    ): Collection<Patch> {
        val sender = model.get<Patch.Sender>()
        val requestId = sender.requestId ?: return listOf()
        requests.cancel(requestId)
        return listOf(sender.copy(requestId = null))
    }

    private fun onSent(model: Model<Patch>): Collection<Patch> {
        return listOf(model.get<Patch.Sender>().copy(requestId = null))
    }
}

class OnEventFailure : Throwable()

class OnRequestFailure : Throwable()
