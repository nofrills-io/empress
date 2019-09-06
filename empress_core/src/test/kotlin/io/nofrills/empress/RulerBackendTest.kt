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
        assertEquals(listOf(Model.Counter(0)), tested.modelSnapshot().all().toList())
        val deferredEvents = async { tested.events().toList() }
        tested.interrupt()
        assertTrue(tested.areChannelsClosedForSend())
        val events = deferredEvents.await()
        assertEquals(0, events.size)
        assertTrue(tested.hasEqualClass(makeRuler()::class.java))
        assertTrue(tested.hasEqualId(makeRuler()::class.java.name))
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
    fun postEvent() = scope.runBlockingTest {
        val deferredEvents = async { tested.events().toList() }
        tested.post(Event.OnIncrement)
        tested.interrupt()

        assertTrue(tested.areChannelsClosedForSend())

        val events = deferredEvents.await()
        assertEquals(1, events.size)
        assertEquals(Event.OnIncrement, events[0])

        assertEquals(Model.Counter(1), tested.modelSnapshot()[Model.Counter::class])
        assertTrue(tested.areChannelsClosedForSend())
    }

    @Test
    fun postEventWithRequest() = scope.runBlockingTest {
        val deferredEvents = async { tested.events().toList() }
        tested.post(Event.OnCalculateClicked)
        tested.interrupt()

        assertFalse(tested.areChannelsClosedForSend())

        val events = deferredEvents.await()
        assertEquals(2, events.size)
        assertEquals(Event.OnCalculateClicked, events[0])
        assertEquals(Event.Calculated, events[1])

        assertTrue(tested.areChannelsClosedForSend())
    }

    @Test(expected = IllegalStateException::class)
    fun postUnhandledEvent() {
        val job = Job()
        val scope = CoroutineScope(Dispatchers.Unconfined + job)
        tested = makeBackend(eventHandlerScope = scope)
        tested.post(Event.Unhandled)
        assertTrue(job.isCancelled)
        job.ensureActive()
    }

    @Test(expected = IllegalStateException::class)
    fun postUnhandledRequest() {
        val job = Job()
        val scope = CoroutineScope(Dispatchers.Unconfined + job)
        tested = makeBackend(requestHandlerScope = scope)
        tested.post(Event.MakeUnhandledRequest)
        assertTrue(job.isCancelled)
        job.ensureActive()
    }

    // TODO add tests from EmpressBackendTestOld, i.e. those regarding second observers

    protected abstract fun makeRuler(initializeWithDuplicate: Boolean = false): RL

    protected abstract fun makeBackend(
        ruler: RL = makeRuler(),
        eventHandlerScope: CoroutineScope = scope,
        requestHandlerScope: CoroutineScope = scope,
        storedModels: Collection<Model>? = null
    ): B
}
