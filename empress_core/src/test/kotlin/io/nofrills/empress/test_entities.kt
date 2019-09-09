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

package io.nofrills.empress

import kotlinx.coroutines.delay

private const val REQUEST_DELAY_MS = 100L

internal sealed class Event {
    object CalculateClicked : Event()
    object Calculated : Event()
    object CancelSending : Event()
    object Decrement : Event()
    object Increment : Event()
    data class Load(val delayMs: Long) : Event()
    object Loaded : Event()
    object MakeUnhandledRequest : Event()
    data class Send(val delayMs: Long) : Event()
    object Sent : Event()
    object Unhandled : Event()
}

internal sealed class Model {
    data class Counter(var count: Int) : Model()
    data class Sender(var requestId: RequestId?) : Model()
}

internal sealed class Request {
    object Calculate : Request()
    data class Load(val delayMs: Long) : Request()
    data class Send(val delayMs: Long) : Request()
    object Unhandled : Request()
}

internal abstract class TestRuler(private val initializeWithDuplicate: Boolean = false) :
    Ruler<Event, Model, Request> {
    override fun initialize(): Collection<Model> {
        if (initializeWithDuplicate) {
            return listOf(Model.Counter(3), Model.Counter(5), Model.Sender(null))
        }
        return listOf(Model.Counter(0), Model.Sender(null))
    }

    override suspend fun onRequest(request: Request): Event {
        return when (request) {
            Request.Calculate -> {
                delay(REQUEST_DELAY_MS)
                Event.Calculated
            }
            is Request.Load -> {
                delay(request.delayMs)
                Event.Loaded
            }
            is Request.Send -> {
                delay(request.delayMs)
                Event.Sent
            }
            Request.Unhandled -> throw UnknownRequest
        }
    }
}

internal class TestEmpress(initializeWithDuplicate: Boolean = false) :
    Empress<Event, Model, Request>, TestRuler(initializeWithDuplicate) {
    override fun onEvent(
        event: Event,
        models: Models<Model>,
        requests: RequestCommander<Request>
    ): Collection<Model> {
        return when (event) {
            Event.CalculateClicked -> {
                requests.post(Request.Calculate)
                emptyList()
            }
            Event.Calculated -> emptyList()
            Event.CancelSending -> {
                val sender = models[Model.Sender::class]
                requests.cancel(sender.requestId)
                listOf(sender.copy(requestId = null))
            }
            Event.Decrement -> {
                val counter = models[Model.Counter::class]
                listOf(counter.copy(count = counter.count - 1))
            }
            Event.Increment -> {
                val counter = models[Model.Counter::class]
                listOf(counter.copy(count = counter.count + 1))
            }
            is Event.Load -> {
                requests.post(Request.Load(event.delayMs))
                emptyList()
            }
            Event.Loaded -> emptyList()
            Event.MakeUnhandledRequest -> {
                requests.post(Request.Unhandled)
                emptyList()
            }
            is Event.Send -> {
                val sender = models[Model.Sender::class]
                val requestId = requests.post(Request.Send(event.delayMs))
                listOf(sender.copy(requestId = requestId))
            }
            Event.Sent -> {
                val sender = models[Model.Sender::class]
                listOf(sender.copy(requestId = null))
            }
            Event.Unhandled -> throw UnknownEvent
        }
    }
}

internal class TestEmperor(initializeWithDuplicate: Boolean = false) :
    Emperor<Event, Model, Request>, TestRuler(initializeWithDuplicate) {
    override fun onEvent(
        event: Event,
        models: Models<Model>,
        requests: RequestCommander<Request>
    ) {
        return when (event) {
            Event.CalculateClicked -> {
                requests.post(Request.Calculate)
                Unit
            }
            Event.Calculated -> Unit
            Event.CancelSending -> {
                val sender = models[Model.Sender::class]
                requests.cancel(sender.requestId)
                Unit
            }
            Event.Decrement -> models[Model.Counter::class].count -= 1
            Event.Increment -> models[Model.Counter::class].count += 1
            is Event.Load -> {
                requests.post(Request.Load(event.delayMs))
                Unit
            }
            Event.Loaded -> Unit
            Event.MakeUnhandledRequest -> {
                requests.post(Request.Unhandled)
                Unit
            }
            is Event.Send -> {
                val sender = models[Model.Sender::class]
                sender.requestId = requests.post(Request.Send(event.delayMs))
            }
            Event.Sent -> models[Model.Sender::class].requestId = null
            Event.Unhandled -> throw UnknownEvent
        }
    }
}

internal object UnknownEvent : Throwable()
internal object UnknownRequest : Throwable()
