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

import io.nofrills.empress.backend.RulerBackend
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.TestCoroutineScope
import kotlinx.coroutines.test.runBlockingTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@UseExperimental(ExperimentalCoroutinesApi::class)
internal abstract class RulerBackendTest<B : RulerBackend<Event, Model, Request>, RL : Ruler<Event, Model, Request>> {
    private lateinit var scope: TestCoroutineScope
    private lateinit var tested: B

    @Before
    fun setUp() {
        scope = TestCoroutineScope()
        tested = makeBackend()
    }

    @After
    fun tearDown() {
        scope.cleanupTestCoroutines()
    }

    @Test
    fun initialState() = scope.runBlockingTest {
        assertEquals(
            listOf(Model.Counter(0), Model.Sender(null)),
            tested.modelSnapshot().all().sortedBy { it::class.java.name }
        )
        val deferredEvents = async { tested.events().toList() }
        tested.interrupt()
        assertTrue(tested.areChannelsClosedForSend())
        val events = deferredEvents.await()
        assertEquals(0, events.size)
    }

    @Test
    fun storedModels() = scope.runBlockingTest {
        tested = makeBackend(storedModels = listOf(Model.Counter(3)))
        assertEquals(Model.Counter(3), tested.modelSnapshot()[Model.Counter::class])
    }

    @Test(expected = IllegalStateException::class)
    fun storedModelsWithDuplicate() {
        tested = makeBackend(storedModels = listOf(Model.Counter(3), Model.Counter(5)))
    }

    @Test(expected = IllegalStateException::class)
    fun initializeWithDuplicates() {
        tested = makeBackend(makeRuler(true))
    }

    @Test
    fun postEffect() = scope.runBlockingTest {
        val deferredEvents = async { tested.events().toList() }
        tested.post { Event.Increment }
        tested.interrupt()

        assertTrue(tested.areChannelsClosedForSend())

        val events = deferredEvents.await()
        assertEquals(1, events.size)
        assertEquals(Event.Increment, events[0])

        assertEquals(Model.Counter(1), tested.modelSnapshot()[Model.Counter::class])
        assertTrue(tested.areChannelsClosedForSend())
    }

    @Test
    fun postEvent() = scope.runBlockingTest {
        val deferredEvents = async { tested.events().toList() }
        tested.post(Event.Increment)
        tested.interrupt()

        assertTrue(tested.areChannelsClosedForSend())

        val events = deferredEvents.await()
        assertEquals(1, events.size)
        assertEquals(Event.Increment, events[0])

        assertEquals(Model.Counter(1), tested.modelSnapshot()[Model.Counter::class])
        assertTrue(tested.areChannelsClosedForSend())
    }

    @Test
    fun postEventWithRequest() = scope.runBlockingTest {
        val deferredEvents = async { tested.events().toList() }
        tested.post(Event.CalculateClicked)
        tested.interrupt()

        assertFalse(tested.areChannelsClosedForSend())

        val events = deferredEvents.await()
        assertEquals(2, events.size)
        assertEquals(Event.CalculateClicked, events[0])
        assertEquals(Event.Calculated, events[1])

        assertTrue(tested.areChannelsClosedForSend())
    }

    @Test
    fun postUnhandledEvent() {
        val job = Job()
        val scope = CoroutineScope(Dispatchers.Unconfined + job)
        tested = makeBackend(eventHandlerScope = scope)
        tested.post(Event.Unhandled)
        assertTrue(job.isCancelled)

        var exceptionCaught = false
        try {
            job.ensureActive()
        } catch (e: CancellationException) {
            exceptionCaught = true
            assertEquals(UnknownEvent::class.java, e.cause!!::class.java)
        }
        assertTrue(exceptionCaught)
    }

    @Test
    fun postUnhandledRequest() {
        val job = Job()
        val scope = CoroutineScope(Dispatchers.Unconfined + job)
        tested = makeBackend(requestHandlerScope = scope)
        tested.post(Event.MakeUnhandledRequest)
        assertTrue(job.isCancelled)

        var exceptionCaught = false
        try {
            job.ensureActive()
        } catch (e: CancellationException) {
            exceptionCaught = true
            assertEquals(UnknownRequest::class.java, e.cause!!::class.java)
        }
        assertTrue(exceptionCaught)
    }

    @Test
    fun delayedFirstObserver() = usingTestScope { tested ->
        tested.post(Event.Increment)
        tested.post(Event.Increment)

        val deferredEvents = async {
            tested.events().toList()
        }

        tested.post(Event.Decrement)
        tested.interrupt()

        val events = deferredEvents.await()

        assertEquals(1, events.size)
        assertEquals(Event.Decrement, events[0])
    }

