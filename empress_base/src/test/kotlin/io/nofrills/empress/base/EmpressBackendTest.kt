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
import kotlinx.coroutines.flow.toCollection
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
        val deferredUpdates = counterAsync(tested)
        tested.post { increment() }
        tested.post { increment() }
        tested.post { delta(2) }
        tested.post { decrement() }
        tested.interrupt()

        val updates = deferredUpdates.cancelChildrenAndAwait()

        val expected = listOf<Model>(
            Model.Counter(0),
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
        val deferredCounters = counterAsync(tested)
        val deferredSenders = senderAsync(tested)
        tested.post { increment() }
        tested.interrupt()

        val counters = deferredCounters.cancelChildrenAndAwait()
        val senders = deferredSenders.cancelChildrenAndAwait()

        assertEquals(listOf(Model.Counter(0), Model.Counter(1)), counters)
        assertEquals(listOf(Model.Sender()), senders)
    }

    @Test
    fun signals() = runBlockingTest {
        val tested = makeTested(this)
        val deferredCounters = counterAsync(tested)
        val deferredSenders = senderAsync(tested)
        val deferredSignals = signalsAsync(tested)

        tested.post { increment() }
        tested.post { sendCounter() }
        tested.interrupt()

        val counters = deferredCounters.cancelChildrenAndAwait()
        val senders = deferredSenders.cancelChildrenAndAwait()
        val signals = deferredSignals.await()

        assertEquals(listOf(Model.Counter(0), Model.Counter(1)), counters)
        assertEquals(listOf(Model.Sender(), Model.Sender(RequestId(1L)), Model.Sender(null)), senders)
        assertEquals(listOf(Signal.CounterSent(1)), signals)
    }

    @Test
    fun cancellingRequest() = runBlockingTest {
        val tested = makeTested(this)
        val deferredCounters = counterAsync(tested)
        val deferredSenders = senderAsync(tested)
        val deferredSignals = signalsAsync(tested)

        pauseDispatcher()
        tested.post { increment() }
        tested.post { sendCounter() }
        advanceTimeBy(500L)
        tested.post { cancelSending() }
        tested.interrupt()
        resumeDispatcher()

        val counters = deferredCounters.cancelChildrenAndAwait()
        val senders = deferredSenders.cancelChildrenAndAwait()
        val signals = deferredSignals.await()

        assertEquals(listOf(Model.Counter(0), Model.Counter(1)), counters)
        assertEquals(listOf(Model.Sender(), Model.Sender(RequestId(1L)), Model.Sender()), senders)
        assertEquals(listOf(Signal.SendingCancelled), signals)
    }

    @Test
    fun cancellingCompletedRequest() = runBlockingTest {
        val testScope = TestCoroutineScope()
        val tested = makeTested(testScope)
        val deferredCounters = counterAsync(tested)
        val deferredSenders = senderAsync(tested)
        val deferredSignals = signalsAsync(tested)

        testScope.pauseDispatcher()

        tested.post { increment() }
        tested.post { sendCounter() }

        testScope.resumeDispatcher()

        tested.post { cancelSending() }
        tested.interrupt()

        val counters = deferredCounters.cancelChildrenAndAwait()
        val senders = deferredSenders.cancelChildrenAndAwait()
        val signals = deferredSignals.await()

        testScope.cleanupTestCoroutines()

        assertEquals(listOf(Model.Counter(0), Model.Counter(1)), counters)
        assertEquals(listOf(Model.Sender(), Model.Sender(RequestId(1L)), Model.Sender()), senders)
        assertEquals(listOf(Signal.CounterSent(1)), signals)
    }

    @Test(expected = IllegalStateException::class)
    fun duplicateInitialModels() {
        DuplicateModelEmpress()
    }

    @Test(expected = IllegalStateException::class)
    fun duplicateStoredModels() = runBlockingTest {
        val models = listOf(Model.Counter(0), Model.Counter(1), Model.Sender())
        makeTested(this, storedModels = models)
    }

    @Test
    fun initialRequestId() = runBlockingTest {
        val tested = makeTested(this, initialRequestId = 11)
        val deferredSenders = senderAsync(tested)
        tested.post { sendCounter() }
        tested.interrupt()
        val senders = deferredSenders.cancelChildrenAndAwait()
        val expected = listOf(
            Model.Sender(),
            Model.Sender(RequestId(12)),
            Model.Sender()
        )
        assertEquals(expected, senders)
    }

    @Test
    fun argumentIsEvaluatedEagerly() = runBlockingTest {
        val tested = makeTested(this)

        val deferredCounters = counterAsync(tested)
        var d = 3
        tested.post { delta(d) }
        d = 7
        yield()
        tested.post { increment() }
        yield()
        tested.interrupt()
        yield()

        val updates = deferredCounters.cancelChildrenAndAwait()
        assertEquals(listOf(Model.Counter(0), Model.Counter(3), Model.Counter(4)), updates)
    }

    @Test
    fun eventHandlersAreLaunchedInOrder() {
        val clientDispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
        val dispatcherForTested = Executors.newFixedThreadPool(2).asCoroutineDispatcher()
        val tested = makeTested(CoroutineScope(dispatcherForTested))
        val n = 1_000

        runBlocking(clientDispatcher) {
            val deferredCounters = counterAsync(tested)
            for (i in 1..n) {
                tested.post { increment() }
                yield()
            }
            tested.interrupt()

            val counters = deferredCounters.cancelChildrenAndAwait()

            var lastValue = -1
            for (counter in counters) {
                assertTrue(counter.count > lastValue)
                lastValue = counter.count
            }
        }
    }

    @Test
    fun delayedFirstObserver() = runBlockingTest {
        val tested = makeTested(this)
        tested.post { decrement() }
        val deferredUpdates = counterAsync(tested)
        tested.post { decrement() }
        tested.interrupt()

        val updates = deferredUpdates.cancelChildrenAndAwait()
        assertEquals(listOf(Model.Counter(-1), Model.Counter(-2)), updates)
    }

    @Test
    fun twoUpdateObservers() = runBlockingTest {
        val tested = makeTested(this)
        val deferredUpdates1 = counterAsync(tested)
        val deferredUpdates2 = counterAsync(tested)

        tested.post { decrement() }
        tested.post { decrement() }
        tested.interrupt()

        val updates1 = deferredUpdates1.cancelChildrenAndAwait()
        val updates2 = deferredUpdates2.cancelChildrenAndAwait()

        assertEquals(listOf(Model.Counter(0), Model.Counter(-1), Model.Counter(-2)), updates1)
        assertEquals(updates1, updates2)
    }

    @Test
    fun delayedSecondObserver() = runBlockingTest {
        val tested = makeTested(this)
        val deferredUpdates1 = counterAsync(tested)

        tested.post { decrement() }

        val deferredUpdates2 = counterAsync(tested)

        tested.post { decrement() }
        tested.interrupt()

        val updates1 = deferredUpdates1.cancelChildrenAndAwait()
        val updates2 = deferredUpdates2.cancelChildrenAndAwait()

        assertEquals(listOf(Model.Counter(0), Model.Counter(-1), Model.Counter(-2)), updates1)
        assertEquals(listOf(Model.Counter(-1), Model.Counter(-2)), updates2)
    }

    @Test
    fun cancellingParentJob() {
        val job = Job()
        val scope = CoroutineScope(Dispatchers.Unconfined + job)
        val tested = makeTested(scope)

        try {
            runBlockingTest {
                val deferredUpdates = senderAsync(tested)
                launch {
                    tested.post { increment() }
                    tested.post { sendCounter() }
                    delay(10)
                    job.cancel()
                }
                deferredUpdates.cancelChildrenAndAwait()
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
        val deferredCounters = counterAsync(tested)
        val deferredSenders = senderAsync(tested)

        tested.post { indirectIncrementAndSend() }
        tested.interrupt()

        val signals = deferredSignals.await()
        val counters = deferredCounters.cancelChildrenAndAwait()
        val senders = deferredSenders.cancelChildrenAndAwait()

        val expectedSignals = listOf(Signal.CounterSent(1))
        assertEquals(expectedSignals, signals)

        assertEquals(listOf(Model.Counter(0), Model.Counter(1)), counters)
        assertEquals(listOf(Model.Sender(), Model.Sender(RequestId(1)), Model.Sender()), senders)
    }

    @Test
    fun cancellingIndirectRequest() = runBlockingTest {
        val tested = makeTested(this)
        val deferredCounters = counterAsync(tested)
        val deferredSenders = senderAsync(tested)
        val deferredSignals = signalsAsync(tested)

        pauseDispatcher()
        tested.post { increment() }
        tested.post { indirectSend() }
        advanceTimeBy(500L)
        tested.post { cancelSending() }
        tested.interrupt()
        resumeDispatcher()

        val counters = deferredCounters.cancelChildrenAndAwait()
        val senders = deferredSenders.cancelChildrenAndAwait()
        val signals = deferredSignals.await()

        assertEquals(listOf(Signal.SendingCancelled), signals)

        assertEquals(listOf(Model.Counter(0), Model.Counter(1)), counters)
        assertEquals(listOf(Model.Sender(), Model.Sender(RequestId(1L)), Model.Sender()), senders)
    }

    @Test
    fun requestArgumentsAreEvaluatedEagerly() = runBlockingTest {
        val tested = makeTested(this)
        val deferredSenders = senderAsync(tested)
        val deferredSignals = signalsAsync(tested)

        tested.post { sendCounterVariableCount() }
        tested.interrupt()

        val senders = deferredSenders.cancelChildrenAndAwait()
        val signals = deferredSignals.await()

        assertEquals(listOf(Signal.CounterSent(0)), signals)

        val expectedSenders = listOf(
            Model.Sender(),
            Model.Sender(RequestId(1L)),
            Model.Sender()
        )
        assertEquals(expectedSenders, senders)
    }

    @Test(expected = OnEventError::class)
    fun eventHandlerError() = runBlockingTest {
        val tested = makeTested(this)
        tested.post { generateError() }
    }

    @Test(expected = OnEventError::class)
    fun eventHandlerIndirectError() = runBlockingTest {
        val tested = makeTested(this)
        tested.post { generateErrorIndirect() }
    }

    @Test(expected = OnRequestError::class)
    fun requestHandlerError() = runBlockingTest {
        val tested = makeTested(this)
        val deferredUpdates = counterAsync(tested)
        tested.post { errorInRequest() }
        tested.interrupt()
        val updates = deferredUpdates.cancelChildrenAndAwait()
        val expected = listOf(Model.Counter(0), Model.Counter(1), Model.Counter(2))
        assertEquals(expected, updates)
    }

    @Test(expected = OnRequestError::class)
    fun requestHandlerErrorIndirect() = runBlockingTest {
        val tested = makeTested(this)
        val deferredUpdates = counterAsync(tested)
        tested.post { errorInRequestIndirect() }
        tested.interrupt()
        deferredUpdates.cancelChildrenAndAwait()
    }

    private fun makeTested(
        coroutineScope: CoroutineScope,
        empress: SampleEmpress = SampleEmpress(),
        storedModels: Collection<Model>? = null,
        initialRequestId: Long? = null
    ): TestEmpressApi<SampleEmpress, Model, Signal> {
        return if (storedModels != null && initialRequestId != null) {
            EmpressBackend(
                empress,
                coroutineScope,
                coroutineScope,
                storedModels,
                initialRequestId
            )
        } else if (storedModels != null) {
            EmpressBackend(
                empress,
                coroutineScope,
                coroutineScope,
                storedModels
            )
        } else if (initialRequestId != null) {
            EmpressBackend(
                empress,
                coroutineScope,
                coroutineScope,
                initialRequestId = initialRequestId
            )
        } else {
            EmpressBackend(empress, coroutineScope, coroutineScope)
        }
    }

    private fun CoroutineScope.counterAsync(api: EmpressApi<SampleEmpress, Model, Signal>): Deferred<List<Model.Counter>> {
        return async(start = CoroutineStart.UNDISPATCHED) {
            val list = mutableListOf<Model.Counter>()
            launch(start = CoroutineStart.UNDISPATCHED) { api.listen { counter }.toCollection(list) }
            list
        }
    }

    private fun CoroutineScope.senderAsync(api: EmpressApi<SampleEmpress, Model, Signal>): Deferred<List<Model.Sender>> {
        return async(start = CoroutineStart.UNDISPATCHED) {
            val list = mutableListOf<Model.Sender>()
            launch(start = CoroutineStart.UNDISPATCHED) { api.listen { sender }.toCollection(list) }
            list
        }
    }

    private fun CoroutineScope.signalsAsync(api: EmpressApi<SampleEmpress, Model, Signal>): Deferred<List<Signal>> {
        return async(start = CoroutineStart.UNDISPATCHED) {
            api.signals().toList()
        }
    }

    private suspend fun <T> Deferred<T>.cancelChildrenAndAwait(): T {
        cancelChildren()
        return await()
    }
}
