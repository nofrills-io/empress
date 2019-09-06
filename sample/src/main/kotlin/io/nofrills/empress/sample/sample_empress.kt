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

import android.os.Parcelable
import io.nofrills.empress.Empress
import io.nofrills.empress.Models
import io.nofrills.empress.RequestCommander
import io.nofrills.empress.RequestId
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

sealed class Model {
    @Parcelize
    data class Counter(var count: Int) : Model(), Parcelable

    data class Sender(val requestId: RequestId?) : Model()
}

sealed class Request {
    class SendCounter(val counterValue: Int) : Request()
    object GetFailure : Request()
}

class SampleEmpress(private val id: String? = null) : Empress<Event, Model, Request> {
    override fun id(): String {
        return id ?: super.id()
    }

    override fun initialize(): Collection<Model> {
        return listOf(Model.Counter(0), Model.Sender(null))
    }

    override fun onEvent(
        event: Event,
        models: Models<Model>,
        requests: RequestCommander<Request>
    ): Collection<Model> {
        return when (event) {
            Event.Decrement -> changeCount(-1, models)
            Event.Increment -> changeCount(1, models)
            Event.SendCounter -> sendCurrentCount(models, requests)
            Event.CancelSendingCounter -> cancelSendingCounter(models, requests)
            Event.CounterSent -> onSent(models)
            Event.GetFailure -> throw OnEventFailure()
            Event.GetFailureWithRequest -> run {
                requests.post(Request.GetFailure)
                emptyList<Model>()
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

    private fun changeCount(d: Int, models: Models<Model>): Collection<Model> {
        val counter = models[Model.Counter::class]
        return listOf(counter.copy(count = counter.count + d))
    }

    private fun sendCurrentCount(
        models: Models<Model>,
        requests: RequestCommander<Request>
    ): Collection<Model> {
        val sender = models[Model.Sender::class]
        if (sender.requestId != null) {
            return listOf()
        }

        val counter = models[Model.Counter::class]
        val requestId = requests.post(Request.SendCounter(counter.count))
        return listOf(sender.copy(requestId = requestId))
    }

    private fun cancelSendingCounter(
        models: Models<Model>,
        requests: RequestCommander<Request>
    ): Collection<Model> {
        val sender = models[Model.Sender::class]
        val requestId = sender.requestId ?: return listOf()
        requests.cancel(requestId)
        return listOf(sender.copy(requestId = null))
    }

    private fun onSent(models: Models<Model>): Collection<Model> {
        return listOf(models[Model.Sender::class].copy(requestId = null))
    }
}

class OnEventFailure : Throwable()

class OnRequestFailure : Throwable()
