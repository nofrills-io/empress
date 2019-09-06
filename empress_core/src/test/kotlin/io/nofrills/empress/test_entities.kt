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
    object OnIncrement : Event()
    object OnCalculateClicked : Event()
    object Calculated : Event()
    object MakeUnhandledRequest : Event()
    object Unhandled : Event()
}

internal sealed class Model {
    data class Counter(var count: Int) : Model()
}

internal sealed class Request {
    object Calculate : Request()
    object Unhandled : Request()
}

internal abstract class TestRuler(private val initializeWithDuplicate: Boolean = false) :
    Ruler<Event, Model, Request> {
    override fun initialize(): Collection<Model> {
        if (initializeWithDuplicate) {
            return listOf(Model.Counter(3), Model.Counter(5))
        }
        return listOf(Model.Counter(0))
    }

    override suspend fun onRequest(request: Request): Event {
        return when (request) {
            Request.Calculate -> {
                delay(REQUEST_DELAY_MS)
                Event.Calculated
            }
            else -> error("Unknown request $request")
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
            Event.OnIncrement -> {
                val counter = models[Model.Counter::class]
                listOf(counter.copy(count = counter.count + 1))
            }
            Event.OnCalculateClicked -> {
                requests.post(Request.Calculate)
                emptyList()
            }
            Event.Calculated -> emptyList()
            Event.MakeUnhandledRequest -> {
                requests.post(Request.Unhandled)
                emptyList()
            }
            else -> error("Unknown event $event")
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
            Event.OnIncrement -> models[Model.Counter::class].count += 1
            Event.OnCalculateClicked -> {
                requests.post(Request.Calculate)
                Unit
            }
            Event.Calculated -> Unit
            Event.MakeUnhandledRequest -> {
                requests.post(Request.Unhandled)
                Unit
            }
            else -> error("Unknown event $event")
        }
    }
}
