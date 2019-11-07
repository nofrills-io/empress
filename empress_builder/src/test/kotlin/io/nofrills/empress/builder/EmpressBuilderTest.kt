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
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.TestCoroutineScope
import kotlinx.coroutines.test.runBlockingTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@ExperimentalCoroutinesApi
class EmpressBuilderTest {
    private lateinit var scope: TestCoroutineScope
    private lateinit var tested: Empress<Event, Model, Request>

    @Before
    fun setUp() {
        scope = TestCoroutineScope()
        tested = Empress {
            initializer { Model.Counter(0) }
            initializer { Model.Sender(null) }

            onEvent<Event.Click> {
                val counter = models[Model.Counter::class]
                listOf(counter.copy(count = counter.count + 1))
            }

            onEvent<Event.Submit> {
                val requestId = requests.post(Request.Send(event.payload))
                listOf(Model.Sender(requestId))
            }

            onRequest<Request.Send> {
                delay(request.payload)
                Event.Sent
            }
        }
    }

    @After
    fun tearDown() {
        scope.cleanupTestCoroutines()
    }

    @Test
    fun initializers() {
        assertEquals(listOf(Model.Counter(0), Model.Sender(null)), tested.initialize())
    }

    @Test
    fun eventHandlers() {
        val model = MockModels(tested.initialize())

        assertEquals(
            listOf(Model.Counter(1)),
            tested.onEvent(Event.Click, model, MockRequests())
        )

        assertEquals(
            listOf(Model.Sender(MockRequestId(1))),
            tested.onEvent(Event.Submit(3), model, MockRequests())
        )
    }

    @Test(expected = NoSuchElementException::class)
    fun unhandledEvent() {
        val model = MockModels(tested.initialize())
        tested.onEvent(Event.Sent, model, MockRequests())
    }

    @Test
    fun requestHandlers() = scope.runBlockingTest {
        assertEquals(Event.Sent, tested.onRequest(Request.Send(1)))
    }

    @Test(expected = NoSuchElementException::class)
    fun unhandledRequest() = scope.runBlockingTest {
        tested.onRequest(Request.Unhandled)
    }

    @Test(expected = IllegalStateException::class)
    fun duplicateInitializer() {
        Empress<Event, Model, Request> {
            initializer { Model.Counter(0) }
            initializer { Model.Counter(1) }
        }
    }

    @Test(expected = IllegalStateException::class)
    fun duplicateEventHandler() {
        Empress<Event, Model, Request> {
            onEvent<Event.Click> { listOf() }
            onEvent<Event.Click> { listOf() }
        }
    }

    @Test(expected = IllegalStateException::class)
    fun duplicateRequestHandler() {
        Empress<Event, Model, Request> {
            onRequest<Request.Send> { Event.Sent }
            onRequest<Request.Send> { Event.Sent }
        }
    }
}
