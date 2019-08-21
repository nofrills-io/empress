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

package io.nofrills.empress.builder

import io.nofrills.empress.Empress
import io.nofrills.empress.Model
import io.nofrills.empress.RequestId
import io.nofrills.empress.Requests
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.TestCoroutineScope
import kotlinx.coroutines.test.runBlockingTest
import org.junit.After
import org.junit.Test

import org.junit.Assert.*
import org.junit.Before
import java.lang.IllegalStateException

class EmpressBuilderTest {
    private lateinit var scope: TestCoroutineScope
    private lateinit var tested: Empress<Event, Patch, Request>

    @Before
    fun setUp() {
        scope = TestCoroutineScope()
        tested = Empress("empressTest") {
            initializer { Patch.Counter(0) }
            initializer { Patch.Sender(null) }

            onEvent<Event.Click> {
                val counter = model.get<Patch.Counter>()
                listOf(counter.copy(count = counter.count + 1))
            }

            onEvent<Event.Submit> {
                val requestId = requests.post(Request.Send(event.payload))
                listOf(Patch.Sender(requestId))
            }

            onRequest<Request.Send> {
                delay(1000)
                Event.Sent
            }
        }
    }

    @After
    fun tearDown() {
        scope.cleanupTestCoroutines()
    }

    @Test
    fun hasCorrectId() {
        assertEquals("empressTest", tested.id())
    }

    @Test
    fun initializers() {
        assertEquals(listOf(Patch.Counter(0), Patch.Sender(null)), tested.initializer())
    }

    @Test
    fun eventHandlers() {
        val model = Model.from(tested.initializer())

        assertEquals(
            listOf(Patch.Counter(1)),
            tested.onEvent(Event.Click, model, MockRequests())
        )

        assertEquals(
            listOf(Patch.Sender(RequestId(1))),
            tested.onEvent(Event.Submit("hello"), model, MockRequests())
        )
    }

    @Test(expected = NoSuchElementException::class)
    fun unhandledEvent() {
        val model = Model.from(tested.initializer())
        tested.onEvent(Event.Sent, model, MockRequests())
    }

    @Test
    fun requestHandlers() = scope.runBlockingTest {
        assertEquals(Event.Sent, tested.onRequest(Request.Send("hello")))
    }

    @Test(expected = NoSuchElementException::class)
    fun unhandledRequest() = scope.runBlockingTest {
        tested.onRequest(Request.Unhandled)
    }

    @Test(expected = IllegalStateException::class)
    fun duplicateInitializer() {
        Empress<Event, Patch, Request>("test") {
            initializer { Patch.Counter(0) }
            initializer { Patch.Counter(1) }
        }
    }

    @Test(expected = IllegalStateException::class)
    fun duplicateEventHandler() {
        Empress<Event, Patch, Request>("test") {
            onEvent<Event.Click> { listOf() }
            onEvent<Event.Click> { listOf() }
        }
    }

    @Test(expected = IllegalStateException::class)
    fun duplicateRequestHandler() {
        Empress<Event, Patch, Request>("test") {
            onRequest<Request.Send> { Event.Sent }
            onRequest<Request.Send> { Event.Sent }
        }
    }
}

internal sealed class Event {
    object Click : Event()
    data class Submit(val payload: String) : Event()
    object Sent : Event()
}

internal sealed class Patch {
    data class Counter(val count: Int) : Patch()
    data class Sender(val requestId: RequestId?) : Patch()
}

internal sealed class Request {
    data class Send(val payload: String) : Request()
    object Unhandled : Request()
}

internal class MockRequests : Requests<Event, Request> {
    private var nextRequestNum = 0

    override fun cancel(requestId: RequestId?): Boolean {
        return true
    }

    override fun post(request: Request): RequestId {
        nextRequestNum += 1
        return RequestId(nextRequestNum)
    }
}
