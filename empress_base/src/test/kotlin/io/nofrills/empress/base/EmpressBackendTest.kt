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
        tested.post { increment() }
        tested.post { increment() }
        tested.post { delta(2) }
        tested.post { decrement() }
        tested.interrupt()
        val counter = tested.model { counter }.value
        assertEquals(Model.Counter(3), counter)
        assertEquals(
            setOf(Model.Counter(3)),
            tested.loadedModels().values.toSet()
        )
    }

    @Test
    fun updatesWithInitialModels() = runBlockingTest {
        val tested = makeTested(this)
        tested.post { increment() }
        tested.interrupt()

        val counter = tested.model { counter }.value
        val sender = tested.model { sender }.value

        assertEquals(Model.Counter(1), counter)
        assertEquals(Model.Sender.Idle, sender)
    }

    @Test
    fun signals() = runBlockingTest {
        val tested = makeTested(this)
        val deferredSignals = signalsAsync(tested)

        tested.post { increment() }
        tested.post { sendCounter() }
        tested.interrupt()

        val counter = tested.model { counter }.value
        val sender = tested.model { sender }.value
        val signals = deferredSignals.await()

        assertEquals(Model.Counter(1), counter)
        assertEquals(Model.Sender.Idle, sender)
        assertEquals(listOf(CounterSignal.CounterSent(1)), signals)
    }

    @Test
    fun cancellingRequest() = runBlockingTest {
        val tested = makeTested(this)
        val deferredSignals = signalsAsync(tested)

        pauseDispatcher()
        tested.post { increment() }
        tested.post { sendCounter() }
        advanceTimeBy(500L)
        tested.post { cancelSending() }
        tested.interrupt()
        resumeDispatcher()

        val counter = tested.model { counter }.value
        val sender = tested.model { sender }.value
        val signals = deferredSignals.await()

        assertEquals(Model.Counter(1), counter)
        assertEquals(Model.Sender.Idle, sender)
        assertEquals(listOf(CounterSignal.SendingCancelled), signals)
    }

    @Test
    fun cancellingCompletedRequest() = runBlockingTest {
        val testScope = TestCoroutineScope()
        val tested = makeTested(testScope)
        val deferredSignals = signalsAsync(tested)

        testScope.pauseDispatcher()

        tested.post { increment() }
        tested.post { sendCounter() }

        testScope.resumeDispatcher()

        tested.post { cancelSending() }
        tested.interrupt()

        val counter = tested.model { counter }.value
        val sender = tested.model { sender }.value
        val signals = deferredSignals.await()

        testScope.cleanupTestCoroutines()

        assertEquals(Model.Counter(1), counter)
        assertEquals(Model.Sender.Idle, sender)
        assertEquals(listOf(CounterSignal.CounterSent(1)), signals)
    }

    @Test
    fun duplicateInitialModels() = runBlockingTest {
        val tested = EmpressBackend(DuplicateModelEmpress(), this, this)
        tested.post { evaluateCounters() }
        tested.interrupt()

        val expectedModels = setOf(Model.Counter(5), Model.Counter(3))
        val actualModels = tested.loadedModels().values.toSet()
        assertEquals(expectedModels, actualModels)
    }

    @Test
    fun duplicateStoredModels() = runBlockingTest {
        val models = mapOf(
            "c1" to Model.Counter(0),
            "c2" to Model.Counter(1),
            "s" to Model.Sender.Idle
        )
        makeTested(this, storedModels = models).interrupt()
    }

    @Test
    fun initialRequestId() = runBlockingTest {
        val tested = makeTested(this, initialRequestId = 11)
        tested.post { sendCounter() }
        tested.interrupt()
        val sender = tested.model { sender }.value
        assertEquals(Model.Sender.Idle, sender)
    }

    @Test
    fun argumentIsEvaluatedEagerly() {
        val dispatcherA = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
        val dispatcherB = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
        val tested = makeTested(CoroutineScope(dispatcherA))

        runBlocking(dispatcherB) {
            var d = 3
            tested.post { delta(d) }
            d = 7
            tested.post { increment() }
            tested.interrupt()
            val counter = tested.model { counter }.value
            assertEquals(Model.Counter(4), counter)
        }
    }

    @Test
    fun eventHandlersAreLaunchedInOrder() {
        val clientDispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
        val dispatcherForTested = Executors.newFixedThreadPool(2).asCoroutineDispatcher()
        val tested = makeTested(CoroutineScope(dispatcherForTested))
        val n = 1_000

        runBlocking(clientDispatcher) {
            val b = StringBuilder()
            for (i in 1..n) {
                tested.post { append("$i") }
                b.append("$i")
            }
            tested.interrupt()

            val data = tested.model { data }.value
            assertEquals(b.toString(), data.text)
        }
    }

    @Test
    fun delayedFirstObserver() = runBlockingTest {
        val tested = makeTested(this)
        tested.post { decrement() }
        tested.post { decrement() }
        tested.interrupt()

        val counter = tested.model { counter }.value
        assertEquals(Model.Counter(-2), counter)
    }

    @Test
    fun twoUpdateObservers() = runBlockingTest {
        val tested = makeTested(this)
        val deferredSignals1 = signalsAsync(tested)
        val deferredSignals2 = signalsAsync(tested)

        tested.post { increment() }
        tested.post { sendCounter() }
        tested.interrupt()

        val updates1 = deferredSignals1.await()
        val updates2 = deferredSignals2.await()

        val expected = listOf(CounterSignal.CounterSent(1))
        assertEquals(expected, updates1)
        assertEquals(updates1, updates2)
    }

    @Test
    fun delayedSecondObserver() = runBlockingTest {
        val tested = makeTested(this)
        val deferredSignals1 = signalsAsync(tested)

        tested.post { increment() }
        tested.post { sendCounter() }
        tested.post { increment() }

        val dl = async {
            pauseDispatcher()
            advanceUntilIdle()

            val deferredSignals2 = signalsAsync(tested)

            resumeDispatcher()
            tested.post { sendCounter(skipIfLoading = false) }
            tested.interrupt()
            deferredSignals2.await()
        }

        val updates1 = deferredSignals1.await()
        val updates2 = dl.await()

        assertEquals(listOf(CounterSignal.CounterSent(1), CounterSignal.CounterSent(2)), updates1)
        assertEquals(listOf(CounterSignal.CounterSent(2)), updates2)
    }

    @Test
    fun cancellingParentJob() {
        val job = Job()
        val scope = CoroutineScope(Dispatchers.Unconfined + job)
        val tested = makeTested(scope)

        try {
            runBlockingTest {
                val deferredSignals = signalsAsync(tested)
                launch {
                    tested.post { increment() }
                    tested.post { sendCounter() }
                    delay(10)
                    job.cancel()
                }
                deferredSignals.await()
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

        tested.post { indirectIncrementAndSend() }
        tested.interrupt()

        val signals = deferredSignals.await()
        val counter = tested.model { counter }.value
        val sender = tested.model { sender }.value

        val expectedSignals = listOf(CounterSignal.CounterSent(1))
        assertEquals(expectedSignals, signals)

        assertEquals(Model.Counter(1), counter)
        assertEquals(Model.Sender.Idle, sender)
    }

    @Test
    fun cancellingIndirectRequest() = runBlockingTest {
        val tested = makeTested(this)
        val deferredSignals = signalsAsync(tested)

        pauseDispatcher()
        tested.post { increment() }
        tested.post { indirectSend() }
        advanceTimeBy(500L)
        tested.post { cancelSending() }
        tested.interrupt()
        resumeDispatcher()

        val counter = tested.model { counter }.value
        val sender = tested.model { sender }.value
        val signals = deferredSignals.await()

        assertEquals(listOf(CounterSignal.SendingCancelled), signals)

        assertEquals(Model.Counter(1), counter)
        assertEquals(Model.Sender.Idle, sender)
    }

    @Test
    fun requestArgumentsAreEvaluatedEagerly() = runBlockingTest {
        val tested = makeTested(this)
        val deferredSignals = signalsAsync(tested)

        tested.post { sendCounterVariableCount() }
        tested.interrupt()

        val sender = tested.model { sender }.value
        val signals = deferredSignals.await()

        assertEquals(listOf(CounterSignal.CounterSent(0)), signals)
        assertEquals(Model.Sender.Idle, sender)
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
        tested.post { errorInRequest() }
        tested.interrupt()
        val counter = tested.model { counter }.value
        assertEquals(Model.Counter(2), counter)
    }

    @Test(expected = OnRequestError::class)
    fun requestHandlerErrorIndirect() = runBlockingTest {
        val tested = makeTested(this)
        tested.post { errorInRequestIndirect() }
        tested.interrupt()
    }

    private fun makeTested(
        coroutineScope: CoroutineScope,
        empress: SampleEmpress = SampleEmpress(),
        storedModels: Map<String, Model>? = null,
        initialRequestId: Long? = null
    ): TestEmpressApi<SampleEmpress> {
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

    private fun CoroutineScope.signalsAsync(api: EmpressApi<SampleEmpress>): Deferred<List<CounterSignal>> {
        return async(start = CoroutineStart.UNDISPATCHED) {
            api.signal { counterSignal }.toList()
        }
    }
}
