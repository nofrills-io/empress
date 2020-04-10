/*
 * Copyright 2020 Mateusz Armatys
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

package io.nofrills.empress.base

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.TestCoroutineScope
import kotlinx.coroutines.test.runBlockingTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.Executors

@OptIn(ExperimentalCoroutinesApi::class)
class EmpressBackendTest {
    @Test
    fun noModelUpdatesUpdates() = runBlockingTest {
        val tested = makeTested(this)
        tested.post { ping() }
        tested.interrupt()
    }

    @Test
    fun modelUpdates() = runBlockingTest {
        val tested = makeTested(this)
        val deferredUpdates = updatesAsync(tested)
        tested.post { increment() }
        tested.post { increment() }
        tested.post { delta(2) }
        tested.post { decrement() }
        tested.interrupt()

        val updates = deferredUpdates.await()
        val expected = listOf<Model>(
            Model.Counter(1),
            Model.Counter(2),
            Model.Counter(4),
            Model.Counter(3)
        )
        assertEquals(expected, updates)
        assertEquals(setOf(Model.Sender(), Model.Counter(3)), tested.models().toSet())
    }

    @Test
    fun updatesWithInitialModels() = runBlockingTest {
        val tested = makeTested(this)
        val deferredUpdates = updatesAsync(tested, withCurrentModels = true)
        tested.post { increment() }
        tested.interrupt()

        val updates = deferredUpdates.await()

        // The order of the first two models (the initial set)
        // is not deterministic.
        val expected = setOf(
            Model.Sender(),
            Model.Counter(0)
        )
        assertEquals(3, updates.size)
        assertEquals(expected, updates.subList(0, 2).toSet())
        assertEquals(Model.Counter(1), updates[2])
    }

    @Test
    fun signals() = runBlockingTest {
        val tested = makeTested(this)
        val deferredUpdates = updatesAsync(tested)
        val deferredSignals = signalsAsync(tested)

        tested.post { increment() }
        tested.post { sendCounter() }
        tested.interrupt()

        val updates = deferredUpdates.await()
        val signals = deferredSignals.await()

        val expectedUpdates = listOf(
            Model.Counter(1),
            Model.Sender(1L),
            Model.Sender(null)
        )
        assertEquals(expectedUpdates, updates)
        assertEquals(listOf(Signal.CounterSent(1)), signals)
    }

    @Test
    fun cancellingRequest() = runBlockingTest {
        val tested = makeTested(this)
        val deferredUpdates = updatesAsync(tested)
        val deferredSignals = signalsAsync(tested)

        pauseDispatcher()
        tested.post { increment() }
        tested.post { sendCounter() }
        advanceTimeBy(500L)
        tested.post { cancelSending() }
        tested.interrupt()
        resumeDispatcher()

        val updates = deferredUpdates.await()
        val signals = deferredSignals.await()

        val expectedModels = listOf(
            Model.Counter(1),
            Model.Sender(1L),
            Model.Sender(null)
        )
        assertEquals(expectedModels, updates)
        assertEquals(listOf(Signal.SendingCancelled), signals)
    }

    @Test
    fun cancellingCompletedRequest() = runBlockingTest {
        val testScope = TestCoroutineScope()
        val tested = makeTested(testScope)
        val deferredUpdates = updatesAsync(tested)
        val deferredSignals = signalsAsync(tested)

        testScope.pauseDispatcher()

        tested.post { increment() }
        tested.post { sendCounter() }

        testScope.resumeDispatcher()

        tested.post { cancelSending() }
        tested.interrupt()

        val updates = deferredUpdates.await()
        val signals = deferredSignals.await()

        testScope.cleanupTestCoroutines()

        val expectedModels = listOf(
            Model.Counter(1),
            Model.Sender(1L),
            Model.Sender(null)
        )
        assertEquals(expectedModels, updates)
        assertEquals(listOf(Signal.CounterSent(1)), signals)
    }

    @Test(expected = IllegalStateException::class)
    fun duplicateInitialModels() = runBlockingTest {
        val models = listOf(Model.Counter(0), Model.Counter(1), Model.Sender())
        makeTested(this, empress = SampleEmpress(models))
    }

    @Test(expected = IllegalStateException::class)
    fun duplicateStoredModels() = runBlockingTest {
        val models = listOf(Model.Counter(0), Model.Counter(1), Model.Sender())
        makeTested(this, storedModels = models)
    }

    @Test
    fun initialHandlerId() = runBlockingTest {
        val tested = makeTested(this, initialHandlerId = 11)
        val deferredUpdates = updatesAsync(tested)
        tested.post { sendCounter() }
        tested.interrupt()
        val updates = deferredUpdates.await()
        val expected = listOf(
            Model.Sender(12),
            Model.Sender()
        )
        assertEquals(expected, updates)
    }

    @Test
    fun argumentIsEvaluatedEagerly() {
        val dispatcherA = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
        val dispatcherB = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
        val tested = makeTested(CoroutineScope(dispatcherA))

        runBlocking(dispatcherB) {
            val deferredUpdates = updatesAsync(tested)
            var d = 3
            tested.post { delta(d) }
            d = 7
            tested.post { increment() }
            tested.interrupt()

            val updates = deferredUpdates.await()
            assertEquals(listOf(Model.Counter(3), Model.Counter(4)), updates)
        }
    }

    @Test
    fun handlersAreLaunchedInOrder() {
        val clientDispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
        val dispatcherForTested = Executors.newFixedThreadPool(2).asCoroutineDispatcher()
        val tested = makeTested(CoroutineScope(dispatcherForTested))
        val n = 1_000

        runBlocking(clientDispatcher) {
            val deferredUpdates = updatesAsync(tested)
            for (i in 1..n) {
                tested.post { delta(i) }
            }
            tested.interrupt()

            val updates = deferredUpdates.await()
            assertEquals(n, updates.size)
            var sum = 0
            for ((i, m) in updates.withIndex()) {
                sum += i + 1
                assertEquals(Model.Counter(sum), m)
            }
        }
    }

    @Test
    fun delayedFirstObserver() = runBlockingTest {
        val tested = makeTested(this)
        tested.post { decrement() }
        val deferredUpdates = updatesAsync(tested)
        tested.post { decrement() }
        tested.interrupt()

        val updates = deferredUpdates.await()
        assertEquals(listOf(Model.Counter(-2)), updates)
    }

    @Test
    fun twoUpdateObservers() = runBlockingTest {
        val tested = makeTested(this)
        val deferredUpdates1 = updatesAsync(tested)
        val deferredUpdates2 = updatesAsync(tested)

        tested.post { decrement() }
        tested.post { decrement() }
        tested.interrupt()

        val updates1 = deferredUpdates1.await()
        val updates2 = deferredUpdates2.await()

        assertEquals(listOf(Model.Counter(-1), Model.Counter(-2)), updates1)
        assertEquals(updates1, updates2)
    }

    @Test
    fun delayedSecondObserver() = runBlockingTest {
        val tested = makeTested(this)
        val deferredUpdates1 = updatesAsync(tested)

        tested.post { decrement() }

        val deferredUpdates2 = updatesAsync(tested)

        tested.post { decrement() }
        tested.interrupt()

        val updates1 = deferredUpdates1.await()
        val updates2 = deferredUpdates2.await()

        assertEquals(listOf(Model.Counter(-1), Model.Counter(-2)), updates1)
        assertEquals(listOf(Model.Counter(-2)), updates2)
    }

    @Test
    fun cancellingParentJob() {
        val job = Job()
        val scope = CoroutineScope(Dispatchers.Unconfined + job)
        val tested = makeTested(scope)

        try {
            runBlockingTest {
                val deferredUpdates =
                    async(start = CoroutineStart.UNDISPATCHED) { tested.updates().toList() }
                launch {
                    tested.post { increment() }
                    tested.post { sendCounter() }
                    delay(10)
                    job.cancel()
                }
                deferredUpdates.await()
            }
        } finally {
            assertTrue((tested as EmpressBackend).areChannelsClosedForSend())
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
    fun indirectActions() = runBlockingTest {
        val tested = makeTested(this)
        val deferredSignals = signalsAsync(tested)
        val deferredUpdates = updatesAsync(tested)

        tested.post { indirectIncrementAndSend() }
        tested.interrupt()

        val signals = deferredSignals.await()
        val updates = deferredUpdates.await()

        val expectedSignals = listOf(Signal.CounterSent(1))
        assertEquals(expectedSignals, signals)

        val expectedUpdates = listOf(
            Model.Counter(1),
            Model.Sender(1),
            Model.Sender(null)
        )
        assertEquals(expectedUpdates, updates)
    }

    @Test
    fun cancellingIndirectRequest() = runBlockingTest {
        val tested = makeTested(this)
        val deferredUpdates = updatesAsync(tested)
        val deferredSignals = signalsAsync(tested)

        pauseDispatcher()
        tested.post { increment() }
        tested.post { indirectSend() }
        advanceTimeBy(500L)
        tested.post { cancelSending() }
        tested.interrupt()
        resumeDispatcher()

        val updates = deferredUpdates.await()
        val signals = deferredSignals.await()

        assertEquals(listOf(Signal.SendingCancelled), signals)

        val expectedModels = listOf(
            Model.Counter(1),
            Model.Sender(1L),
            Model.Sender(null)
        )
        assertEquals(expectedModels, updates)
    }

    private fun makeTested(
        coroutineScope: CoroutineScope,
        empress: SampleEmpress = SampleEmpress(),
        storedModels: Collection<Model>? = null,
        initialHandlerId: Long? = null
    ): TestEmpressApi<SampleEmpress, Model, Signal> {
        return if (storedModels != null && initialHandlerId != null) {
            EmpressBackend(
                empress,
                coroutineScope,
                coroutineScope,
                storedModels,
                initialHandlerId
            )
        } else if (storedModels != null) {
            EmpressBackend(
                empress,
                coroutineScope,
                coroutineScope,
                storedModels
            )
        } else if (initialHandlerId != null) {
            EmpressBackend(
                empress,
                coroutineScope,
                coroutineScope,
                initialRequestId = initialHandlerId
            )
        } else {
            EmpressBackend(empress, coroutineScope, coroutineScope)
        }
    }

    private fun <S : Any> CoroutineScope.signalsAsync(api: EmpressApi<*, *, S>): Deferred<List<S>> {
        return async(start = CoroutineStart.UNDISPATCHED) {
            api.signals().toList()
        }
    }

    private fun <M : Any> CoroutineScope.updatesAsync(
        api: EmpressApi<*, M, *>,
        withCurrentModels: Boolean = false
    ): Deferred<List<M>> {
        return async(start = CoroutineStart.UNDISPATCHED) {
            api.updates(withCurrentModels).toList()
        }
    }
}