    @Test
    fun twoObservers() = usingTestScope { tested ->
        val deferredEvents1 = async {
            tested.events().toList()
        }
        val deferredEvents2 = async {
            tested.events().toList()
        }

        tested.post(Event.Decrement)
        tested.post(Event.Decrement)
        tested.interrupt()

        val events1 = deferredEvents1.await()
        val events2 = deferredEvents2.await()

        assertEquals(2, events1.size)
        assertEquals(Event.Decrement, events1[0])
        assertEquals(Event.Decrement, events1[1])

        assertEquals(events1, events2)
    }

    @Test
    fun delayedSecondObserver() = usingTestScope { tested ->
        val deferredEvents1 = async {
            tested.events().toList()
        }

        tested.post(Event.Increment)
        tested.post(Event.Increment)

        val deferredEvents2 = async {
            tested.events().toList()
        }

        tested.post(Event.Decrement)
        tested.interrupt()

        val updates1 = deferredEvents1.await()
        val updates2 = deferredEvents2.await()

        assertEquals(3, updates1.size)
        assertEquals(Event.Increment, updates1[0])
        assertEquals(Event.Increment, updates1[1])
        assertEquals(Event.Decrement, updates1[2])

        assertEquals(1, updates2.size)
        assertEquals(Event.Decrement, updates2[0])
    }

    @Test
    fun cancellingRequest() = usingTestScope { tested ->
        val deferredEvents = async {
            tested.events().toList()
        }
        launch {
            tested.post(Event.Send(1000))
            delay(10)
            tested.post(Event.CancelSending)
            tested.interrupt()
        }
        val events = deferredEvents.await()

        assertEquals(2, events.size)
        assertEquals(Event.Send(1000), events[0])
        assertEquals(Event.CancelSending, events[1])
    }

    @Test
    fun cancellingCompletedRequest() = usingTestScope { tested ->
        val deferredEvents = async {
            tested.events().toList()
        }
        launch {
            tested.post(Event.Send(1))
            delay(10)
            tested.post(Event.CancelSending)
            tested.interrupt()
        }
        val updates = deferredEvents.await()

        assertEquals(3, updates.size)
        assertEquals(Event.Send(1), updates[0])
        assertEquals(Event.Sent, updates[1])
        assertEquals(Event.CancelSending, updates[2])
    }

    @Test
    fun cancellingOneOfTwoRequests() = usingTestScope { tested ->
        val deferredEvents = async {
            tested.events().toList()
        }
        launch {
            tested.post(Event.Load(1000))
            tested.post(Event.Send(1000))
            delay(10)
            tested.post(Event.CancelSending)
            tested.interrupt()
        }
        val updates = deferredEvents.await()

        assertEquals(4, updates.size)
        assertEquals(Event.Load(1000), updates[0])
        assertEquals(Event.Send(1000), updates[1])
        assertEquals(Event.CancelSending, updates[2])
        assertEquals(Event.Loaded, updates[3])
    }

    @Test
    fun cancelNonExistentRequest() = usingTestScope { tested ->
        val deferredEvents = async {
            tested.events().toList()
        }

        launch {
            tested.post(Event.CancelSending)
            tested.interrupt()
        }

        val updates = deferredEvents.await()
        assertEquals(Event.CancelSending, updates[0])
    }

    @Test
    fun cancellingParentJob() {
        val job = Job()
        val scope = CoroutineScope(Dispatchers.Unconfined + job)
        val tested = makeBackend(eventHandlerScope = scope, requestHandlerScope = scope)

        try {
            runBlockingTest {
                val deferredEvents = async {
                    tested.events().toList()
                }
                launch {
                    tested.post(Event.Send(1000))
                    delay(10)
                    job.cancel()
                }
                deferredEvents.await()
            }
        } finally {
            assertTrue(tested.areChannelsClosedForSend())
        }

        var exceptionCaught = false
        try {
            job.ensureActive()
        } catch (e: CancellationException) {
            exceptionCaught = true
        }
        assertTrue(exceptionCaught)
    }

    @Test
    fun eventsBuffer() = usingTestScope {
        val eventCount = RulerBackend.HANDLED_EVENTS_CHANNEL_CAPACITY * 2
        val deferredEvents = async {
            tested.events().toList()
        }

        for (i in 1..eventCount) {
            tested.post(Event.Increment)
        }
        tested.interrupt()
        val events = deferredEvents.await()

        assertEquals(eventCount, events.size)
        assertEquals(eventCount, tested.modelSnapshot()[Model.Counter::class].count)
    }

    protected abstract fun makeRuler(initializeWithDuplicate: Boolean = false): RL

    protected abstract fun makeBackend(
        ruler: RL = makeRuler(),
        eventHandlerScope: CoroutineScope = scope,
        requestHandlerScope: CoroutineScope = scope,
        storedModels: Collection<Model>? = null
    ): B

    protected fun usingTestScope(block: suspend TestCoroutineScope.(B) -> Unit) {
        scope.runBlockingTest {
            block.invoke(scope, tested)
        }
    }
}
