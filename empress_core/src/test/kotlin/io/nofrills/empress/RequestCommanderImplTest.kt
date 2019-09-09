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

import io.nofrills.empress.internal.RequestCommanderImpl
import io.nofrills.empress.internal.RequestHolder
import io.nofrills.empress.internal.RequestIdImpl
import io.nofrills.empress.internal.RequestIdProducer
import kotlinx.coroutines.*
import kotlinx.coroutines.test.TestCoroutineScope
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@ExperimentalCoroutinesApi
class RequestCommanderImplTest {
    private lateinit var eventHandler: EventHandler
    private lateinit var scope: TestCoroutineScope
    private lateinit var tested: RequestCommanderImpl<Event, Request>

    private val requestHandler = object : RequestHandler<Event, Request> {
        override suspend fun onRequest(request: Request): Event {
            return when (request) {
                Request.Action -> {
                    delay(ACTION_DELAY_MS)
                    Event.ActionSuccess
                }
                else -> error("Unknown request")
            }
        }
    }

    @Before
    fun setUp() {
        eventHandler = EventHandler()
        scope = TestCoroutineScope()
        tested = makeRequestCommander(requestHandler, scope, eventHandler::sendEvent)
    }

    @After
    fun tearDown() {
        scope.cleanupTestCoroutines()
    }

    @Test
    fun cancelNonExistingRequest() {
        assertFalse(tested.cancel(RequestIdImpl(1)))
    }

    @Test(expected = IllegalStateException::class)
    fun unhandledRequest() {
        val job = Job()
        val scope = CoroutineScope(Dispatchers.Unconfined + job)
        tested = makeRequestCommander(requestHandler, scope, eventHandler::sendEvent)
        tested.post(Request.Unhandled)
        assertTrue(job.isCancelled)
        job.ensureActive()
    }

    @Test
    fun sampleRequest() {
        val requestId = tested.post(Request.Action)
        scope.advanceUntilIdle()
        assertEquals(Event.ActionSuccess, eventHandler.lastEvent)
        assertFalse(tested.cancel(requestId))
    }

    @Test
    fun cancelNullRequest() {
        assertFalse(tested.cancel(null))
    }

    @Test
    fun cancellingRequest() {
        scope.pauseDispatcher()
        val requestId = tested.post(Request.Action)

        scope.advanceTimeBy(ACTION_DELAY_MS / 2)
        assertTrue(tested.cancel(requestId))
        assertFalse(tested.cancel(requestId))

        scope.advanceUntilIdle()
        assertNull(eventHandler.lastEvent)
    }

    companion object {
        private const val ACTION_DELAY_MS = 100L

        private fun makeRequestCommander(
            requestHandler: RequestHandler<Event, Request>,
            scope: CoroutineScope,
            sendEvent: suspend (Event) -> Unit
        ): RequestCommanderImpl<Event, Request> {
            return RequestCommanderImpl(
                RequestIdProducer(),
                requestHandler,
                RequestHolder(),
                scope,
                sendEvent
            )
        }
    }

    private sealed class Event {
        object ActionSuccess : Event()
    }

    private sealed class Request {
        object Action : Request()
        object Unhandled : Request()
    }

    private class EventHandler {
        var lastEvent: Event? = null

        suspend fun sendEvent(event: Event) {
            delay(1)
            lastEvent = event
        }
    }
}
