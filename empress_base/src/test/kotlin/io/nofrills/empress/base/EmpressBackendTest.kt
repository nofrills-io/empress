package io.nofrills.empress.base

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.TestCoroutineScope
import kotlinx.coroutines.test.runBlockingTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.util.concurrent.Executors

@OptIn(ExperimentalCoroutinesApi::class)
class EmpressBackendTest {
    private lateinit var scope: TestCoroutineScope
    private lateinit var tested: EmpressApi<SampleEmpress, Model, Signal>

    @Before
    fun setUp() {
        scope = TestCoroutineScope()
        tested = EmpressBackend(SampleEmpress(), scope)
    }

    @After
    fun tearDown() {
        scope.cleanupTestCoroutines()
    }

    @Test
    fun noModelUpdatesUpdates() = scope.runBlockingTest {
        tested.post { ping() }
    }

    @Test
    fun modelUpdates() = scope.runBlockingTest {
        val deferredUpdates = async { tested.updates().toList() }
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
    fun signals() = scope.runBlockingTest {
        val deferredUpdates = async { tested.updates().toList() }
        val deferredSignals = async { tested.signals().toList() }

        tested.post { increment() }
        tested.post { sendCounter() }
        tested.interrupt()

        val updates = deferredUpdates.await()
        val signals = deferredSignals.await()

        val expectedUpdates = listOf(
            Model.Counter(1),
            Model.Sender(HandlerId(2L)),
            Model.Sender(null)
        )
        assertEquals(expectedUpdates, updates)
        assertEquals(listOf(Signal.CounterSent), signals)
    }

    @Test
    fun cancellingHandler() = scope.runBlockingTest {
        val deferredUpdates = async { tested.updates().toList() }
        val deferredSignals = async { tested.signals().toList() }

        pauseDispatcher()
        tested.post { increment() }
        tested.post { sendCounter() }
        advanceTimeBy(500L)
        tested.post { cancelSending() }
        resumeDispatcher()
        tested.interrupt()

        val updates = deferredUpdates.await()
        val signals = deferredSignals.await()

        val expectedModels = listOf(
            Model.Counter(1),
            Model.Sender(HandlerId(2L)),
            Model.Sender(null)
        )
        assertEquals(expectedModels, updates)
        assertEquals(listOf(Signal.SendingCancelled), signals)
    }

    @Test
    fun cancellingCompletedHandler() = scope.runBlockingTest {
        val deferredUpdates = async { tested.updates().toList() }
        val deferredSignals = async { tested.signals().toList() }

        pauseDispatcher()
        tested.post { increment() }
        tested.post { sendCounter() }
        resumeDispatcher()
        tested.post { cancelSending() }
        tested.interrupt()

        val updates = deferredUpdates.await()
        val signals = deferredSignals.await()

        val expectedModels = listOf(
            Model.Counter(1),
            Model.Sender(HandlerId(2L)),
            Model.Sender(null)
        )
        assertEquals(expectedModels, updates)
        assertEquals(listOf(Signal.CounterSent), signals)
    }

    @Test(expected = IllegalStateException::class)
    fun duplicateInitialModels() {
        val models = listOf(Model.Counter(0), Model.Counter(1), Model.Sender())
        EmpressBackend(SampleEmpress(models), scope)
    }

    @Test(expected = IllegalStateException::class)
    fun duplicateStoredModels() {
        val models = listOf(Model.Counter(0), Model.Counter(1), Model.Sender())
        EmpressBackend(SampleEmpress(), scope, storedModels = models)
    }

    @Test
    fun initialHandlerId() = scope.runBlockingTest {
        tested = EmpressBackend(SampleEmpress(), scope, initialHandlerId = 11L)
        val deferredUpdates = async { tested.updates().toList() }
        tested.post { sendCounter() }
        tested.interrupt()
        val updates = deferredUpdates.await()
        val expected = listOf(
            Model.Sender(HandlerId(12L)),
            Model.Sender()
        )
        assertEquals(expected, updates)
    }

    @Test
    fun argumentIsEvaluatedEagerly() {
        val dispatcherA = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
        val dispatcherB = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
        tested = EmpressBackend(SampleEmpress(), CoroutineScope(dispatcherA))

        runBlocking(dispatcherB) {
            val deferredUpdates = async { tested.updates().toList() }
            coroutineScope {
                launch {
                    var d = 1
                    tested.post { delta(d, withDelay = true) }
                    d = 3
                }
            }
            tested.post { increment() }
            tested.interrupt()

            val updates = deferredUpdates.await()
            assertEquals(listOf(Model.Counter(1), Model.Counter(2)), updates)
        }
    }

    @Test
    fun handlersAreLaunchedInOrder() {
        val dispatcherA = Executors.newFixedThreadPool(2).asCoroutineDispatcher()
        val dispatcherB = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
        tested = EmpressBackend(SampleEmpress(), CoroutineScope(dispatcherA))

        runBlocking(dispatcherB) {
            val deferredUpdates = async { tested.updates().toList() }
            coroutineScope {
                for (i in 0..1_000) {
                    launch { tested.post { delta(i, withAfterDelay = true) } }
                }
            }
            tested.interrupt()

            val updates = deferredUpdates.await()
            var sum = 0
            for ((i, m) in updates.withIndex()) {
                sum += i
                assertEquals(Model.Counter(sum), m)
            }
        }
    }

    @Test
    fun delayedFirstObserver() = scope.runBlockingTest {
        tested.post { decrement() }
        val deferredUpdates = async { tested.updates().toList() }
        tested.post { decrement() }
        tested.interrupt()

        val updates = deferredUpdates.await()
        assertEquals(listOf(Model.Counter(-2)), updates)
    }

    @Test
    fun twoUpdateObservers() = scope.runBlockingTest {
        val deferredUpdates1 = async { tested.updates().toList() }
        val deferredUpdates2 = async { tested.updates().toList() }

        tested.post { decrement() }
        tested.post { decrement() }
        tested.interrupt()

        val updates1 = deferredUpdates1.await()
        val updates2 = deferredUpdates2.await()

        assertEquals(listOf(Model.Counter(-1), Model.Counter(-2)), updates1)
        assertEquals(updates1, updates2)
    }

    @Test
    fun delayedSecondObserver() = scope.runBlockingTest {
        val deferredUpdates1 = async { tested.updates().toList() }

        tested.post { decrement() }

        val deferredUpdates2 = async { tested.updates().toList() }

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
        tested = EmpressBackend(SampleEmpress(), scope)

        try {
            runBlockingTest {
                val deferredUpdates = async { tested.updates().toList() }
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
}
