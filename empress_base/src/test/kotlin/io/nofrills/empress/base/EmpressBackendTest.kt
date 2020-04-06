package io.nofrills.empress.base

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.TestCoroutineScope
import kotlinx.coroutines.test.runBlockingTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

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
    fun signaling() = scope.runBlockingTest {
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
    fun cancelHandler() = scope.runBlockingTest {
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
        val scopeA = TestCoroutineScope()
        val scopeB = TestCoroutineScope()
        tested = EmpressBackend(SampleEmpress(), scopeA)

        scopeB.runBlockingTest {
            val deferredUpdates = async { tested.updates().toList() }
            var d = 1
            scopeA.pauseDispatcher()
            tested.post { delta(d) }
            d = 3
            tested.post { increment() }
            tested.interrupt()
            scopeA.advanceUntilIdle()
            scopeA.cleanupTestCoroutines()

            val updates = deferredUpdates.await()
            assertEquals(listOf(Model.Counter(1), Model.Counter(2)), updates)
        }
    }
}

sealed class Model {
    data class Counter(val count: Int) : Model()
    data class Sender(val handlerId: HandlerId? = null) : Model()
}

sealed class Signal {
    object CounterSent : Signal()
    object SendingCancelled : Signal()
}

class SampleEmpress(private val models: Collection<Model>? = null) : Empress<Model, Signal>() {
    override fun initialModels(): Collection<Model> {
        return models ?: listOf(Model.Counter(0), Model.Sender())
    }

    fun decrement() {
        val count = get<Model.Counter>().count
        queueUpdate(Model.Counter(count - 1))
    }

    suspend fun increment() {
        val count = get<Model.Counter>().count
        update(Model.Counter(count + 1))
    }

    suspend fun delta(d: Int) {
        delay(10)
        val count = get<Model.Counter>().count
        update(Model.Counter(count + d))
    }

    suspend fun sendCounter() {
        if (get<Model.Sender>().handlerId != null) return

        update(Model.Sender(handlerId()))
        val count = get<Model.Counter>().count
        delay(count * 1000L)
        signal(Signal.CounterSent)
        update(Model.Sender(null))
    }

    fun cancelSending() {
        val handlerId = get<Model.Sender>().handlerId ?: return
        cancelHandler(handlerId)
        queueUpdate(Model.Sender(null))
        queueSignal(Signal.SendingCancelled)
    }
}