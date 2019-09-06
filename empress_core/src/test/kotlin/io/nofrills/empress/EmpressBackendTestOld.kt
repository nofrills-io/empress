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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

//@ExperimentalCoroutinesApi
//class EmpressBackendTestOld {
//    private val baseModel = Model.from(
//        listOf(
//            Patch.Counter(0),
//            Patch.Loader(null),
//            Patch.Sender(null)
//        )
//    )
//    private val empress = TestEmpress()
//    private var testScope: TestCoroutineScope? = null
//
//    @After
//    fun tearDown() {
//        testScope?.cleanupTestCoroutines()
//    }
//
//    @Test
//    fun simpleUsage() = usingTestScope { tested ->
//        assertEquals("io.nofrills.empress.TestEmpress", empress.id())
//        assertTrue(tested.hasEqualClass(TestEmpress::class.java))
//        assertEquals(baseModel, tested.modelSnapshot())
//
//        val deferredUpdates = async {
//            tested.updates().toList()
//        }
//
//        tested.send(Event.Increment)
//        tested.send(Event.Increment)
//        tested.interrupt()
//
//        val updates = deferredUpdates.await()
//
//        assertEquals(2, updates.size)
//        assertEquals(
//            Update(Model.from(baseModel, listOf(Patch.Counter(1))), Event.Increment),
//            updates[0]
//        )
//        assertEquals(
//            Update(Model.from(baseModel, listOf(Patch.Counter(2))), Event.Increment),
//            updates[1]
//        )
//        assertTrue(tested.areChannelsClosed())
//    }
//
//    @Test
//    fun simpleUsageWithRequest() = usingTestScope { tested ->
//        val deferredUpdates = async {
//            tested.updates().toList()
//        }
//
//        tested.send(Event.Send(1))
//        tested.interrupt()
//
//        val updates = deferredUpdates.await()
//
//        assertEquals(2, updates.size)
//        assertEquals(
//            Update<Event, Patch>(
//                Model.from(baseModel, listOf(Patch.Sender(RequestId(1)))),
//                Event.Send(1)
//            ),
//            updates[0]
//        )
//        assertEquals(
//            Update<Event, Patch>(
//                Model.from(baseModel, listOf(Patch.Sender(null))),
//                Event.CounterSent
//            ), updates[1]
//        )
//    }
//
//    @Test
//    fun delayedFirstObserver() = usingTestScope { tested ->
//        tested.send(Event.Increment)
//        tested.send(Event.Increment)
//
//        val deferredUpdates = async {
//            tested.updates().toList()
//        }
//
//        tested.send(Event.Decrement)
//        tested.interrupt()
//
//        val updates = deferredUpdates.await()
//
//        assertEquals(1, updates.size)
//        assertEquals(Model.from(baseModel, listOf(Patch.Counter(1))), updates[0].model)
//        assertEquals(Event.Decrement, updates[0].event)
//    }
//
//    @Test
//    fun twoObservers() = usingTestScope { tested ->
//        val deferredUpdates1 = async {
//            tested.updates().toList()
//        }
//        val deferredUpdates2 = async {
//            tested.updates().toList()
//        }
//
//        tested.send(Event.Decrement)
//        tested.send(Event.Decrement)
//        tested.interrupt()
//
//        val updates1 = deferredUpdates1.await()
//        val updates2 = deferredUpdates2.await()
//        assertEquals(updates1, updates2)
//
//        assertEquals(2, updates1.size)
//        assertEquals(
//            Update(Model.from(baseModel, listOf(Patch.Counter(-1))), Event.Decrement),
//            updates1[0]
//        )
//        assertEquals(
//            Update(Model.from(baseModel, listOf(Patch.Counter(-2))), Event.Decrement),
//            updates1[1]
//        )
//    }
//
//    @Test
//    fun delayedSecondObserver() = usingTestScope { tested ->
//        val deferredUpdates1 = async {
//            tested.updates().toList()
//        }
//
//        tested.send(Event.Increment)
//        tested.send(Event.Increment)
//
//        val deferredUpdates2 = async {
//            tested.updates().toList()
//        }
//
//        tested.send(Event.Decrement)
//        tested.interrupt()
//
//        val updates1 = deferredUpdates1.await()
//        val updates2 = deferredUpdates2.await()
//
//        assertEquals(3, updates1.size)
//        assertEquals(
//            Update(Model.from(baseModel, listOf(Patch.Counter(1))), Event.Increment),
//            updates1[0]
//        )
//        assertEquals(
//            Update(Model.from(baseModel, listOf(Patch.Counter(2))), Event.Increment),
//            updates1[1]
//        )
//        assertEquals(
//            Update(Model.from(baseModel, listOf(Patch.Counter(1))), Event.Decrement),
//            updates1[2]
//        )
//
//        assertEquals(1, updates2.size)
//        assertEquals(
//            Update(Model.from(baseModel, listOf(Patch.Counter(1))), Event.Decrement),
//            updates2[0]
//        )
//    }
//
//    @Test
//    fun cancellingRequest() = usingTestScope { tested ->
//        val deferredUpdates = async {
//            tested.updates().toList()
//        }
//        launch {
//            tested.send(Event.Send(1000))
//            delay(10)
//            tested.send(Event.CancelSending)
//            tested.interrupt()
//        }
//        val updates = deferredUpdates.await()
//
//        assertEquals(2, updates.size)
//        assertEquals(
//            Update<Event, Patch>(
//                Model.from(baseModel, listOf(Patch.Sender(RequestId(1)))),
//                Event.Send(1000)
//            ), updates[0]
//        )
//        assertEquals(
//            Update<Event, Patch>(
//                Model.from(baseModel, listOf(Patch.Sender(null))),
//                Event.CancelSending
//            ), updates[1]
//        )
//    }
//
//    @Test
//    fun cancellingCompletedRequest() = usingTestScope { tested ->
//        val deferredUpdates = async {
//            tested.updates().toList()
//        }
//        launch {
//            tested.send(Event.Send(1))
//            delay(10)
//            tested.send(Event.CancelSending)
//            tested.interrupt()
//        }
//        val updates = deferredUpdates.await()
//
//        assertEquals(3, updates.size)
//        assertEquals(
//            Update<Event, Patch>(
//                Model.from(baseModel, listOf(Patch.Sender(RequestId(1)))),
//                Event.Send(1)
//            ), updates[0]
//        )
//        assertEquals(
//            Update<Event, Patch>(
//                Model.from(baseModel, listOf(Patch.Sender(null))),
//                Event.CounterSent
//            ), updates[1]
//        )
//        assertEquals(
//            Update<Event, Patch>(
//                Model.from(baseModel),
//                Event.CancelSending
//            ), updates[2]
//        )
//    }
//
//    @Test
//    fun cancellingOneOfTwoRequests() = usingTestScope { tested ->
//        val deferredUpdates = async {
//            tested.updates().toList()
//        }
//        launch {
//            tested.send(Event.Load(1000))
//            tested.send(Event.Send(1000))
//            delay(10)
//            tested.send(Event.CancelSending)
//            tested.interrupt()
//        }
//        val updates = deferredUpdates.await()
//
//        assertEquals(4, updates.size)
//
//        val model0 = Model.from(baseModel, listOf(Patch.Loader(RequestId(1))))
//        assertEquals(
//            Update<Event, Patch>(
//                model0,
//                Event.Load(1000)
//            ), updates[0]
//        )
//
//        val model1 = Model.from(model0, listOf(Patch.Sender(RequestId(2))))
//        assertEquals(
//            Update<Event, Patch>(
//                model1,
//                Event.Send(1000)
//            ), updates[1]
//        )
//
//        val model2 = Model.from(model1, listOf(Patch.Sender(null)))
//        assertEquals(
//            Update<Event, Patch>(
//                model2,
//                Event.CancelSending
//            ), updates[2]
//        )
//
//        val model3 = Model.from(model2, listOf(Patch.Loader(null)))
//        assertEquals(
//            Update<Event, Patch>(
//                model3,
//                Event.Loaded
//            ), updates[3]
//        )
//    }
//
//    @Test
//    fun cancelNonExistentRequest() = usingTestScope { tested ->
//        val deferredUpdates = async {
//            tested.updates().toList()
//        }
//
//        launch {
//            tested.send(Event.CancelSending)
//            tested.interrupt()
//        }
//
//        val updates = deferredUpdates.await()
//        assertEquals(
//            Update(
//                Model.from(baseModel, emptyList()),
//                Event.CancelSending
//            ), updates[0]
//        )
//    }
//
//    @Test(expected = EventTrouble::class)
//    fun throwingErrorInEventHandler() {
//        val scope = CoroutineScope(Dispatchers.Unconfined)
//        val tested = makeEmpressBackend(scope)
//
//        runBlocking {
//            val deferredUpdates = async {
//                tested.updates().toList()
//            }
//            tested.send(Event.GetEventFailure)
//            deferredUpdates.await()
//        }
//    }
//
//    @Test(expected = RequestTrouble::class)
//    fun throwingErrorInRequestHandler() {
//        runBlocking {
//            val scope = CoroutineScope(Dispatchers.Unconfined)
//            val tested = makeEmpressBackend(scope)
//            val deferred = async { tested.updates().toList() }
//            tested.send(Event.GetEventFailureWithRequest)
//            deferred.await()
//        }
//    }
//
//    @Test(expected = CancellationException::class)
//    fun cancellingParentJob() {
//        val job = Job()
//        val tested = makeEmpressBackend(CoroutineScope(Dispatchers.Unconfined + job))
//
//        try {
//            runBlockingTest {
//                launch {
//                    tested.send(Event.Send(1000))
//                    delay(10)
//                    job.cancel()
//                }
//                assertEquals(
//                    Model.from(baseModel, listOf(Patch.Sender(RequestId(1)))),
//                    tested.modelSnapshot()
//                )
//                tested.updates().toList()
//            }
//        } finally {
//            assertTrue(tested.areChannelsClosed())
//        }
//    }
//
//    @Test
//    fun storedPatches() = runBlockingTest {
//        val storedPatches = listOf(Patch.Counter(3))
//        val tested = makeEmpressBackend(
//            CoroutineScope(Dispatchers.Unconfined),
//            storedPatches = storedPatches
//        )
//        assertEquals(
//            Model.from(listOf(Patch.Counter(3), Patch.Loader(null), Patch.Sender(null))),
//            tested.modelSnapshot()
//        )
//        tested.interrupt()
//    }
//
//    private fun usingTestScope(block: suspend TestCoroutineScope.(EmpressBackend<Event, Patch, Request>) -> Unit) {
//        val scope = TestCoroutineScope()
//        testScope = scope
//        val empressBackend = makeEmpressBackend(scope)
//        scope.runBlockingTest {
//            block.invoke(scope, empressBackend)
//        }
//    }
//
//    private fun makeEmpressBackend(
//        coroutineScope: CoroutineScope,
//        storedPatches: Collection<Patch>? = null
//    ): EmpressBackend<Event, Patch, Request> {
//        return EmpressBackend(
//            empress,
//            coroutineScope,
//            storedPatches
//        )
//    }
//}