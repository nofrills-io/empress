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

import io.nofrills.empress.MutableEmpress
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.TestCoroutineScope
import kotlinx.coroutines.test.runBlockingTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@ExperimentalCoroutinesApi
class MutableEmpressBuilderTest {
    private lateinit var scope: TestCoroutineScope
    private lateinit var tested: MutableEmpress<Event, Model, Request>

    @Before
    fun setUp() {
        scope = TestCoroutineScope()
        tested = MutableEmpress("mutableEmpressTest") {
            initializer { Model.Counter(0) }
            initializer { Model.Sender(null) }

            onEvent<Event.Click> {
                models[Model.Counter::class].count += 1
            }

            onEvent<Event.Submit> {
                val requestId = requests.post(Request.Send(event.payload))
                models[Model.Sender::class].requestId = requestId
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
    fun hasCorrectId() {
        assertEquals("mutableEmpressTest", tested.id())
    }

    @Test
    fun initializers() {
        assertEquals(listOf(Model.Counter(0), Model.Sender(null)), tested.initialize())
    }

    @Test
    fun eventHandlers() {
        val models = MockModels(tested.initialize())

        tested.onEvent(Event.Click, models, MockRequests())
        assertEquals(
            Model.Counter(1),
            models[Model.Counter::class]
        )

        tested.onEvent(Event.Submit(3), models, MockRequests())
        assertEquals(
            Model.Sender(MockRequestId(1)),
            models[Model.Sender::class]
        )
    }

    @Test(expected = NoSuchElementException::class)
    fun unhandledEvent() {
        val models = MockModels(tested.initialize())
        tested.onEvent(Event.Sent, models, MockRequests())
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
        MutableEmpress<Event, Model, Request>("test") {
            initializer { Model.Counter(0) }
            initializer { Model.Counter(1) }
        }
    }

    @Test(expected = IllegalStateException::class)
    fun duplicateEventHandler() {
        MutableEmpress<Event, Model, Request>("test") {
            onEvent<Event.Click> { }
            onEvent<Event.Click> { }
        }
    }

    @Test(expected = IllegalStateException::class)
    fun duplicateRequestHandler() {
        MutableEmpress<Event, Model, Request>("test") {
            onRequest<Request.Send> { Event.Sent }
            onRequest<Request.Send> { Event.Sent }
        }
    }
}
